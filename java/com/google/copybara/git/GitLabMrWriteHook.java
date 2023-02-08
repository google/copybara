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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.GeneralOptions;
import com.google.copybara.exception.RedundantChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitDestination.MessageInfo;
import com.google.copybara.git.GitDestination.WriterImpl.DefaultWriteHook;
import com.google.copybara.git.gitlab.api.GitLabApi;
import com.google.copybara.git.gitlab.api.MergeRequest;
import com.google.copybara.git.gitlab.util.GitLabHost;
import com.google.copybara.revision.Change;
import com.google.copybara.util.console.Console;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A write hook for GitLab Mr.
 */
public class GitLabMrWriteHook extends DefaultWriteHook {

  private final String repoUrl;
  private final GeneralOptions generalOptions;
  private final GitLabOptions gitLabOptions;
  private final boolean partialFetch;
  private final Console console;
  private final GitLabHost glHost;
  @Nullable
  private final String prBranchToUpdate;
  private final boolean allowEmptyDiff;

  GitLabMrWriteHook(
      GeneralOptions generalOptions,
      String repoUrl,
      GitLabOptions gitLabOptions,
      @Nullable String prBranchToUpdate,
      boolean partialFetch,
      boolean allowEmptyDiff,
      Console console,
      GitLabHost glHost) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.repoUrl = Preconditions.checkNotNull(repoUrl);
    this.gitLabOptions = Preconditions.checkNotNull(gitLabOptions);
    this.prBranchToUpdate = prBranchToUpdate;
    this.partialFetch = partialFetch;
    this.allowEmptyDiff = allowEmptyDiff;
    this.console = Preconditions.checkNotNull(console);
    this.glHost = Preconditions.checkNotNull(glHost);
  }

  @Override
  public void beforePush(
      GitRepository scratchClone,
      MessageInfo messageInfo,
      boolean skipPush,
      List<? extends Change<?>> originChanges)
      throws ValidationException, RepoException {
    if (skipPush || generalOptions.allowEmptyDiff(allowEmptyDiff)) {
      return;
    }
    for (Change<?> originalChange : originChanges) {
      String configProjectName = glHost.getProjectNameFromUrl(repoUrl);
      GitLabApi api = gitLabOptions.newGitLabApi();

      ImmutableList<MergeRequest> mergeRequests =
          api.getMergeRequests(
              configProjectName,
              prBranchToUpdate);

      // Just ignore empt-diff check when the size of prs is not equal to 1.
      // If the list size is empty, no pr has been created before.
      // If the list size is greater than 1, there might be something wrong.
      // We don't want to throw EmptyChangeException for some of the mrs with the empty diff.
      if (mergeRequests.size() != 1) {
        return;
      }
      SameGitTree sameGitTree =
          new SameGitTree(scratchClone, repoUrl, generalOptions, partialFetch);
      MergeRequest mergeRequest = mergeRequests.get(0);
      if (sameGitTree.hasSameTree(mergeRequest.getSha())) {
        throw new RedundantChangeException(
            String.format(
                "Skipping push to the existing mr %s/-/merge_requests/%s as the change %s is empty.",
                asHttpsUrl(), mergeRequest.getNumber(), originalChange.getRef()),
            mergeRequest.getSha());
      }
    }
  }

  private String asHttpsUrl() throws ValidationException {
    return gitLabOptions.getGitlabUrl() + "/" + glHost.getProjectNameFromUrl(repoUrl);
  }

  protected GitLabMrWriteHook withUpdatedMrBranch(String prBranchToUpdate) {
    return new GitLabMrWriteHook(
        this.generalOptions,
        this.repoUrl,
        this.gitLabOptions,
        prBranchToUpdate,
        this.partialFetch,
        this.allowEmptyDiff,
        this.console,
        this.glHost);
  }
}
