/*
 * Copyright (C) 2022 Google Inc.
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
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.checks.Checker;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.gitlab.api.GitLabApi;
import com.google.copybara.git.gitlab.api.GitLabApiTransport;
import com.google.copybara.git.gitlab.api.GitLabApiTransportImpl;
import com.google.copybara.util.console.Console;

import javax.annotation.Nullable;

/**
 * Options related to GitLab
 */
@Parameters(separators = "=")
public class GitLabOptions implements Option {

  protected final GeneralOptions generalOptions;
  private final GitOptions gitOptions;

  public GitLabOptions(GeneralOptions generalOptions, GitOptions gitOptions) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
  }

  @Parameter(names = "--gitlab-url",
      description = "Overwrite default gitlab url", arity = 1, required = true)
  String gitlabUrl = null;

  /**
   * Returns a new {@link GitLabApi} instance for the given project.
   *
   * <p>The project for 'https://gitlab.com/foo/bar' is 'foo/bar'.
   */
  public GitLabApi newGitLabApi() throws RepoException {
    return newGitLabApi(generalOptions.console());
  }

  public String getGitlabUrl() {
    return gitlabUrl;
  }

  /**
   * Returns a new {@link GitLabApi} instance for the given project  enforcing the given
   * {@link Checker}.
   *
   * <p>The project for 'https://gitlab.com/foo/bar' is 'foo/bar'.
   */
  public GitLabApi newGitLabApi(Console console)
      throws RepoException {
    GitRepository repo = getCredentialsRepo();

    String storePath = gitOptions.getCredentialHelperStorePath();
    if (storePath == null) {
      storePath = "~/.git-credentials";
    }
    GitLabApiTransport transport = newTransport(repo, storePath, console);
    return new GitLabApi(transport, generalOptions.profiler());
  }

  @VisibleForTesting
  protected GitRepository getCredentialsRepo() throws RepoException {
    return gitOptions.cachedBareRepoForUrl("just_for_gitlab_api");
  }

  /**
   * Validate if a {@link Checker} is valid to use with GitHub endpoints.
   */
  public void validateEndpointChecker(@Nullable Checker checker) throws ValidationException {
    // Accept any by default
  }

  private GitLabApiTransport newTransport(
      GitRepository repo, String storePath, Console console) {
    return new GitLabApiTransportImpl(repo, newHttpTransport(), storePath, console, gitlabUrl);
  }

  protected HttpTransport newHttpTransport() {
    return new NetHttpTransport();
  }
}
