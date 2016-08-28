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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.re2j.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A string which is interpolated with named variables. The string is composed of interpolated and
 * non-interpolated (literal) pieces called tokens.
 */
final class TemplateTokens {
  private enum TokenType {
    LITERAL, INTERPOLATION;
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
  private final ImmutableMap<String, Integer> groupIndexes;
  private final ImmutableList<Token> tokens;
  private final Set<String> unusedGroups;

  TemplateTokens(Location location, String template, Map<String, Pattern> regexGroups)
      throws EvalException {
    this.location = location;
    this.template = Preconditions.checkNotNull(template);

    Builder builder = new Builder();
    builder.location = location;
    builder.parse(template);
    this.before = builder.buildBefore(regexGroups);
    this.groupIndexes = ImmutableMap.copyOf(builder.groupIndexes);
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
          template.append("$").append(before.groupIndexes.get(token.value));
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
    return template;
  }

  private static class Builder {
    List<Token> tokens = new ArrayList<>();
    Map<String, Integer> groupIndexes = new HashMap<>();
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
     */
    Pattern buildBefore(Map<String, Pattern> regexesByInterpolationName) throws EvalException {
      StringBuilder fullPattern = new StringBuilder();
      int groupCount = 1;
      for (Token token : tokens) {
        switch (token.type) {
          case INTERPOLATION:
            Pattern subPattern = regexesByInterpolationName.get(token.value);
            if (subPattern == null) {
              throw new EvalException(
                  location, "Interpolation is used but not defined: " + token.value);
            }
            fullPattern.append(String.format("(%s)", subPattern.pattern()));
            if (groupIndexes.put(token.value, groupCount) != null) {
              throw new EvalException(
                  location, "Regex group is used in template multiple times: " + token.value);
            }
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
  void validateUnused() throws EvalException {
    if (!unusedGroups.isEmpty()) {
      throw new EvalException(
          location, "Following interpolations are defined but not used: " + unusedGroups);
    }
  }
}
