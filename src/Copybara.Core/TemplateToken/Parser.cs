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

using System.Collections.Immutable;
using System.Text;

namespace Copybara.TemplateToken;

/// <summary>
/// Parse strings like "foo${bar}baz" in a series of literal and interpolation variables.
/// </summary>
public class Parser
{
    public Parser()
    {
    }

    /// <summary>
    /// Parses a template. In the raw string representation, interpolation is done with
    /// <c>${var_name}</c>. Literal dollar signs can be represented with <c>$$</c>.
    /// </summary>
    /// <exception cref="Starlark.Eval.EvalException">if the template is malformed.</exception>
    public IReadOnlyList<Token> Parse(string template)
    {
        var result = ImmutableArray.CreateBuilder<Token>();
        var currentLiteral = new StringBuilder();
        int c = 0;
        while (c < template.Length)
        {
            char thisChar = template[c];
            c++;
            if (thisChar != '$')
            {
                currentLiteral.Append(thisChar);
                continue;
            }
            if (c >= template.Length)
            {
                throw Starlark.Eval.Starlark.Errorf(
                    "Expect $ or {{ after every $ in string: {0}", template);
            }
            thisChar = template[c];
            c++;
            switch (thisChar)
            {
                case '$':
                    currentLiteral.Append('$');
                    break;
                case '{':
                    result.Add(new Token(currentLiteral.ToString(), TokenType.Literal));
                    currentLiteral = new StringBuilder();
                    int terminating = template.IndexOf('}', c);
                    if (terminating == -1)
                    {
                        throw Starlark.Eval.Starlark.Errorf(
                            "Unterminated '${{'. Expected '}}': {0}", template);
                    }
                    if (c == terminating)
                    {
                        throw Starlark.Eval.Starlark.Errorf(
                            "Expect non-empty interpolated value name: {0}", template);
                    }
                    result.Add(
                        new Token(template.Substring(c, terminating - c), TokenType.Interpolation));
                    c = terminating + 1;
                    break;
                default:
                    throw Starlark.Eval.Starlark.Errorf(
                        "Expect $ or {{ after every $ in string: {0}", template);
            }
        }
        result.Add(new Token(currentLiteral.ToString(), TokenType.Literal));
        return result.ToImmutable();
    }
}
