/*
 * Copyright (C) 2019 Google Inc.
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

/// <summary>Represents a GitHub App detail. https://developer.github.com/v3/apps/#response</summary>
[StarlarkBuiltin("github_app_obj", Doc = "Detail about a GitHub App.")]
public class GitHubApp : IStarlarkValue
{
    [JsonPropertyName("id")]
    public int Id { get; set; }

    [JsonPropertyName("slug")]
    public string? Slug { get; set; }

    [JsonPropertyName("name")]
    public string? Name { get; set; }

    [StarlarkMethod("id", Doc = "The GitHub App's Id", StructField = true)]
    public int GetId() => Id;

    [StarlarkMethod("slug", Doc = "The url-friendly name of the GitHub App.", StructField = true)]
    public string? GetSlug() => Slug;

    [StarlarkMethod("name", Doc = "The GitHub App's name", StructField = true, AllowReturnNones = true)]
    public string? GetName() => Name;

    public override string ToString() => $"GitHubApp{{id={Id}, slug={Slug}, name={Name}}}";
}
