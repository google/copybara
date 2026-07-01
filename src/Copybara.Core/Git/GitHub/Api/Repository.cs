/*
 * Copyright (C) 2024 Google LLC
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
/// This class is only used to represent a GitHub Repository object returned by the GitHub REST API,
/// see https://docs.github.com/en/rest/repos/repos?apiVersion=2022-11-28#get-a-repository.
/// </summary>
public class Repository : IStarlarkValue
{
    [JsonPropertyName("default_branch")]
    public string? DefaultBranch { get; set; }

    [JsonPropertyName("html_url")]
    public string? HtmlUrl { get; set; }

    [JsonPropertyName("fork")]
    public bool Fork { get; set; }

    [JsonPropertyName("id")]
    public int Id { get; set; }

    [StarlarkMethod("id", Doc = "Release id", StructField = true)]
    public string? GetDefaultBranch() => DefaultBranch;

    [StarlarkMethod("html_url", Doc = "HTML URL of the reference", StructField = true)]
    public string? GetHtmlUrl() => HtmlUrl;

    [StarlarkMethod("fork", Doc = "Whether the reference is a fork", StructField = true)]
    public bool GetIsFork() => Fork;

    public int GetId() => Id;
}
