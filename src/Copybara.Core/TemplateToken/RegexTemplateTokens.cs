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
using Copybara.Common;
using Starlark.Eval;
using Starlark.Syntax;

namespace Copybara.TemplateToken;

/// <summary>
/// A string which is interpolated with named variables. The string is composed of interpolated and
/// non-interpolated (literal) pieces called tokens.
/// </summary>
public sealed class RegexTemplateTokens
{
    private readonly string _template;
    private readonly Regex _before;
    private readonly ImmutableListMultimap<string, int> _groupIndexes;
    private readonly IReadOnlyList<Token> _tokens;
    private readonly ISet<string> _unusedGroups;
    private readonly Location _location;

    public RegexTemplateTokens(
        string template,
        IReadOnlyDictionary<string, Regex> regexGroups,
        bool repeatedGroups,
        Location location)
        : this(template, regexGroups, repeatedGroups, matchExactly: false, location)
    {
    }

    public RegexTemplateTokens(
        string template,
        IReadOnlyDictionary<string, Regex> regexGroups,
        bool repeatedGroups,
        bool matchExactly,
        Location location)
    {
        _template = Preconditions.CheckNotNull(template);

        _tokens = new Parser().Parse(template).ToList();
        _location = Preconditions.CheckNotNull(location);

        var groupIndexesBuilder = ImmutableListMultimap<string, int>.CreateBuilder();
        _before = BuildBefore(regexGroups, repeatedGroups, matchExactly, groupIndexesBuilder);
        _groupIndexes = groupIndexesBuilder.Build();

        var used = new HashSet<string>(_groupIndexes.Keys);
        _unusedGroups = new HashSet<string>(regexGroups.Keys.Where(k => !used.Contains(k)));
    }

    /// <summary>
    /// How this template can be used when it is the "before" value of core.replace - as a regex to
    /// search for.
    /// </summary>
    public Regex GetBefore() => _before;

    public IReadOnlyList<Token> GetTokens() => _tokens;

    public bool IsEmpty() => _template.Length == 0;

    /// <summary>Is this template regex-free.</summary>
    public bool IsLiteral() =>
        IsEmpty() || (_tokens.Count == 1 && _tokens[0].GetTokenType() == TokenType.Literal);

    public ImmutableListMultimap<string, int> GetGroupIndexes() => _groupIndexes;

    public Replacer CreateReplacer(
        RegexTemplateTokens after,
        bool firstOnly,
        bool multiline,
        IReadOnlyList<Regex>? patternsToIgnore) =>
        new(this, _before, after, callback: null, firstOnly, multiline, patternsToIgnore, _location);

    public Replacer CallbackReplacer(
        RegexTemplateTokens after,
        IAlterAfterTemplate callback,
        bool firstOnly,
        bool multiline,
        IReadOnlyList<Regex>? patternsToIgnore) =>
        new(this, _before, after, callback, firstOnly, multiline, patternsToIgnore, _location);

    public sealed class Replacer
    {
        private readonly RegexTemplateTokens _owner;
        private readonly Regex _before;
        private readonly RegexTemplateTokens _after;
        private readonly bool _firstOnly;
        private readonly bool _multiline;
        private readonly string _afterReplaceTemplate;
        private readonly ImmutableListMultimap<string, int> _repeatedGroups;
        private readonly Location _location;
        private readonly IReadOnlyList<Regex>? _patternsToIgnore;
        private readonly IAlterAfterTemplate? _callback;

        internal Replacer(
            RegexTemplateTokens owner,
            Regex before,
            RegexTemplateTokens after,
            IAlterAfterTemplate? callback,
            bool firstOnly,
            bool multiline,
            IReadOnlyList<Regex>? patternsToIgnore,
            Location location)
        {
            _owner = owner;
            _before = before;
            _after = after;
            _afterReplaceTemplate = _after.After(owner);
            // Precompute the repeated groups as this should be used only on rare occasions and we
            // don't want to iterate over the map for every line.
            var repeatedBuilder = ImmutableListMultimap<string, int>.CreateBuilder();
            foreach (var key in owner._groupIndexes.Keys)
            {
                var values = owner._groupIndexes.Get(key);
                if (values.Length > 1)
                {
                    repeatedBuilder.PutAll(key, values);
                }
            }
            _repeatedGroups = repeatedBuilder.Build();
            _firstOnly = firstOnly;
            _multiline = multiline;
            _callback = callback;
            _patternsToIgnore = patternsToIgnore;
            _location = location;
        }

