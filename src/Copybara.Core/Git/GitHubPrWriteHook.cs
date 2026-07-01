/*
 * Copyright (C) 2020 Google Inc.
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
using Copybara.Git.GitHub.Api;
using Copybara.Git.GitHub.Util;
using Copybara.Revision;
using Console = Copybara.Util.Console.Console;
using GitHubApiClient = Copybara.Git.GitHub.Api.GitHubApi;
using ImmutableSetMultimap = Copybara.Common.ImmutableSetMultimap<string, Copybara.Git.GitHub.Api.CheckRunConclusion>;

namespace Copybara.Git;

/// <summary>
/// A write hook for GitHub PR. Port of <c>com.google.copybara.git.GitHubPrWriteHook</c>.
/// </summary>
public class GitHubPrWriteHook : GitDestination.DefaultWriteHook
{
    private readonly string _repoUrl;
    private readonly GeneralOptions _generalOptions;
    private readonly GitHubOptions _gitHubOptions;
    private readonly bool _partialFetch;
    private readonly IReadOnlySet<string> _allowEmptyDiffMergeStatuses;
    private readonly ImmutableSetMultimap _allowEmptyDiffCheckSuitesConclusion;
    private readonly Console _console;
    private readonly GitHubHost _ghHost;
    private readonly string? _prBranchToUpdate;
    private readonly bool _allowEmptyDiff;
    private readonly CredentialFileHandler? _creds;

    public GitHubPrWriteHook(
        GeneralOptions generalOptions,
        string repoUrl,
        GitHubOptions gitHubOptions,
        string? prBranchToUpdate,
        bool partialFetch,
        bool allowEmptyDiff,
        IReadOnlySet<string> allowEmptyDiffMergeStatuses,
        ImmutableSetMultimap allowEmptyDiffCheckSuitesConclusion,
        Console console,
        GitHubHost ghHost,
        CredentialFileHandler? creds)
    {
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
        _repoUrl = Preconditions.CheckNotNull(repoUrl);
        _gitHubOptions = Preconditions.CheckNotNull(gitHubOptions);
        _prBranchToUpdate = prBranchToUpdate;
        _partialFetch = partialFetch;
        _allowEmptyDiff = allowEmptyDiff;
        _allowEmptyDiffMergeStatuses = allowEmptyDiffMergeStatuses;
        _allowEmptyDiffCheckSuitesConclusion = allowEmptyDiffCheckSuitesConclusion;
        _console = Preconditions.CheckNotNull(console);
        _ghHost = Preconditions.CheckNotNull(ghHost);
        _creds = creds;
    }

    public void BeforePush(
        GitRepository scratchClone,
        GitDestination.MessageInfo messageInfo,
        bool skipPush,
        IReadOnlyList<IIntegrateLabel> integrateLabels,
        IReadOnlyList<object> originChanges)
    {
        if (skipPush || _generalOptions.AllowEmptyDiffValue(_allowEmptyDiff))
        {
            return;
        }
        foreach (var originalChange in originChanges.Cast<Change<IRevision>>())
        {
            string projectName = _ghHost.GetProjectNameFromUrl(_repoUrl);
            GitHubApiClient api =
                _gitHubOptions.NewGitHubRestApi(
                    _ghHost.GetHost(), projectName, null, _creds, _console);

            try
            {
                var pullRequests =
                    api.GetPullRequestsAsync(
                        projectName,
                        GitHubApiClient.PullRequestListParams.Default.WithHead(
                            $"{_ghHost.GetUserNameFromUrl(_repoUrl)}:{_prBranchToUpdate}"))
                        .GetAwaiter().GetResult();
                // Just ignore empty-diff check when the size of prs is not equal to 1.
                if (pullRequests.Count != 1)
                {
                    return;
                }
                var sameGitTree =
                    new SameGitTree(scratchClone, _repoUrl, _generalOptions, _partialFetch);
                PullRequest pullRequest = pullRequests[0];
                if (sameGitTree.HasSameTree(pullRequest.GetHead()!.GetSha()!)
                    && SkipUploadBasedOnPrStatus(projectName, api, pullRequest.GetNumber())
                    && SkipUploadBasedOnCheckSuites(projectName, api, pullRequest.GetHead()!.GetSha()!))
                {
                    throw new RedundantChangeException(
                        $"Skipping push to the existing pr {_repoUrl}/pull/{pullRequest.GetNumber()}"
                            + $" as the change {originalChange.Ref} is empty.",
                        pullRequest.GetHead()!.GetSha()!);
                }
            }
            catch (GitHubApiException e)
            {
                if (e.GetResponseCode() == GitHubApiResponseCode.NOT_FOUND
                    || e.GetResponseCode() == GitHubApiResponseCode.UNPROCESSABLE_ENTITY)
                {
                    _console.VerboseFmt("Branch {0} does not exist", _prBranchToUpdate!);
                }
                throw;
            }
        }
    }

    private bool SkipUploadBasedOnCheckSuites(string project, GitHubApiClient api, string sha)
    {
        // Not used, we skip by default and avoid doing an API rpc.
        if (_allowEmptyDiffCheckSuitesConclusion.IsEmpty)
        {
            return true;
        }
        var checkSuites = api.GetCheckSuitesAsync(project, sha).GetAwaiter().GetResult();
        bool slugFound = false;
        foreach (var suite in checkSuites)
        {
            if (!_allowEmptyDiffCheckSuitesConclusion.ContainsKey(suite.GetApp()!.GetSlug()!))
            {
                _console.VerboseFmt(
                    "Skipping Check-suite {0} as it not part of skip empty diff suites: {1}",
                    suite.GetApp()!.GetName()!,
                    string.Join(", ", _allowEmptyDiffCheckSuitesConclusion.Keys));
                continue;
            }
            slugFound = true;
            var conclusions = _allowEmptyDiffCheckSuitesConclusion.Get(suite.GetApp()!.GetSlug()!);
            var suiteConclusion =
                suite.GetConclusion() != null
                    ? CheckRunConclusions.FromValue(suite.GetConclusion()!) ?? CheckRunConclusion.NONE
                    : CheckRunConclusion.NONE;
            if (conclusions.Contains(suiteConclusion))
            {
                _console.InfoFmt(
                    "Uploading change because check-suite {0}({1}) conclusion is {2}, that is in the"
                        + " list of conclusions to upload on empty diff: {3}",
                    suite.GetApp()!.GetSlug()!,
                    suite.GetId(),
                    suite.GetConclusion()!,
                    string.Join(", ", conclusions.Select(c => c.GetApiVal())));
                return false;
            }

            _console.InfoFmt(
                "Ignoring check-suite {0}({1}) because conclusion is {2}, that is NOT in the list of"
                    + " conclusions to upload on empty diff for this slug: {3}",
                suite.GetApp()!.GetSlug()!,
                suite.GetId(),
                suite.GetConclusion()!,
                string.Join(", ", conclusions.Select(c => c.GetApiVal())));
        }
        if (!slugFound)
        {
            _console.WarnFmt(
                "Skipping upload: Couldn't find any slug name that matched the configured slugs in"
                    + " the config file. copy.bara.sky suits slug names are: {0} but present suits for"
                    + " commit {1} are: {2}",
                string.Join(", ", _allowEmptyDiffCheckSuitesConclusion.Keys),
                sha,
                string.Join(", ", checkSuites.Select(s => s.GetApp()!.GetSlug()!)));
        }
        return true;
    }

    private bool SkipUploadBasedOnPrStatus(string configProjectName, GitHubApiClient api, long prNumber)
    {
        // The previous PR is received by searching PRs by branch name, and GitHub doesn't return
        // the 'mergeable' field there. So we do an additional request to get the full data.
        PullRequest completePr =
            api.GetPullRequestAsync(configProjectName, prNumber).GetAwaiter().GetResult();
        bool? mergeable = completePr.IsMergeable();
        if (mergeable == null || !mergeable.Value)
        {
            _console.VerboseFmt("Not skipping upload because 'mergeable' is: {0}", (object?)mergeable ?? "null");
            return false;
        }

        // If user hasn't set any value, we don't look at mergeable status at all and assume we skip.
        if (_allowEmptyDiffMergeStatuses.Count == 0)
        {
            return true;
        }

        string? mergeableState = completePr.GetMergeableState();
        if (mergeableState == null)
        {
            _console.Warn("Not skipping upload because 'mergeable status' is null");
            return false;
        }
        if (_allowEmptyDiffMergeStatuses.Contains(mergeableState.ToUpperInvariant()))
        {
            _console.InfoFmt(
                "Uploading change because mergeable status is {0}, that is in the list of statuses"
                    + " to upload changes: {1}",
                mergeableState.ToUpperInvariant(),
                string.Join(", ", _allowEmptyDiffMergeStatuses));
            return false;
        }

        _console.InfoFmt(
            "Skipping upload because mergeable status is {0}, that is NOT in the list of statuses to"
                + " upload changes: {1}",
            mergeableState.ToUpperInvariant(),
            string.Join(", ", _allowEmptyDiffMergeStatuses));
        return true;
    }

    public GitHubPrWriteHook WithUpdatedPrBranch(string prBranchToUpdate) =>
        new(
            _generalOptions,
            _repoUrl,
            _gitHubOptions,
            prBranchToUpdate,
            _partialFetch,
            _allowEmptyDiff,
            _allowEmptyDiffMergeStatuses,
            _allowEmptyDiffCheckSuitesConclusion,
            _console,
            _ghHost,
            _creds);
}
