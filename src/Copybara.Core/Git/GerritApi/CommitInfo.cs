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

namespace Copybara.Git.GerritApi;

/// <summary>https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#commit-info</summary>
[StarlarkBuiltin("gerritapi.CommitInfo", Doc = "Gerrit commit information.")]
public class CommitInfo : IStarlarkPrintableValue
{
    [JsonPropertyName("commit")]
    public string? Commit { get; set; }

    [JsonPropertyName("parents")]
    public IReadOnlyList<ParentCommitInfo>? Parents { get; set; }

    [JsonPropertyName("author")]
    public GitPersonInfo? Author { get; set; }

    [JsonPropertyName("committer")]
    public GitPersonInfo? Committer { get; set; }

    [JsonPropertyName("subject")]
    public string? Subject { get; set; }

    [JsonPropertyName("message")]
    public string? Message { get; set; }

    [StarlarkMethod(
        "commit",
        Doc =
            "The commit ID. Not set if included in a RevisionInfo entity that is contained "
            + "in a map which has the commit ID as key.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetCommit() => Commit;

    public IReadOnlyList<ParentCommitInfo> GetParents() =>
        Parents is null ? ImmutableArray<ParentCommitInfo>.Empty : Parents.ToImmutableArray();

    [StarlarkMethod(
        "parents",
        Doc =
            "The parent commits of this commit as a list of CommitInfo entities. "
            + "In each parent only the commit and subject fields are populated.",
        StructField = true)]
    public IReadOnlyList<ParentCommitInfo> GetMessagesForSkylark() => GetParents();

    [StarlarkMethod(
        "author",
        Doc = "The author of the commit as a GitPersonInfo entity.",
        StructField = true,
        AllowReturnNones = true)]
    public GitPersonInfo? GetAuthor() => Author;

    [StarlarkMethod(
        "committer",
        Doc = "The committer of the commit as a GitPersonInfo entity.",
        StructField = true,
        AllowReturnNones = true)]
    public GitPersonInfo? GetCommitter() => Committer;

    [StarlarkMethod(
        "subject",
        Doc = "The subject of the commit (header line of the commit message).",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetSubject() => Subject;

    [StarlarkMethod(
        "message",
        Doc = "The commit message.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetMessage() => Message;

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());

    public override string ToString() =>
        $"CommitInfo{{commit={Commit}, parents={Parents}, author={Author}, "
        + $"committer={Committer}, subject={Subject}, message={Message}}}";
}