        public string Replace(string content)
        {
            IReadOnlyList<string> originalRanges = _multiline
                ? new[] { content }
                : content.Split('\n');

            var newRanges = new List<string>(originalRanges.Count);
            foreach (string line in originalRanges)
            {
                newRanges.Add(ReplaceLine(line));
            }
            return string.Join("\n", newRanges);
        }

        private string ReplaceLine(string line)
        {
            if (_patternsToIgnore != null)
            {
                foreach (Regex patternToIgnore in _patternsToIgnore)
                {
                    if (IsFullMatch(patternToIgnore, line))
                    {
                        return line;
                    }
                }
            }

            var sb = new StringBuilder(line.Length);
            int lastAppend = 0;
            Match matcher = _before.Match(line);
            while (matcher.Success)
            {
                bool skipLine = false;
                foreach (var key in _repeatedGroups.Keys)
                {
                    // Check that all the references of the repeated group match the same string.
                    var groupIndexes = _repeatedGroups.Get(key);
                    string value = matcher.Groups[groupIndexes[0]].Value;
                    for (int i = 1; i < groupIndexes.Length; i++)
                    {
                        if (!value.Equals(matcher.Groups[groupIndexes[i]].Value))
                        {
                            skipLine = true;
                            break;
                        }
                    }
                    if (skipLine)
                    {
                        break;
                    }
                }
                if (skipLine)
                {
                    return line;
                }

                string replaceTemplate;
                if (_callback != null)
                {
                    var groupValues = new Dictionary<int, string>();
                    for (int i = 0; i <= GroupCount(matcher); i++)
                    {
                        groupValues[i] = matcher.Groups[i].Value;
                    }
                    replaceTemplate = _callback.Alter(groupValues, _afterReplaceTemplate);
                }
                else
                {
                    replaceTemplate = _afterReplaceTemplate;
                }

                // Emulate Matcher.appendReplacement: append the text between the last match and this
                // one, then the expanded replacement.
                sb.Append(line, lastAppend, matcher.Index - lastAppend);
                sb.Append(ExpandReplacement(replaceTemplate, matcher));
                lastAppend = matcher.Index + matcher.Length;

                if (_firstOnly)
                {
                    break;
                }
                matcher = matcher.NextMatch();
            }
            // appendTail.
            sb.Append(line, lastAppend, line.Length - lastAppend);
            return sb.ToString();
        }

        public override string ToString() =>
            $"s/{_owner}/{_after}/{(_firstOnly ? "" : "g")}";

        public Location GetLocation() => _location;

        public bool IsFirstOnly() => _firstOnly;
    }

    /// <summary>
    /// How this template can be used when it is the "after" value of core.replace - as a string to
    /// insert in place of the regex, possibly including $N, referring to captured groups.
    ///
    /// <para>Returns a template in which the literals are escaped (if they are a $ or {) and the
    /// interpolations appear as $N, where N is the group's index as given by <c>groupIndexes</c>.</para>
    /// </summary>
    private string After(RegexTemplateTokens before)
    {
        var template = new StringBuilder();
        foreach (Token token in _tokens)
        {
            switch (token.GetTokenType())
            {
                case TokenType.Interpolation:
                    template.Append('$').Append(before._groupIndexes.Get(token.GetValue())[0]);
                    break;
                case TokenType.Literal:
                    string value = token.GetValue();
                    for (int c = 0; c < value.Length; c++)
                    {
                        char thisChar = value[c];
                        if (thisChar == '$' || thisChar == '\\')
                        {
                            template.Append('\\');
                        }
                        template.Append(thisChar);
                    }
                    break;
            }
        }
        return template.ToString();
    }

    public override string ToString() =>
        _template
            .Replace("\n", "\\n")
            .Replace("\r", "\\r")
            .Replace("\t", "\\t");

    public override bool Equals(object? other)
    {
        if (other is RegexTemplateTokens comp)
        {
            return _before.ToString() == comp._before.ToString() && _tokens.SequenceEqual(comp._tokens);
        }
        return false;
    }

    public override int GetHashCode() => HashCode.Combine(_before.ToString(), TokensHash(_tokens));

    private static int TokensHash(IReadOnlyList<Token> tokens)
    {
        var hash = default(HashCode);
        foreach (var t in tokens)
        {
            hash.Add(t);
        }
        return hash.ToHashCode();
    }

