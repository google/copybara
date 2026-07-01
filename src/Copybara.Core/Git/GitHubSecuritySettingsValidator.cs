/*
 * Copyright (C) 2023 Google Inc.
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
using Console = Copybara.Util.Console.Console;
using GitHubApiClient = Copybara.Git.GitHub.Api.GitHubApi;

namespace Copybara.Git;

/// <summary>
/// Provides Statement Predicates for GitHub Security related predicates. Port of
/// <c>com.google.copybara.git.GitHubSecuritySettingsValidator</c>.
/// </summary>
public class GitHubSecuritySettingsValidator
{
    public const string AllStarPredicateType = "github.organization.all_star_installed";
    public const string TwoFactorPredicateType = "github.organization.2FA_requirement_enabled";

    private readonly LazyResourceLoader<GitHubApiClient> _apiLoader;
    private readonly IReadOnlyList<int> _allStarAppIds;
    private readonly Console _console;

    public GitHubSecuritySettingsValidator(
        LazyResourceLoader<GitHubApiClient> apiLoader,
        IReadOnlyList<int> allStarAppIds,
        Console console)
    {
        _apiLoader = apiLoader;
        _console = console;
        _allStarAppIds = allStarAppIds;
    }

    /// <summary>
    /// Provisions a <see cref="StatementPredicate"/> that describes whether the origin GitHub
    /// repository has two factor authentication enabled to <paramref name="changes"/>.
    /// </summary>
    public ImmutableArray<ChangeWithApprovals> MapTwoFactorAuth(
        ImmutableArray<ChangeWithApprovals> changes, string organization)
    {
        if (changes.IsEmpty)
        {
            return ImmutableArray<ChangeWithApprovals>.Empty;
        }
        if (!HasTwoFactorEnabled(organization))
        {
            return changes;
        }
        return AppendPredicateToAll(
            changes,
            new StatementPredicate(
                TwoFactorPredicateType,
                "Whether the organization that the change originated from has two factor"
                    + " authentication requirement enabled.",
                changes[^1].GetChange().GetRevision().GetUrl()!));
    }

    /// <summary>
    /// Provisions a <see cref="StatementPredicate"/> that describes whether the origin GitHub
    /// repository has AllStar installed to <paramref name="changes"/>.
    /// </summary>
    public ImmutableArray<ChangeWithApprovals> MapAllStar(
        ImmutableArray<ChangeWithApprovals> changes, string organization)
    {
        if (changes.IsEmpty)
        {
            return ImmutableArray<ChangeWithApprovals>.Empty;
        }
        if (!HasAllStar(organization))
        {
            return changes;
        }
        return AppendPredicateToAll(
            changes,
            new StatementPredicate(
                AllStarPredicateType,
                "Whether the organization that the change originated from has allstar installed",
                changes[^1].GetChange().GetRevision().GetUrl()!));
    }

    private static ImmutableArray<ChangeWithApprovals> AppendPredicateToAll(
        ImmutableArray<ChangeWithApprovals> changes, StatementPredicate predicate)
    {
        var builder = ImmutableArray.CreateBuilder<ChangeWithApprovals>();
        foreach (var change in changes)
        {
            builder.Add(change.AddApprovals(new[] { predicate }));
        }
        return builder.ToImmutable();
    }

    private bool HasAllStar(string organization)
    {
        try
        {
            return _apiLoader.Load(_console).GetInstallationsAsync(organization)
                .GetAwaiter().GetResult()
                .Any(installation => _allStarAppIds.Contains(installation.GetAppId()));
        }
        catch (GitHubApiException e)
        {
            throw HandleGitHubException(
                e,
                "Confirming AllStar app installation",
                "Please review your copybara app permissions, this request requires admin:read"
                    + " permissions.");
        }
    }

    private bool HasTwoFactorEnabled(string organization)
    {
        try
        {
            bool? twoFactorEnabled =
                _apiLoader.Load(_console).GetOrganizationAsync(organization)
                    .GetAwaiter().GetResult().GetTwoFactorRequirementEnabled();
            if (twoFactorEnabled == null)
            {
                _console.WarnFmt(
                    "Copybara could not confirm that 2FA requirement is being enforced in the '{0}'"
                        + " GitHub organization, so it will be assumed as being not enforced. Please"
                        + " confirm Copybara is given admin:org permissions with your GitHub org"
                        + " admins and try again.",
                    organization);
                return false;
            }
            return twoFactorEnabled.Value;
        }
        catch (GitHubApiException e)
        {
            throw HandleGitHubException(e, "Confirm organizational enforcement of 2FA", "");
        }
    }

    /// <summary>
    /// Wraps a <see cref="GitHubApiException"/> as a user error if the GitHub response code is a
    /// user issue. Otherwise, throws it as is.
    /// </summary>
    private static Exception HandleGitHubException(
        GitHubApiException e, string operationAttempted, string userRecourse)
    {
        if (e.GetResponseCode() is GitHubApiResponseCode.NOT_FOUND
            or GitHubApiResponseCode.FORBIDDEN
            or GitHubApiResponseCode.UNAUTHORIZED)
        {
            string userRecourseIfAny =
                !string.IsNullOrEmpty(userRecourse)
                    ? $"Possible user recourse: '{userRecourse}'."
                    : "";
            return new ValidationException(
                $"Encountered user error while attempting to '{operationAttempted}'. With Github"
                    + $" HTTP response code '{e.GetResponseCode()}'. {userRecourseIfAny}",
                e);
        }
        return e;
    }
}
