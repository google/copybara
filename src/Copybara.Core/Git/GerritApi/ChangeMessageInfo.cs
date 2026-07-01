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

namespace Copybara.Git.GerritApi;

/// <summary>
/// https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#change-message-info
/// </summary>
[StarlarkBuiltin("gerritapi.ChangeMessageInfo", Doc = "Gerrit change message information.")]
public class ChangeMessageInfo : IStarlarkPrintableValue
{
    [JsonPropertyName("id")]
    public string? Id { get; set; }

    [JsonPropertyName("author")]
    public AccountInfo? Author { get; set; }

    [JsonPropertyName("real_author")]
    public AccountInfo? RealAuthor { get; set; }

    [JsonPropertyName("date")]
    public string? Date { get; set; }

    [JsonPropertyName("message")]
    public string? Message { get; set; }

    [JsonPropertyName("tag")]
    public string? Tag { get; set; }

    [JsonPropertyName("_revision_number")]
    public int RevisionNumber { get; set; }

    [StarlarkMethod(
        "id",
        Doc = "The ID of the message.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetId() => Id;

    [StarlarkMethod(
        "author",
        Doc =
            "Author of the message as an AccountInfo entity.\n"
            + "Unset if written by the Gerrit system.",
        StructField = true,
        AllowReturnNones = true)]
    public AccountInfo? GetAuthor() => Author;

    [StarlarkMethod(
        "real_author",
        Doc =
            "Real author of the message as an AccountInfo entity.\n"
            + "Set if the message was posted on behalf of another user.",
        StructField = true,
        AllowReturnNones = true)]
    public AccountInfo? GetRealAuthor() => RealAuthor;

    public DateTimeOffset GetDate() => GerritApiUtil.ParseTimestamp(Date!);

    [StarlarkMethod(
        "date",
        Doc = "The timestamp of when this identity was constructed.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetDateForSkylark() => Date;

    [StarlarkMethod(
        "message",
        Doc = "The text left by the user.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetMessage() => Message;

    [StarlarkMethod(
        "tag",
        Doc =
            "Value of the tag field from ReviewInput set while posting the review. "
            + "NOTE: To apply different tags on on different votes/comments multiple "
            + "invocations of the REST call are required.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetTag() => Tag;

    [StarlarkMethod(
        "revision_number",
        Doc = "Which patchset (if any) generated this message.",
        StructField = true)]
    public int GetRevisionNumber() => RevisionNumber;

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());

    public override string ToString() =>
        $"ChangeMessageInfo{{id={Id}, author={Author}, realAuthor={RealAuthor}, date={Date}, "
        + $"message={Message}, tag={Tag}, revisionNumber={RevisionNumber}}}";
}
