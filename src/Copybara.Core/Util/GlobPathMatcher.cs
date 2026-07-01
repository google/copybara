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
using System.Text.RegularExpressions;

namespace Copybara.Util;

/// <summary>
/// A <see cref="IPathMatcher"/> that implements the <c>glob:</c> syntax of
/// <c>java.nio.file.FileSystem.getPathMatcher</c>. The full glob string is anchored against the
/// whole (normalized, <c>/</c>-separated) path.
///
/// <para>Supported syntax (matching the Java <c>glob:</c> grammar):</para>
/// <list type="bullet">
///   <item><description><c>*</c> matches zero or more characters, not crossing a <c>/</c>.</description></item>
///   <item><description><c>**</c> matches zero or more characters, crossing <c>/</c> boundaries.</description></item>
///   <item><description><c>?</c> matches exactly one character that is not a <c>/</c>.</description></item>
///   <item><description><c>[...]</c> a character class; supports <c>!</c> or <c>^</c> negation and
///   <c>a-z</c> ranges. Inside a class, <c>*</c>, <c>?</c>, <c>{</c> etc. are literal.</description></item>
///   <item><description><c>{a,b,c}</c> a group matching any of the comma-separated subpatterns
///   (not nestable, as in Java).</description></item>
///   <item><description><c>\</c> escapes the following metacharacter.</description></item>
/// </list>
/// </summary>
public sealed class GlobPathMatcher : IPathMatcher, IEquatable<GlobPathMatcher>
{
    private readonly Regex _regex;
    private readonly string _glob;

    private GlobPathMatcher(Regex regex, string glob)
    {
        _regex = regex;
        _glob = glob;
    }

    /// <summary>Compiles a matcher from a full glob string (already including any root prefix).</summary>
    public static GlobPathMatcher Compile(string glob)
    {
        string regex = Translate(glob);
        return new GlobPathMatcher(new Regex(regex, RegexOptions.Singleline), glob);
    }

    public bool Matches(string path) => _regex.IsMatch(PathNormalizer.Normalize(path));

    public override string ToString() => _glob;

    public bool Equals(GlobPathMatcher? other) => other is not null && other._glob == _glob;

    public override bool Equals(object? obj) => Equals(obj as GlobPathMatcher);

    public override int GetHashCode() => _glob.GetHashCode();

    private const string RegexMeta = ".^$+{[]|()";

    /// <summary>Translates a Java <c>glob:</c> pattern into an anchored .NET regular expression.</summary>
    internal static string Translate(string glob)
    {
        var sb = new StringBuilder();
        sb.Append('^');
        int i = 0;
        int n = glob.Length;
        bool inGroup = false;
        while (i < n)
        {
            char c = glob[i++];
            switch (c)
            {
                case '\\':
                    if (i >= n)
                    {
                        throw new ArgumentException(
                            "No character to escape at end of pattern: " + glob);
                    }
                    char next = glob[i++];
                    AppendLiteral(sb, next);
                    break;
                case '/':
                    sb.Append('/');
                    break;
                case '[':
                    i = TranslateBracket(glob, i, sb);
                    break;
                case '{':
                    if (inGroup)
                    {
                        throw new ArgumentException("Cannot nest groups in pattern: " + glob);
                    }
                    inGroup = true;
                    sb.Append("(?:(?:");
                    break;
                case '}':
                    if (inGroup)
                    {
                        inGroup = false;
                        sb.Append("))");
                    }
                    else
                    {
                        sb.Append('}');
                    }
                    break;
                case ',':
                    if (inGroup)
                    {
                        sb.Append(")|(?:");
                    }
                    else
                    {
                        sb.Append(',');
                    }
                    break;
                case '*':
                    if (i < n && glob[i] == '*')
                    {
                        // ** crosses directory boundaries.
                        sb.Append(".*");
                        i++;
                    }
                    else
                    {
                        // * matches anything but the separator.
                        sb.Append("[^/]*");
                    }
                    break;
                case '?':
                    sb.Append("[^/]");
                    break;
                default:
                    AppendLiteral(sb, c);
                    break;
            }
        }
        if (inGroup)
        {
            throw new ArgumentException("Missing '}' in pattern: " + glob);
        }
        sb.Append('$');
        return sb.ToString();
    }

    private static int TranslateBracket(string glob, int i, StringBuilder sb)
    {
        int n = glob.Length;
        sb.Append('[');
        if (i < n && (glob[i] == '!' || glob[i] == '^'))
        {
            sb.Append('^');
            i++;
        }
        // A ']' immediately following the (possibly negated) '[' is a literal ']'.
        if (i < n && glob[i] == ']')
        {
            sb.Append("\\]");
            i++;
        }
        bool closed = false;
        while (i < n)
        {
            char c = glob[i++];
            if (c == ']')
            {
                closed = true;
                break;
            }
            if (c == '\\')
            {
                if (i >= n)
                {
                    throw new ArgumentException("No character to escape in class: " + glob);
                }
                AppendClassLiteral(sb, glob[i++]);
            }
            else if (c == '/')
            {
                // Java glob classes cannot contain the separator.
                throw new ArgumentException("Explicit 'name separator' in class: " + glob);
            }
            else if (c == '-')
            {
                // Preserve ranges as-is.
                sb.Append('-');
            }
            else
            {
                AppendClassLiteral(sb, c);
            }
        }
        if (!closed)
        {
            throw new ArgumentException("Missing ']' in pattern: " + glob);
        }
        sb.Append(']');
        return i;
    }

    private static void AppendLiteral(StringBuilder sb, char c)
    {
        if (RegexMeta.IndexOf(c) >= 0 || c == '*' || c == '?' || c == '\\' || c == '}')
        {
            sb.Append('\\');
        }
        sb.Append(c);
    }

    private static void AppendClassLiteral(StringBuilder sb, char c)
    {
        if (c == '\\' || c == ']' || c == '^' || c == '[')
        {
            sb.Append('\\');
        }
        sb.Append(c);
    }
}

/// <summary>Normalizes a filesystem path for glob matching: backslashes become <c>/</c>.</summary>
internal static class PathNormalizer
{
    public static string Normalize(string path) => path.Replace('\\', '/');
}
