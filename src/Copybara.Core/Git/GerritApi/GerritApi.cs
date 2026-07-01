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
using Copybara.Common;
using Copybara.Exceptions;
using ProfilerType = Copybara.Profiler.Profiler;

namespace Copybara.Git.GerritApi;

/// <summary>
/// A mini API for getting and updating Gerrit projects through the Gerrit REST API. Port of
/// <c>com.google.copybara.git.gerritapi.GerritApi</c>.
/// </summary>
public class GerritApi
{
    protected readonly IGerritApiTransport Transport;
    protected readonly ProfilerType ProfilerInstance;

    public GerritApi(IGerritApiTransport transport, ProfilerType profiler)
    {
        Transport = Preconditions.CheckNotNull(transport);
        ProfilerInstance = Preconditions.CheckNotNull(profiler);
    }

    public async Task<IReadOnlyList<ChangeInfo>> GetChangesAsync(ChangesQuery query)
    {
        using (ProfilerInstance.Start("gerrit_get_changes"))
        {
            var result = await Transport
                .GetAsync<List<ChangeInfo>>("/changes/?" + query.AsUrlParams())
                .ConfigureAwait(false);
            return (result ?? new List<ChangeInfo>()).ToImmutableArray();
        }
    }

    public async Task<ChangeInfo> GetChangeAsync(string changeId, GetChangeInput input)
    {
        using (ProfilerInstance.Start("gerrit_get_change"))
        {
            return (await Transport
                .GetAsync<ChangeInfo>("/changes/" + changeId + "?" + input.AsUrlParams())
                .ConfigureAwait(false))!;
        }
    }

    public async Task<ChangeInfo> GetChangeDetailAsync(string changeId, GetChangeInput input)
    {
        using (ProfilerInstance.Start("gerrit_get_change_detail"))
        {
            return (await Transport
                .GetAsync<ChangeInfo>("/changes/" + changeId + "/detail?" + input.AsUrlParams())
                .ConfigureAwait(false))!;
        }
    }

    public async Task<ChangeInfo> AbandonChangeAsync(string changeId, AbandonInput abandonInput)
    {
        using (ProfilerInstance.Start("gerrit_abandon_change"))
        {
            return (await Transport
                .PostAsync<ChangeInfo>("/changes/" + changeId + "/abandon", abandonInput)
                .ConfigureAwait(false))!;
        }
    }

    public async Task<ChangeInfo> RestoreChangeAsync(string changeId, RestoreInput restoreInput)
    {
        using (ProfilerInstance.Start("gerrit_restore_change"))
        {
            return (await Transport
                .PostAsync<ChangeInfo>("/changes/" + changeId + "/restore", restoreInput)
                .ConfigureAwait(false))!;
        }
    }

    public async Task<SubmitRequirementResultInfo> CheckSubmitRequirementAsync(
        string changeId, SubmitRequirementInput submitRequirementInput)
    {
        using (ProfilerInstance.Start("gerrit_check_submit_requirement"))
        {
            return (await Transport
                .PostAsync<SubmitRequirementResultInfo>(
                    "/changes/" + changeId + "/check.submit_requirement", submitRequirementInput)
                .ConfigureAwait(false))!;
        }
    }

    /// <summary>
    /// Look for a Gerrit project using its ID. The ID differs from the name in that certain
    /// characters are escaped. E.g. plugins%2Freplication vs plugins/replication.
    /// </summary>
    /// <returns>a ProjectInfo if project is found, otherwise null.</returns>
    public async Task<ProjectInfo?> GetProjectByIdAsync(string id)
    {
        using (ProfilerInstance.Start("gerrit_list_projects"))
        {
            try
            {
                return await Transport
                    .GetAsync<ProjectInfo>("/projects/" + id)
                    .ConfigureAwait(false);
            }
            catch (GerritApiException e)
            {
                if (e.GetResponseCode() == GerritApiException.ResponseCodeValue.NOT_FOUND)
                {
                    return null;
                }

                throw;
            }
        }
    }

