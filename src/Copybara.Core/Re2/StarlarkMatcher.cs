/*
 * Copyright (C) 2022 Google Inc.
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

using System.Text.RegularExpressions;
using Starlark.Annot;
using Starlark.Eval;

using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Re2;

/// <summary>A Starlark wrapper of a regex matcher, mirroring re2j's <c>Matcher</c>.</summary>
/// <remarks>
/// NOTE(port): re2j's <c>Matcher</c> is stateful. This port emulates that state on top of
/// <see cref="System.Text.RegularExpressions.Regex"/>: <see cref="Matches"/> attempts a full-input
/// match, <see cref="Find"/> scans for the next match, and the query methods (<c>group</c>,
/// <c>start</c>, <c>end</c>) operate on the most recent successful match.
/// </remarks>
[StarlarkBuiltin(
    "re2_matcher",
    Doc = "A RE2 regex pattern matcher object to perform regexes in Starlark")]
public sealed class StarlarkMatcher : IStarlarkValue
{
    private readonly Regex _pattern;
    private readonly string _input;
    private Match? _match;

    public StarlarkMatcher(Regex pattern, string input)
    {
        _pattern = pattern;
        _input = input;
    }

    [StarlarkMethod("matches", Doc = "Return true if the string matches the regex pattern.")]
    public bool Matches()
    {
        var m = _pattern.Match(_input);
        if (m.Success && m.Index == 0 && m.Length == _input.Length)
        {
            _match = m;
            return true;
        }
        _match = null;
        return false;
    }

    [StarlarkMethod(
        "find",
        Doc = "Return true if the string matches the regex pattern.")]
    public bool Find(
        [Param(Name = "start", Doc = "The input position where the search begins", Named = true,
            AllowedTypes = new[] { typeof(StarlarkInt), typeof(NoneType) },
            DefaultValue = "None")]
        object start)
    {
        Match m;
        if (StarlarkRt.IsNullOrNone(start))
        {
            int from = _match is { Success: true }
                ? (_match.Length == 0 ? _match.Index + 1 : _match.Index + _match.Length)
                : 0;
            m = from <= _input.Length ? _pattern.Match(_input, from) : Match.Empty;
        }
        else
        {
            int from = ((StarlarkInt)start).ToInt("start");
            m = _pattern.Match(_input, from);
        }

        _match = m.Success ? m : null;
        return m.Success;
    }

    [StarlarkMethod(
        "start",
        Doc = "Return the start position of a matching group")]
    public int Start(
        [Param(Name = "group", Named = true,
            AllowedTypes = new[] { typeof(StarlarkInt), typeof(string) },
            DefaultValue = "0")]
        object group)
    {
        var g = RequireGroup(group, "start()");
        return g.Success ? g.Index : -1;
    }

    [StarlarkMethod(
        "group",
        Doc = "Return a matching group")]
    public string Group(
        [Param(Name = "group", Named = true,
            AllowedTypes = new[] { typeof(StarlarkInt), typeof(string) },
            DefaultValue = "0")]
        object group)
    {
        var g = RequireGroup(group, "group()");
        return g.Success ? g.Value : "";
    }

    [StarlarkMethod(
        "end",
        Doc = "Return the end position of a matching group")]
    public int End(
        [Param(Name = "group", Named = true,
            AllowedTypes = new[] { typeof(StarlarkInt), typeof(string) },
            DefaultValue = "0")]
        object group)
    {
        var g = RequireGroup(group, "end()");
        return g.Success ? g.Index + g.Length : -1;
    }

    [StarlarkMethod("group_count", Doc = "Return the number of groups found for a match")]
    public int GroupCount() =>
        // Java's groupCount does not count the whole-match group 0.
        _pattern.GetGroupNumbers().Length - 1;

    [StarlarkMethod(
        "replace_all",
        Doc = "Replace all instances matching the regex")]
    public string ReplaceAll(
        [Param(Name = "replacement", Named = true, DefaultValue = "0")] string replacement) =>
        _pattern.Replace(_input, ConvertReplacement(replacement));

    [StarlarkMethod(
        "replace_first",
        Doc = "Replace the first instance matching the regex")]
    public string ReplaceFirst(
        [Param(Name = "replacement", Named = true, DefaultValue = "0")] string replacement) =>
        _pattern.Replace(_input, ConvertReplacement(replacement), 1);

    private Group RequireGroup(object group, string method)
    {
        if (_match is not { } match)
        {
            throw new EvalException(
                $"Call to {method} is not allowed before calling matches()");
        }

        try
        {
            return group is string name
                ? match.Groups[name]
                : match.Groups[((StarlarkInt)group).ToInt("group")];
        }
        catch (Exception e) when (e is ArgumentException or IndexOutOfRangeException)
        {
            throw new EvalException($"Invalid group for {method}", e);
        }
    }

    // NOTE(port): re2j / Java use $N and ${name} for group references, which matches .NET's
    // substitution syntax, so replacement templates are passed through unchanged.
    private static string ConvertReplacement(string replacement) => replacement;
}
