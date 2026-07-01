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

using Copybara.Exceptions;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Toml;

/// <summary>Module for parsing TOML in Starlark.</summary>
/// <remarks>
/// NOTE(port): upstream uses the tomlj library. This port uses a small hand-rolled TOML reader (see
/// <see cref="TomlParser"/>); see that type for supported features and gaps.
/// </remarks>
[StarlarkBuiltin("toml", Doc = "Module for parsing TOML in Copybara.")]
public sealed class TomlModule : IStarlarkValue
{
    [StarlarkMethod("parse", Doc = "Parse the TOML content. Returns a toml object.")]
    public TomlContent Parse(
        [Param(Name = "content", Doc = "TOML content to be parsed", Named = true,
            AllowedTypes = new[] { typeof(string) })]
        string tomlContent)
    {
        var result = TomlParser.Parse(tomlContent);

        ValidationException.CheckCondition(
            !result.HasErrors,
            "There were errors parsing the TOML string. Errors: {0}",
            string.Join(", ", result.Errors));

        return new TomlContent(result.Root);
    }
}
