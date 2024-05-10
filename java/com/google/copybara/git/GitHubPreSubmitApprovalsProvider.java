/*
 * Copyright (C) 2023 Google LLC
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.approval.ApprovalsProvider;
import com.google.copybara.approval.ChangeWithApprovals;
import com.google.copybara.approval.StatementPredicate;
import com.google.copybara.approval.UserPredicate;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.github.api.Review;
import com.google.copybara.git.github.util.GitHubHost;
import com.google.copybara.util.console.Console;
import java.util.Collection;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Fills out change predicates for post submit GitHub origin changes. */
public class GitHubPreSubmitApprovalsProvider implements ApprovalsProvider {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  GitHubOptions githubOptions;
  GitHubHost githubHost;
  GitHubSecuritySettingsValidator securitySettingsValidator;
  GitHubUserApprovalsValidator userApprovalsValidator;
  @Nullable private final CredentialFileHandler creds;

  public GitHubPreSubmitApprovalsProvider(
      GitHubOptions githubOptions,
      GitHubHost githubHost,
      GitHubSecuritySettingsValidator securitySettingsValidator,
      GitHubUserApprovalsValidator userApprovalsValidator,
      @Nullable CredentialFileHandler creds) {
    this.githubOptions = githubOptions;
    this.securitySettingsValidator = securitySettingsValidator;
    this.userApprovalsValidator = userApprovalsValidator;
    this.githubHost = githubHost;
    this.creds = creds;
  }

  /**
   * Given a list of changes, return a list of changes that have approvals. Particularly provides
   * {@link UserPredicate} and {@link GitHubOrganizationSettingPredicate}
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
      ImmutableList<ChangeWithApprovals> changes,
      Function<String, Collection<String>> labelFinder,
      Console console)
      throws RepoException, ValidationException {
    if (changes.isEmpty()) {
      return new ApprovalsResult(ImmutableList.of());
    }

    String sampleUrl = Iterables.getLast(changes).getChange().getRevision().getUrl();
    String org = githubHost.getUserNameFromUrl(sampleUrl);
    String projectId = githubHost.getProjectNameFromUrl(sampleUrl);

    ImmutableList<ChangeWithApprovals> approvalsInProgress = changes;

    try {
      approvalsInProgress = securitySettingsValidator.mapTwoFactorAuth(approvalsInProgress, org);
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
      approvalsInProgress = securitySettingsValidator.mapAllStar(approvalsInProgress, org);
    } catch (ValidationException | RepoException e) {
      console.warnFmt(
          "Could not validate GitHub organization security settings for AllStar installation with"
              + " error '%s'. Skipping this step...",
          e.getMessage());
      logger.atWarning().withCause(e).log(
          "Could not validate GitHub organization security settings for AllStar installation."
              + " Skipping this step...");
    }

    // find the branch the pull request is being made against. Need this to find validate postsubmit
    // commits.
    String baseBranch =
        Iterables.getOnlyElement(
            extractLabelValues(changes, labelFinder, GitHubPrOrigin.GITHUB_BASE_BRANCH));
    String prNumber =
        Iterables.getOnlyElement(
            extractLabelValues(changes, labelFinder, GitHubPrOrigin.GITHUB_PR_NUMBER_LABEL));
    String prHeadSha =
        Iterables.getOnlyElement(
            extractLabelValues(changes, labelFinder, GitHubPrOrigin.GITHUB_PR_HEAD_SHA));
    String prAuthor =
        Iterables.getOnlyElement(
            extractLabelValues(changes, labelFinder, GitHubPrOrigin.GITHUB_PR_USER));
    String baselineSha =
        Iterables.getOnlyElement(
            extractLabelValues(changes, labelFinder, GitHubPrOrigin.GITHUB_BASE_BRANCH_SHA1));

    // A bit counterintuitive, but it is actually backwards, [latest_change...earliest_change].
    // This finds the partition point where inclusively at the baseline index and to the right is a
    // postsubmit commit.
    int baseLineIndex =
        Iterables.indexOf(
            approvalsInProgress,
            change ->
                ((GitRevision) change.getChange().getRevision()).getSha1().equals(baselineSha));

    // Slice the changes into two sublists representing the postsubmits and presubmits.
    ImmutableList<ChangeWithApprovals> preSubmitChanges =
        baseLineIndex != -1 ? approvalsInProgress.subList(0, baseLineIndex) : approvalsInProgress;
    ImmutableList<ChangeWithApprovals> postSubmitChanges =
        baseLineIndex != -1
            ? approvalsInProgress.subList(baseLineIndex, approvalsInProgress.size())
            : ImmutableList.of();

    // TODO(linjordan) debug log, remove.
    logger.atInfo().log(
        "Partitioned presubmit GitHub changes into presubmit: %s, postsubmit:%s",
        preSubmitChanges, postSubmitChanges);

    // Last step do user validations, concatenate, and return
    return new ApprovalsResult(
        ImmutableList.<ChangeWithApprovals>builder()
            .addAll(
                tryPresubmitUserValidation(
                    preSubmitChanges,
                    projectId,
                    Integer.parseInt(prNumber),
                    prHeadSha,
                    prAuthor,
                    console))
            .addAll(tryPostSubmitUserValidation(postSubmitChanges, baseBranch, console))
            .build());
  }

  public ImmutableList<ChangeWithApprovals> tryPostSubmitUserValidation(
      ImmutableList<ChangeWithApprovals> postSubmitChanges, String branch, Console console)
      throws ValidationException, RepoException {
    try {
      return userApprovalsValidator.mapApprovalsForUserPredicates(postSubmitChanges, branch);
    } catch (ValidationException e) {
      console.warnFmt(
          "Could not do postsubmit changes validation with"
              + " error '%s'. Leaving changes as is and skipping this step...",
          e.getMessage());
      logger.atWarning().withCause(e).log(
          "Could not do postsubmit changes validation with error '%s'."
              + "Leaving changes as is and skipping this step...",
          e.getMessage());
      return postSubmitChanges;
    }
  }

  /**
   * Performs user validation on a list of presubmit changes. If an error occurs, this method will
   * log to the console and return the default value {@code presubmitChanges}
   *
   * @param presubmitChanges the presubmit changes to do user validation on. If this function fails,
   *     the return value will default to this.
   * @param projectId the org/repo identifier that hosts the pull request e.g. "google/copybara"
   * @param prNumber the pull request number
   * @param prHeadSha the 40 character git commit sha of the pull request HEAD commit
   * @param author the author of pull request #{@code prNumber}
   * @param console to let the user know of issues that occurred during validation
   */
  public ImmutableList<ChangeWithApprovals> tryPresubmitUserValidation(
      ImmutableList<ChangeWithApprovals> presubmitChanges,
      String projectId,
      int prNumber,
      String prHeadSha,
      String author,
      Console console) {
    ImmutableList.Builder<ChangeWithApprovals> presubmitApprovalsInProgress =
        ImmutableList.builder();
    ImmutableList<Review> reviews = null;
    try {
      reviews = this.githubOptions.newGitHubRestApi(projectId, creds)
          .getReviews(projectId, prNumber);
    } catch (RepoException | ValidationException e) {
      console.warnFmt(
          "Could not do presubmit changes validation with"
              + " error '%s'. Leaving changes as is and skipping this step...",
          e.getMessage());
      logger.atWarning().withCause(e).log(
          "Could not do presubmit changes validation with error '%s'."
              + "Leaving changes as is and skipping this step...",
          e.getMessage());
      return presubmitChanges;
    }

    ImmutableList<String> headApprovers = extractHeadApprovers(reviews, prHeadSha);
    for (ChangeWithApprovals change : presubmitChanges) {
      String sha = ((GitRevision) change.getChange().getRevision()).getSha1();
      ChangeWithApprovals newChange =
          change.addApprovals(
              ImmutableList.<StatementPredicate>builder()
                  .addAll(
                      mapToUserPredicates(
                          headApprovers,
                          UserPredicate.UserPredicateType.LGTM,
                          change.getChange().getRevision().getUrl(),
                          sha))
                  .add(
                      new UserPredicate(
                          author,
                          UserPredicate.UserPredicateType.OWNER,
                          change.getChange().getRevision().getUrl(),
                          String.format(
                              "GitHub user '%s' authored change with sha '%s'.", author, sha)))
                  .build());
      presubmitApprovalsInProgress.add(newChange);
    }
    return presubmitApprovalsInProgress.build();
  }

