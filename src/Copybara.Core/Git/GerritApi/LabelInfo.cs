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

/// <summary>https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#label-info</summary>
[StarlarkBuiltin("gerritapi.LabelInfo", Doc = "Gerrit label information.")]
public class LabelInfo : IStarlarkPrintableValue
{
    [JsonPropertyName("optional")]
    public bool Optional { get; set; }

    [JsonPropertyName("approved")]
    public AccountInfo? Approved { get; set; }

    [JsonPropertyName("rejected")]
    public AccountInfo? Rejected { get; set; }

    [JsonPropertyName("recommended")]
    public AccountInfo? Recommended { get; set; }

    [JsonPropertyName("disliked")]
    public AccountInfo? Disliked { get; set; }

    [JsonPropertyName("blocking")]
    public bool Blocking { get; set; }

    [JsonPropertyName("value")]
    public int Value { get; set; }

    [JsonPropertyName("default_value")]
    public int DefaultValue { get; set; }

    [JsonPropertyName("values")]
    public IReadOnlyDictionary<string, string>? Values { get; set; }

    [JsonPropertyName("all")]
    public IReadOnlyList<ApprovalInfo>? All { get; set; }

    public bool IsOptional() => Optional;

    [StarlarkMethod(
        "approved",
        Doc =
            "One user who approved this label on the change (voted the maximum value) as an "
            + "AccountInfo entity.",
        StructField = true,
        AllowReturnNones = true)]
    public AccountInfo? GetApproved() => Approved;

    [StarlarkMethod(
        "rejected",
        Doc =
            "One user who rejected this label on the change (voted the minimum value) as an "
            + "AccountInfo entity.",
        StructField = true,
        AllowReturnNones = true)]
    public AccountInfo? GetRejected() => Rejected;

    [StarlarkMethod(
        "recommended",
        Doc =
            "One user who recommended this label on the change (voted positively, but not the "
            + "maximum value) as an AccountInfo entity.",
        StructField = true,
        AllowReturnNones = true)]
    public AccountInfo? GetRecommended() => Recommended;

    [StarlarkMethod(
        "disliked",
        Doc =
            "One user who disliked this label on the change (voted negatively, but not the "
            + "minimum value) as an AccountInfo entity.",
        StructField = true,
        AllowReturnNones = true)]
    public AccountInfo? GetDisliked() => Disliked;

    [StarlarkMethod(
        "blocking",
        Doc = "If true, the label blocks submit operation. If not set, the default is false.",
        StructField = true)]
    public bool IsBlocking() => Blocking;

    [StarlarkMethod(
        "value",
        Doc =
            "The voting value of the user who recommended/disliked this label on the change if "
            + "it is not `\"+1\"`/`\"-1\"`.",
        StructField = true)]
    public int GetValue() => Value;

    [StarlarkMethod(
        "default_value",
        Doc =
            "The default voting value for the label. This value may be outside the range "
            + "specified in permitted_labels.",
        StructField = true)]
    public int GetDefaultValue() => DefaultValue;

    [StarlarkMethod(
        "values",
        Doc =
            "A map of all values that are allowed for this label. The map maps the values "
            + "(`\"-2\"`, `\"-1\"`, `\"0\"`, `\"+1\"`, `\"+2\"`) to the value descriptions.",
        StructField = true)]
    public IReadOnlyDictionary<string, string> GetValues() =>
        Values is null ? ImmutableDictionary<string, string>.Empty : Values.ToImmutableDictionary();

    [StarlarkMethod(
        "all",
        Doc =
            "List of all approvals for this label as a list of ApprovalInfo entities. Items "
            + "in this list may not represent actual votes cast by users; if a user votes on "
            + "any label, a corresponding ApprovalInfo will appear in this list for all labels.",
        StructField = true)]
    public IReadOnlyList<ApprovalInfo> GetAll() =>
        All is not null ? All.ToImmutableArray() : ImmutableArray<ApprovalInfo>.Empty;

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());

    public override string ToString() =>
        $"LabelInfo{{optional={Optional}, approved={Approved}, rejected={Rejected}, "
        + $"recommended={Recommended}, disliked={Disliked}, blocking={Blocking}, value={Value}, "
        + $"defaultValue={DefaultValue}, values={Values}, all={All}}}";
}
