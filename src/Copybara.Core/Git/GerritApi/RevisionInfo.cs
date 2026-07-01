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

/// <summary>See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#revision-info</summary>
[StarlarkBuiltin("gerritapi.RevisionInfo", Doc = "Gerrit revision information.")]
public class RevisionInfo : IStarlarkPrintableValue
{
    [JsonPropertyName("kind")]
    public string? KindString { get; set; }

    [JsonPropertyName("_number")]
    public int PatchsetNumber { get; set; }

    [JsonPropertyName("created")]
    public string? Created { get; set; }

    [JsonPropertyName("uploader")]
    public AccountInfo? Uploader { get; set; }

    [JsonPropertyName("ref")]
    public string? Ref { get; set; }

    [JsonPropertyName("fetch")]
    public IReadOnlyDictionary<string, FetchInfo>? Fetch { get; set; }

    [JsonPropertyName("commit")]
    public CommitInfo? Commit { get; set; }

    public RevisionKind GetKind() => Enum.Parse<RevisionKind>(KindString!);

    [StarlarkMethod(
        "kind",
        Doc =
            "The change kind. Valid values are REWORK, TRIVIAL_REBASE, MERGE_FIRST_PARENT_UPDATE, "
            + "NO_CODE_CHANGE, and NO_CHANGE.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetKindAsString() => KindString;

    [StarlarkMethod(
        "patchset_number",
        Doc = "The patch set number, or edit if the patch set is an edit.",
        StructField = true)]
    public int GetPatchsetNumber() => PatchsetNumber;

    [StarlarkMethod(
        "created",
        Doc = "The timestamp of when the patch set was created.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetCreated() => Created;

    [StarlarkMethod(
        "uploader",
        Doc = "The uploader of the patch set as an AccountInfo entity.",
        StructField = true,
        AllowReturnNones = true)]
    public AccountInfo? GetUploader() => Uploader;

    [StarlarkMethod(
        "ref",
        Doc = "The Git reference for the patch set.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetRef() => Ref;

    public IReadOnlyDictionary<string, FetchInfo> GetFetch() =>
        Fetch is null ? ImmutableDictionary<string, FetchInfo>.Empty : Fetch.ToImmutableDictionary();

    [StarlarkMethod(
        "commit",
        Doc = "The commit of the patch set as CommitInfo entity.",
        StructField = true,
        AllowReturnNones = true)]
    public CommitInfo? GetCommit() => Commit;

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());

    public override string ToString() =>
        $"RevisionInfo{{kind={KindString}, patchsetNumber={PatchsetNumber}, created={Created}, "
        + $"uploader={Uploader}, ref={Ref}, fetch={Fetch}, commit={Commit}}}";
}

/// <summary>
/// See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#revision-info
/// </summary>
/// <remarks>NOTE(port): Java nests this as <c>RevisionInfo.Kind</c>; here it is a sibling type named
/// <c>RevisionKind</c> to avoid a naming collision within the same namespace.</remarks>
public enum RevisionKind
{
    REWORK,
    TRIVIAL_REBASE,
    MERGE_FIRST_PARENT_UPDATE,
    NO_CODE_CHANGE,
    NO_CHANGE,
}
