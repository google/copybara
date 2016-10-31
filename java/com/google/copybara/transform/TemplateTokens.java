/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara.transform;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.copybara.ValidationException;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A string which is interpolated with named variables. The string is composed of interpolated and
 * non-interpolated (literal) pieces called tokens.
 */
public final class TemplateTokens {
  private enum TokenType {
    LITERAL, INTERPOLATION
  }

  /**
   * Either a string literal or interpolated value.
   */
  private static final class Token {
    final String value;
    final TokenType type;

    Token(String value, TokenType type) {
      this.value = Preconditions.checkNotNull(value);
      this.type = Preconditions.checkNotNull(type);
    }
  }

  private final Location location;
  private final String template;
  private final Pattern before;
  private final Multimap<String, Integer> groupIndexes;
  private final ImmutableList<Token> tokens;
  private final Set<String> unusedGroups;

  public TemplateTokens(Location location, String template, Map<String, Pattern> regexGroups,
      boolean repeatedGroups) throws EvalException {
    this.location = location;
    this.template = Preconditions.checkNotNull(template);

    Builder builder = new Builder();
    builder.location = location;
    builder.parse(template);
    this.before = builder.buildBefore(regexGroups, repeatedGroups);
    this.groupIndexes = ArrayListMultimap.create(builder.groupIndexes);
    this.tokens = ImmutableList.copyOf(builder.tokens);
    this.unusedGroups = Sets.difference(regexGroups.keySet(), groupIndexes.keySet());
  }

  /**
   * How this template can be used when it is the "before" value of core.replace - as a regex to
   * search for.
   */
  Pattern getBefore() {
    return before;
  }

  public Replacer replacer(TemplateTokens after, boolean firstOnly, boolean multiline) {
    return new Replacer(after, null, firstOnly, multiline);
  }

  public Replacer callbackReplacer(
      TemplateTokens after, AlterAfterTemplate callback, boolean firstOnly, boolean multiline) {
    return new Replacer(after, callback, firstOnly, multiline);
  }

  public class Replacer {

    private final TemplateTokens after;
    private final boolean firstOnly;
    private final boolean multiline;
    private final String afterReplaceTemplate;
    private final Multimap<String, Integer> repeatedGroups = ArrayListMultimap.create();

    @Nullable
    private final AlterAfterTemplate callback;


    private Replacer(
        TemplateTokens after, AlterAfterTemplate callback, boolean firstOnly, boolean multiline) {
      this.after = after;
      afterReplaceTemplate = this.after.after(TemplateTokens.this);
      // Precomputed the repeated groups as this should be used only on rare occasions and we
      // don't want to iterate over the map for every line.
      for (Entry<String, Collection<Integer>> e : groupIndexes.asMap().entrySet()) {
        if (e.getValue().size() > 1) {
          repeatedGroups.putAll(e.getKey(), e.getValue());
        }
      }
      this.firstOnly = firstOnly;
      this.multiline = multiline;
      this.callback = callback;
    }

    public String replace(String content) {
      List<String> originalRanges = multiline
          ? ImmutableList.of(content)
          : Splitter.on('\n').splitToList(content);

      List<String> newRanges = new ArrayList<>(originalRanges.size());
      for (String line : originalRanges) {
        newRanges.add(replaceLine(line));
      }
      return Joiner.on('\n').join(newRanges);
    }

    private String replaceLine(String line) {
      Matcher matcher = before.matcher(line);
      StringBuffer sb = new StringBuffer();
      while (matcher.find()) {
        for (Collection<Integer> groupIndexes : repeatedGroups.asMap().values()) {
          // Check that all the references of the repeated group match the same string
          Iterator<Integer> iterator = groupIndexes.iterator();
          String value = matcher.group(iterator.next());
          while (iterator.hasNext()) {
            if (!value.equals(matcher.group(iterator.next()))) {
              return line;
            }
          }
        }
        String replaceTemplate;
        if (callback != null) {
          ImmutableMap.Builder<Integer, String> groupValues =
              ImmutableMap.<Integer, String>builder();
          for (int i = 0; i <= matcher.groupCount(); i++) {
            groupValues.put(i, matcher.group(i));
          }
          replaceTemplate = callback.alter(groupValues.build(), afterReplaceTemplate);
        } else {
          replaceTemplate = afterReplaceTemplate;
        }

        matcher.appendReplacement(sb, replaceTemplate);
        if (firstOnly) {
          break;
        }
      }
      matcher.appendTail(sb);
      return sb.toString();
    }

    @Override
    public String toString() {
      return String.format("s/%s/%s/%s", TemplateTokens.this, after, firstOnly ? "" : "g");
    }
  }