  private ImmutableList<String> extractHeadApprovers(
      ImmutableList<Review> reviews, String headSha) {
    return reviews.stream()
        .filter(review -> review.isApproved() && review.getCommitId().equals(headSha))
        .map(review -> review.getUser().getLogin())
        .collect(toImmutableList());
  }

  private ImmutableList<String> extractLabelValues(
      ImmutableList<ChangeWithApprovals> changes,
      Function<String, Collection<String>> labelFinder,
      String key)
      throws RepoException {
    // not all revisions share labels so find the first one that has what we are looking for
    for (ChangeWithApprovals change : changes) {
      ImmutableList<String> values = change.getChange().getRevision().associatedLabel(key);
      if (!values.isEmpty()) {
        return values;
      }
    }

    // look among the public and hidden transform labels
    if (labelFinder != null && !labelFinder.apply(key).isEmpty()) {
      return ImmutableList.copyOf(labelFinder.apply(key));
    }
    throw new RepoException(String.format("Could not find the value for label '%s'", key));
  }

  private ImmutableList<UserPredicate> mapToUserPredicates(
      ImmutableList<String> userIds, UserPredicate.UserPredicateType type, String url, String sha) {
    return userIds.stream()
        .map(
            userId ->
                new UserPredicate(
                    userId,
                    type,
                    url,
                    String.format(
                        "GitHub user '%s' approved pull request associated with commit sha '%s' at"
                            + " HEAD",
                        userId, sha)))
        .collect(toImmutableList());
  }
}
