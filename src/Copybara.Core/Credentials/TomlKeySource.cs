/*
 * Copyright (C) 2023 Google Inc.
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
using Copybara.Common;
using Starlark.Eval;

namespace Copybara.Credentials;

/// <summary>Fetches a value located within a toml file.</summary>
/// <remarks>
/// The upstream implementation uses the <c>org.tomlj</c> library. This port ships a minimal,
/// self-contained TOML reader that supports the subset of TOML needed to resolve a dotted key
/// path to a string value (top-level and <c>[table]</c> sections, dotted keys, basic and literal
/// strings). It is not a full TOML parser.
/// </remarks>
public class TomlKeySource : CredentialIssuer, IStarlarkValue
{
    private readonly string _file;
    private readonly string _dotPath;

    public TomlKeySource(string file, string keyPath)
    {
        _file = file;
        _dotPath = keyPath;
    }

    public Credential Issue()
    {
        IReadOnlyDictionary<string, string> values;
        try
        {
            values = ParseStringValues(File.ReadAllText(_file));
        }
        catch (IOException e)
        {
            throw new CredentialIssuingException("Error reading Toml file.", e);
        }

        if (!values.TryGetValue(_dotPath, out var data))
        {
            throw new CredentialIssuingException(
                string.Format("key {0} not found in file {1}", _dotPath, _file));
        }

        return new StaticSecret(_dotPath, data);
    }

    public ImmutableSetMultimap<string, string> Describe() =>
        ImmutableSetMultimap<string, string>.CreateBuilder()
            .Put("type", "Toml")
            .Put("dotPath", _dotPath)
            .Build();

    /// <summary>
    /// Parses the string-valued entries of a TOML document into a flat map keyed by fully-qualified
    /// dotted path (e.g. <c>foo.bar.baz</c>).
    /// </summary>
    private static IReadOnlyDictionary<string, string> ParseStringValues(string content)
    {
        var result = new Dictionary<string, string>();
        string prefix = "";

        foreach (var rawLine in content.Split('\n'))
        {
            var line = StripComment(rawLine).Trim();
            if (line.Length == 0)
            {
                continue;
            }

            if (line[0] == '[')
            {
                // Table header: [a.b.c] or [[a.b]] (array of tables — treated like a table header).
                int end = line.IndexOf(']');
                if (end < 0)
                {
                    continue;
                }

                var header = line.Substring(1, end - 1).Trim();
                if (header.StartsWith("[") && header.EndsWith("]"))
                {
                    header = header.Substring(1, header.Length - 2).Trim();
                }

                prefix = JoinKeyParts(header);
                continue;
            }

            int eq = line.IndexOf('=');
            if (eq < 0)
            {
                continue;
            }

            var key = line.Substring(0, eq).Trim();
            var valueText = line.Substring(eq + 1).Trim();

            if (!TryParseString(valueText, out var value))
            {
                continue;
            }

            var fullKey = JoinKeyParts(key);
            if (prefix.Length > 0)
            {
                fullKey = prefix + "." + fullKey;
            }

            result[fullKey] = value;
        }

        return result;
    }

    private static string JoinKeyParts(string key)
    {
        // Normalize dotted keys, stripping quotes and surrounding whitespace from each part.
        var parts = key.Split('.');
        var builder = new StringBuilder();
        for (var i = 0; i < parts.Length; i++)
        {
            if (i > 0)
            {
                builder.Append('.');
            }

            builder.Append(Unquote(parts[i].Trim()));
        }

        return builder.ToString();
    }

    private static string Unquote(string s)
    {
        if (s.Length >= 2 &&
            ((s[0] == '"' && s[^1] == '"') || (s[0] == '\'' && s[^1] == '\'')))
        {
            return s.Substring(1, s.Length - 2);
        }

        return s;
    }

    private static bool TryParseString(string valueText, out string value)
    {
        value = "";
        if (valueText.Length < 2)
        {
            return false;
        }

        char quote = valueText[0];
        if (quote == '"' && valueText[^1] == '"')
        {
            value = Unescape(valueText.Substring(1, valueText.Length - 2));
            return true;
        }

        if (quote == '\'' && valueText[^1] == '\'')
        {
            // Literal string: no escaping.
            value = valueText.Substring(1, valueText.Length - 2);
            return true;
        }

        return false;
    }

    private static string Unescape(string s)
    {
        if (s.IndexOf('\\') < 0)
        {
            return s;
        }

        var builder = new StringBuilder(s.Length);
        for (var i = 0; i < s.Length; i++)
        {
            var c = s[i];
            if (c == '\\' && i + 1 < s.Length)
            {
                var next = s[++i];
                builder.Append(next switch
                {
                    'n' => '\n',
                    't' => '\t',
                    'r' => '\r',
                    '"' => '"',
                    '\\' => '\\',
                    _ => next,
                });
            }
            else
            {
                builder.Append(c);
            }
        }

        return builder.ToString();
    }

    private static string StripComment(string line)
    {
        // Removes a trailing '#' comment, honoring quoted strings.
        bool inBasic = false;
        bool inLiteral = false;
        for (var i = 0; i < line.Length; i++)
        {
            var c = line[i];
            if (c == '"' && !inLiteral)
            {
                inBasic = !inBasic;
            }
            else if (c == '\'' && !inBasic)
            {
                inLiteral = !inLiteral;
            }
            else if (c == '#' && !inBasic && !inLiteral)
            {
                return line.Substring(0, i);
            }
        }

        return line;
    }
}
