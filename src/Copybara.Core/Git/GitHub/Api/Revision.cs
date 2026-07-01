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

using System.Text.Json.Serialization;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// Represents a revision: information about the origin of a pull request like the ref (branch) or
/// specific SHA-1.
/// </summary>
[StarlarkBuiltin(
    "github_api_revision_obj",
    Doc = "Information about a GitHub revision (Used in Pull Request and other entities)")]
public class Revision : IStarlarkValue
{
    [JsonPropertyName("label")]
    public string? Label { get; set; }

    [JsonPropertyName("ref")]
    public string? Ref { get; set; }

    [JsonPropertyName("sha")]
    public string? Sha { get; set; }

    [JsonPropertyName("repo")]
    public Repository? Repo { get; set; }

    [StarlarkMethod("label", Doc = "Label for the revision", StructField = true)]
    public string? GetLabel() => Label;

    [StarlarkMethod("ref", Doc = "Reference", StructField = true)]
    public string? GetRef() => Ref;

    [StarlarkMethod("sha", Doc = "SHA of the reference", StructField = true)]
    public string? GetSha() => Sha;

    [StarlarkMethod("repo", Doc = "Repository", StructField = true)]
    public Repository? GetRepo() => Repo;

    public override string ToString() =>
        $"Revision{{label={Label}, ref={Ref}, sha={Sha}, repo={Repo}}}";
}
