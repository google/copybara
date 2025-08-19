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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import com.google.api.client.http.HttpStatusCodes;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.copybara.GeneralOptions;
import com.google.copybara.exception.RedundantChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitDestination.MessageInfo;
import com.google.copybara.git.GitDestination.WriterImpl.DefaultWriteHook;
import com.google.copybara.git.gitlab.GitLabUtil;
import com.google.copybara.git.gitlab.api.GitLabApi;
import com.google.copybara.git.gitlab.api.GitLabApiException;
import com.google.copybara.git.gitlab.api.entities.ListProjectMergeRequestParams;
import com.google.copybara.git.gitlab.api.entities.MergeRequest;
import com.google.copybara.git.gitlab.api.entities.MergeRequest.DetailedMergeStatus;
import com.google.copybara.git.gitlab.api.entities.Project;
import com.google.copybara.revision.Change;
import com.google.copybara.util.console.Console;
import java.net.URI;
import java.net.URLDecoder;
import java.util.List;
import java.util.Optional;

/**
 * A {@link com.google.copybara.git.GitDestination.WriterImpl.WriteHook} for GitLab Merge Requests.
 *
 * <p>This write hook is able to query the GitLab API for the merge requests for a given branch and
 * skip the push if the resulting change is empty.
 */
public class GitLabMrWriteHook extends DefaultWriteHook {
  private final GitLabMrWriteHookParams params;

  private GitLabMrWriteHook(GitLabMrWriteHookParams params) {
    this.params = Preconditions.checkNotNull(params);
  }

  /**
   * Checks if the push to the merge request branch should be skipped.
   *
   * <p>If the push is not skipped, the write hook will check if the resulting change is empty and
   * throw a {@link RedundantChangeException} if it is.
   *
   * @param localRepo the local copy of the Git repository, used to check for empty diffs
   * @param messageInfo the {@link MessageInfo} of the push
   * @param skipPush whether the push should be skipped, in which case this method will not perform
   *     any checks
   * @param integrateLabels the list of {@link IntegrateLabel}s to be applied to the push
   * @param originChanges the list of {@link Change}s from the origin
   * @throws RepoException if there is an error fetching the merge requests from the GitLab API
   * @throws ValidationException if the project info cannot be obtained from the GitLab API
   * @throws RedundantChangeException if the resulting change is empty and the push should be
   *     skipped
   */
  @Override
  public void beforePush(
      GitRepository localRepo,
      MessageInfo messageInfo,
      boolean skipPush,
      List<IntegrateLabel> integrateLabels,
      List<? extends Change<?>> originChanges)
      throws RepoException, ValidationException {
    Console console = params.generalOptions().console();
    if (skipPush) {
      console.verboseFmt("Not performing empty-diff check because skipPush is true");
      return;
    }
    if (params.allowEmptyDiff()) {
      console.verboseFmt("Not performing empty-diff check because allowEmptyDiff is true");
      return;
    }

    for (Change<?> change : originChanges) {
      String urlEncodedProjectPath = GitLabUtil.getUrlEncodedProjectPath(params.repoUrl());
      Project project = getProject(urlEncodedProjectPath, console);

      Optional<MergeRequest> mergeRequest = getMergeRequest(project.getId(), console);
      if (mergeRequest.isPresent()) {
        checkMergeRequestForEmptyDiff(localRepo, mergeRequest.get(), change, console);
      }
    }
  }