    public async Task<ProjectInfo> CreateProjectAsync(string project)
    {
        using (ProfilerInstance.Start("gerrit_create_project"))
        {
            return (await Transport
                .PutAsync<ProjectInfo>("/projects/" + Escape(project), new ProjectInput())
                .ConfigureAwait(false))!;
        }
    }

    private static string Escape(string project)
    {
        // Gerrit does a good validation in the server side, but we do some basic checks
        ValidationException.CheckCondition(
            !project.Contains(' '), "Invalid project name, has spaces: '%s'", project);
        return project.Replace("/", "%2F");
    }

    public async Task<ProjectAccessInfo> GetAccessInfoAsync(string project)
    {
        using (ProfilerInstance.Start("gerrit_access"))
        {
            return (await Transport
                .GetAsync<ProjectAccessInfo>("/projects/" + project + "/access")
                .ConfigureAwait(false))!;
        }
    }

    public async Task<ReviewResult> SetReviewAsync(
        string changeId, string revisionId, SetReviewInput setReviewInput)
    {
        using (ProfilerInstance.Start("gerrit_set_review"))
        {
            return (await Transport
                .PostAsync<ReviewResult>(
                    "/changes/" + changeId + "/revisions/" + revisionId + "/review", setReviewInput)
                .ConfigureAwait(false))!;
        }
    }

    public async Task DeleteReviewerAsync(
        string changeId, long accountId, DeleteReviewerInput deleteReviewerInput)
    {
        using (ProfilerInstance.Start("gerrit_delete_reviewer_by_account_id"))
        {
            await Transport
                .PostAsync<Empty>(
                    "/changes/" + changeId + "/reviewers/" + accountId + "/delete",
                    deleteReviewerInput)
                .ConfigureAwait(false);
        }
    }

    public async Task DeleteReviewerAsync(
        string changeId, string email, DeleteReviewerInput deleteReviewerInput)
    {
        using (ProfilerInstance.Start("gerrit_delete_reviewer_by_email"))
        {
            await Transport
                .PostAsync<Empty>(
                    "/changes/" + changeId + "/reviewers/" + email + "/delete", deleteReviewerInput)
                .ConfigureAwait(false);
        }
    }

    public async Task AddReviewerAsync(string changeId, ReviewerInput reviewerInput)
    {
        using (ProfilerInstance.Start("gerrit_add_reviewer"))
        {
            await Transport
                .PostAsync<Empty>("/changes/" + changeId + "/reviewers", reviewerInput)
                .ConfigureAwait(false);
        }
    }

    public async Task<AccountInfo> GetSelfAccountAsync()
    {
        using (ProfilerInstance.Start("gerrit_get_self"))
        {
            return (await Transport
                .GetAsync<AccountInfo>("/accounts/self")
                .ConfigureAwait(false))!;
        }
    }

    public async Task<IReadOnlyDictionary<string, ActionInfo>> GetActionsAsync(
        string changeId, string revision)
    {
        using (ProfilerInstance.Start("gerrit_get_actions"))
        {
            var result = await Transport
                .GetAsync<Dictionary<string, ActionInfo>>(
                    "/changes/" + changeId + "/revisions/" + revision + "/actions")
                .ConfigureAwait(false);
            return (result ?? new Dictionary<string, ActionInfo>()).ToImmutableDictionary();
        }
    }

    public async Task DeleteVoteAsync(
        string changeId, string accountId, string labelId, DeleteVoteInput deleteVoteInput)
    {
        using (ProfilerInstance.Start("gerrit_delete_reviewer_vote"))
        {
            await Transport
                .PostAsync<Empty>(
                    "/changes/" + changeId + "/reviewers/" + accountId + "/votes/" + labelId
                        + "/delete",
                    deleteVoteInput)
                .ConfigureAwait(false);
        }
    }

    public async Task<ChangeInfo> SubmitChangeAsync(string changeId, SubmitInput submitInput)
    {
        using (ProfilerInstance.Start("gerrit_submit_change"))
        {
            return (await Transport
                .PostAsync<ChangeInfo>("/changes/" + changeId + "/submit", submitInput)
                .ConfigureAwait(false))!;
        }
    }
}
