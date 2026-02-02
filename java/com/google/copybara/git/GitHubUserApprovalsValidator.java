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
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.copybara.LazyResourceLoader;
import com.google.copybara.approval.ChangeWithApprovals;
import com.google.copybara.approval.UserPredicate;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.github.api.CommitHistoryResponse;
import com.google.copybara.git.github.api.CommitHistoryResponse.AssociatedPullRequestNode;
import com.google.copybara.git.github.api.CommitHistoryResponse.AssociatedPullRequests;
import com.google.copybara.git.github.api.CommitHistoryResponse.HistoryNode;
import com.google.copybara.git.github.api.GitHubApi;
import com.google.copybara.git.github.api.GitHubGraphQLApi;
import com.google.copybara.git.github.api.GitHubGraphQLApi.GetCommitHistoryParams;
import com.google.copybara.git.github.util.GitHubHost;
import com.google.copybara.util.console.Console;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/** Utility class for performing validation for GitHub pull request approvals. */
public class GitHubUserApprovalsValidator {
  private static final int GET_COMMIT_HISTORY_MAX_RETRIES = 3;
  private final LazyResourceLoader<GitHubApi> restApiLoader;
  private final LazyResourceLoader<GitHubGraphQLApi> graphQlApiLoader;
  private final Console console;
  private final GitHubHost githubHost;
  private final GetCommitHistoryParams params;

  /**
   * Creates a utility object that can provision {@code UserPredicates} that describes the change's
   * authorship and approving reviewers based on a {@code ChangeWithApprovals} commit and pull
   * request history on GitHub.
   *
   * @param apiLoader - GraphQL api used to query for GitHub commit and pull request history.
   * @param console - used to output relevant issues to the user.
   * @param githubHost utility object to extract project identifiers from GitHub URLs.
   * @param params used to describe scope of commit history to review.
   */
  public GitHubUserApprovalsValidator(
      LazyResourceLoader<GitHubApi> restApiLoader,
      LazyResourceLoader<GitHubGraphQLApi> graphQlApiLoader,
      Console console,
      GitHubHost githubHost,
      GetCommitHistoryParams params) {
    this.restApiLoader = restApiLoader;
    this.graphQlApiLoader = graphQlApiLoader;
    this.console = console;
    this.githubHost = githubHost;
    this.params = params;
  }

  /**
   * Bestows {@code UserPredicate} to list of changes. For each change, one UserPredicate for the
   * author and one for each user approval. PreConditions: {@code changes} all originate from the
   * same GitHub project AND this method assumes at most one pull request will associated with each
   * pull request and if that is not the case, it will skip over predicate provisioning for the
   * change.
   *
   * @param changes the changes to validate and provision {@code UserPredicates} on.
   * @param branch the branch to look for {@code changes}. If null or empty, the default branch will
   *     be inferred.
   */
  public ImmutableList<ChangeWithApprovals> mapApprovalsForUserPredicates(
      ImmutableList<ChangeWithApprovals> changes, @Nullable String branch)
      throws ValidationException, RepoException {
    if (changes.isEmpty()) {
      return ImmutableList.of();
    }
    String url = Iterables.getLast(changes).getChange().getRevision().getUrl();
    String organization = githubHost.getUserNameFromUrl(url);

    String projectName = githubHost.getProjectNameFromUrl(url);
    String repository = projectName.substring(projectName.lastIndexOf("/") + 1);
    ImmutableList.Builder<ChangeWithApprovals> builder = ImmutableList.builder();

    CommitHistoryResponse response = null;
    for (int i = 0; i < GET_COMMIT_HISTORY_MAX_RETRIES; i++) {
      response =
          graphQlApiLoader
              .load(console)
              .getCommitHistory(
                  organization,
                  repository,
                  !Strings.isNullOrEmpty(branch) ? branch : getDefaultBranch(projectName),
                  this.params.getCopyWithCommits(this.params.getCommits() * (i + 1)));
      if (allCommitsFound(changes, response)) {
        break;
      }
      console.warnFmt(
          "Commit history response did not contain all commits, retrying with"
              + " larger commit window. Current window: %d",
          this.params.getCommits() * (i + 1));
    }

    for (ChangeWithApprovals change : changes) {
      String sha = ((GitRevision) change.getChange().getRevision()).getSha1();

      AssociatedPullRequests associatedPullRequests = getAssociatedPullRequest(sha, response);
      if (associatedPullRequests == null || associatedPullRequests.getEdges().isEmpty()) {
        console.warnFmt(
            "Expected to find at least one pull request associated with commit sha '%s', but found"
                + " 0'. Consider expanding the commit history validation window via"
                + " --gql-commit-history-override. Skipping authorship and approval predicate"
                + " provisioning for this commit...",
            sha);
        builder.add(change);
        continue;
      }
      AssociatedPullRequestNode pullRequest =
          Iterables.getFirst(associatedPullRequests.getEdges(), null).getNode();

      // now add author
      String author = pullRequest.getAuthor().getLogin();
      UserPredicate authorPredicate =
          new UserPredicate(
              author,
              UserPredicate.UserPredicateType.OWNER,
              change.getChange().getRevision().getUrl(),
              String.format("GitHub user '%s' authored change with sha '%s'.", author, sha));
      ChangeWithApprovals changeInProgress = change.addApprovals(ImmutableList.of(authorPredicate));

      // now add approvers if any
      ImmutableList<String> approvers = extractApprovers(pullRequest);
      for (String approverLogin : approvers) {
        UserPredicate approverPredicate =
            new UserPredicate(
                approverLogin,
                UserPredicate.UserPredicateType.LGTM,
                change.getChange().getRevision().getUrl(),
                String.format(
                    "GitHub user '%s' approved change with sha '%s'.", approverLogin, sha));
        changeInProgress = changeInProgress.addApprovals(ImmutableList.of(approverPredicate));
      }

      builder.add(changeInProgress);
    }
    return builder.build();
  }

