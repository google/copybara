/*
 * Copyright (C) 2025 Google LLC
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

namespace Copybara.Git.GitLab.Api.Entities;

/// <summary>Represents a GitLab Merge Request.</summary>
/// <seealso href="https://docs.gitlab.com/api/merge_requests/#response"/>
public class MergeRequest : IGitLabApiEntity
{
    [JsonPropertyName("id")]
    public int Id { get; set; }

    [JsonPropertyName("iid")]
    public int Iid { get; set; }

    [JsonPropertyName("sha")]
    public string? Sha { get; set; }

    [JsonPropertyName("title")]
    public string? Title { get; set; }

    [JsonPropertyName("description")]
    public string? Description { get; set; }

    [JsonPropertyName("detailed_merge_status")]
    public DetailedMergeStatus DetailedMergeStatus { get; set; }

    [JsonPropertyName("source_branch")]
    public string? SourceBranch { get; set; }

    [JsonPropertyName("state")]
    public State State { get; set; }

    [JsonPropertyName("web_url")]
    public string? WebUrl { get; set; }

    /// <summary>Creates a new instance of <see cref="MergeRequest"/>.</summary>
    public MergeRequest()
    {
    }

    /// <summary>Constructs a new instance of <see cref="MergeRequest"/> with the given parameters.</summary>
    public MergeRequest(
        int id,
        int iid,
        string? sha,
        string? title,
        string? description,
        DetailedMergeStatus detailedMergeStatus,
        string? sourceBranch,
        string? webUrl,
        State state)
    {
        Id = id;
        Iid = iid;
        Sha = sha;
        Title = title;
        Description = description;
        DetailedMergeStatus = detailedMergeStatus;
        SourceBranch = sourceBranch;
        WebUrl = webUrl;
        State = state;
    }

    /// <summary>
    /// Returns the ID of the merge request. When querying for an MR, use <see cref="GetIid"/> instead.
    /// </summary>
    public int GetId() => Id;

    /// <summary>Returns the internal ID (iid) of the merge request.</summary>
    public int GetIid() => Iid;

    /// <summary>Returns the title of the MR.</summary>
    public string? GetTitle() => Title;

    /// <summary>Returns the description of the MR.</summary>
    public string? GetDescription() => Description;

    /// <summary>Returns the detailed merge status of the merge request.</summary>
    public DetailedMergeStatus GetDetailedMergeStatus() => DetailedMergeStatus;

    /// <summary>Returns the name of the source branch of the merge request.</summary>
    public string? GetSourceBranch() => SourceBranch;

    /// <summary>Returns the Web URL of the merge request.</summary>
    public string? GetWebUrl() => WebUrl;

    /// <summary>Returns the diff head SHA of the merge request.</summary>
    public string? GetSha() => Sha;

    /// <summary>Returns the state of the merge request.</summary>
    public State GetState() => State;
}

/// <summary>Represents all possible merge statuses for a merge request.</summary>
/// <seealso href="https://docs.gitlab.com/api/merge_requests/#merge-status"/>
[JsonConverter(typeof(JsonStringEnumConverter<DetailedMergeStatus>))]
public enum DetailedMergeStatus
{
    [JsonStringEnumMemberName("approvals_syncing")]
    ApprovalsSyncing,

    [JsonStringEnumMemberName("checking")]
    Checking,

    [JsonStringEnumMemberName("ci_must_pass")]
    CiMustPass,

    [JsonStringEnumMemberName("ci_still_running")]
    CiStillRunning,

    [JsonStringEnumMemberName("commits_status")]
    CommitsStatus,

    [JsonStringEnumMemberName("conflict")]
    Conflict,

    [JsonStringEnumMemberName("discussions_not_resolved")]
    DiscussionsNotResolved,

    [JsonStringEnumMemberName("draft_status")]
    DraftStatus,

    [JsonStringEnumMemberName("jira_association_missing")]
    JiraAssociationMissing,

    [JsonStringEnumMemberName("mergeable")]
    Mergeable,

    [JsonStringEnumMemberName("merge_request_blocked")]
    MergeRequestBlocked,

    [JsonStringEnumMemberName("merge_time")]
    MergeTime,

    [JsonStringEnumMemberName("need_rebase")]
    NeedRebase,

    [JsonStringEnumMemberName("not_approved")]
    NotApproved,

    [JsonStringEnumMemberName("not_open")]
    NotOpen,

    [JsonStringEnumMemberName("preparing")]
    Preparing,

    [JsonStringEnumMemberName("requested_changes")]
    RequestedChanges,

    [JsonStringEnumMemberName("security_policy_violations")]
    SecurityPolicyViolations,

    [JsonStringEnumMemberName("status_checks_must_pass")]
    StatusChecksMustPass,

    [JsonStringEnumMemberName("unchecked")]
    Unchecked,

    [JsonStringEnumMemberName("locked_paths")]
    LockedPaths,

    [JsonStringEnumMemberName("locked_lfs_files")]
    LockedLfsFiles,
}

/// <summary>Represents the possible states of a merge request.</summary>
/// <seealso href="https://docs.gitlab.com/api/merge_requests/#response"/>
[JsonConverter(typeof(JsonStringEnumConverter<State>))]
public enum State
{
    [JsonStringEnumMemberName("opened")]
    Opened,

    [JsonStringEnumMemberName("closed")]
    Closed,

    [JsonStringEnumMemberName("merged")]
    Merged,

    [JsonStringEnumMemberName("locked")]
    Locked,
}
