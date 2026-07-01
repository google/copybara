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

using System.Globalization;
using System.Text;

namespace Copybara.Toml;

/// <summary>
/// A minimal, hand-rolled TOML parser sufficient for Copybara's Starlark <c>toml.parse</c> surface.
/// </summary>
/// <remarks>
/// NOTE(port): upstream uses the tomlj library. This is a small, from-scratch reader that produces
/// a nested dictionary tree of plain .NET values (<see cref="string"/>, <see cref="long"/>,
/// <see cref="double"/>, <see cref="bool"/>, <see cref="DateTimeOffset"/>, <see cref="List{T}"/> of
/// values, and nested <c>Dictionary&lt;string, object&gt;</c>).
///
/// SUPPORTED: comments; bare/quoted keys; dotted keys; standard tables <c>[a.b]</c>; array-of-tables
/// <c>[[a.b]]</c>; basic and literal strings (single-line); multi-line basic and literal strings;
/// integers (with <c>_</c> separators, hex/oct/bin prefixes); floats (incl. inf/nan/exponents);
/// booleans; offset date-times; arrays (nested, multi-line); inline tables.
///
/// GAPS (TODO): full TOML date/time coverage is limited to offset date-times parseable by
/// <see cref="DateTimeOffset"/> — local date-time / local date / local time are returned as raw
/// strings. Strict duplicate-key / table-redefinition validation is best-effort. Unicode escape
/// edge cases beyond <c>\uXXXX</c>/<c>\UXXXXXXXX</c> are not exhaustively validated.
/// </remarks>
internal static class TomlParser
{
    public sealed class TomlParseResult
    {
        public Dictionary<string, object?> Root { get; } = new();
        public List<string> Errors { get; } = new();
        public bool HasErrors => Errors.Count > 0;
    }

    public static TomlParseResult Parse(string content)
    {
        var result = new TomlParseResult();
        try
        {
            new Impl(content, result).Run();
        }
        catch (TomlParseException e)
        {
            result.Errors.Add(e.Message);
        }
        return result;
    }

    private sealed class TomlParseException(string message) : Exception(message);

    private sealed class Impl(string text, TomlParseResult result)
    {
        private readonly string _text = text;
        private int _pos;
        private int _line = 1;
        // The table into which bare key/value pairs are currently written.
        private Dictionary<string, object?> _current = result.Root;

        public void Run()
        {
            while (true)
            {
                SkipWhitespaceAndComments();
                if (Eof)
                {
                    return;
                }

                char c = Peek();
                if (c == '[')
                {
                    ParseTableHeader();
                }
                else
                {
                    ParseKeyValue();
                }
                SkipToLineEndTrivia();
            }
        }

        private bool Eof => _pos >= _text.Length;
        private char Peek() => _text[_pos];
        private char PeekAt(int off) => _pos + off < _text.Length ? _text[_pos + off] : '\0';

        private char Next()
        {
            char c = _text[_pos++];
            if (c == '\n')
            {
                _line++;
            }
            return c;
        }

        private TomlParseException Err(string msg) =>
            new($"line {_line}: {msg}");

        private void SkipWhitespaceAndComments()
        {
            while (!Eof)
            {
                char c = Peek();
                if (c is ' ' or '\t' or '\r' or '\n')
                {
                    Next();
                }
                else if (c == '#')
                {
                    while (!Eof && Peek() != '\n')
                    {
                        Next();
                    }
                }
                else
                {
                    break;
                }
            }
        }

        private void SkipInlineWhitespace()
        {
            while (!Eof && (Peek() == ' ' || Peek() == '\t'))
            {
                Next();
            }
        }

        // After a value, expect optional whitespace, optional comment, then newline or EOF.
        private void SkipToLineEndTrivia()
        {
            SkipInlineWhitespace();
            if (Eof)
            {
                return;
            }
            char c = Peek();
            if (c == '#')
            {
                while (!Eof && Peek() != '\n')
                {
                    Next();
                }
            }
            else if (c == '\r')
            {
                Next();
            }
            else if (c != '\n')
            {
                throw Err($"unexpected trailing content '{c}'");
            }
            if (!Eof && Peek() == '\n')
            {
                Next();
            }
        }

