/*
 * Copyright (C) 2023 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.approval.ApprovalsProvider;
import com.google.copybara.approval.ChangeWithApprovals;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.github.util.GitHubHost;
import com.google.copybara.revision.Revision;
import com.google.copybara.util.console.Console;

/** Fills out change predicates for post submit GitHub origin changes. */
public class GitHubPostSubmitApprovalsProvider implements ApprovalsProvider {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final String branch;
  private final GitHubHost githubHost;
  private final GitHubSecuritySettingsValidator securitySettingsValidator;
  private final GitHubUserApprovalsValidator userApprovalsValidator;

  // TODO(linjordan): GitOrigin.getConfigRef() returns a nullable, may still need to inject a
  // GitRepository to lsRemote for a primary branch.

  // TODO(linjordan): add user facing documentation for requiring branch specification in config.

  /**
   * An implementation of the {@code ApprovalsProvider} that specializes in describing postsubmit
   * imports.
   *
   * @param githubHost utility object used to extract a change's GitHub origin components from the
   *     URL.
   * @param branch the branch to target postsubmit approval provisions for. If left null or empty,
   *     this object will ls-remote for an primary branch of the remote origin.
   * @param securitySettingsValidator utility object used to provision statement predicates on a
   *     GitHub repos security setting state.
   * @param userApprovalsValidator utility object used to provision statement predicates on a GitHub
   *     pull request approval and authorship state.
   */
  public GitHubPostSubmitApprovalsProvider(
      GitHubHost githubHost,
      String branch,
      GitHubSecuritySettingsValidator securitySettingsValidator,
      GitHubUserApprovalsValidator userApprovalsValidator) {
    this.githubHost = githubHost;
    this.branch = branch;
    this.securitySettingsValidator = securitySettingsValidator;
    this.userApprovalsValidator = userApprovalsValidator;
  }

  /**
   * Given a list of changes, return a list of changes that have approvals. Particularly provides
   * {@code UserPredicate} and {@code StatementPredicates} that describe GitHub org settings.
   *
   * @param changes changes to be verified with the existing approvals.
   * @param console console, in case some message need to be printed
   * @throws RepoException if access to the origin system fails because of being unavailable, server
   *     error, etc.
   * @throws ValidationException if failure is attributable to the user setup (e.g. permission
   *     errors, etc.)
   */
  @Override
  public ApprovalsResult computeApprovals(
      ImmutableList<ChangeWithApprovals> changes, Console console)
      throws RepoException, ValidationException {
    if (changes.isEmpty()) {
      return new ApprovalsResult(ImmutableList.of());
    }
    Revision sampleRevision = Iterables.getLast(changes).getChange().getRevision();
    String projectId = githubHost.getProjectNameFromUrl(sampleRevision.getUrl());
    String organization = githubHost.getUserNameFromUrl(sampleRevision.getUrl());

    ImmutableList<ChangeWithApprovals> unusualChanges =
        findChangesWithUnExpectedOrigin(projectId, changes);
    if (!unusualChanges.isEmpty()) {
      console.warnFmt(
          "Expected all changes to originate from GitHub project '%s'. But these changes have other"
              + " origins %s. Skipping statement predicate provisioning for this change list...",
          projectId, unusualChanges);
      return new ApprovalsResult(changes);
    }

    ImmutableList<ChangeWithApprovals> approvalsInProgress = changes;
    try {
      approvalsInProgress =
          securitySettingsValidator.mapTwoFactorAuth(approvalsInProgress, organization);
    } catch (ValidationException | RepoException e) {
      console.warnFmt(
          "Could not validate GitHub organization security settings for two factor authentication"
              + " requirements with error '%s'. Skipping this step...",
          e.getMessage());
      logger.atWarning().withCause(e).log(
          "Could not validate GitHub organization security settings for two factor authentication"
              + " requirements. Skipping this step...");
    }
    try {
      approvalsInProgress = securitySettingsValidator.mapAllStar(approvalsInProgress, organization);
    } catch (ValidationException | RepoException e) {
      console.warnFmt(
          "Could not validate GitHub organization security settings for AllStar installation with"
              + " error '%s'. Skipping this step...",
          e.getMessage());
      logger.atWarning().withCause(e).log(
          "Could not validate GitHub organization security settings for AllStar installation."
              + " Skipping this step...");
    }
    try {
      approvalsInProgress =
          userApprovalsValidator.mapApprovalsForUserPredicates(approvalsInProgress, branch);
    } catch (ValidationException | RepoException e) {
      console.warnFmt(
          "Could not validate user approvals and authorship with error '%s'. Skipping this step...",
          e.getMessage());
      logger.atWarning().withCause(e).log(
          "Could not validate user approvals and authorship. Skipping this step...");
    }

    return new ApprovalsResult(approvalsInProgress);
  }

  /**
   * Finds subset of changes in {@code changes} that do not origin from {@code projectId}
   *
   * @param projectId the expected GitHub project that {@code changes} all come from
   * @param changes the list of changes where this method searches for changes that do not originate
   *     from {@code projectId}
   */
  private ImmutableList<ChangeWithApprovals> findChangesWithUnExpectedOrigin(
      String projectId, ImmutableList<ChangeWithApprovals> changes) throws ValidationException {
    ImmutableList.Builder<ChangeWithApprovals> unusualChanges = ImmutableList.builder();
    for (ChangeWithApprovals change : changes) {
      if (!githubHost
          .getProjectNameFromUrl(change.getChange().getRevision().getUrl())
          .equals(projectId)) {
        unusualChanges.add(change);
      }
    }
    return unusualChanges.build();
  }
}
