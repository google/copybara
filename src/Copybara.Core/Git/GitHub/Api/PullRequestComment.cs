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

/// <summary>
/// Represents pull request comments returned by:
///
/// <list type="bullet">
///   <item>https://api.github.com/repos/REPO_ID/pulls/PR_NUMBER/comments</item>
///   <item>https://api.github.com/repos/REPO_ID/pulls/comments/COMMENT_ID</item>
/// </list>
/// </summary>
[StarlarkBuiltin(
    "github_api_pull_request_comment_obj",
    Doc =
        "Information about a pull request comment as defined in"
        + " https://developer.github.com/v3/pulls/comments/. This is a subset of the available"
        + " fields in GitHub")]
public class PullRequestComment : IStarlarkValue
{
    [JsonPropertyName("id")]
    public long Id { get; set; }

    [JsonPropertyName("diff_hunk")]
    public string? DiffHunk { get; set; }

    [JsonPropertyName("path")]
    public string? Path { get; set; }

    [JsonPropertyName("position")]
    public int? Position { get; set; }

    [JsonPropertyName("original_position")]
    public int? OriginalPosition { get; set; }

    [JsonPropertyName("commit_id")]
    public string? CommitId { get; set; }

    [JsonPropertyName("original_commit_id")]
    public string? OriginalCommitId { get; set; }

    [JsonPropertyName("user")]
    public User? UserValue { get; set; }

    [JsonPropertyName("body")]
    public string? Body { get; set; }

    [JsonPropertyName("created_at")]
    public string? CreatedAt { get; set; }

    [JsonPropertyName("updated_at")]
    public string? UpdatedAt { get; set; }

    [StarlarkMethod("id", Doc = "Comment identifier", StructField = true)]
    public string GetIdAsStr() => Id.ToString();

    public long GetId() => Id;

    [StarlarkMethod("user", Doc = "The user who posted the comment", StructField = true)]
    public User? GetUser() => UserValue;

    [StarlarkMethod("body", Doc = "Body of the comment", StructField = true)]
    public string? GetBody() => Body;

    [StarlarkMethod("position", Doc = "Position of the comment", StructField = true)]
    public int GetPosition() => Position ?? 0;

    [StarlarkMethod("original_position", Doc = "Original position of the comment", StructField = true)]
    public int GetOriginalPosition() => OriginalPosition ?? 0;

    public string? GetCommitId() => CommitId;

    public string? GetOriginalCommitId() => OriginalCommitId;

    [StarlarkMethod("diff_hunk", Doc = "The diff hunk where the comment was posted", StructField = true)]
    public string? GetDiffHunk() => DiffHunk;

    [StarlarkMethod("path", Doc = "The file path", StructField = true)]
    public string? GetPath() => Path;

    public DateTimeOffset GetCreatedAt() => DateTimeOffset.Parse(CreatedAt!);

    public DateTimeOffset GetUpdatedAt() => DateTimeOffset.Parse(UpdatedAt!);

    public override string ToString() =>
        $"PullRequestComment{{id={Id}, user={UserValue}, body={Body}, createdAt={CreatedAt},"
        + $" updatedAt={UpdatedAt}}}";
}
