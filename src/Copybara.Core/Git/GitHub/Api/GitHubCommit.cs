/*
 * Copyright (C) 2017 Google Inc.
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

/// <summary>Represents GitHub information for a commit.</summary>
[StarlarkBuiltin(
    "github_api_github_commit_obj",
    Doc =
        "Information about a commit as defined in"
        + " https://developer.github.com/v3/git/commits/#get-a-commit."
        + " This is a subset of the available fields in GitHub")]
public class GitHubCommit : IStarlarkValue
{
    [JsonPropertyName("sha")]
    public string? Sha { get; set; }

    [JsonPropertyName("url")]
    public string? Url { get; set; }

    [JsonPropertyName("html_url")]
    public string? HtmlUrl { get; set; }

    [JsonPropertyName("author")]
    public User? Author { get; set; }

    [JsonPropertyName("committer")]
    public User? Committer { get; set; }

    [JsonPropertyName("commit")]
    public Commit? CommitData { get; set; }

    [StarlarkMethod("sha", Doc = "SHA of the commit", StructField = true)]
    public string? GetSha() => Sha;

    public string? GetUrl() => Url;

    [StarlarkMethod("html_url", Doc = "GitHub url for the commit", StructField = true)]
    public string? GetHtmlUrl() => HtmlUrl;

    [StarlarkMethod(
        "author", Doc = "GitHub information about the author of the change", StructField = true)]
    public User? GetAuthor() => Author;

    [StarlarkMethod(
        "committer", Doc = "GitHub information about the committer of the change", StructField = true)]
    public User? GetCommitter() => Committer;

    [StarlarkMethod(
        "commit",
        Doc = "Information about the commit, like the message or git commit author/committer",
        StructField = true)]
    public Commit? GetCommit() => CommitData;

    public override string ToString() =>
        $"GitHubCommit{{sha={Sha}, url={Url}, html_url={HtmlUrl}, author={Author},"
        + $" committer={Committer}, commit={CommitData}}}";
}
