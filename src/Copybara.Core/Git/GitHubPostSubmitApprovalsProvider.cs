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
using Copybara.Git.GitHub.Util;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Git;

/// <summary>
/// Fills out change predicates for post submit GitHub origin changes. Port of
/// <c>com.google.copybara.git.GitHubPostSubmitApprovalsProvider</c>.
/// </summary>
public class GitHubPostSubmitApprovalsProvider : IApprovalsProvider
{
    private readonly string? _branch;
    private readonly GitHubHost _githubHost;
    private readonly GitHubSecuritySettingsValidator _securitySettingsValidator;
    private readonly GitHubUserApprovalsValidator _userApprovalsValidator;

    public GitHubPostSubmitApprovalsProvider(
        GitHubHost githubHost,
        string? branch,
        GitHubSecuritySettingsValidator securitySettingsValidator,
        GitHubUserApprovalsValidator userApprovalsValidator)
    {
        _githubHost = githubHost;
        _branch = branch;
        _securitySettingsValidator = securitySettingsValidator;
        _userApprovalsValidator = userApprovalsValidator;
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
        var sampleRevision = changes[^1].GetChange().GetRevision();
        string projectId = _githubHost.GetProjectNameFromUrl(sampleRevision.GetUrl()!);
        string organization = _githubHost.GetUserNameFromUrl(sampleRevision.GetUrl()!);

        var unusualChanges = FindChangesWithUnexpectedOrigin(projectId, changes);
        if (!unusualChanges.IsEmpty)
        {
            console.WarnFmt(
                "Expected all changes to originate from GitHub project '{0}'. But these changes have"
                    + " other origins {1}. Skipping statement predicate provisioning for this change"
                    + " list...",
                projectId,
                string.Join(", ", unusualChanges.Select(c => c.ToString())));
            return new ApprovalsResult(changes);
        }

        ImmutableArray<ChangeWithApprovals> approvalsInProgress = changes;
        try
        {
            approvalsInProgress =
                _securitySettingsValidator.MapTwoFactorAuth(approvalsInProgress, organization);
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
            approvalsInProgress =
                _securitySettingsValidator.MapAllStar(approvalsInProgress, organization);
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            console.WarnFmt(
                "Could not validate GitHub organization security settings for AllStar installation"
                    + " with error '{0}'. Skipping this step...",
                e.Message);
        }
        try
        {
            approvalsInProgress =
                _userApprovalsValidator.MapApprovalsForUserPredicates(approvalsInProgress, _branch);
        }
        catch (Exception e) when (e is ValidationException or RepoException)
        {
            console.WarnFmt(
                "Could not validate user approvals and authorship with error '{0}'. Skipping this"
                    + " step...",
                e.Message);
        }

        return new ApprovalsResult(approvalsInProgress);
    }

    private ImmutableArray<ChangeWithApprovals> FindChangesWithUnexpectedOrigin(
        string projectId, ImmutableArray<ChangeWithApprovals> changes)
    {
        var unusualChanges = ImmutableArray.CreateBuilder<ChangeWithApprovals>();
        foreach (var change in changes)
        {
            if (_githubHost.GetProjectNameFromUrl(change.GetChange().GetRevision().GetUrl()!)
                != projectId)
            {
                unusualChanges.Add(change);
            }
        }
        return unusualChanges.ToImmutable();
    }
}
