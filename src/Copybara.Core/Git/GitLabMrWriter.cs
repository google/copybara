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
using Copybara.Checks;
using Copybara.Common;
using Copybara.Effect;
using Copybara.Exceptions;
using Copybara.Git.GitLab.Api;
using Copybara.Git.GitLab.Api.Entities;
using Copybara.Revision;
using Copybara.Util;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Git;

/// <summary>
/// A <see cref="GitDestination.WriterImpl{TS}"/> that writes to GitLab merge requests. Port of
/// <c>com.google.copybara.git.GitLabMrWriter</c>.
/// </summary>
public sealed class GitLabMrWriter
    : GitDestination.WriterImpl<GitLabMrDestination.GitLabWriterState>
{
    private readonly GitLabMrWriterParams _params;

    private GitLabMrWriter(GitLabMrWriterParams @params)
        : base(
            @params.SkipPush,
            @params.RepoUrl.ToString(),
            @params.TargetBranch,
            @params.SourceBranch,
            @params.PartialFetch,
            tagNameTemplate: null,
            tagMsgTemplate: null,
            @params.GeneralOptions,
            @params.GitOptions,
            @params.WriteHook,
            @params.State,
            nonFastForwardPush: true,
            @params.Integrates,
            @params.DestinationOptions.LastRevFirstParent,
            @params.DestinationOptions.IgnoreIntegrationErrors,
            @params.DestinationOptions.LocalRepoPath,
            @params.DestinationOptions.CommitterName,
            @params.DestinationOptions.CommitterEmail,
            @params.DestinationOptions.RebaseWhenBaseline(),
            @params.GitOptions.VisitChangePageSize,
            @params.GitOptions.GitTagOverwrite,
            @params.Checker,
            @params.DestinationOptions,
            @params.Credentials)
    {
        _params = @params;
    }

    /// <summary>
    /// Uploads the fully transformed repository to GitLab, and creates/updates a merge request
    /// associated with the changes.
    /// </summary>
    public override IReadOnlyList<DestinationEffect> Write(
        TransformResult transformResult, Glob destinationFiles, Console console)
    {
        var result = new List<DestinationEffect>(
            base.Write(transformResult, destinationFiles, console));

        if (_params.WriterContext.IsDryRun())
        {
            console.WarnFmt("Not writing MR to GitLab as we are running in --dry-run mode.");
            return result;
        }
        if (State.GetMergeRequestNumber() != null)
        {
            console.WarnFmt(
                "Not writing MR to GitLab as a merge request has already been written by this"
                    + " destination.");
            return result;
        }

        GitLabApi gitLabApi = _params.GitLabApi;
        ChangeMessage msg = ChangeMessage.ParseMessage(transformResult.GetSummary().Trim());
        string title = GetTitle(transformResult, msg);
        string mrBody = GetMrBody(transformResult, msg);

        var assignees =
            LabelFinder.MapLabels(transformResult.GetLabelFinder(), _params.AssigneeTemplates);

        var mergeRequests =
            gitLabApi.GetProjectMergeRequests(
                _params.Project.GetId(),
                new ListProjectMergeRequestParams(_params.SourceBranch));

        if (mergeRequests.Count == 0)
        {
            console.Progress("Creating new MR");
            result.AddRange(CreateMergeRequests(title, mrBody, assignees, transformResult, console));
        }
        else
        {
            string mrIids = string.Join(", ", mergeRequests.Select(mr => mr.GetIid().ToString()));
            if (mergeRequests.Count > 1)
            {
                console.WarnFmt("Found more than one MR! IIDs: {0}", mrIids);
            }
            console.ProgressFmt("Updating existing MRs: {0}", mrIids);
            result.AddRange(
                UpdateExistingMergeRequests(
                    mergeRequests, title, mrBody, assignees, transformResult, console));
        }

        return result;
    }

    private string GetMrBody(TransformResult transformResult, ChangeMessage msg) =>
        _params.BodyTemplate != null
            ? LabelFinder.MapLabels(transformResult.GetLabelFinder(), _params.BodyTemplate, "body")
            : msg.ToString();

    private string GetTitle(TransformResult transformResult, ChangeMessage msg)
    {
        string title =
            _params.TitleTemplate != null
                ? LabelFinder.MapLabels(
                    transformResult.GetLabelFinder(), _params.TitleTemplate, "title")
                : msg.FirstLine();
        ValidationException.CheckCondition(
            !string.IsNullOrEmpty(title), "Merge request title can not be empty.");
        return title;
    }

    private IReadOnlyList<DestinationEffect> CreateMergeRequests(
        string title,
        string description,
        IReadOnlyList<string> assignees,
        TransformResult transformResult,
        Console console)
    {
        console.ProgressFmt(
            "Creating MR for project {0}, source branch {1}, target branch {2}, and assignees {3}",
            _params.Project.GetId(), _params.SourceBranch, _params.TargetBranch,
            string.Join(", ", assignees));
        MergeRequest? newMr =
            _params.GitLabApi.CreateMergeRequest(
                new CreateMergeRequestParams(
                    _params.Project.GetId(),
                    _params.SourceBranch,
                    _params.TargetBranch,
                    title,
                    description,
                    MapAssigneeUsernamesToIds(assignees, console)));
        if (newMr != null)
        {
            int mergeRequestIid = newMr.GetIid();
            _params.State.SetMrNumber(mergeRequestIid);
            console.ProgressFmt("Created merge request at {0}", newMr.GetWebUrl());
            return ImmutableArray.Create(
                new DestinationEffect(
                    DestinationEffect.EffectType.CREATED,
                    $"Merge Request {newMr.GetWebUrl()} created",
                    transformResult.GetChanges().GetCurrent().Cast<OriginRef>().ToList(),
                    new DestinationEffect.DestinationRef(
                        mergeRequestIid.ToString(), "merge_request", newMr.GetWebUrl())));
        }
        throw new RepoException(
            "Attempted to create a new merge request, but the API did not respond with information"
                + " about the new merge request");
    }

    private IReadOnlyList<int> MapAssigneeUsernamesToIds(
        IReadOnlyList<string> assignees, Console console)
    {
        var assigneeIds = new List<int>();
        foreach (var assignee in assignees)
        {
            var user = _params.GitLabApi.GetListUsers(new ListUsersParams(assignee));
            if (user.Count == 1)
            {
                assigneeIds.Add(user[0].GetId());
            }
            else if (user.Count > 1)
            {
                throw new ValidationException(
                    $"Found more than 1 user for {assignee}. This should not happen, as a username"
                        + " maps to one user. Please report this to the Copybara team");
            }
            else
            {
                console.WarnFmt("Could not find a user for the username {0}, skipping", assignee);
            }
        }
        return assigneeIds;
    }

    private IReadOnlyList<DestinationEffect> UpdateExistingMergeRequests(
        IReadOnlyList<MergeRequest> mergeRequests,
        string title,
        string description,
        IReadOnlyList<string> assignees,
        TransformResult transformResult,
        Console console)
    {
        var results = new List<DestinationEffect>();

        foreach (var mergeRequest in mergeRequests)
        {
            console.ProgressFmt(
                "Updating MR {0} for project {1}, source branch {2}, target branch {3}, and"
                    + " assignees {4}",
                mergeRequest.GetIid(),
                _params.Project.GetId(),
                _params.SourceBranch,
                _params.TargetBranch,
                string.Join(", ", assignees));
            UpdateMergeRequestParams.StateEvent? newState = null;
            if (mergeRequest.GetState() == Copybara.Git.GitLab.Api.Entities.State.Closed)
            {
                console.WarnFmt("Existing MR {0} is closed, reopening.", mergeRequest.GetIid());
                newState = UpdateMergeRequestParams.StateEvent.Reopen;
            }
            MergeRequest? updatedMr =
                _params.GitLabApi.UpdateMergeRequest(
                    new UpdateMergeRequestParams(
                        _params.Project.GetId(),
                        mergeRequest.GetIid(),
                        title,
                        description,
                        MapAssigneeUsernamesToIds(assignees, console),
                        newState));
            if (updatedMr != null)
            {
                console.ProgressFmt("Updated MR located at {0}", updatedMr.GetWebUrl());
                results.Add(
                    new DestinationEffect(
                        DestinationEffect.EffectType.UPDATED,
                        $"Merge Request {updatedMr.GetWebUrl()} updated",
                        transformResult.GetChanges().GetCurrent().Cast<OriginRef>().ToList(),
                        new DestinationEffect.DestinationRef(
                            updatedMr.GetIid().ToString(), "merge_request", updatedMr.GetWebUrl())));
            }
            else
            {
                throw new RepoException(
                    "Attempted to create a new merge request, but the API did not respond with"
                        + " information about the new merge request");
            }
        }

        return results;
    }

    /// <summary>Params for <see cref="GitLabMrWriter"/>.</summary>
    public sealed record GitLabMrWriterParams(
        GitLabApi GitLabApi,
        string? TitleTemplate,
        string? BodyTemplate,
        IReadOnlyList<string> AssigneeTemplates,
        Project Project,
        WriterContext WriterContext,
        bool SkipPush,
        Uri RepoUrl,
        string SourceBranch,
        string TargetBranch,
        bool PartialFetch,
        GeneralOptions GeneralOptions,
        GitOptions GitOptions,
        GitLabMrWriteHook WriteHook,
        GitLabMrDestination.GitLabWriterState State,
        IEnumerable<GitIntegrateChanges> Integrates,
        IChecker? Checker,
        GitDestinationOptions DestinationOptions,
        CredentialFileHandler Credentials)
    {
        /// <summary>Creates a new <see cref="GitLabMrWriter"/> instance from these parameters.</summary>
        public GitLabMrWriter CreateWriter() => new(this);
    }
}
