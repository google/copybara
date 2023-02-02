/*
 * Copyright (C) 2020 Google Inc.
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
import com.google.common.collect.ImmutableSet;
import com.google.copybara.GeneralOptions;
import com.google.copybara.exception.RedundantChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitDestination.MessageInfo;
import com.google.copybara.git.GitDestination.WriterImpl.DefaultWriteHook;
import com.google.copybara.git.github.api.GitHubApi;
import com.google.copybara.git.github.api.GitHubApi.PullRequestListParams;
import com.google.copybara.git.github.api.GitHubApiException;
import com.google.copybara.git.github.api.GitHubApiException.ResponseCode;
import com.google.copybara.git.github.api.PullRequest;
import com.google.copybara.git.github.util.GitHubHost;
import com.google.copybara.revision.Change;
import com.google.copybara.util.console.Console;
import java.util.List;
import javax.annotation.Nullable;

/** A write hook for GitHub Pr. */
public class GitHubPrWriteHook extends DefaultWriteHook {

  private final String repoUrl;
  private final GeneralOptions generalOptions;
  private final GitHubOptions gitHubOptions;
  private final boolean partialFetch;
  private final ImmutableSet<String> allowEmptyDiffMergeStatuses;
  private final Console console;
  private GitHubHost ghHost;
  @Nullable private final String prBranchToUpdate;
  private  final boolean allowEmptyDiff;

  GitHubPrWriteHook(
      GeneralOptions generalOptions,
      String repoUrl,
      GitHubOptions gitHubOptions,
      @Nullable String prBranchToUpdate,
      boolean partialFetch,
      boolean allowEmptyDiff,
      ImmutableSet<String> allowEmptyDiffMergeStatuses, Console console,
      GitHubHost ghHost) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.repoUrl = Preconditions.checkNotNull(repoUrl);
    this.gitHubOptions = Preconditions.checkNotNull(gitHubOptions);
    this.prBranchToUpdate = prBranchToUpdate;
    this.partialFetch = partialFetch;
    this.allowEmptyDiff = allowEmptyDiff;
    this.allowEmptyDiffMergeStatuses = allowEmptyDiffMergeStatuses;
    this.console = Preconditions.checkNotNull(console);
    this.ghHost = Preconditions.checkNotNull(ghHost);
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
      String configProjectName = ghHost.getProjectNameFromUrl(repoUrl);
      GitHubApi api = gitHubOptions.newGitHubRestApi(configProjectName);

      try {
        ImmutableList<PullRequest> pullRequests =
            api.getPullRequests(
                configProjectName,
                PullRequestListParams.DEFAULT.withHead(
                    String.format(
                        "%s:%s", ghHost.getUserNameFromUrl(repoUrl), this.prBranchToUpdate)));
        // Just ignore empt-diff check when the size of prs is not equal to 1.
        // If the list size is empty, no pr has been created before.
        // If the list size is greater than 1, there might be something wrong.
        // We don't want to throw EmptyChangeException for some of the prs with the empty diff.
        if (pullRequests.size() != 1) {
          return;
        }
        SameGitTree sameGitTree =
            new SameGitTree(scratchClone, repoUrl, generalOptions, partialFetch);
        PullRequest pullRequest = pullRequests.get(0);
        if (sameGitTree.hasSameTree(pullRequest.getHead().getSha())
            && skipUploadBasedOnPrStatus(configProjectName, api,
            pullRequest.getNumber())) {
          throw new RedundantChangeException(
              String.format(
                  "Skipping push to the existing pr %s/pull/%s as the change %s is empty.",
                  repoUrl, pullRequest.getNumber(), originalChange.getRef()),
              pullRequest.getHead().getSha());
        }
      } catch (GitHubApiException e) {
        if (e.getResponseCode() == ResponseCode.NOT_FOUND
            || e.getResponseCode() == ResponseCode.UNPROCESSABLE_ENTITY) {
          console.verboseFmt("Branch %s does not exist", this.prBranchToUpdate);
        }
        throw e;
      }
    }
  }

  private boolean skipUploadBasedOnPrStatus(String configProjectName, GitHubApi api, long prNumber)
      throws ValidationException, RepoException {
    // This call to getPullRequest might look like unnecessary, but it is not. The previous
    // pull request is received by searching PRs by branch name, and for some reason, GitHub
    // doesn't return this 'experimental' field. So we are forced to do an additional request
    // to get the full data of the PR.
    PullRequest completePr = api.getPullRequest(configProjectName, prNumber);
    Boolean mergeable = completePr.isMergeable();
    if (mergeable == null || !mergeable) {
      console.verboseFmt("Not skipping upload because 'mergeable' is: %s", mergeable);
      return false;
    }

    // If user hasn't set any value, we don't look at mergeable status at all and assume we
    // can skip.
    if (allowEmptyDiffMergeStatuses.isEmpty()) {
      return true;
    }

    String mergeableState = completePr.getMergeableState();
    // By default, if we don't know the status (mergeable_state is not stable API), we upload
    // a new patch
    if (mergeableState == null) {
      // Warn because it might be that GH has stopped populating it.
      console.warn("Not skipping upload because 'mergeable status' is null");
      return false;
    }
    // Valid values https://docs.github.com/en/graphql/reference/enums#mergestatestatus
    if (allowEmptyDiffMergeStatuses.contains(mergeableState.toUpperCase())) {
      console.infoFmt("Uploading change because mergeable status is %s, that is in the"
              + " list of statuses to upload changes: %s", mergeableState.toUpperCase(),
          allowEmptyDiffMergeStatuses);
      return false;
    } else {
      console.infoFmt("Skipping upload because mergeable status is %s, that is NOT in the"
              + " list of statuses to upload changes: %s", mergeableState.toUpperCase(),
          allowEmptyDiffMergeStatuses);
      return true;
    }
  }

  protected GitHubPrWriteHook withUpdatedPrBranch(String prBranchToUpdate) {
    return new GitHubPrWriteHook(
        this.generalOptions,
        this.repoUrl,
        this.gitHubOptions,
        prBranchToUpdate,
        this.partialFetch,
        this.allowEmptyDiff,
        this.allowEmptyDiffMergeStatuses,
        this.console,
        this.ghHost);
  }
}