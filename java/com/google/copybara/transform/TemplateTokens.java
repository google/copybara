// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.config.ConfigValidationException;

import java.util.ArrayList;
import java.util.List;

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

  private final ImmutableList<Token> components;

  private TemplateTokens(ImmutableList<Token> components) {
    this.components = Preconditions.checkNotNull(components);
  }

  @Override
  public String toString() {
    return components.toString();
  }

  private static void addLiteralIfNotEmpty(List<Token> components, String literal) {
    if (!literal.isEmpty()) {
      components.add(new Token(literal, TokenType.LITERAL));
    }
  }

  /**
   * Parses a template. In the raw string representation, interpolation is done with
   * {@code ${var_name}}. Literal dollar signs can be represented with {@code $$}.
   *
   * @throws ConfigValidationException if the template is malformed
   */
  public static TemplateTokens parse(String template) {
    StringBuilder currentLiteral = new StringBuilder();
    List<Token> components = new ArrayList<>();
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
          addLiteralIfNotEmpty(components, currentLiteral.toString());
          currentLiteral = new StringBuilder();
          int terminating = template.indexOf('}', c);
          if (terminating == -1) {
            throw new ConfigValidationException("Unterminated '${'. Expected '}': " + template);
          }
          if (c == terminating) {
            throw new ConfigValidationException(
                "Expect non-empty interpolated value name: " + template);
          }
          components.add(
              new Token(template.substring(c, terminating), TokenType.INTERPOLATION));
          c = terminating + 1;
          break;
        default:
          throw new ConfigValidationException("Expect $ or { after every $ in string: " + template);
      }
    }
    addLiteralIfNotEmpty(components, currentLiteral.toString());
    return new TemplateTokens(ImmutableList.copyOf(components));
  }
}
