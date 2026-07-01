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

namespace Copybara.Util.Console;

/// <summary>
/// Translates the Java <c>String.format</c> printf-style format strings used throughout the console
/// subsystem (the <c>*Fmt</c> methods) into concrete strings.
///
/// <para>Callers pass printf conversions such as <c>%s</c> and <c>%d</c>. This helper consumes the
/// format arguments sequentially, replacing conversions with the corresponding argument. <c>%%</c>
/// is emitted as a literal <c>%</c>. Any conversion is rendered simply via the argument's string
/// representation, which is sufficient for the console's usage.</para>
/// </summary>
internal static class ConsoleFormat
{
    public static string Printf(string format, params object?[] args)
    {
        if (format == null)
        {
            throw new ArgumentNullException(nameof(format));
        }
        args ??= Array.Empty<object?>();

        var sb = new StringBuilder(format.Length + 16);
        int argIndex = 0;
        for (int i = 0; i < format.Length; i++)
        {
            char c = format[i];
            if (c != '%')
            {
                sb.Append(c);
                continue;
            }

            // Lone '%' at the end: emit as-is.
            if (i + 1 >= format.Length)
            {
                sb.Append('%');
                break;
            }

            char next = format[i + 1];
            if (next == '%')
            {
                sb.Append('%');
                i++;
                continue;
            }

            if (next == 'n')
            {
                sb.Append(Environment.NewLine);
                i++;
                continue;
            }

            // Any other conversion (e.g. %s, %d) consumes the next argument sequentially.
            if (argIndex < args.Length)
            {
                sb.Append(Convert(args[argIndex]));
                argIndex++;
            }
            else
            {
                // Not enough arguments: keep the conversion literally.
                sb.Append(c);
                sb.Append(next);
            }
            i++;
        }

        return sb.ToString();
    }

    private static string Convert(object? arg) => arg?.ToString() ?? "null";
}
