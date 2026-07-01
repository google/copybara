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

using System.Collections.Immutable;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Git.GitLab;
using Copybara.Git.GitLab.Api;
using Copybara.Git.GitLab.Api.Entities;
using Copybara.Revision;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Git;

/// <summary>
/// A <see cref="GitDestination.IWriteHook"/> for GitLab Merge Requests. Port of
/// <c>com.google.copybara.git.GitLabMrWriteHook</c>.
///
/// <para>This write hook is able to query the GitLab API for the merge requests for a given branch
/// and skip the push if the resulting change is empty.</para>
/// </summary>
public sealed class GitLabMrWriteHook : GitDestination.DefaultWriteHook
{
    private const int StatusCodeNotFound = 404;

    private readonly GitLabMrWriteHookParams _params;

    private GitLabMrWriteHook(GitLabMrWriteHookParams @params)
    {
        _params = Preconditions.CheckNotNull(@params);
    }

    /// <summary>Checks if the push to the merge request branch should be skipped.</summary>
    public void BeforePush(
        GitRepository localRepo,
        GitDestination.MessageInfo messageInfo,
        bool skipPush,
        IReadOnlyList<IIntegrateLabel> integrateLabels,
        IReadOnlyList<object> originChanges)
    {
        Console console = _params.GeneralOptions.GetConsole();
        if (skipPush)
        {
            console.VerboseFmt("Not performing empty-diff check because skipPush is true");
            return;
        }
        if (_params.AllowEmptyDiff)
        {
            console.VerboseFmt("Not performing empty-diff check because allowEmptyDiff is true");
            return;
        }

        foreach (var change in originChanges.Cast<Change<IRevision>>())
        {
            string urlEncodedProjectPath = GitLabUtil.GetUrlEncodedProjectPath(_params.RepoUrl);
            Project project = GetProject(urlEncodedProjectPath, console);

            MergeRequest? mergeRequest = GetMergeRequest(project.GetId(), console);
            if (mergeRequest != null)
            {
                CheckMergeRequestForEmptyDiff(localRepo, mergeRequest, change, console);
            }
        }
    }

    private void CheckMergeRequestForEmptyDiff(
        GitRepository localRepo, MergeRequest mergeRequest, Change<IRevision> change, Console console)
    {
        var sameGitTree = new SameGitTree(
            localRepo, _params.RepoUrl.ToString(), _params.GeneralOptions, _params.PartialFetch);
        if (_params.AllowEmptyDiffMergeStatuses.Contains(mergeRequest.GetDetailedMergeStatus()))
        {
            console.VerboseFmt(
                "Not performing empty-diff check because mergeable status is {0} for MR {1}. Allowed"
                    + " merge statuses for empty-diff: {2}",
                mergeRequest.GetDetailedMergeStatus(),
                mergeRequest.GetIid(),
                _params.AllowEmptyDiffMergeStatuses);
            return;
        }
        bool contentsAreSame =
            mergeRequest.GetSha() != null
                && sameGitTree.HasSameTree(mergeRequest.GetSha()!)
                && mergeRequest.GetDetailedMergeStatus() == DetailedMergeStatus.Mergeable;
        if (contentsAreSame)
        {
            if (_params.GeneralOptions.IsForced())
            {
                console.WarnFmt(
                    "Change {0} is empty, but pushing to the MR {1} anyway due to --force flag.",
                    change.Ref, mergeRequest.GetIid());
            }
            else
            {
                throw new RedundantChangeException(
                    $"Skipping push to the existing MR {mergeRequest.GetIid()} in repo"
                        + $" {_params.RepoUrl} as the change {change.Ref} is empty.",
                    mergeRequest.GetSha()!);
            }
        }
    }

    private MergeRequest? GetMergeRequest(int projectId, Console console)
    {
        var mergeRequests = _params.GitLabApi.GetProjectMergeRequests(
            projectId,
            new ListProjectMergeRequestParams(_params.MrBranchToUpdate));

        if (mergeRequests.Count == 0)
        {
            console.VerboseFmt(
                "Not performing empty-diff check because no merge requests found for repo {0} and"
                    + " branch {1}.",
                _params.RepoUrl, _params.MrBranchToUpdate);
            return null;
        }

        if (mergeRequests.Count > 1)
        {
            console.WarnFmt(
                "Not performing empty-diff check because more than one merge request was found for"
                    + " repo {0} and branch {1}. MR IDs: {2}",
                _params.RepoUrl,
                _params.MrBranchToUpdate,
                string.Join(", ", mergeRequests.Select(mr => mr.GetIid().ToString())));

            return null;
        }

        return mergeRequests[0];
    }

    private Project GetProject(string urlEncodedProjectPath, Console console)
    {
        try
        {
            return _params.GitLabApi.GetProject(urlEncodedProjectPath)
                ?? throw new ValidationException(
                    "Failed to obtain project info from URL " + _params.RepoUrl);
        }
        catch (GitLabApiException e)
        {
            if (e.GetResponseCode() == StatusCodeNotFound)
            {
                console.WarnFmt(
                    "The project {0} was not found", Uri.UnescapeDataString(urlEncodedProjectPath));
            }
            throw;
        }
    }

    /// <summary>Parameters for <see cref="GitLabMrWriteHook"/>.</summary>
    public sealed record GitLabMrWriteHookParams(
        bool AllowEmptyDiff,
        GitLabApi GitLabApi,
        Uri RepoUrl,
        string MrBranchToUpdate,
        GeneralOptions GeneralOptions,
        bool PartialFetch,
        IReadOnlySet<DetailedMergeStatus> AllowEmptyDiffMergeStatuses)
    {
        /// <summary>Creates a new <see cref="GitLabMrWriteHook"/> instance from these parameters.</summary>
        public GitLabMrWriteHook CreateWriteHook() => new(this);
    }
}
