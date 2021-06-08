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

import static com.google.copybara.config.SkylarkUtil.check;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.copybara.templatetoken.Parser;
import com.google.copybara.templatetoken.Token;
import com.google.copybara.templatetoken.Token.TokenType;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;
import net.starlark.java.eval.EvalException;
import net.starlark.java.syntax.Location;

/**
 * A string which is interpolated with named variables. The string is composed of interpolated and
 * non-interpolated (literal) pieces called tokens.
 */
public final class RegexTemplateTokens {

  private final String template;
  private final Pattern before;
  private final ArrayListMultimap<String, Integer> groupIndexes = ArrayListMultimap.create();
  private final ImmutableList<Token> tokens;
  private final Set<String> unusedGroups;
  private final Location location;

  public RegexTemplateTokens(
      String template, Map<String, Pattern> regexGroups, boolean repeatedGroups, Location location)
      throws EvalException {
    this(template, regexGroups, repeatedGroups, false, location);
  }

  public RegexTemplateTokens(
      String template,
      Map<String, Pattern> regexGroups,
      boolean repeatedGroups,
      boolean matchExactly,
      Location location)
      throws EvalException {
    this.template = Preconditions.checkNotNull(template);

    this.tokens = ImmutableList.copyOf(new Parser().parse(template));
    this.location = Preconditions.checkNotNull(location);
    this.before = buildBefore(regexGroups, repeatedGroups, matchExactly);

    this.unusedGroups = Sets.difference(regexGroups.keySet(), groupIndexes.keySet());
  }

  /**
   * How this template can be used when it is the "before" value of core.replace - as a regex to
   * search for.
   */
  public Pattern getBefore() {
    return before;
  }

  public ImmutableList<Token> getTokens() {
    return tokens;
  }

  public boolean isEmpty() {
    return template.isEmpty();
  }

  /** Is this template regex-free */
  public boolean isLiteral() {
    return this.isEmpty()
        || (this.tokens.size() == 1 && this.tokens.get(0).getType() == TokenType.LITERAL);
  }

  public ImmutableListMultimap<String, Integer> getGroupIndexes() {
    return ImmutableListMultimap.copyOf(groupIndexes);
  }

  public Replacer replacer(
      RegexTemplateTokens after, boolean firstOnly, boolean multiline,
      List<Pattern> patternsToIgnore) {
    // TODO(malcon): Remove reconstructing pattern once RE2J doesn't synchronize on matching.
    return new Replacer(Pattern.compile(before.pattern(), before.flags()), after, null, firstOnly,
                        multiline, patternsToIgnore, location);
  }

  public Replacer callbackReplacer(
      RegexTemplateTokens after, AlterAfterTemplate callback, boolean firstOnly,
      boolean multiline,
      @Nullable List<Pattern> patternsToIgnore) {
    return new Replacer(Pattern.compile(before.pattern()), after, callback, firstOnly, multiline,
                        patternsToIgnore, location);
  }

  public class Replacer {

    private final Pattern before;
    private final RegexTemplateTokens after;
    private final boolean firstOnly;
    private final boolean multiline;
    private final String afterReplaceTemplate;
    private final Multimap<String, Integer> repeatedGroups = ArrayListMultimap.create();
    private final Location location;

    @Nullable
    private final List<Pattern> patternsToIgnore;

    @Nullable
    private final AlterAfterTemplate callback;


    private Replacer(Pattern before, RegexTemplateTokens after,
        @Nullable AlterAfterTemplate callback,
        boolean firstOnly, boolean multiline, @Nullable List<Pattern> patternsToIgnore,
        Location location) {
      this.before = before;
      this.after = after;
      afterReplaceTemplate = this.after.after(RegexTemplateTokens.this);
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
      this.patternsToIgnore = patternsToIgnore;
      this.location = location;
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
      if (patternsToIgnore != null) {
        for (Pattern patternToIgnore : patternsToIgnore) {
          if (patternToIgnore.matches(line)) {
            return line;
          }
        }
      }

      Matcher matcher = before.matcher(line);
      StringBuilder sb = new StringBuilder(line.length());
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
              ImmutableMap.builder();
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
      return String.format("s/%s/%s/%s", RegexTemplateTokens.this, after, firstOnly ? "" : "g");
    }

    public Location getLocation() {
      return location;
    }

    public boolean isFirstOnly() {
      return firstOnly;
    }
  }

  /**
   * How this template can be used when it is the "after" value of core.replace - as a string to
   * insert in place of the regex, possibly including $N, referring to captured groups.
   *
   * <p>Returns a template in which the literals are escaped (if they are a $ or {) and the
   * interpolations appear as $N, where N is the group's index as given by {@code groupIndexes}.
   */
  private String after(RegexTemplateTokens before) {
    StringBuilder template = new StringBuilder();
    for (Token token : tokens) {
      switch (token.getType()) {
        case INTERPOLATION:
          template.append("$").append(before.groupIndexes.get(token.getValue()).iterator().next());
          break;
        case LITERAL:
          for (int c = 0; c < token.getValue().length(); c++) {
            char thisChar = token.getValue().charAt(c);
            if (thisChar == '$' || thisChar == '\\') {
              template.append('\\');
            }
            template.append(thisChar);
          }
          break;
      }
    }
    return template.toString();
  }

  @Override
  public String toString() {
    return template
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof RegexTemplateTokens) {
      RegexTemplateTokens comp = (RegexTemplateTokens) other;
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

  /**
   * Converts this sequence of tokens into a regex which can be used to search a string. It
   * automatically quotes literals and represents interpolations as named groups.
   *
   * <p>It also fills groupIndexes with all the interpolation locations.
   *
   * @param regexesByInterpolationName map from group name to the regex to interpolate when the
   *     group is mentioned
   * @param repeatedGroups true if a regex group is allowed to be used multiple times
   * @param matchExactly true if the regex must match the entire input string
   */
  private Pattern buildBefore(
      Map<String, Pattern> regexesByInterpolationName, boolean repeatedGroups, boolean matchExactly)
      throws EvalException {
    int groupCount = 1;
    StringBuilder fullPattern = new StringBuilder();
    if (matchExactly) {
      fullPattern.append("^");
    }
    for (Token token : tokens) {
      switch (token.getType()) {
        case INTERPOLATION:
          Pattern subPattern = regexesByInterpolationName.get(token.getValue());
          check(subPattern != null, "Interpolation is used but not defined: %s", token.getValue());
          fullPattern.append(String.format("(%s)", subPattern.pattern()));
          check(
              groupIndexes.get(token.getValue()).isEmpty() || repeatedGroups,
              "Regex group is used in template multiple times: %s",
              token.getValue());
          groupIndexes.put(token.getValue(), groupCount);
          groupCount += subPattern.groupCount() + 1;
          break;
        case LITERAL:
          fullPattern.append(Pattern.quote(token.getValue()));
          break;
      }
    }
    if (matchExactly) {
      fullPattern.append("$");
    }
    return Pattern.compile(fullPattern.toString(), Pattern.MULTILINE);
  }

  /**
   * Checks that the set of interpolated tokens matches {@code definedInterpolations}.
   *
   * @throws EvalException if not all interpolations are used in this template
   */
  public void validateUnused() throws EvalException {
    check(
        unusedGroups.isEmpty(),
        "Following interpolations are defined but not used: %s",
        unusedGroups);
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
    String alter(Map<Integer, String> groupValues, String template);
  }
}
