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
import com.beust.jcommander.Parameters;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
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
import com.google.copybara.git.github.api.GitHubGraphQLApi;
import com.google.copybara.git.github.util.GitHubHost;
import com.google.copybara.jcommander.DurationConverter;
import com.google.copybara.jcommander.GreaterThanZeroListValidator;
import com.google.copybara.jcommander.SemicolonSeparatedListSplitter;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Options related to GitHub
 */
@Parameters(separators = "=")
public class GitHubOptions implements Option {

  protected final GeneralOptions generalOptions;
  private final GitOptions gitOptions;

  @Parameter(
      names = "--gql-commit-history-override",
      description =
          "Flag used to target GraphQL params 'first' arguments in the event the defaults are over"
              + " or underusing the api ratelimit. This should be rarely used for repos that don't"
              + " fit well in our defaults. E.g. 50,5,5 represent 50 commits, 5 PRs for each"
              + " commit, 5 reviews per PR",
      validateWith = GreaterThanZeroListValidator.class)
  public List<Integer> gqlOverride = ImmutableList.of(50, 5, 5);

  @Parameter(
      names = "--allstar-app-ids",
      description =
          "Flag used to set AllStar GitHub app id aliases. See https://github.com/ossf/allstar.",
      splitter = SemicolonSeparatedListSplitter.class,
      validateWith = GreaterThanZeroListValidator.class)
  public List<Integer> allStarAppIds = ImmutableList.of(119816);

  @Parameter(
      names = "--github-pr-branch-deletion-delay",
      description = "Length of time, in seconds, to wait before deleting the GitHub PR branch",
      converter = DurationConverter.class,
      hidden = true)
  public Duration githubPrBranchDeletionDelay = null;

  @Parameter(
      names = "--github-api-bearer-auth",
      description = "If using a token for GitHub access, bearer auth might be required",
      arity = 1)
  public boolean gitHubApiBearerAuth = false;


  public GitHubOptions(GeneralOptions generalOptions, GitOptions gitOptions) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
  }

  /** Returns a lazy supplier of {@link GitHubApi}. */
  public LazyResourceLoader<GitHubApi> newGitHubApiSupplier(
      String url,
      @Nullable Checker checker,
      @Nullable CredentialFileHandler credentials,
      GitHubHost ghHost) {
    return (console) -> {
      String project = ghHost.getProjectNameFromUrl(url);
      return newGitHubRestApi(project, checker, credentials, console);
    };
  }

  /** Returns a lazy supplier of {@link GitHubGraphQLApi}. */
  public LazyResourceLoader<GitHubGraphQLApi> newGitHubGraphQLApiSupplier(
      String url,
      @Nullable Checker checker,
      @Nullable CredentialFileHandler credentials,
      GitHubHost ghHost) {
    return (console) -> {
      String project = ghHost.getProjectNameFromUrl(url);
      return newGitHubGraphQLApi(project, checker, credentials, console);
    };
  }

  /**
   * Returns a new {@link GitHubApi} instance for the given project.
   *
   * <p>The project for 'https://github.com/foo/bar' is 'foo/bar'.
   */
  public GitHubApi newGitHubRestApi(
      String gitHubProject, @Nullable CredentialFileHandler credentials) throws RepoException {
    return newGitHubRestApi(
        gitHubProject, /* checker= */ null, credentials, generalOptions.console());
  }

  /**
   * Returns a new {@link GitHubApi} instance for the given project enforcing the given {@link
   * Checker}.
   *
   * <p>The project for 'https://github.com/foo/bar' is 'foo/bar'.
   */
  public GitHubApi newGitHubRestApi(
      String gitHubProject,
      @Nullable Checker checker,
      @Nullable CredentialFileHandler credentials,
      Console console)
      throws RepoException {
    GitRepository repo = getCredentialsRepo(credentials);
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

  /**
   * Returns a new {@link GitHubApi} instance for the given project.
   *
   * <p>The project for 'https://github.com/foo/bar' is 'foo/bar'.
   */
  public GitHubGraphQLApi newGitHubGraphQLApi(
      String gitHubProject, @Nullable CredentialFileHandler credentials) throws RepoException {
    return newGitHubGraphQLApi(
        gitHubProject, /* checker= */ null, credentials, generalOptions.console());
  }

  /**
   * Returns a new {@link GitHubApi} instance for the given project enforcing the given {@link
   * Checker}.
   *
   * <p>The project for 'https://github.com/foo/bar' is 'foo/bar'.
   */
  public GitHubGraphQLApi newGitHubGraphQLApi(
      String gitHubProject,
      @Nullable Checker checker,
      @Nullable CredentialFileHandler credentials,
      Console console)
      throws RepoException {
    GitRepository repo = getCredentialsRepo(credentials);

    String storePath = gitOptions.getCredentialHelperStorePath();
    if (storePath == null) {
      storePath = "~/.git-credentials";
    }
    GitHubApiTransport transport = newTransport(repo, storePath, console);
    if (checker != null) {
      transport = new GitHubApiTransportWithChecker(transport, new ApiChecker(checker, console));
    }
    return new GitHubGraphQLApi(transport, generalOptions.profiler());
  }

  @Parameter(names = "--github-destination-delete-pr-branch",
      description = "Overwrite git.github_destination delete_pr_branch field", arity = 1)
  Boolean gitHubDeletePrBranch = null;

  @VisibleForTesting
  protected GitRepository getCredentialsRepo(@Nullable CredentialFileHandler creds)
      throws RepoException {
    GitRepository repo = gitOptions.cachedBareRepoForUrl("just_for_github_api");
    if (creds != null) {
      try {
        creds.install(repo, gitOptions.getConfigCredsFile(generalOptions));
      } catch (IOException e) {
        throw new RepoException("Unable to create creds file.", e);
      }
    }
    return repo;
  }

  /** Validate if a {@link Checker} is valid to use with GitHub endpoints. */
  public void validateEndpointChecker(@Nullable Checker checker) throws ValidationException {
    // Accept any by default
  }

  private GitHubApiTransport newTransport(
      GitRepository repo, String storePath, Console console) {
    return new GitHubApiTransportImpl(
        repo, newHttpTransport(), storePath, gitHubApiBearerAuth, console);
  }

  protected HttpTransport newHttpTransport() {
    return new NetHttpTransport();
  }
}