        private void ParseTableHeader()
        {
            Next(); // '['
            bool arrayOfTables = !Eof && Peek() == '[';
            if (arrayOfTables)
            {
                Next();
            }

            SkipInlineWhitespace();
            var keys = ParseKeyPath();
            SkipInlineWhitespace();

            if (Eof || Next() != ']')
            {
                throw Err("expected ']' after table header");
            }
            if (arrayOfTables)
            {
                if (Eof || Next() != ']')
                {
                    throw Err("expected ']]' after array-of-tables header");
                }
            }

            _current = arrayOfTables
                ? DescendArrayOfTables(result.Root, keys)
                : DescendTable(result.Root, keys);
        }

        private Dictionary<string, object?> DescendTable(
            Dictionary<string, object?> root, List<string> keys)
        {
            var node = root;
            for (int i = 0; i < keys.Count; i++)
            {
                string key = keys[i];
                if (!node.TryGetValue(key, out object? existing))
                {
                    var child = new Dictionary<string, object?>();
                    node[key] = child;
                    node = child;
                }
                else if (existing is Dictionary<string, object?> dict)
                {
                    node = dict;
                }
                else if (existing is List<object?> list && list.Count > 0
                    && list[^1] is Dictionary<string, object?> last)
                {
                    node = last;
                }
                else
                {
                    throw Err($"key '{key}' is already defined with a non-table value");
                }
            }
            return node;
        }

        private Dictionary<string, object?> DescendArrayOfTables(
            Dictionary<string, object?> root, List<string> keys)
        {
            var node = root;
            for (int i = 0; i < keys.Count - 1; i++)
            {
                node = DescendTable(node, new List<string> { keys[i] });
            }

            string lastKey = keys[^1];
            if (!node.TryGetValue(lastKey, out object? existing))
            {
                var list = new List<object?>();
                node[lastKey] = list;
                var entry = new Dictionary<string, object?>();
                list.Add(entry);
                return entry;
            }
            if (existing is List<object?> arr)
            {
                var entry = new Dictionary<string, object?>();
                arr.Add(entry);
                return entry;
            }
            throw Err($"key '{lastKey}' is already defined and is not an array of tables");
        }

        private void ParseKeyValue()
        {
            var keys = ParseKeyPath();
            SkipInlineWhitespace();
            if (Eof || Next() != '=')
            {
                throw Err("expected '=' in key/value pair");
            }
            SkipInlineWhitespace();
            object? value = ParseValue();

            // Place the value, descending dotted keys into (implicit) tables.
            var node = _current;
            for (int i = 0; i < keys.Count - 1; i++)
            {
                string k = keys[i];
                if (!node.TryGetValue(k, out object? existing))
                {
                    var child = new Dictionary<string, object?>();
                    node[k] = child;
                    node = child;
                }
                else if (existing is Dictionary<string, object?> dict)
                {
                    node = dict;
                }
                else
                {
                    throw Err($"key '{k}' is already defined with a non-table value");
                }
            }
            node[keys[^1]] = value;
        }

        private List<string> ParseKeyPath()
        {
            var keys = new List<string>();
            while (true)
            {
                SkipInlineWhitespace();
                keys.Add(ParseSingleKey());
                SkipInlineWhitespace();
                if (!Eof && Peek() == '.')
                {
                    Next();
                    continue;
                }
                break;
            }
            return keys;
        }

        private string ParseSingleKey()
        {
            if (Eof)
            {
                throw Err("expected a key");
            }
            char c = Peek();
            if (c == '"')
            {
                return ParseBasicString();
            }
            if (c == '\'')
            {
                return ParseLiteralString();
            }

            var sb = new StringBuilder();
            while (!Eof)
            {
                c = Peek();
                if (char.IsLetterOrDigit(c) || c == '_' || c == '-')
                {
                    sb.Append(Next());
                }
                else
                {
                    break;
                }
            }
            if (sb.Length == 0)
            {
                throw Err($"invalid bare key starting with '{c}'");
            }
            return sb.ToString();
        }

        private object? ParseValue()
        {
            if (Eof)
            {
                throw Err("expected a value");
            }
            char c = Peek();
            switch (c)
            {
                case '"':
                    return PeekAt(1) == '"' && PeekAt(2) == '"'
                        ? ParseMultilineBasicString()
                        : ParseBasicString();
                case '\'':
                    return PeekAt(1) == '\'' && PeekAt(2) == '\''
                        ? ParseMultilineLiteralString()
                        : ParseLiteralString();
                case '[':
                    return ParseArray();
                case '{':
                    return ParseInlineTable();
                case 't':
                case 'f':
                    return ParseBool();
                default:
                    return ParseNumberOrDate();
            }
        }