  private boolean allCommitsFound(
      ImmutableList<ChangeWithApprovals> changes, CommitHistoryResponse response) {
    List<HistoryNode> historyNodes =
        Optional.ofNullable(response)
            .map(CommitHistoryResponse::getData)
            .map(CommitHistoryResponse.Data::getRepository)
            .map(CommitHistoryResponse.Repository::getRef)
            .map(CommitHistoryResponse.Ref::getTarget)
            .map(CommitHistoryResponse.Target::getHistoryNodes)
            .map(CommitHistoryResponse.HistoryNodes::getNodes)
            .orElse(ImmutableList.of());
    ImmutableSet<String> responseOids =
        historyNodes.stream().map(HistoryNode::getOid).collect(toImmutableSet());
    for (ChangeWithApprovals change : changes) {
      String sha = ((GitRevision) change.getChange().getRevision()).getSha1();
      if (!responseOids.contains(sha)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Finds the pull requests associated with {@code sha}.
   *
   * @param sha the commit sha to look for
   * @param response the response to comb through
   */
  @Nullable
  private AssociatedPullRequests getAssociatedPullRequest(
      String sha, CommitHistoryResponse response) {
    List<HistoryNode> historyNodes =
        Optional.ofNullable(response)
            .map(CommitHistoryResponse::getData)
            .map(CommitHistoryResponse.Data::getRepository)
            .map(CommitHistoryResponse.Repository::getRef)
            .map(CommitHistoryResponse.Ref::getTarget)
            .map(CommitHistoryResponse.Target::getHistoryNodes)
            .map(CommitHistoryResponse.HistoryNodes::getNodes)
            .orElse(ImmutableList.of());
    HistoryNode history =
        Iterables.tryFind(historyNodes, node -> node.getOid().equals(sha)).orNull();
    console.warnFmtIf(
        history == null,
        "Unable to find history node for sha '%s' -- the full CommitHistoryResponse is: %s",
        sha,
        response);

    return history != null ? history.getAssociatedPullRequests() : null;
  }

  private static ImmutableList<String> extractApprovers(AssociatedPullRequestNode pullRequest) {
    return pullRequest.getLatestOpinionatedReviews().getEdges().stream()
        .filter(review -> review.getNode().getState().equals("APPROVED"))
        .map(reviewer -> reviewer.getNode().getAuthor().getLogin())
        .collect(toImmutableList());
  }

  @Nullable
  private String getDefaultBranch(String projectId) {
    try {
      String branch = restApiLoader.load(console).getRepository(projectId).getDefaultBranch();
      console.infoFmt("Inferred primary branch as '%s'", branch);
      return branch;
    } catch (ValidationException | RepoException e) {
      console.warnFmt(
          "Failed to get branch for project %s with error '%s'", projectId, e.getMessage());
      return null;
    }
  }
}