    /// <summary>
    /// Converts this sequence of tokens into a regex which can be used to search a string. It
    /// automatically quotes literals and represents interpolations as named groups.
    ///
    /// <para>It also fills groupIndexes with all the interpolation locations.</para>
    /// </summary>
    private Regex BuildBefore(
        IReadOnlyDictionary<string, Regex> regexesByInterpolationName,
        bool repeatedGroups,
        bool matchExactly,
        ImmutableListMultimap<string, int>.Builder groupIndexes)
    {
        // Track the group counts registered per-name so far, to detect repeated usage.
        var seenNames = new HashSet<string>();
        int groupCount = 1;
        var fullPattern = new StringBuilder();
        if (matchExactly)
        {
            fullPattern.Append('^');
        }
        foreach (Token token in _tokens)
        {
            switch (token.GetTokenType())
            {
                case TokenType.Interpolation:
                    Regex? subPattern =
                        regexesByInterpolationName.TryGetValue(token.GetValue(), out var sp) ? sp : null;
                    Check(
                        subPattern != null,
                        "Interpolation is used but not defined: {0}",
                        token.GetValue());
                    fullPattern.Append($"({subPattern!})");
                    Check(
                        !seenNames.Contains(token.GetValue()) || repeatedGroups,
                        "Regex group is used in template multiple times: {0}. "
                        + "If you require multiple references to the same regex group, "
                        + "set `repeated_groups=True`.",
                        token.GetValue());
                    seenNames.Add(token.GetValue());
                    groupIndexes.Put(token.GetValue(), groupCount);
                    groupCount += GroupCount(subPattern!) + 1;
                    break;
                case TokenType.Literal:
                    fullPattern.Append(Regex.Escape(token.GetValue()));
                    break;
            }
        }
        if (matchExactly)
        {
            fullPattern.Append('$');
        }
        return new Regex(fullPattern.ToString(), RegexOptions.Multiline);
    }

    /// <summary>
    /// Checks that the set of interpolated tokens matches <c>definedInterpolations</c>.
    /// </summary>
    /// <exception cref="EvalException">if not all interpolations are used in this template.</exception>
    public void ValidateUnused()
    {
        Check(
            _unusedGroups.Count == 0,
            "Following interpolations are defined but not used: [{0}]",
            string.Join(", ", _unusedGroups));
    }

    // Port of com.google.copybara.starlark.StarlarkUtil.check: throws EvalException when false.
    private static void Check(bool condition, string format, params object?[] args)
    {
        if (!condition)
        {
            throw Starlark.Eval.Starlark.Errorf(format, args);
        }
    }

    // Number of capturing groups in a regex (excluding the whole-match group 0), matching
    // re2j/java.util.regex Pattern.groupCount().
    private static int GroupCount(Regex regex) => regex.GetGroupNumbers().Length - 1;

    private static int GroupCount(Match match) => match.Groups.Count - 1;

    private static bool IsFullMatch(Regex regex, string input)
    {
        Match m = regex.Match(input);
        return m.Success && m.Index == 0 && m.Length == input.Length;
    }

    // Expands a replacement template containing $N group references against the given match,
    // emulating java.util.regex Matcher.appendReplacement semantics (\\ and $ are escapes).
    private static string ExpandReplacement(string template, Match match)
    {
        var sb = new StringBuilder();
        for (int i = 0; i < template.Length; i++)
        {
            char ch = template[i];
            if (ch == '\\')
            {
                i++;
                if (i < template.Length)
                {
                    sb.Append(template[i]);
                }
            }
            else if (ch == '$')
            {
                i++;
                var num = new StringBuilder();
                while (i < template.Length && char.IsDigit(template[i]))
                {
                    num.Append(template[i]);
                    i++;
                }
                i--; // step back; the for loop will advance.
                if (num.Length > 0)
                {
                    int groupNum = int.Parse(num.ToString());
                    if (groupNum < match.Groups.Count && match.Groups[groupNum].Success)
                    {
                        sb.Append(match.Groups[groupNum].Value);
                    }
                }
            }
            else
            {
                sb.Append(ch);
            }
        }
        return sb.ToString();
    }

    /// <summary>Callback for <see cref="CallbackReplacer"/>.</summary>
    public interface IAlterAfterTemplate
    {
        /// <summary>
        /// Upon encountering a match, the replacer will call the callback with the matched groups
        /// and the template to be used in the replace. The return value of this function will be
        /// used in place of <c>template</c>, i.e. group tokens like '$1' in the return value will be
        /// replaced with the group values. Note that the groupValues are immutable.
        /// </summary>
        /// <param name="groupValues">
        /// The values of the groups in the before pattern. 0 holds the entire match.
        /// </param>
        /// <param name="template">The replacement template the replacer would normally use.</param>
        /// <returns>The template to be used instead.</returns>
        string Alter(IReadOnlyDictionary<int, string> groupValues, string template);
    }
}