        private string ParseBasicString()
        {
            Next(); // opening quote
            var sb = new StringBuilder();
            while (true)
            {
                if (Eof)
                {
                    throw Err("unterminated string");
                }
                char c = Next();
                if (c == '"')
                {
                    return sb.ToString();
                }
                if (c == '\\')
                {
                    sb.Append(ReadEscape());
                }
                else if (c == '\n')
                {
                    throw Err("newline in single-line string");
                }
                else
                {
                    sb.Append(c);
                }
            }
        }

        private string ParseMultilineBasicString()
        {
            Next(); Next(); Next(); // """
            // A newline immediately following the opening delimiter is trimmed.
            if (!Eof && Peek() == '\r')
            {
                Next();
            }
            if (!Eof && Peek() == '\n')
            {
                Next();
            }

            var sb = new StringBuilder();
            while (true)
            {
                if (Eof)
                {
                    throw Err("unterminated multi-line string");
                }
                if (Peek() == '"' && PeekAt(1) == '"' && PeekAt(2) == '"')
                {
                    Next(); Next(); Next();
                    return sb.ToString();
                }
                char c = Next();
                if (c == '\\')
                {
                    // Line-ending backslash: trim trailing whitespace/newlines.
                    int save = _pos;
                    SkipInlineWhitespace();
                    if (!Eof && (Peek() == '\n' || Peek() == '\r'))
                    {
                        while (!Eof && (Peek() is ' ' or '\t' or '\r' or '\n'))
                        {
                            Next();
                        }
                        continue;
                    }
                    _pos = save;
                    sb.Append(ReadEscape());
                }
                else
                {
                    sb.Append(c);
                }
            }
        }

        private string ParseLiteralString()
        {
            Next(); // opening '
            var sb = new StringBuilder();
            while (true)
            {
                if (Eof)
                {
                    throw Err("unterminated literal string");
                }
                char c = Next();
                if (c == '\'')
                {
                    return sb.ToString();
                }
                if (c == '\n')
                {
                    throw Err("newline in single-line literal string");
                }
                sb.Append(c);
            }
        }

        private string ParseMultilineLiteralString()
        {
            Next(); Next(); Next(); // '''
            if (!Eof && Peek() == '\r')
            {
                Next();
            }
            if (!Eof && Peek() == '\n')
            {
                Next();
            }
            var sb = new StringBuilder();
            while (true)
            {
                if (Eof)
                {
                    throw Err("unterminated multi-line literal string");
                }
                if (Peek() == '\'' && PeekAt(1) == '\'' && PeekAt(2) == '\'')
                {
                    Next(); Next(); Next();
                    return sb.ToString();
                }
                sb.Append(Next());
            }
        }

        private string ReadEscape()
        {
            if (Eof)
            {
                throw Err("unterminated escape sequence");
            }
            char c = Next();
            switch (c)
            {
                case 'b': return "\b";
                case 't': return "\t";
                case 'n': return "\n";
                case 'f': return "\f";
                case 'r': return "\r";
                case '"': return "\"";
                case '\\': return "\\";
                case 'u': return ReadUnicode(4);
                case 'U': return ReadUnicode(8);
                default:
                    throw Err($"invalid escape sequence '\\{c}'");
            }
        }

        private string ReadUnicode(int digits)
        {
            if (_pos + digits > _text.Length)
            {
                throw Err("invalid unicode escape");
            }
            string hex = _text.Substring(_pos, digits);
            for (int i = 0; i < digits; i++)
            {
                Next();
            }
            if (!int.TryParse(hex, NumberStyles.HexNumber, CultureInfo.InvariantCulture,
                    out int code))
            {
                throw Err($"invalid unicode escape '\\{(digits == 4 ? 'u' : 'U')}{hex}'");
            }
            return char.ConvertFromUtf32(code);
        }

        private bool ParseBool()
        {
            if (_text.AsSpan(_pos).StartsWith("true"))
            {
                _pos += 4;
                return true;
            }
            if (_text.AsSpan(_pos).StartsWith("false"))
            {
                _pos += 5;
                return false;
            }
            throw Err("invalid boolean value");
        }

        private object? ParseNumberOrDate()
        {
            int start = _pos;
            while (!Eof)
            {
                char c = Peek();
                if (c is ',' or ']' or '}' or '\n' or '\r' or '#')
                {
                    break;
                }
                Next();
            }
            string token = _text.Substring(start, _pos - start).Trim();
            if (token.Length == 0)
            {
                throw Err("expected a value");
            }
            return InterpretScalar(token);
        }

