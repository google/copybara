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

using System.Text.Json.Serialization;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// A Release object.
/// https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28#create-a-release
/// </summary>
[StarlarkBuiltin(
    "github_release_obj",
    Doc = "GitHub API value type for a release. See "
        + "https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28#create-a-release")]
public class Release : IStarlarkValue
{
    [JsonPropertyName("id")]
    public int Id { get; set; }

    [JsonPropertyName("tarball_url")]
    public string? Tarball { get; set; }

    [JsonPropertyName("zipball_url")]
    public string? Zip { get; set; }

    [StarlarkMethod("id", Doc = "Release id", StructField = true)]
    public int GetId() => Id;

    [StarlarkMethod("tarball", Doc = "Tarball Url", StructField = true)]
    public string? GetTarball() => Tarball;

    [StarlarkMethod("zip", Doc = "Zip Url", StructField = true)]
    public string? GetZip() => Zip;
}
