/*
 * Copyright (C) 2018 Google Inc.
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
using Copybara.Git.GitHub.Api;
using Copybara.Git.GitHub.Util;
using Copybara.Revision;
using Copybara.TemplateToken;
using Console = Copybara.Util.Console.Console;
using GitHubApiClient = Copybara.Git.GitHub.Api.GitHubApi;

namespace Copybara.Git;

/// <summary>
/// A write hook for git.github_destination. Port of
/// <c>com.google.copybara.git.GitHubWriteHook</c>.
/// </summary>
public class GitHubWriteHook : GitDestination.DefaultWriteHook
{
    private readonly string _repoUrl;
    private readonly GeneralOptions _generalOptions;
    private readonly GitHubOptions _gitHubOptions;
    private readonly bool _deletePrBranch;
    private readonly Console _console;
    private readonly IChecker? _endpointChecker;
    private readonly GitHubHost _ghHost;
    private readonly string? _prBranchToUpdate;
    private readonly CredentialFileHandler? _creds;
    private readonly bool _pushToFork;

    public GitHubWriteHook(
        GeneralOptions generalOptions,
        string repoUrl,
        GitHubOptions gitHubOptions,
        string? prBranchToUpdate,
        bool deletePrBranch,
        Console console,
        IChecker? endpointChecker,
        GitHubHost ghHost,
        CredentialFileHandler? creds,
        bool pushToFork)
    {
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
        _repoUrl = Preconditions.CheckNotNull(repoUrl);
        _gitHubOptions = Preconditions.CheckNotNull(gitHubOptions);
        _prBranchToUpdate = prBranchToUpdate;
        _deletePrBranch = deletePrBranch;
        _console = console;
        _endpointChecker = endpointChecker;
        _ghHost = ghHost;
        _creds = creds;
        _pushToFork = pushToFork;
    }

    private PullRequest GetPrFromNumber(string project, long prNumber)
    {
        using (_generalOptions.Profiler().Start("github_api_get_pr"))
        {
            return _gitHubOptions
                .NewGitHubRestApi(_ghHost.GetHost(), project, null, _creds, _console)
                .GetPullRequestAsync(project, prNumber)
                .GetAwaiter().GetResult();
        }
    }

    public void BeforePush(
        GitRepository scratchClone,
        GitDestination.MessageInfo messageInfo,
        bool skipPush,
        IReadOnlyList<IIntegrateLabel> integrateLabels,
        IReadOnlyList<object> originChanges)
    {
        string configProjectName = _ghHost.GetProjectNameFromUrl(_repoUrl);
        GitHubApiClient api =
            _gitHubOptions.NewGitHubRestApi(
                _ghHost.GetHost(), configProjectName, null, _creds, _console);

        if (_pushToFork)
        {
            if (integrateLabels.Count == 0)
            {
                _console.VerboseFmt("No integrate labels found in push to fork.");
                return;
            }

            IIntegrateLabel label = integrateLabels[0];

            if (label is GitHubPrIntegrateLabel integrateLabel)
            {
                PullRequest pr = GetPrFromNumber(configProjectName, integrateLabel.GetPrNumber());

                string pullRequestBranch = pr.GetHead()!.GetRef()!;
                string completeRef = $"refs/heads/{pullRequestBranch}"; // head commit of the branch

                string pullRequestRepoUrl = pr.GetHead()!.GetRepo()!.GetHtmlUrl()!;

                if (pr.GetHead()!.GetSha() != integrateLabel.GetRevision().GetHash())
                {
                    _console.ErrorFmt(
                        "The head commit of the PR {0} is not the same as the commit that was used to"
                            + " create the PR. This is likely due to a commit being pushed to the PR"
                            + " branch after the PR was created. This is not supported by Copybara.",
                        pr.GetNumber());
                    return;
                }
                try
                {
                    _generalOptions.RepoTask<string>(
                        "push squash commit to the pull request fork branch",
                        () =>
                            scratchClone
                                .Push()
                                .WithRefspecs(
                                    pullRequestRepoUrl,
                                    new[] { scratchClone.CreateRefSpec("HEAD:" + completeRef) })
                                .WithForceLease(
                                    ImmutableDictionary<string, string>.Empty.Add(
                                        completeRef, integrateLabel.GetRevision().GetHash()))
                                .Run());
                    return;
                }
                catch (GitHubApiException e)
                {
                    if (e.GetResponseCode() == GitHubApiResponseCode.NOT_FOUND
                        || e.GetResponseCode() == GitHubApiResponseCode.UNPROCESSABLE_ENTITY)
                    {
                        _console.VerboseFmt("Branch {0} does not exist", pullRequestBranch);
                    }
                }
            }
            else
            {
                _console.VerboseFmt("did not find integrate label: {0}", label);
            }
        }

        if (skipPush || _prBranchToUpdate == null)
        {
            return;
        }

        foreach (var change in originChanges.Cast<Change<IRevision>>())
        {
            var labelDict = change.GetLabelsForSkylark();
            string updatedPrBranchName = GetUpdatedPrBranch(labelDict);
            string completeRef = $"refs/heads/{updatedPrBranchName}";
            try
            {
                // Fails with NOT_FOUND if it doesn't exist.
                api.GetReferenceAsync(configProjectName, completeRef).GetAwaiter().GetResult();
                _generalOptions.RepoTask<string>(
                    "push current commit to the head of pr_branch_to_update",
                    () =>
                        scratchClone
                            .Push()
                            .WithRefspecs(
                                _repoUrl,
                                new[] { scratchClone.CreateRefSpec("+HEAD:" + completeRef) })
                            .Run());
            }
            catch (GitHubApiException e)
            {
                if (e.GetResponseCode() == GitHubApiResponseCode.NOT_FOUND
                    || e.GetResponseCode() == GitHubApiResponseCode.UNPROCESSABLE_ENTITY)
                {
                    _console.VerboseFmt("Branch {0} does not exist", updatedPrBranchName);
                    continue;
                }
                throw;
            }
        }
    }

    public override IReadOnlyList<DestinationEffect> AfterPush(
        string serverResponse,
        GitDestination.MessageInfo messageInfo,
        GitRevision pushedRevision,
        IReadOnlyList<object> originChanges)
    {
        var baseEffects = new List<DestinationEffect>(
            base.AfterPush(serverResponse, messageInfo, pushedRevision, originChanges));
        if (_prBranchToUpdate == null || !_deletePrBranch)
        {
            return baseEffects;
        }
        string projectId = _ghHost.GetProjectNameFromUrl(_repoUrl);
        GitHubApiClient api =
            _gitHubOptions.NewGitHubRestApi(_ghHost.GetHost(), projectId, null, _creds, _console);

        if (originChanges.Count != 0)
        {
            if (_gitHubOptions.GithubPrBranchDeletionDelay != null)
            {
                Thread.Sleep(_gitHubOptions.GithubPrBranchDeletionDelay.Value);
            }
            foreach (var change in originChanges.Cast<Change<IRevision>>())
            {
                var labelDict = change.GetLabelsForSkylark();
                string updatedPrBranchName = GetUpdatedPrBranch(labelDict);
                ValidationException.CheckCondition(
                    updatedPrBranchName != "master", "Cannot delete 'master' branch from GitHub");

                string completeRef = $"refs/heads/{updatedPrBranchName}";
                try
                {
                    api.DeleteReferenceAsync(projectId, completeRef).GetAwaiter().GetResult();
                    baseEffects.Add(
                        new DestinationEffect(
                            DestinationEffect.EffectType.UPDATED,
                            $"Reference '{completeRef}' deleted",
                            new[] { change },
                            new DestinationEffect.DestinationRef(
                                completeRef,
                                "ref_deleted",
                                "https://github.com/" + projectId + "/tree/" + updatedPrBranchName)));
                }
                catch (GitHubApiException e)
                {
                    if (e.GetResponseCode() == GitHubApiResponseCode.NOT_FOUND
                        || e.GetResponseCode() == GitHubApiResponseCode.UNPROCESSABLE_ENTITY)
                    {
                        _console.InfoFmt("Branch {0} does not exist", updatedPrBranchName);
                        continue;
                    }
                    throw;
                }
            }
        }
        return baseEffects;
    }

    public override IEndpoint GetFeedbackEndPoint(Console console)
    {
        _gitHubOptions.ValidateEndpointChecker(_endpointChecker);
        return new GitHubEndPoint(
            _gitHubOptions.NewGitHubApiSupplier(_repoUrl, _endpointChecker, _creds, _ghHost),
            _repoUrl,
            console,
            _ghHost,
            _creds);
    }

    public override ImmutableListMultimap<string, string> Describe()
    {
        if (_prBranchToUpdate == null)
        {
            return ImmutableListMultimap<string, string>.Empty;
        }
        return ImmutableListMultimap<string, string>.Of("pr_branch_to_update", _prBranchToUpdate);
    }

    private string GetUpdatedPrBranch(IReadOnlyDictionary<string, string> labelDict)
    {
        try
        {
            return GitHubUtil.GetValidBranchName(
                new LabelTemplate(_prBranchToUpdate!).Resolve(
                    label => labelDict.TryGetValue(label, out var v) ? v : null));
        }
        catch (LabelTemplate.LabelNotFoundException e)
        {
            throw new ValidationException(
                $"Template '{_prBranchToUpdate}' has an error: {e.Message}", e);
        }
    }

    public bool IsDeletePrBranch() => _deletePrBranch;
}