        private object InterpretScalar(string token)
        {
            // Date-time: contains a '-' or ':' pattern suggestive of a timestamp.
            if (LooksLikeDateTime(token) &&
                DateTimeOffset.TryParse(token, CultureInfo.InvariantCulture,
                    DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal, out var dto))
            {
                return dto;
            }

            switch (token)
            {
                case "inf":
                case "+inf":
                    return double.PositiveInfinity;
                case "-inf":
                    return double.NegativeInfinity;
                case "nan":
                case "+nan":
                case "-nan":
                    return double.NaN;
            }

            string noUnderscore = token.Replace("_", "");

            // Prefixed integers.
            if (noUnderscore.StartsWith("0x", StringComparison.OrdinalIgnoreCase))
            {
                return Convert.ToInt64(noUnderscore[2..], 16);
            }
            if (noUnderscore.StartsWith("0o", StringComparison.OrdinalIgnoreCase))
            {
                return Convert.ToInt64(noUnderscore[2..], 8);
            }
            if (noUnderscore.StartsWith("0b", StringComparison.OrdinalIgnoreCase))
            {
                return Convert.ToInt64(noUnderscore[2..], 2);
            }

            bool isFloat = noUnderscore.Contains('.')
                || noUnderscore.Contains('e', StringComparison.OrdinalIgnoreCase);
            if (!isFloat && long.TryParse(noUnderscore, NumberStyles.AllowLeadingSign,
                    CultureInfo.InvariantCulture, out long l))
            {
                return l;
            }
            if (double.TryParse(noUnderscore, NumberStyles.Float, CultureInfo.InvariantCulture,
                    out double d))
            {
                return d;
            }

            // Fall back: this may be a local date/time that DateTimeOffset couldn't parse.
            if (LooksLikeDateTime(token))
            {
                return token; // GAP: local date/time returned as raw string.
            }

            throw Err($"could not parse value '{token}'");
        }

        private static bool LooksLikeDateTime(string token)
        {
            // e.g. 1979-05-27T07:32:00Z, 1979-05-27, 07:32:00
            if (token.Length < 5)
            {
                return false;
            }
            bool hasDate = token.Length >= 10 && token[4] == '-' && token[7] == '-';
            bool hasTime = token.Contains(':');
            return hasDate || hasTime;
        }

        private List<object?> ParseArray()
        {
            Next(); // '['
            var list = new List<object?>();
            while (true)
            {
                SkipWhitespaceAndComments();
                if (Eof)
                {
                    throw Err("unterminated array");
                }
                if (Peek() == ']')
                {
                    Next();
                    return list;
                }
                list.Add(ParseValue());
                SkipWhitespaceAndComments();
                if (Eof)
                {
                    throw Err("unterminated array");
                }
                char c = Peek();
                if (c == ',')
                {
                    Next();
                }
                else if (c == ']')
                {
                    Next();
                    return list;
                }
                else
                {
                    throw Err($"expected ',' or ']' in array, got '{c}'");
                }
            }
        }

        private Dictionary<string, object?> ParseInlineTable()
        {
            Next(); // '{'
            var table = new Dictionary<string, object?>();
            SkipInlineWhitespace();
            if (!Eof && Peek() == '}')
            {
                Next();
                return table;
            }
            while (true)
            {
                SkipInlineWhitespace();
                var keys = ParseKeyPath();
                SkipInlineWhitespace();
                if (Eof || Next() != '=')
                {
                    throw Err("expected '=' in inline table");
                }
                SkipInlineWhitespace();
                object? value = ParseValue();

                var node = table;
                for (int i = 0; i < keys.Count - 1; i++)
                {
                    string k = keys[i];
                    if (node.TryGetValue(k, out object? ex)
                        && ex is Dictionary<string, object?> d)
                    {
                        node = d;
                    }
                    else
                    {
                        var child = new Dictionary<string, object?>();
                        node[k] = child;
                        node = child;
                    }
                }
                node[keys[^1]] = value;

                SkipInlineWhitespace();
                if (Eof)
                {
                    throw Err("unterminated inline table");
                }
                char c = Next();
                if (c == ',')
                {
                    continue;
                }
                if (c == '}')
                {
                    return table;
                }
                throw Err($"expected ',' or '}}' in inline table, got '{c}'");
            }
        }
    }
}
