/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.templatetoken;

import com.google.common.collect.ImmutableList;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;

/**
 * Parse strings like "foo${bar}baz" in a series of literal and interpolation variables
 */
public class Parser {
  public Parser() {}

  /**
   * Parses a template. In the raw string representation, interpolation is
   * done with {@code ${var_name}}. Literal dollar signs can be represented with {@code $$}.
   *
   * @throws EvalException if the template is malformed
   */
  public ImmutableList<Token> parse(String template) throws EvalException {
    ImmutableList.Builder<Token> result = ImmutableList.builder();
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
        throw Starlark.errorf("Expect $ or { after every $ in string: %s", template);
      }
      thisChar = template.charAt(c);
      c++;
      switch (thisChar) {
        case '$':
          currentLiteral.append('$');
          break;
        case '{':
          result.add(new Token(currentLiteral.toString(), Token.TokenType.LITERAL));
          currentLiteral = new StringBuilder();
          int terminating = template.indexOf('}', c);
          if (terminating == -1) {
            throw Starlark.errorf("Unterminated '${'. Expected '}': %s", template);
          }
          if (c == terminating) {
            throw Starlark.errorf("Expect non-empty interpolated value name: %s", template);
          }
          result.add(
              new Token(template.substring(c, terminating), Token.TokenType.INTERPOLATION));
          c = terminating + 1;
          break;
        default:
          throw Starlark.errorf("Expect $ or { after every $ in string: %s", template);
      }
    }
    result.add(new Token(currentLiteral.toString(), Token.TokenType.LITERAL));
    return result.build();
  }
}
