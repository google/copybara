// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.copybara.ConfigValidationException;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.EvalException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A string which is interpolated with named variables. The string is composed of interpolated and
 * non-interpolated (literal) pieces called tokens.
 */
public final class TemplateTokens {
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

    @Override
    public String toString() {
      return String.format("{%s, %s}", value, type);
    }
  }

  private final String template;
  private final ImmutableList<Token> tokens;

  private TemplateTokens(ImmutableList<Token> tokens) {
    this.template = escapeLiterals(tokens);
    this.tokens = Preconditions.checkNotNull(tokens);
  }

  @Override
  public String toString() {
    return tokens.toString();
  }

  /**
   * Returns the template as a string which can be used as replacement text. The literals are
   * escaped where necessary to disambiguate them from interpolations. The returned string can be
   * used with {@link Matcher#replaceAll(String)}.
   */
  public String template() {
    return template;
  }

  /**
   * Returns a template in which the literals are escaped if necessary and the interpolations appear
   * as {@code ${NAME}}. It particular the backslashes and {@code $} are each escaped with a
   * backslash. This causes them to not be interpreted as capture references.
   */
  private static String escapeLiterals(Iterable<Token> tokens) {
    StringBuilder template = new StringBuilder();
    for (Token token : tokens) {
      switch (token.type) {
        case INTERPOLATION:
          template.append(String.format("${%s}", token.value));
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

  /**
   * Converts this sequence of tokens into a regex which can be used to search a string. It
   * automatically quotes literals and represents interpolations as named groups.
   *
   * @param regexesByInterpolationName map from group name to the regex to interpolate when the
   * group is mentioned
   */
  public Pattern toRegex(ImmutableMap<String, Pattern> regexesByInterpolationName) {
    StringBuilder fullPattern = new StringBuilder();
    for (Token token : tokens) {
      switch (token.type) {
        case INTERPOLATION:
          fullPattern.append(String.format("(?<%s>%s)",
              token.value, regexesByInterpolationName.get(token.value).pattern()));
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

  /**
   * Checks that the set of interpolated tokens matches {@code definedInterpolations}.
   *
   * @throws EvalException if the set of interpolations in this does not match the set
   * passed
   */
  public void validateInterpolations(Location location, String field,
      Set<String> definedInterpolations, boolean ignoreNotUsed) throws EvalException {
    Set<String> used = new HashSet<>();
    for (Token token : tokens) {
      if (token.type == TokenType.INTERPOLATION) {
        used.add(token.value);
      }
    }
    Set<String> undefined = Sets.difference(used, definedInterpolations);
    if (!undefined.isEmpty()) {
      throw new EvalException(location, String.format(
          "'%s' field: Following interpolations are used but not defined: %s", field, undefined));
    }
    if (ignoreNotUsed) {
      return;
    }
    Set<String> unused = Sets.difference(definedInterpolations, used);
    if (!unused.isEmpty()) {
      throw new EvalException(location, String.format(
          "'%s' field: Following interpolations are defined but not used: %s", field, unused));
    }
  }

  private static void addLiteralIfNotEmpty(List<Token> tokens, String literal) {
    if (!literal.isEmpty()) {
      tokens.add(new Token(literal, TokenType.LITERAL));
    }
  }

  /**
   * Parses a template. In the raw string representation, interpolation is done with
   * {@code ${var_name}}. Literal dollar signs can be represented with {@code $$}.
   *
   * @throws ConfigValidationException if the template is malformed
   */
  public static TemplateTokens parse(String template) throws ConfigValidationException {
    StringBuilder currentLiteral = new StringBuilder();
    List<Token> tokens = new ArrayList<>();
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
          addLiteralIfNotEmpty(tokens, currentLiteral.toString());
          currentLiteral = new StringBuilder();
          int terminating = template.indexOf('}', c);
          if (terminating == -1) {
            throw new ConfigValidationException("Unterminated '${'. Expected '}': " + template);
          }
          if (c == terminating) {
            throw new ConfigValidationException(
                "Expect non-empty interpolated value name: " + template);
          }
          tokens.add(
              new Token(template.substring(c, terminating), TokenType.INTERPOLATION));
          c = terminating + 1;
          break;
        default:
          throw new ConfigValidationException("Expect $ or { after every $ in string: " + template);
      }
    }
    addLiteralIfNotEmpty(tokens, currentLiteral.toString());
    return new TemplateTokens(ImmutableList.copyOf(tokens));
  }
}
