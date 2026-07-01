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

/// <summary>Regex functions to work with re2 like regexes in Starlark.</summary>
/// <remarks>
/// NOTE(port): upstream uses re2j; this port uses <see cref="System.Text.RegularExpressions"/>,
/// an accepted deviation.
/// </remarks>
[StarlarkBuiltin("re2", Doc = "Set of functions to work with regexes in Copybara.")]
public sealed class Re2Module : IStarlarkValue
{
    [StarlarkMethod("compile", Doc = "Create a regex pattern")]
    public StarlarkPattern Compile(
        [Param(Name = "regex")] string regex)
    {
        try
        {
            return new StarlarkPattern(new Regex(regex));
        }
        catch (ArgumentException e)
        {
            throw new EvalException($"Unable to parse regex '{regex}'.", e);
        }
    }

    [StarlarkMethod(
        "quote",
        Doc = "Quote a string to be matched literally if used within a regex pattern")]
    public string Quote(
        [Param(Name = "string")] string @string) =>
        Regex.Escape(@string);
}
