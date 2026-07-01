/*
 * Copyright (C) 2018 Google Inc.
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
/// Represents issue comments returned by https://api.github.com/repos/REPO_ID/issues/comments.
/// </summary>
[StarlarkBuiltin(
    "github_api_issue_comment_obj",
    Doc =
        "Information about an issue comment as defined in"
        + " https://docs.github.com/en/rest/issues/comments. This is a subset of the available"
        + " fields in GitHub")]
public class IssueComment : IStarlarkValue
{
    [JsonPropertyName("id")]
    public long Id { get; set; }

    [JsonPropertyName("user")]
    public User? UserValue { get; set; }

    [JsonPropertyName("body")]
    public string? Body { get; set; }

    [JsonPropertyName("author_association")]
    public string? AuthorAssociationRaw { get; set; }

    [JsonPropertyName("created_at")]
    public string? CreatedAt { get; set; }

    [JsonPropertyName("updated_at")]
    public string? UpdatedAt { get; set; }

    public AuthorAssociation GetAuthorAssociation() =>
        AuthorAssociationRaw == null
            ? AuthorAssociation.NONE
            : Enum.Parse<AuthorAssociation>(AuthorAssociationRaw);

    [StarlarkMethod("id", Doc = "Comment identifier", StructField = true)]
    public long GetId() => Id;

    [StarlarkMethod("user", Doc = "Comment user", StructField = true)]
    public User? GetUser() => UserValue;

    [StarlarkMethod("body", Doc = "Body of the comment", StructField = true)]
    public string? GetBody() => Body;

    public DateTimeOffset GetCreatedAt() => DateTimeOffset.Parse(CreatedAt!);

    public DateTimeOffset GetUpdatedAt() => DateTimeOffset.Parse(UpdatedAt!);

    public override string ToString() =>
        $"IssueComment{{id={Id}, user={UserValue}, body={Body},"
        + $" authorAssociation={AuthorAssociationRaw}, createdAt={CreatedAt}, updatedAt={UpdatedAt}}}";
}
