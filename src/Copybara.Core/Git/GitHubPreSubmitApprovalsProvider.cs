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

namespace Copybara.Git;

/// <summary>
/// Fills out change predicates for presubmit GitHub origin changes. Port of
/// <c>com.google.copybara.git.GitHubPreSubmitApprovalsProvider</c>.
/// </summary>
public class GitHubPreSubmitApprovalsProvider : IApprovalsProvider
{
    private readonly GitHubOptions _githubOptions;
    private readonly GitHubHost _githubHost;
    private readonly GitHubSecuritySettingsValidator _securitySettingsValidator;
    private readonly GitHubUserApprovalsValidator _userApprovalsValidator;
    private readonly CredentialFileHandler? _creds;

    public GitHubPreSubmitApprovalsProvider(
        GitHubOptions githubOptions,
        GitHubHost githubHost,
        GitHubSecuritySettingsValidator securitySettingsValidator,
        GitHubUserApprovalsValidator userApprovalsValidator,
        CredentialFileHandler? creds)
    {
        _githubOptions = githubOptions;
        _securitySettingsValidator = securitySettingsValidator;
        _userApprovalsValidator = userApprovalsValidator;
        _githubHost = githubHost;
        _creds = creds;
    }

    public ApprovalsResult ComputeApprovals(
        ImmutableArray<ChangeWithApprovals> changes,
        Func<string, IReadOnlyCollection<string>>? labelFinder,
        Console console)
    {
        if (changes.IsEmpty)
        {
            return new ApprovalsResult(ImmutableArray<ChangeWithApprovals>.Empty);
        }

        string sampleUrl = changes[^1].GetChange().GetRevision().GetUrl()!;
        string org = _githubHost.GetUserNameFromUrl(sampleUrl);
        string projectId = _githubHost.GetProjectNameFromUrl(sampleUrl);

        ImmutableArray<ChangeWithApprovals> approvalsInProgress = changes;

        try
        {
            approvalsInProgress = _securitySettingsValidator.MapTwoFactorAuth(approvalsInProgress, org);
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            console.WarnFmt(
                "Could not validate GitHub organization security settings for two factor"
                    + " authentication requirements with error '{0}'. Skipping this step...",
                e.Message);
        }
        try
        {
            approvalsInProgress = _securitySettingsValidator.MapAllStar(approvalsInProgress, org);
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            console.WarnFmt(
                "Could not validate GitHub organization security settings for AllStar installation"
                    + " with error '{0}'. Skipping this step...",
                e.Message);
        }

        // Find the branch the pull request is being made against. Need this to validate postsubmit
        // commits.
        string baseBranch =
            ExtractLabelValues(changes, labelFinder, GitHubPrOrigin.GithubBaseBranch).Single();
        string prNumber =
            ExtractLabelValues(changes, labelFinder, GitHubPrOrigin.GithubPrNumberLabel).Single();
        string prHeadSha =
            ExtractLabelValues(changes, labelFinder, GitHubPrOrigin.GithubPrHeadSha).Single();
        string prAuthor =
            ExtractLabelValues(changes, labelFinder, GitHubPrOrigin.GithubPrUser).Single();
        string baselineSha =
            ExtractLabelValues(changes, labelFinder, GitHubPrOrigin.GithubBaseBranchSha1).Single();

        // A bit counterintuitive, but the list is [latest_change...earliest_change]. This finds the
        // partition point where, inclusively at the baseline index and to the right, is a postsubmit
        // commit.
        int baseLineIndex = -1;
        for (int i = 0; i < approvalsInProgress.Length; i++)
        {
            if (((GitRevision)approvalsInProgress[i].GetChange().GetRevision()).GetHash()
                == baselineSha)
            {
                baseLineIndex = i;
                break;
            }
        }

        ImmutableArray<ChangeWithApprovals> preSubmitChanges =
            baseLineIndex != -1
                ? approvalsInProgress[..baseLineIndex]
                : approvalsInProgress;
        ImmutableArray<ChangeWithApprovals> postSubmitChanges =
            baseLineIndex != -1
                ? approvalsInProgress[baseLineIndex..]
                : ImmutableArray<ChangeWithApprovals>.Empty;

        var result = ImmutableArray.CreateBuilder<ChangeWithApprovals>();
        result.AddRange(
            TryPresubmitUserValidation(
                preSubmitChanges, projectId, int.Parse(prNumber), prHeadSha, prAuthor, console));
        result.AddRange(TryPostSubmitUserValidation(postSubmitChanges, baseBranch, console));
        return new ApprovalsResult(result.ToImmutable());
    }

