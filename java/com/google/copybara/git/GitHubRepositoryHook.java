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

package com.google.copybara.git;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.github.api.GitHubApi;
import com.google.copybara.git.github.util.GitHubHost;
import java.util.Objects;
import javax.annotation.Nullable;

/** Defines a behavior to perform before checking out a GitHub repository. */
public final class GitHubRepositoryHook implements GitRepositoryHook {
  private final GitHubOptions gitHubOptions;
  private final GitRepositoryData gitRepositoryData;
  private final CredentialFileHandler creds;

  public GitHubRepositoryHook(
      GitRepositoryData gitRepositoryData,
      GitHubOptions gitHubOptions,
      @Nullable CredentialFileHandler creds) {
    this.gitHubOptions = Preconditions.checkNotNull(gitHubOptions);
    this.gitRepositoryData = Preconditions.checkNotNull(gitRepositoryData);
    this.creds = creds;
  }

  /**
   * Validates the GitHub repository data against the actual GitHub repository data.
   *
   * @throws ValidationException if the GitHub repository data does not match the actual GitHub
   *     repository data.
   * @throws RepoException if the GitHub repository data cannot be retrieved.
   */
  @Override
  public void beforeCheckout() throws ValidationException, RepoException {
    if (!shouldRun(gitRepositoryData)) {
      return;
    }
    GitHubHost gitHubHost = new GitHubHost("github.com");
    String projectId = gitHubHost.getProjectNameFromUrl(gitRepositoryData.url());
    GitHubApi api = gitHubOptions.newGitHubRestApi(projectId, creds);
    long actualId = api.getRepository(projectId).getId();
    if (!Objects.equals(String.valueOf(actualId), getGitRepositoryData().id())) {
      throw new ValidationException(
          String.format(
              "Expected repository id %s but got repo id %s: please check the origin repository and"
                  + " confirm it has not been replaced.",
              getGitRepositoryData().id(), actualId));
    }
  }

  @Override
  public GitRepositoryData getGitRepositoryData() {
    return gitRepositoryData;
  }

  private boolean shouldRun(GitRepositoryData gitRepositoryData) {
    return !Strings.isNullOrEmpty(gitRepositoryData.id());
  }
}
