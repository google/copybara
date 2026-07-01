/*
 * Copyright (C) 2023 Google LLC
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
using Copybara.Approval;
using Copybara.Exceptions;
using Copybara.Git.GitHub.Api;
using Copybara.Git.GitHub.Util;
using Console = Copybara.Util.Console.Console;
using GitHubApiClient = Copybara.Git.GitHub.Api.GitHubApi;

namespace Copybara.Git;

/// <summary>
/// Utility class for performing validation for GitHub pull request approvals. Port of
/// <c>com.google.copybara.git.GitHubUserApprovalsValidator</c>.
/// </summary>
public class GitHubUserApprovalsValidator
{
    private const int GetCommitHistoryMaxRetries = 3;

    private readonly LazyResourceLoader<GitHubApiClient> _restApiLoader;
    private readonly LazyResourceLoader<GitHubGraphQLApi> _graphQlApiLoader;
    private readonly Console _console;
    private readonly GitHubHost _githubHost;
    private readonly GitHubGraphQLApi.GetCommitHistoryParams _params;

    public GitHubUserApprovalsValidator(
        LazyResourceLoader<GitHubApiClient> restApiLoader,
        LazyResourceLoader<GitHubGraphQLApi> graphQlApiLoader,
        Console console,
        GitHubHost githubHost,
        GitHubGraphQLApi.GetCommitHistoryParams @params)
    {
        _restApiLoader = restApiLoader;
        _graphQlApiLoader = graphQlApiLoader;
        _console = console;
        _githubHost = githubHost;
        _params = @params;
    }

    /// <summary>
    /// Bestows a <see cref="UserPredicate"/> to a list of changes. For each change, one UserPredicate
    /// for the author and one for each user approval.
    /// </summary>
    public ImmutableArray<ChangeWithApprovals> MapApprovalsForUserPredicates(
        ImmutableArray<ChangeWithApprovals> changes, string? branch)
    {
        if (changes.IsEmpty)
        {
            return ImmutableArray<ChangeWithApprovals>.Empty;
        }
        string url = changes[^1].GetChange().GetRevision().GetUrl()!;
        string organization = _githubHost.GetUserNameFromUrl(url);
        string projectName = _githubHost.GetProjectNameFromUrl(url);
        string repository = projectName.Substring(projectName.LastIndexOf('/') + 1);
        var builder = ImmutableArray.CreateBuilder<ChangeWithApprovals>();

        CommitHistoryResponse? response = null;
        for (int i = 0; i < GetCommitHistoryMaxRetries; i++)
        {
            response =
                _graphQlApiLoader.Load(_console)
                    .GetCommitHistoryAsync(
                        organization,
                        repository,
                        !string.IsNullOrEmpty(branch) ? branch! : GetDefaultBranch(projectName)!,
                        _params.GetCopyWithCommits(_params.GetCommits() * (i + 1)))
                    .GetAwaiter().GetResult();
            if (AllCommitsFound(changes, response))
            {
                break;
            }
            _console.WarnFmt(
                "Commit history response did not contain all commits, retrying with larger commit"
                    + " window. Current window: {0}",
                _params.GetCommits() * (i + 1));
        }

        foreach (var change in changes)
        {
            string sha = ((GitRevision)change.GetChange().GetRevision()).GetHash();

            var associatedPullRequests = GetAssociatedPullRequest(sha, response);
            if (associatedPullRequests == null
                || associatedPullRequests.GetEdges() == null
                || associatedPullRequests.GetEdges()!.Count == 0)
            {
                _console.WarnFmt(
                    "Expected to find at least one pull request associated with commit sha '{0}', but"
                        + " found 0'. Consider expanding the commit history validation window via"
                        + " --gql-commit-history-override. Skipping authorship and approval predicate"
                        + " provisioning for this commit...",
                    sha);
                builder.Add(change);
                continue;
            }
            var pullRequest = associatedPullRequests.GetEdges()![0].GetNode()!;

            string author = pullRequest.GetAuthor()!.GetLogin()!;
            var authorPredicate =
                new UserPredicate(
                    author,
                    UserPredicate.UserPredicateType.OWNER,
                    change.GetChange().GetRevision().GetUrl()!,
                    $"GitHub user '{author}' authored change with sha '{sha}'.");
            ChangeWithApprovals changeInProgress = change.AddApprovals(new[] { authorPredicate });

            foreach (string approverLogin in ExtractApprovers(pullRequest))
            {
                var approverPredicate =
                    new UserPredicate(
                        approverLogin,
                        UserPredicate.UserPredicateType.LGTM,
                        change.GetChange().GetRevision().GetUrl()!,
                        $"GitHub user '{approverLogin}' approved change with sha '{sha}'.");
                changeInProgress = changeInProgress.AddApprovals(new[] { approverPredicate });
            }

            builder.Add(changeInProgress);
        }
        return builder.ToImmutable();
    }

    private static bool AllCommitsFound(
        ImmutableArray<ChangeWithApprovals> changes, CommitHistoryResponse? response)
    {
        var historyNodes = GetHistoryNodes(response);
        var responseOids =
            historyNodes.Where(n => n.GetOid() != null).Select(n => n.GetOid()!).ToImmutableHashSet();
        foreach (var change in changes)
        {
            string sha = ((GitRevision)change.GetChange().GetRevision()).GetHash();
            if (!responseOids.Contains(sha))
            {
                return false;
            }
        }
        return true;
    }

    private static IReadOnlyList<CommitHistoryResponse.HistoryNode> GetHistoryNodes(
        CommitHistoryResponse? response) =>
        response?.GetData()?.GetRepository()?.GetRef()?.GetTarget()?.GetHistoryNodes()?.GetNodes()
            ?? new List<CommitHistoryResponse.HistoryNode>();

    private CommitHistoryResponse.AssociatedPullRequests? GetAssociatedPullRequest(
        string sha, CommitHistoryResponse? response)
    {
        var historyNodes = GetHistoryNodes(response);
        var history = historyNodes.FirstOrDefault(node => node.GetOid() == sha);
        _console.WarnFmtIf(
            history == null,
            "Unable to find history node for sha '{0}' -- the full CommitHistoryResponse is: {1}",
            sha,
            response!);
        return history?.GetAssociatedPullRequests();
    }

    private static ImmutableArray<string> ExtractApprovers(
        CommitHistoryResponse.AssociatedPullRequestNode pullRequest) =>
        (pullRequest.GetLatestOpinionatedReviews()?.GetEdges()
            ?? new List<CommitHistoryResponse.AuthorEdges>())
            .Where(review => review.GetNode()?.GetState() == "APPROVED")
            .Select(reviewer => reviewer.GetNode()!.GetAuthor()!.GetLogin()!)
            .ToImmutableArray();

    private string? GetDefaultBranch(string projectId)
    {
        try
        {
            string? branch =
                _restApiLoader.Load(_console).GetRepositoryAsync(projectId)
                    .GetAwaiter().GetResult().GetDefaultBranch();
            _console.InfoFmt("Inferred primary branch as '{0}'", branch!);
            return branch;
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            _console.WarnFmt(
                "Failed to get branch for project {0} with error '{1}'", projectId, e.Message);
            return null;
        }
    }
}
