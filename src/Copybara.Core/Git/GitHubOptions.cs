/*
 * Copyright (C) 2017 Google LLC
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
using Copybara.Exceptions;
using Copybara.Git.GitHub.Api;
using Copybara.Git.GitHub.Util;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Git;

/// <summary>
/// Options related to GitHub. Port of <c>com.google.copybara.git.GitHubOptions</c>.
/// </summary>
public class GitHubOptions : IOption
{
    protected readonly GeneralOptions GeneralOptions;
    protected readonly GitOptions GitOptions;
    private HttpClient? _httpTransport;

    /// <summary>
    /// Flag used to target GraphQL params 'first' arguments in the event the defaults are over or
    /// underusing the api ratelimit.
    /// </summary>
    [Flag("--gql-commit-history-override",
        "Flag used to target GraphQL params 'first' arguments in the event the defaults are over"
            + " or underusing the api ratelimit. The flag value should be semicolon separated."
            + " This should be rarely used for repos that don't fit well in our defaults. E.g."
            + " '50;5;5' represent 50 commits, 5 PRs for each commit, 5 reviews per PR")]
    public List<int> GqlOverride { get; set; } = new() { 50, 5, 5 };

    /// <summary>Flag used to set AllStar GitHub app id aliases.</summary>
    [Flag("--allstar-app-ids",
        "Flag used to set AllStar GitHub app id aliases. See https://github.com/ossf/allstar.")]
    public List<int> AllStarAppIds { get; set; } = new() { 119816 };

    /// <summary>Length of time to wait before deleting the GitHub PR branch.</summary>
    [Flag("--github-pr-branch-deletion-delay",
        "Length of time, in seconds, to wait before deleting the GitHub PR branch")]
    public TimeSpan? GithubPrBranchDeletionDelay { get; set; }

    /// <summary>If using a token for GitHub access, bearer auth might be required.</summary>
    [Flag("--github-api-bearer-auth",
        "If using a token for GitHub access, bearer auth might be required", Arity = 1)]
    public bool GitHubApiBearerAuth { get; set; }

    /// <summary>Overwrite git.github_destination delete_pr_branch field.</summary>
    [Flag("--github-destination-delete-pr-branch",
        "Overwrite git.github_destination delete_pr_branch field", Arity = 1)]
    public bool? GitHubDeletePrBranch { get; set; }

    public GitHubOptions(GeneralOptions generalOptions, GitOptions gitOptions)
    {
        GeneralOptions = Preconditions.CheckNotNull(generalOptions);
        GitOptions = Preconditions.CheckNotNull(gitOptions);
    }

    /// <summary>Returns a lazy supplier of <see cref="GitHubApi"/>.</summary>
    public LazyResourceLoader<GitHubApi> NewGitHubApiSupplier(
        string url, IChecker? checker, CredentialFileHandler? credentials, GitHubHost ghHost) =>
        LazyResourceLoader.Memoized<GitHubApi>(console =>
        {
            string project = ghHost.GetProjectNameFromUrl(url);
            return NewGitHubRestApi(ghHost.GetHost(), project, checker, credentials, console!);
        });

    /// <summary>Returns a lazy supplier of <see cref="GitHubGraphQLApi"/>.</summary>
    public LazyResourceLoader<GitHubGraphQLApi> NewGitHubGraphQLApiSupplier(
        string url, IChecker? checker, CredentialFileHandler? credentials, GitHubHost ghHost) =>
        LazyResourceLoader.Memoized<GitHubGraphQLApi>(console =>
        {
            string project = ghHost.GetProjectNameFromUrl(url);
            return NewGitHubGraphQLApi(ghHost.GetHost(), project, checker, credentials, console!);
        });

    /// <summary>
    /// Returns a new <see cref="GitHubApi"/> instance for the given project enforcing the given
    /// <see cref="IChecker"/>.
    /// </summary>
    public virtual GitHubApi NewGitHubRestApi(
        string gitHubHostName,
        string gitHubProject,
        IChecker? checker,
        CredentialFileHandler? credentials,
        Console console)
    {
        GitRepository repo = GetCredentialsRepo(credentials);
        string storePath = GitOptions.GetCredentialHelperStorePath() ?? "~/.git-credentials";
        IGitHubApiTransport transport = NewTransport(gitHubHostName, repo, storePath, console);
        if (checker != null)
        {
            transport = new GitHubApiTransportWithChecker(transport, new ApiChecker(checker, console));
        }
        return new GitHubApi(transport, GeneralOptions.Profiler(), console);
    }

    /// <summary>
    /// Returns a new <see cref="GitHubGraphQLApi"/> instance for the given project enforcing the
    /// given <see cref="IChecker"/>.
    /// </summary>
    public virtual GitHubGraphQLApi NewGitHubGraphQLApi(
        string gitHubHostName,
        string gitHubProject,
        IChecker? checker,
        CredentialFileHandler? credentials,
        Console console)
    {
        GitRepository repo = GetCredentialsRepo(credentials);
        string storePath = GitOptions.GetCredentialHelperStorePath() ?? "~/.git-credentials";
        IGitHubApiTransport transport = NewTransport(gitHubHostName, repo, storePath, console);
        if (checker != null)
        {
            transport = new GitHubApiTransportWithChecker(transport, new ApiChecker(checker, console));
        }
        return new GitHubGraphQLApi(transport, GeneralOptions.Profiler());
    }

    public IGitRepositoryHook GetGitRepositoryHook(
        IGitRepositoryHook.GitRepositoryData gitRepositoryData,
        CredentialFileHandler? credentials) =>
        new GitHubRepositoryHook(
            gitRepositoryData, this, credentials, GeneralOptions.GetConsole());

    protected virtual GitRepository GetCredentialsRepo(CredentialFileHandler? creds)
    {
        GitRepository repo = GitOptions.CachedBareRepoForUrl("just_for_github_api");
        if (creds != null)
        {
            try
            {
                creds.Install(repo, GitOptions.GetConfigCredsFile(GeneralOptions));
            }
            catch (IOException e)
            {
                throw new RepoException("Unable to create creds file.", e);
            }
        }
        return repo;
    }

    /// <summary>Validate if a <see cref="IChecker"/> is valid to use with GitHub endpoints.</summary>
    public virtual void ValidateEndpointChecker(IChecker? checker)
    {
        // Accept any by default.
    }

    protected virtual IGitHubApiTransport NewTransport(
        string gitHubHostName, GitRepository repo, string storePath, Console console) =>
        new GitHubApiTransportImpl(
            repo, NewHttpTransport(), storePath, GitHubApiBearerAuth, console, gitHubHostName);

    protected virtual HttpClient NewHttpTransport() => _httpTransport ??= new HttpClient();
}