  private void checkMergeRequestForEmptyDiff(
      GitRepository localRepo, MergeRequest mergeRequest, Change<?> change, Console console)
      throws RepoException, ValidationException {
    SameGitTree sameGitTree =
        new SameGitTree(
            localRepo, params.repoUrl().toString(), params.generalOptions(), params.partialFetch());
    if (params.allowEmptyDiffMergeStatuses().contains(mergeRequest.getDetailedMergeStatus())) {
      console.verboseFmt(
          "Not performing empty-diff check because mergeable status is %s for MR %s. Allowed"
              + " merge statuses for empty-diff: %s",
          mergeRequest.getDetailedMergeStatus(),
          mergeRequest.getIid(),
          params.allowEmptyDiffMergeStatuses());
      return;
    }
    boolean contentsAreSame =
        mergeRequest.getSha() != null
            && sameGitTree.hasSameTree(mergeRequest.getSha())
            && mergeRequest.getDetailedMergeStatus() == DetailedMergeStatus.MERGEABLE;
    if (contentsAreSame) {
      if (params.generalOptions().isForced()) {
        console.warnFmt(
            "Change %s is empty, but pushing to the MR %d anyway due to --force flag.",
            change.getRef(), mergeRequest.getIid());
      } else {
        throw new RedundantChangeException(
            String.format(
                "Skipping push to the existing MR %d in repo %s as the change %s is empty.",
                mergeRequest.getIid(), params.repoUrl(), change.getRef()),
            mergeRequest.getSha());
      }
    }
  }

  private Optional<MergeRequest> getMergeRequest(int projectId, Console console)
      throws ValidationException, GitLabApiException {
    ImmutableList<MergeRequest> mergeRequests =
        params
            .gitLabApi()
            .getProjectMergeRequests(
                projectId,
                new ListProjectMergeRequestParams(Optional.of(params.mrBranchToUpdate())));

    if (mergeRequests.isEmpty()) {
      console.verboseFmt(
          "Not performing empty-diff check because no merge requests found for repo %s and branch"
              + " %s.",
          params.repoUrl(), params.mrBranchToUpdate());
      return Optional.empty();
    }

    if (mergeRequests.size() > 1) {
      console.warnFmt(
          "Not performing empty-diff check because more than one merge request was found for repo"
              + " %s and branch %s. MR IDs: %s",
          params.repoUrl(),
          params.mrBranchToUpdate(),
          mergeRequests.stream()
              .map(mergeRequest -> Integer.toString(mergeRequest.getIid()))
              .collect(joining(", ")));

      return Optional.empty();
    }

    return Optional.ofNullable(Iterables.getOnlyElement(mergeRequests));
  }

  private Project getProject(String urlEncodedProjectPath, Console console)
      throws ValidationException, GitLabApiException {
    Project project;
    try {
      project =
          params
              .gitLabApi()
              .getProject(urlEncodedProjectPath)
              .orElseThrow(
                  () ->
                      new ValidationException(
                          "Failed to obtain project info from URL " + params.repoUrl()));
    } catch (GitLabApiException e) {
      if (e.getResponseCode().isPresent()
          && e.getResponseCode().get() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
        console.warnFmt(
            "The project %s was not found", URLDecoder.decode(urlEncodedProjectPath, UTF_8));
      }
      throw e;
    }
    return project;
  }

  /**
   * A record class for the parameters of {@link GitLabMrWriteHook}.
   *
   * @param allowEmptyDiff whether the write hook should allow migrations resulting in empty diffs
   *     to be uploaded to the destination
   * @param gitLabApi the GitLab API client to be used by the write hook
   * @param repoUrl the URL of the GitLab repository to be updated
   * @param mrBranchToUpdate the name of the merge request branch to be updated
   * @param generalOptions the {@link GeneralOptions} to be used
   * @param partialFetch whether partial fetch should be used when fetching from the destination
   * @param allowEmptyDiffMergeStatuses the list of {@link DetailedMergeStatus} that we should allow
   *     uploading an empty-diff change for
   */
  public record GitLabMrWriteHookParams(
      boolean allowEmptyDiff,
      GitLabApi gitLabApi,
      URI repoUrl,
      String mrBranchToUpdate,
      GeneralOptions generalOptions,
      boolean partialFetch,
      ImmutableSet<DetailedMergeStatus> allowEmptyDiffMergeStatuses) {

    /** Creates a new {@link GitLabMrWriteHook} instance with the parameters of this record. */
    public GitLabMrWriteHook createWriteHook() {
      return new GitLabMrWriteHook(this);
    }
  }
}
