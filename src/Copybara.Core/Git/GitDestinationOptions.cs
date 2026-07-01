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

using Copybara.Authoring;
using Copybara.Common;
using Copybara.Exceptions;

namespace Copybara.Git;

/// <summary>
/// Arguments for <see cref="GitDestination"/>, <see cref="GitOrigin"/>, and other Git components.
/// Port of <c>com.google.copybara.git.GitDestinationOptions</c>.
/// </summary>
public sealed class GitDestinationOptions : IOption
{
    private readonly GeneralOptions _generalOptions;
    private readonly GitOptions _gitOptions;

    [Flag(
        "--git-committer-name",
        "If set, overrides the committer name for the generated commits in git destination.")]
    public string CommitterName { get; set; } = "";

    [Flag(
        "--git-committer-email",
        "If set, overrides the committer e-mail for the generated commits in git destination.")]
    public string CommitterEmail { get; set; } = "";

    [Flag(
        "--git-skip-checker",
        "If true and git.destination has a configured checker, it will not be used in the"
            + " migration.",
        Arity = 1)]
    public bool SkipGitChecker { get; set; }

    public GitDestinationOptions(GeneralOptions generalOptions, GitOptions gitOptions)
    {
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
        _gitOptions = Preconditions.CheckNotNull(gitOptions);
    }

    internal Author GetCommitter() => new(CommitterName, CommitterEmail);

    [Flag("--git-destination-url", "If set, overrides the git destination URL.")]
    internal string? Url { get; set; }

    [Flag(
        "--git-destination-fetch",
        "If set, overrides the git destination fetch reference.")]
    public string? Fetch { get; set; }

    [Flag(
        "--git-destination-push",
        "If set, overrides the git destination push reference.")]
    public string? Push { get; set; }

    [Flag(
        "--git-destination-path",
        "If set, the tool will use this directory for the local repository. Note that if the"
            + " directory exists it needs to be a git repository. Copybara will revert any"
            + " staged/unstaged changes. For example, you can override destination url with a local"
            + " non-bare repo (or existing empty folder) with this flag.")]
    public string? LocalRepoPath { get; set; }

    [Flag(
        "--git-destination-last-rev-first-parent",
        "Use git --first-parent flag when looking for last-rev in previous commits")]
    internal bool LastRevFirstParent { get; set; }

    [Flag(
        "--git-destination-non-fast-forward",
        "Allow non-fast-forward pushes to the destination. We only allow this when used with"
            + " different push != fetch references.")]
    internal bool NonFastForwardPush { get; set; }

    [Flag(
        "--git-destination-ignore-integration-errors",
        "If an integration error occurs, ignore it and continue without the integrate")]
    internal bool IgnoreIntegrationErrors { get; set; }

    [Flag(
        "--nogit-destination-rebase",
        "Don't rebase the change automatically for workflows CHANGE_REQUEST mode")]
    public bool NoRebase { get; set; }

    [Flag(
        "--git-destination-fetch-depth",
        "Use a shallow clone of the specified depth for git.destination")]
    internal int? FetchDepth { get; set; }

    public int? GetFetchDepth() => FetchDepth;

    internal bool RebaseWhenBaseline() => !NoRebase;

    /// <summary>
    /// Returns a non-bare repo. Either because it uses a custom worktree or because it is a user
    /// non-bare repo.
    /// </summary>
    internal GitRepository LocalGitRepo(string url, CredentialFileHandler? creds)
    {
        GitRepository repo = GetLocalGitRepository(url);
        if (creds != null)
        {
            try
            {
                creds.Install(repo, _gitOptions.GetConfigCredsFile(_generalOptions));
            }
            catch (IOException e)
            {
                throw new RepoException("Unable to store git credentials", e);
            }
        }
        return repo;
    }

    private GitRepository GetLocalGitRepository(string url)
    {
        try
        {
            if (string.IsNullOrEmpty(LocalRepoPath))
            {
                return _gitOptions.CachedBareRepoForUrl(url)
                    .WithWorkTree(_generalOptions.GetDirFactory().NewTempDir("git_dest"));
            }
            string path = LocalRepoPath;

            if (!Directory.Exists(path) || (Directory.Exists(path) && IsGitRepoOrEmptyDir(path)))
            {
                Directory.CreateDirectory(path);
                return _gitOptions.InitRepo(
                    GitRepository.NewRepo(
                        _generalOptions.IsVerbose(),
                        path,
                        _gitOptions.GetGitEnvironment(_generalOptions.GetEnvironment()),
                        _generalOptions.RepoTimeout,
                        _gitOptions.GitNoVerify,
                        _gitOptions.GetPushOptionsValidator()));
            }
            throw new RepoException(path + " is not empty and is not a git repository");
        }
        catch (IOException e)
        {
            throw new RepoException("Cannot create local repository", e);
        }
    }

    private static bool IsGitRepoOrEmptyDir(string path) =>
        Directory.Exists(Path.Combine(path, ".git"))
        || (!Directory.EnumerateFileSystemEntries(path).Any());

    /// <summary>Used internally to be able to traverse the local repo once a migration finishes.</summary>
    public string? CustomLocalBranch { get; set; }

    /// <summary>Returns the local branch that will be used for working on the change before pushing.</summary>
    internal string GetLocalBranch(string resolvedPush, bool dryRun) =>
        LocalRepoPath != null
            ? resolvedPush // This is nicer for the user
            : CustomLocalBranch
                ?? "copybara/resolvedPush-" + Guid.NewGuid() + (dryRun ? "-dryrun" : "");
}
