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

using System.Text.Json.Serialization;
using Starlark.Annot;

namespace Copybara.Git.GerritApi;

/// <summary>https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#approval-info</summary>
[StarlarkBuiltin("gerritapi.ApprovalInfo", Doc = "Gerrit approval information.")]
public class ApprovalInfo : AccountInfo
{
    [JsonPropertyName("value")]
    public int Value { get; set; }

    [JsonPropertyName("date")]
    public string? Date { get; set; }

    public ApprovalInfo()
    {
    }

    public ApprovalInfo(long accountId, string? email, int value)
        : base(accountId, email)
    {
        Value = value;
    }

    [StarlarkMethod(
        "value",
        Doc =
            "The vote that the user has given for the label. If present and zero, the user "
            + "is permitted to vote on the label. If absent, the user is not permitted to vote "
            + "on that label.",
        StructField = true)]
    public int GetValue() => Value;

    public DateTimeOffset GetDate() => GerritApiUtil.ParseTimestamp(Date!);

    [StarlarkMethod(
        "date",
        Doc = "The time and date describing when the approval was made.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetDateForSkylark() => Date;

    public override string ToString() =>
        $"ApprovalInfo{{accountId={AccountId}, name={Name}, email={Email}, "
        + $"secondaryEmails={FormatList(SecondaryEmails)}, username={Username}, "
        + $"value={Value}, date={Date}}}";
}
