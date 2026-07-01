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

using System.Text;

namespace Copybara.Exceptions;

/// <summary>
/// Indicates that the configuration is wrong or some error attributable to the user happened. For
/// example wrong flag usage, errors in fields or errors that we discover during execution.
/// </summary>
public class ValidationException : Exception
{
    public ValidationException(string message)
        : base(message)
    {
    }

    public ValidationException(string message, Exception? cause)
        : base(message, cause)
    {
    }

    /// <summary>
    /// Check a condition and throw <see cref="ValidationException"/> if false.
    /// </summary>
    /// <exception cref="ValidationException">if <paramref name="condition"/> is false</exception>
    public static void CheckCondition(bool condition, string format, params object?[] args)
    {
        if (!condition)
        {
            // Don't try to format if there are no args. This allows strings like '%Fooooo'.
            if (args.Length == 0)
            {
                throw new ValidationException(format);
            }

            throw new ValidationException(Format(format, args));
        }
    }

    /// <summary>
    /// Check a condition and throw <see cref="ValidationException"/> if false.
    /// </summary>
    /// <exception cref="ValidationException">if <paramref name="condition"/> is false</exception>
    public static void CheckCondition(bool condition, string msg)
    {
        if (!condition)
        {
            throw new ValidationException(msg);
        }
    }

    /// <summary>
    /// Throw a <see cref="ValidationException"/> that can be retried.
    /// </summary>
    public static ValidationException RetriableException(string message)
    {
        return new ValidationException(message);
    }

    /// <summary>
    /// Translates a Java/printf-style format string (using conversions such as <c>%s</c> and
    /// <c>%d</c>) into a .NET composite format string and applies <see cref="string.Format(string, object?[])"/>.
    /// Conversions are consumed sequentially from <paramref name="args"/>. A literal percent sign is
    /// written using <c>%%</c>.
    /// </summary>
    private static string Format(string fmt, object?[] args)
    {
        var builder = new StringBuilder(fmt.Length + 16);
        var argIndex = 0;

        for (var i = 0; i < fmt.Length; i++)
        {
            var c = fmt[i];
            if (c == '%' && i + 1 < fmt.Length)
            {
                var next = fmt[i + 1];
                if (next == '%')
                {
                    builder.Append('%');
                    i++;
                    continue;
                }

                // Treat any single-letter conversion (e.g. %s, %d, %b) as a positional argument.
                if (char.IsLetter(next))
                {
                    builder.Append('{').Append(argIndex++).Append('}');
                    i++;
                    continue;
                }
            }

            // Escape braces so they are treated literally by string.Format.
            if (c == '{')
            {
                builder.Append("{{");
            }
            else if (c == '}')
            {
                builder.Append("}}");
            }
            else
            {
                builder.Append(c);
            }
        }

        return string.Format(builder.ToString(), args);
    }
}
