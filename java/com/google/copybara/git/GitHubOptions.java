/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.copybara.git;

import com.beust.jcommander.Parameter;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LazyResourceLoader;
import com.google.copybara.Option;
import com.google.copybara.checks.ApiChecker;
import com.google.copybara.checks.Checker;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.github.api.GitHubApi;
import com.google.copybara.git.github.api.GitHubApiTransport;
import com.google.copybara.git.github.api.GitHubApiTransportImpl;
import com.google.copybara.git.github.api.GitHubApiTransportWithChecker;
import com.google.copybara.git.github.util.GitHubHost;
import com.google.copybara.util.console.Console;
import javax.annotation.Nullable;

/**
 * Options related to GitHub
 */
public class GitHubOptions implements Option {

  protected final GeneralOptions generalOptions;
  private final GitOptions gitOptions;

  public GitHubOptions(GeneralOptions generalOptions, GitOptions gitOptions) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
  }

  /** Returns a lazy supplier of {@link GitHubApi}. */
  public LazyResourceLoader<GitHubApi> newGitHubApiSupplier(
      String url, @Nullable Checker checker, GitHubHost ghHost) {
    return (console) -> {
      String project = ghHost.getProjectNameFromUrl(url);
      return checker == null ? newGitHubApi(project) : newGitHubApi(project, checker, console);
    };
  }

  /**
   * Returns a new {@link GitHubApi} instance for the given project.
   *
   * <p>The project for 'https://github.com/foo/bar' is 'foo/bar'.
   */
  public GitHubApi newGitHubApi(String gitHubProject) throws RepoException {
    return newGitHubApi(gitHubProject, /*checker*/ null, generalOptions.console());
  }

  /**
   * Returns a new {@link GitHubApi} instance for the given project  enforcing the given
   * {@link Checker}.
   *
   * <p>The project for 'https://github.com/foo/bar' is 'foo/bar'.
   */
  public GitHubApi newGitHubApi(String gitHubProject, @Nullable Checker checker, Console console)
      throws RepoException {
    GitRepository repo = getCredentialsRepo();

    String storePath = gitOptions.getCredentialHelperStorePath();
    if (storePath == null) {
      storePath = "~/.git-credentials";
    }
    GitHubApiTransport transport = newTransport(repo, storePath, console);
    if (checker != null) {
      transport = new GitHubApiTransportWithChecker(transport, new ApiChecker(checker, console));
    }
    return new GitHubApi(transport, generalOptions.profiler());
  }

  @Parameter(names = "--github-destination-delete-pr-branch",
      description = "Overwrite git.github_destination delete_pr_branch field", arity = 1)
  Boolean gitHubDeletePrBranch = null;

  @VisibleForTesting
  protected GitRepository getCredentialsRepo() throws RepoException {
    return gitOptions.cachedBareRepoForUrl("just_for_github_api");
  }

  /** Validate if a {@link Checker} is valid to use with GitHub endpoints. */
  public void validateEndpointChecker(@Nullable Checker checker) throws ValidationException {
    // Accept any by default
  }

  private GitHubApiTransport newTransport(
      GitRepository repo, String storePath, Console console) {
    return new GitHubApiTransportImpl(repo, newHttpTransport(), storePath, console);
  }

  protected HttpTransport newHttpTransport() {
    return new NetHttpTransport();
  }
}
