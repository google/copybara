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

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.base.Preconditions;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LazyResourceLoader;
import com.google.copybara.Option;
import com.google.copybara.checks.Checker;
import com.google.copybara.exception.RepoException;
import com.google.copybara.git.github.api.GitHubApiTransport;
import com.google.copybara.git.github.api.GitHubApiTransportImpl;
import com.google.copybara.git.github.api.GitHubApiTransportWithChecker;
import com.google.copybara.git.github.api.GithubApi;
import com.google.copybara.git.github.util.GithubUtil;
import com.google.copybara.util.console.Console;
import javax.annotation.Nullable;

/**
 * Options related to GitHub
 */
public class GithubOptions implements Option {

  protected final GeneralOptions generalOptions;
  private final GitOptions gitOptions;

  public GithubOptions(GeneralOptions generalOptions, GitOptions gitOptions) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
  }

  /**
   * Returns a lazy supplier of {@link GithubApi}.
   */
  public LazyResourceLoader<GithubApi> newGitHubApiSupplier(
      String url, @Nullable Checker checker) {
    return (console) -> {
      String project = GithubUtil.getProjectNameFromUrl(url);
      return checker == null ? newGitHubApi(project) : newGitHubApi(project, checker, console);
    };
  }

  /**
   * Returns a new {@link GithubApi} instance for the given project.
   *
   * <p>The project for 'https://github.com/foo/bar' is 'foo/bar'.
   */
  public GithubApi newGitHubApi(String gitHubProject) throws RepoException {
    return newGitHubApi(gitHubProject, /*checker*/ null, generalOptions.console());
  }

  /**
   * Returns a new {@link GithubApi} instance for the given project  enforcing the given
   * {@link Checker}.
   *
   * <p>The project for 'https://github.com/foo/bar' is 'foo/bar'.
   */
  public GithubApi newGitHubApi(String gitHubProject, @Nullable Checker checker, Console console)
      throws RepoException {
    GitRepository repo = gitOptions.cachedBareRepoForUrl("just_for_github_api");

    String storePath = gitOptions.getCredentialHelperStorePath();
    if (storePath == null) {
      storePath = "~/.git-credentials";
    }
    GitHubApiTransport transport = newTransport(repo, storePath, console);
    if (checker != null) {
      transport = new GitHubApiTransportWithChecker(transport, checker, console);
    }
    return new GithubApi(transport, generalOptions.profiler());
  }

  private GitHubApiTransport newTransport(
      GitRepository repo, String storePath, Console console) {
    return new GitHubApiTransportImpl(repo, newHttpTransport(), storePath, console);
  }

  protected HttpTransport newHttpTransport() {
    return new NetHttpTransport();
  }
}
