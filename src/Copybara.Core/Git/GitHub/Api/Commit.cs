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

/// <summary>Represents the current status of a ref, as returned by the git/refs API call.</summary>
[StarlarkBuiltin(
    "github_api_commit_obj",
    Doc =
        "Commit field for GitHub commit information"
        + " https://developer.github.com/v3/git/commits/#get-a-commit."
        + " This is a subset of the available fields in GitHub")]
public class Commit : IStarlarkValue
{
    [JsonPropertyName("message")]
    public string? Message { get; set; }

    [JsonPropertyName("author")]
    public CommitAuthor? Author { get; set; }

    [JsonPropertyName("committer")]
    public CommitAuthor? Committer { get; set; }

    [StarlarkMethod("message", Doc = "Message of the commit", StructField = true)]
    public string? GetMessage() => Message;

    [StarlarkMethod("author", Doc = "Author of the commit", StructField = true)]
    public CommitAuthor? GetAuthor() => Author;

    [StarlarkMethod("committer", Doc = "Committer of the commit", StructField = true)]
    public CommitAuthor? GetCommitter() => Committer;

    [StarlarkBuiltin(
        "github_api_commit_author_obj",
        Doc =
            "Author/Committer for commit field for GitHub commit information"
            + " https://developer.github.com/v3/git/commits/#get-a-commit."
            + " This is a subset of the available fields in GitHub")]
    public class CommitAuthor : IStarlarkValue
    {
        [JsonPropertyName("name")]
        public string? Name { get; set; }

        [JsonPropertyName("email")]
        public string? Email { get; set; }

        [JsonPropertyName("date")]
        public string? Date { get; set; }

        public DateTimeOffset GetDate() => DateTimeOffset.Parse(Date!);

        [StarlarkMethod("date", Doc = "Date of the commit", StructField = true)]
        public string? GetDateForSkylark() => Date;

        [StarlarkMethod("name", Doc = "Name of the author/committer", StructField = true)]
        public string? GetName() => Name;

        [StarlarkMethod("email", Doc = "Email of the author/committer", StructField = true)]
        public string? GetEmail() => Email;

        public override string ToString() =>
            $"CommitAuthor{{name={Name}, email={Email}, date={Date}}}";
    }

    public override string ToString() =>
        $"Commit{{message={Message}, author={Author}, committer={Committer}}}";
}
