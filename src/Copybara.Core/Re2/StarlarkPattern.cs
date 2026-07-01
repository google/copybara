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

namespace Copybara.Re2;

/// <summary>A Starlark wrapper of a regex pattern.</summary>
/// <remarks>
/// NOTE(port): upstream uses re2j (RE2 semantics). This port backs regexes with
/// <see cref="System.Text.RegularExpressions"/>, an accepted deviation. Most patterns behave
/// identically, but RE2-specific behaviors (e.g. linear-time guarantees, some syntax) may diverge.
/// </remarks>
[StarlarkBuiltin(
    "re2_pattern",
    Doc = "A RE2 regex pattern object to perform regexes in Starlark")]
public sealed class StarlarkPattern : IStarlarkValue
{
    private readonly Regex _pattern;

    public StarlarkPattern(Regex pattern)
    {
        _pattern = pattern;
    }

    /// <summary>The underlying compiled pattern.</summary>
    public Regex Pattern => _pattern;

    [StarlarkMethod(
        "matches",
        Doc = "Return true if the string matches the regex pattern")]
    public bool Matches(
        [Param(Name = "input", Named = true)] string input)
    {
        // Java's Pattern.matches requires the whole input to match the pattern.
        var m = _pattern.Match(input);
        return m.Success && m.Index == 0 && m.Length == input.Length;
    }

    [StarlarkMethod(
        "matcher",
        Doc = "Return a Matcher for the given input.")]
    public StarlarkMatcher Matcher(
        [Param(Name = "input", Named = true)] string input) =>
        new(_pattern, input);
}
