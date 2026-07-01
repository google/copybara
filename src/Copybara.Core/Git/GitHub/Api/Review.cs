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
using Copybara.Exceptions;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// Represents a pull request review element returned by
/// https://api.github.com/repos/REPO_ID/pulls/NUMBER/reviews.
/// </summary>
public class Review
{
    [JsonPropertyName("id")]
    public long Id { get; set; }

    [JsonPropertyName("user")]
    public User? UserValue { get; set; }

    [JsonPropertyName("body")]
    public string? Body { get; set; }

    [JsonPropertyName("commit_id")]
    public string? CommitId { get; set; }

    [JsonPropertyName("state")]
    public string? State { get; set; }

    [JsonPropertyName("author_association")]
    public string? AuthorAssociationRaw { get; set; }

    public long GetId() => Id;

    public User? GetUser() => UserValue;

    public string? GetBody() => Body;

    public string? GetCommitId() => CommitId;

    public string? GetState() => State;

    public bool IsApproved() => "APPROVED".Equals(GetState());

    /// <exception cref="RepoException">if the author association value cannot be parsed.</exception>
    public AuthorAssociation GetAuthorAssociation()
    {
        if (AuthorAssociationRaw == null)
        {
            return AuthorAssociation.NONE;
        }

        if (Enum.TryParse<AuthorAssociation>(AuthorAssociationRaw, out var parsed))
        {
            return parsed;
        }

        throw new RepoException(
            $"Unable to parse Review notification, got unexpected state value {AuthorAssociationRaw}");
    }

    public override string ToString() =>
        $"Review{{id={Id}, user={UserValue}, body={Body}, commitId={CommitId}, state={State}}}";
}
