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
using System.Globalization;
using System.Text.Json.Serialization;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Git.GerritApi;

/// <summary>https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#change-info</summary>
[StarlarkBuiltin("gerritapi.ChangeInfo", Doc = "Gerrit change information.")]
public class ChangeInfo : IStarlarkPrintableValue
{
    [JsonPropertyName("id")]
    public string? Id { get; set; }

    [JsonPropertyName("triplet_id")]
    public string? TripletId { get; set; }

    [JsonPropertyName("project")]
    public string? Project { get; set; }

    [JsonPropertyName("branch")]
    public string? Branch { get; set; }

    [JsonPropertyName("topic")]
    public string? Topic { get; set; }

    [JsonPropertyName("change_id")]
    public string? ChangeIdField { get; set; }

    [JsonPropertyName("subject")]
    public string? Subject { get; set; }

    [JsonPropertyName("status")]
    public string? StatusString { get; set; }

    [JsonPropertyName("created")]
    public string? Created { get; set; }

    [JsonPropertyName("updated")]
    public string? Updated { get; set; }

    [JsonPropertyName("submitted")]
    public string? Submitted { get; set; }

    [JsonPropertyName("submittable")]
    public bool Submittable { get; set; }

    [JsonPropertyName("work_in_progress")]
    public bool WorkInProgress { get; set; }

    [JsonPropertyName("_number")]
    public long Number { get; set; }

    [JsonPropertyName("owner")]
    public AccountInfo? Owner { get; set; }

    [JsonPropertyName("submit_requirements")]
    public IReadOnlyList<SubmitRequirementResultInfo>? SubmitRequirementsField { get; set; }

    [JsonPropertyName("labels")]
    public IReadOnlyDictionary<string, LabelInfo>? LabelsField { get; set; }

    [JsonPropertyName("messages")]
    public IReadOnlyList<ChangeMessageInfo>? MessagesField { get; set; }

    [JsonPropertyName("current_revision")]
    public string? CurrentRevision { get; set; }

    [JsonPropertyName("revisions")]
    public IReadOnlyDictionary<string, RevisionInfo>? AllRevisionsField { get; set; }

    [JsonPropertyName("_more_changes")]
    public bool MoreChanges { get; set; }

    [JsonPropertyName("reviewers")]
    public IReadOnlyDictionary<string, IReadOnlyList<AccountInfo>>? ReviewersField { get; set; }

