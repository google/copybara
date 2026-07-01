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

using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Git.GitHub.Api;
using Copybara.Git.GitHub.Util;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Git;

/// <summary>
/// Defines a behavior to perform before checking out a GitHub repository. Port of
/// <c>com.google.copybara.git.GitHubRepositoryHook</c>.
/// </summary>
public sealed class GitHubRepositoryHook : IGitRepositoryHook
{
    private readonly GitHubOptions _gitHubOptions;
    private readonly IGitRepositoryHook.GitRepositoryData _gitRepositoryData;
    private readonly CredentialFileHandler? _creds;
    private readonly Console _console;

    public GitHubRepositoryHook(
        IGitRepositoryHook.GitRepositoryData gitRepositoryData,
        GitHubOptions gitHubOptions,
        CredentialFileHandler? creds,
        Console console)
    {
        _gitHubOptions = Preconditions.CheckNotNull(gitHubOptions);
        _gitRepositoryData = Preconditions.CheckNotNull(gitRepositoryData);
        _creds = creds;
        _console = Preconditions.CheckNotNull(console);
    }

    /// <summary>
    /// Validates the GitHub repository data against the actual GitHub repository data.
    /// </summary>
    /// <exception cref="ValidationException">
    /// if the GitHub repository data does not match the actual GitHub repository data.
    /// </exception>
    /// <exception cref="RepoException">if the GitHub repository data cannot be retrieved.</exception>
    public void BeforeCheckout()
    {
        if (!ShouldRun(_gitRepositoryData))
        {
            return;
        }
        var gitHubHost = new GitHubHost("github.com");
        string projectId = gitHubHost.GetProjectNameFromUrl(_gitRepositoryData.Url);
        GitHubApi api =
            _gitHubOptions.NewGitHubRestApi(
                gitHubHost.GetHost(), projectId, null, _creds, _console);
        long actualId = api.GetRepositoryAsync(projectId).GetAwaiter().GetResult().GetId();
        if (!string.Equals(actualId.ToString(), GetGitRepositoryData().Id))
        {
            throw new ValidationException(
                $"Expected repository id {GetGitRepositoryData().Id} but got repo id {actualId}:"
                    + " please check the origin repository and confirm it has not been replaced.");
        }
    }

    public IGitRepositoryHook.GitRepositoryData GetGitRepositoryData() => _gitRepositoryData;

    private static bool ShouldRun(IGitRepositoryHook.GitRepositoryData gitRepositoryData) =>
        !string.IsNullOrEmpty(gitRepositoryData.Id);
}