  /**
   * How this template can be used when it is the "after" value of core.replace - as a string to
   * insert in place of the regex, possibly including $N, referring to captured groups.
   *
   * <p>Returns a template in which the literals are escaped (if they are a $ or {) and the
   * interpolations appear as $N, where N is the group's index as given by {@code groupIndexes}.
   */
  String after(TemplateTokens before) {
    StringBuilder template = new StringBuilder();
    for (Token token : tokens) {
      switch (token.type) {
        case INTERPOLATION:
          template.append("$").append(before.groupIndexes.get(token.value).iterator().next());
          break;
        case LITERAL:
          for (int c = 0; c < token.value.length(); c++) {
            char thisChar = token.value.charAt(c);
            if (thisChar == '$' || thisChar == '\\') {
              template.append('\\');
            }
            template.append(thisChar);
          }
          break;
        default:
          throw new IllegalStateException(token.type.toString());
      }
    }
    return template.toString();
  }

  @Override
  public String toString() {
    return getTemplate();
  }

  public ImmutableList<String> getGroupNames()  {
    return ImmutableList.copyOf(groupIndexes.keySet());
  }

  public String getTemplate() {
    return template;
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof TemplateTokens) {
      TemplateTokens comp = (TemplateTokens) other;
      return before.equals(comp.before)
          && tokens.equals(comp.tokens);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(before, tokens);
  }

  private static class Builder {
    List<Token> tokens = new ArrayList<>();
    Multimap<String, Integer> groupIndexes = ArrayListMultimap.create();
    Location location;

    /**
     * Parses a template. In the raw string representation, interpolation is
     * done with {@code ${var_name}}. Literal dollar signs can be represented with {@code $$}.
     *
     * @throws EvalException if the template is malformed
     */
    void parse(String template) throws EvalException {
      StringBuilder currentLiteral = new StringBuilder();
      int c = 0;
      while (c < template.length()) {
        char thisChar = template.charAt(c);
        c++;
        if (thisChar != '$') {
          currentLiteral.append(thisChar);
          continue;
        }
        if (c >= template.length()) {
          throw new EvalException(location, "Expect $ or { after every $ in string: " + template);
        }
        thisChar = template.charAt(c);
        c++;
        switch (thisChar) {
          case '$':
            currentLiteral.append('$');
            break;
          case '{':
            tokens.add(new Token(currentLiteral.toString(), TokenType.LITERAL));
            currentLiteral = new StringBuilder();
            int terminating = template.indexOf('}', c);
            if (terminating == -1) {
              throw new EvalException(location, "Unterminated '${'. Expected '}': " + template);
            }
            if (c == terminating) {
              throw new EvalException(
                  location, "Expect non-empty interpolated value name: " + template);
            }
            tokens.add(
                new Token(template.substring(c, terminating), TokenType.INTERPOLATION));
            c = terminating + 1;
            break;
          default:
            throw new EvalException(location, "Expect $ or { after every $ in string: " + template);
        }
      }
      tokens.add(new Token(currentLiteral.toString(), TokenType.LITERAL));
    }

    /**
     * Converts this sequence of tokens into a regex which can be used to search a string. It
     * automatically quotes literals and represents interpolations as named groups.
     *
     * @param regexesByInterpolationName map from group name to the regex to interpolate when the
     * group is mentioned
     * @param repeatedGroups true if a regex group is allowed to be used multiple times
     */
    Pattern buildBefore(Map<String, Pattern> regexesByInterpolationName, boolean repeatedGroups)
        throws EvalException {
      int groupCount = 1;
      StringBuilder fullPattern = new StringBuilder();
      for (Token token : tokens) {
        switch (token.type) {
          case INTERPOLATION:
            Pattern subPattern = regexesByInterpolationName.get(token.value);
            if (subPattern == null) {
              throw new EvalException(
                  location, "Interpolation is used but not defined: " + token.value);
            }
            fullPattern.append(String.format("(%s)", subPattern.pattern()));
            if (groupIndexes.get(token.value).size() > 0 && !repeatedGroups) {
              throw new EvalException(
                  location, "Regex group is used in template multiple times: " + token.value);
            }
            groupIndexes.put(token.value, groupCount);
            groupCount += subPattern.groupCount() + 1;
            break;
          case LITERAL:
            fullPattern.append(Pattern.quote(token.value));
            break;
          default:
            throw new IllegalStateException(token.type.toString());
        }
      }
      return Pattern.compile(fullPattern.toString(), Pattern.MULTILINE);
    }
  }

  /**
   * Checks that the set of interpolated tokens matches {@code definedInterpolations}.
   *
   * @throws EvalException if not all interpolations are used in this template
   */
  public void validateUnused() throws EvalException {
    if (!unusedGroups.isEmpty()) {
      throw new EvalException(
          location, "Following interpolations are defined but not used: " + unusedGroups);
    }
  }

  /**
   * Callback for {@see callbackReplacer}.
   */
  public interface AlterAfterTemplate {

    /**
     *  Upon encountering a match, the replacer will call the callback with the matched groups and
     *  the template to be used in the replace. The return value of this function will be used
     *  in place of {@code template}, i.e. group tokens like '$1' in the return value will be
     *  replaced with the group values. Note that the groupValues are immutable.
     * @param groupValues The values of the groups in the before pattern. 0 holds the entire match.
     * @param template The replacement template the replacer would normally use
     * @return The template to be used instead
     */
    public String alter(Map<Integer, String> groupValues, String template);
  }
}
