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
/// Input for creating a release.
/// https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28#create-a-release
/// </summary>
[StarlarkBuiltin(
    "github_create_release_obj",
    Doc = "GitHub API value type for release params. See "
        + "https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28#create-a-release")]
public class CreateReleaseRequest : IStarlarkValue
{
    [JsonPropertyName("tag_name")]
    public string? TagName { get; set; }

    [JsonPropertyName("body")]
    public string? Body { get; set; }

    [JsonPropertyName("name")]
    public string? Name { get; set; }

    [JsonPropertyName("target_commitish")]
    public string? TargetCommitish { get; set; }

    [JsonPropertyName("prerelease")]
    public bool? PreRelease { get; set; }

    [JsonPropertyName("draft")]
    public bool? Draft { get; set; }

    [JsonPropertyName("make_latest")]
    public bool? MakeLatest { get; set; }

    [JsonPropertyName("generate_release_notes")]
    public bool? GenerateReleaseNotes { get; set; }

    public CreateReleaseRequest(string tagName)
    {
        TagName = tagName;
    }

    public CreateReleaseRequest()
    {
        // just for reflection.
    }

    [StarlarkMethod(
        "with_body",
        Doc = "Set the body for the release.")]
    public CreateReleaseRequest WithBody([Param(Name = "body", Doc = "Body for the release")] string body)
    {
        Body = body;
        return this;
    }

    [StarlarkMethod(
        "with_name",
        Doc = "Set the name for the release.")]
    public CreateReleaseRequest WithName([Param(Name = "name", Doc = "Name for the release")] string name)
    {
        Name = name;
        return this;
    }

    [StarlarkMethod(
        "with_commitish",
        Doc = "Set the commitish to be used for the release. Defaults to HEAD")]
    public CreateReleaseRequest WithCommitish(
        [Param(Name = "commitish", Doc = "Commitish for the release")] string targetCommitish)
    {
        TargetCommitish = targetCommitish;
        return this;
    }

    [StarlarkMethod(
        "set_draft",
        Doc = "Is this a draft release?")]
    public CreateReleaseRequest WithDraft([Param(Name = "draft", Doc = "Mark release as draft?")] bool draft)
    {
        Draft = draft;
        return this;
    }

    [StarlarkMethod(
        "set_latest",
        Doc = "Is this the latest release?")]
    public CreateReleaseRequest WithMakeLatest(
        [Param(Name = "make_latest", Doc = "Mark release as latest?")] bool makeLatest)
    {
        MakeLatest = makeLatest;
        return this;
    }

    [StarlarkMethod(
        "set_prerelease",
        Doc = "Is this a prerelease?")]
    public CreateReleaseRequest WithPreRelease(
        [Param(Name = "prerelease", Doc = "Mark release as prerelease?")] bool preRelease)
    {
        PreRelease = preRelease;
        return this;
    }

    [StarlarkMethod(
        "set_generate_release_notes",
        Doc = "Generate release notes?")]
    public CreateReleaseRequest WithGenerateReleaseNotes(
        [Param(Name = "generate_notes", Doc = "Generate notes?")] bool generateReleaseNotes)
    {
        GenerateReleaseNotes = generateReleaseNotes;
        return this;
    }

    public string? GetTagName() => TagName;

    public string? GetBody() => Body;

    public string? GetName() => Name;

    public string? GetTargetCommitish() => TargetCommitish;
}