    [StarlarkMethod(
        "id",
        Doc =
            "The ID of the change in the format \"`<project>~<branch>~<Change-Id>`\", where "
            + "'project', 'branch' and 'Change-Id' are URL encoded. For 'branch' the "
            + "refs/heads/ prefix is omitted.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetId() => Id;

    [StarlarkMethod(
        "triplet_id",
        Doc =
            "The ID of the change in the format \"'<project>~<branch>~<Change-Id>'\", where 'project'"
            + " and 'branch' are URL encoded. For 'branch' the refs/heads/ prefix is omitted.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetTripletId() => TripletId;

    [StarlarkMethod(
        "project",
        Doc = "The name of the project.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetProject() => Project;

    [StarlarkMethod(
        "branch",
        Doc = "The name of the target branch.\nThe refs/heads/ prefix is omitted.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetBranch() => Branch;

    [StarlarkMethod(
        "topic",
        Doc = "The topic to which this change belongs.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetTopic() => Topic;

    [StarlarkMethod(
        "change_id",
        Doc = "The Change-Id of the change.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetChangeId() => ChangeIdField;

    [StarlarkMethod(
        "subject",
        Doc = "The subject of the change (header line of the commit message).",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetSubject() => Subject;

    public ChangeStatus GetStatus() => Enum.Parse<ChangeStatus>(StatusString!);

    [StarlarkMethod(
        "status",
        Doc = "The status of the change (NEW, MERGED, ABANDONED).",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetStatusAsString() => StatusString;

    public DateTimeOffset GetCreated() => GerritApiUtil.ParseTimestamp(Created!);

    [StarlarkMethod(
        "created",
        Doc = "The timestamp of when the change was created.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetCreatedForSkylark() => Created;

    public DateTimeOffset GetUpdated() => GerritApiUtil.ParseTimestamp(Updated!);

    [StarlarkMethod(
        "updated",
        Doc = "The timestamp of when the change was last updated.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetUpdatedForSkylark() => Updated;

    public DateTimeOffset GetSubmitted() => GerritApiUtil.ParseTimestamp(Submitted!);

    [StarlarkMethod(
        "submitted",
        Doc = "The timestamp of when the change was submitted.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetSubmittedForSkylark() => Submitted;

    [StarlarkMethod(
        "submittable",
        Doc =
            "Whether the change has been approved by the project submit rules. Only set if "
            + "requested via additional field SUBMITTABLE.",
        StructField = true)]
    public bool IsSubmittable() => Submittable;

    [StarlarkMethod(
        "work_in_progress",
        Doc = "Whether the change is marked as \"Work in progress\".",
        StructField = true)]
    public bool IsWorkInProgress() => WorkInProgress;

    public long GetNumber() => Number;

    [StarlarkMethod("number", Doc = "The legacy numeric ID of the change.", StructField = true)]
    public string GetNumberAsString() => Number.ToString(CultureInfo.InvariantCulture);

    [StarlarkMethod(
        "owner",
        Doc = "The owner of the change as an AccountInfo entity.",
        StructField = true,
        AllowReturnNones = true)]
    public AccountInfo? GetOwner() => Owner;

    public IReadOnlyList<SubmitRequirementResultInfo> GetSubmitRequirements() =>
        SubmitRequirementsField is null
            ? ImmutableArray<SubmitRequirementResultInfo>.Empty
            : SubmitRequirementsField.ToImmutableArray();

    [StarlarkMethod(
        "submit_requirements",
        Doc = "A list of the evaluated submit requirements for the change.",
        StructField = true)]
    public IReadOnlyList<SubmitRequirementResultInfo> GetSubmitRequirementsForSkylark() =>
        GetSubmitRequirements();

    public IReadOnlyDictionary<string, LabelInfo> GetLabels() =>
        LabelsField is null ? ImmutableDictionary<string, LabelInfo>.Empty : LabelsField.ToImmutableDictionary();

    [StarlarkMethod(
        "labels",
        Doc =
            "The labels of the change as a map that maps the label names to LabelInfo entries.\n"
            + "Only set if labels or detailed labels are requested.",
        StructField = true)]
    public IReadOnlyDictionary<string, LabelInfo> GetLabelsForSkylark() => GetLabels();

    public IReadOnlyList<ChangeMessageInfo> GetMessages() =>
        MessagesField is null ? ImmutableArray<ChangeMessageInfo>.Empty : MessagesField.ToImmutableArray();

    [StarlarkMethod(
        "messages",
        Doc =
            "Messages associated with the change as a list of ChangeMessageInfo entities.\n"
            + "Only set if messages are requested.",
        StructField = true)]
    public IReadOnlyList<ChangeMessageInfo> GetMessagesForSkylark() => GetMessages();

    [StarlarkMethod(
        "current_revision",
        Doc =
            "The commit ID of the current patch set of this change.\n"
            + "Only set if the current revision is requested or if all revisions are requested.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetCurrentRevision() => CurrentRevision;

    public IReadOnlyDictionary<string, RevisionInfo> GetAllRevisions() =>
        AllRevisionsField is null
            ? ImmutableDictionary<string, RevisionInfo>.Empty
            : AllRevisionsField.ToImmutableDictionary();

    [StarlarkMethod(
        "revisions",
        Doc =
            "All patch sets of this change as a map that maps the commit ID of the patch set to a "
            + "RevisionInfo entity.\n"
            + "Only set if the current revision is requested (in which case it will only contain "
            + "a key for the current revision) or if all revisions are requested.",
        StructField = true)]
    public IReadOnlyDictionary<string, RevisionInfo> GetAllRevisionsForSkylark() => GetAllRevisions();

    public IReadOnlyDictionary<string, IReadOnlyList<AccountInfo>> GetReviewers() =>
        ReviewersField is null
            ? ImmutableDictionary<string, IReadOnlyList<AccountInfo>>.Empty
            : ReviewersField.ToImmutableDictionary();

    public bool IsMoreChanges() => MoreChanges;

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());

    public override string ToString() =>
        $"ChangeInfo{{id={Id}, project={Project}, branch={Branch}, topic={Topic}, "
        + $"changeId={ChangeIdField}, subject={Subject}, status={StatusString}, created={Created}, "
        + $"updated={Updated}, submitted={Submitted}, submittable={Submittable}, "
        + $"work_in_progress={WorkInProgress}, number={Number}, owner={Owner}, "
        + $"submitRequirements={SubmitRequirementsField}, labels={LabelsField}, "
        + $"messages={MessagesField}, currentRevision={CurrentRevision}, "
        + $"allRevisions={AllRevisionsField}, moreChanges={MoreChanges}}}";
}