    public ImmutableArray<ChangeWithApprovals> TryPostSubmitUserValidation(
        ImmutableArray<ChangeWithApprovals> postSubmitChanges, string branch, Console console)
    {
        try
        {
            return _userApprovalsValidator.MapApprovalsForUserPredicates(postSubmitChanges, branch);
        }
        catch (ValidationException e)
        {
            console.WarnFmt(
                "Could not do postsubmit changes validation with error '{0}'. Leaving changes as is"
                    + " and skipping this step...",
                e.Message);
            return postSubmitChanges;
        }
    }

    public ImmutableArray<ChangeWithApprovals> TryPresubmitUserValidation(
        ImmutableArray<ChangeWithApprovals> presubmitChanges,
        string projectId,
        int prNumber,
        string prHeadSha,
        string author,
        Console console)
    {
        var presubmitApprovalsInProgress = ImmutableArray.CreateBuilder<ChangeWithApprovals>();
        IReadOnlyList<Review> reviews;
        try
        {
            reviews =
                _githubOptions
                    .NewGitHubRestApi(_githubHost.GetHost(), projectId, null, _creds, console)
                    .GetReviewsAsync(projectId, prNumber)
                    .GetAwaiter().GetResult();
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            console.WarnFmt(
                "Could not do presubmit changes validation with error '{0}'. Leaving changes as is"
                    + " and skipping this step...",
                e.Message);
            return presubmitChanges;
        }

        var headApprovers = ExtractHeadApprovers(reviews, prHeadSha);
        foreach (var change in presubmitChanges)
        {
            string sha = ((GitRevision)change.GetChange().GetRevision()).GetHash();
            var predicates = new List<StatementPredicate>();
            predicates.AddRange(
                MapToUserPredicates(
                    headApprovers,
                    UserPredicate.UserPredicateType.LGTM,
                    change.GetChange().GetRevision().GetUrl()!,
                    sha));
            predicates.Add(
                new UserPredicate(
                    author,
                    UserPredicate.UserPredicateType.OWNER,
                    change.GetChange().GetRevision().GetUrl()!,
                    $"GitHub user '{author}' authored change with sha '{sha}'."));
            presubmitApprovalsInProgress.Add(change.AddApprovals(predicates));
        }
        return presubmitApprovalsInProgress.ToImmutable();
    }

    private static ImmutableArray<string> ExtractHeadApprovers(
        IReadOnlyList<Review> reviews, string headSha) =>
        reviews
            .Where(review => review.IsApproved() && review.GetCommitId() == headSha)
            .Select(review => review.GetUser()!.GetLogin()!)
            .ToImmutableArray();

    private static ImmutableArray<string> ExtractLabelValues(
        ImmutableArray<ChangeWithApprovals> changes,
        Func<string, IReadOnlyCollection<string>>? labelFinder,
        string key)
    {
        // Not all revisions share labels so find the first one that has what we are looking for.
        foreach (var change in changes)
        {
            var values = change.GetChange().GetRevision().AssociatedLabel(key);
            if (values.Count != 0)
            {
                return values.ToImmutableArray();
            }
        }

        // Look among the public and hidden transform labels.
        if (labelFinder != null)
        {
            var values = labelFinder(key);
            if (values.Count != 0)
            {
                return values.ToImmutableArray();
            }
        }
        throw new RepoException($"Could not find the value for label '{key}'");
    }

    private static ImmutableArray<UserPredicate> MapToUserPredicates(
        ImmutableArray<string> userIds,
        UserPredicate.UserPredicateType type,
        string url,
        string sha) =>
        userIds
            .Select(userId =>
                new UserPredicate(
                    userId,
                    type,
                    url,
                    $"GitHub user '{userId}' approved pull request associated with commit sha"
                        + $" '{sha}' at HEAD"))
            .ToImmutableArray();
}
