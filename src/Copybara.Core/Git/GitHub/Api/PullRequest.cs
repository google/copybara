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

using System.Collections.Immutable;
using System.Text.Json.Serialization;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// Represents a pull request returned by https://api.github.com/repos/REPO_ID/pulls/NUMBER.
/// </summary>
[StarlarkBuiltin(
    "github_api_pull_request_obj",
    Doc =
        "Information about a pull request as defined in"
        + " https://docs.github.com/en/rest/reference/pulls. This is a subset of the available"
        + " fields in GitHub")]
public class PullRequest : PullRequestOrIssue
{
    [JsonPropertyName("head")]
    public Revision? Head { get; set; }

    [JsonPropertyName("base")]
    public Revision? Base { get; set; }

    [JsonPropertyName("requested_reviewers")]
    public List<User>? RequestedReviewers { get; set; }

    [JsonPropertyName("mergeable")]
    public bool? Mergeable { get; set; }

    [JsonPropertyName("merged")]
    public bool? Merged { get; set; }

    [JsonPropertyName("mergeable_state")]
    public string? MergeableState { get; set; }

    [JsonPropertyName("draft")]
    public bool Draft { get; set; }

    [JsonPropertyName("commits")]
    public int? Commits { get; set; }

    [StarlarkMethod("head", Doc = "Information about head", StructField = true)]
    public Revision? GetHead() => Head;

    [StarlarkMethod("base", Doc = "Information about base", StructField = true)]
    public Revision? GetBase() => Base;

    [StarlarkMethod("draft", Doc = "Whether pull request is a draft", StructField = true)]
    public bool GetDraft() => Draft;

    [StarlarkMethod("merged", Doc = "Whether pull request has been merged", StructField = true)]
    public bool GetMerged() => Merged ?? false;

    [StarlarkMethod("commits", Doc = "Number of commits in the PR", StructField = true)]
    public StarlarkInt GetCommits() => StarlarkInt.Of(Commits ?? 0);

    public bool? IsMergeable() => Mergeable;

    public string? GetMergeableState() => MergeableState;

    public IReadOnlyList<User> GetRequestedReviewers() =>
        RequestedReviewers == null
            ? ImmutableArray<User>.Empty
            : RequestedReviewers.ToImmutableArray();

    public override string ToString() =>
        base.ToString() + $"{{head={Head}, base={Base}, mergeable={Mergeable}}}";
}
