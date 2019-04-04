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

import static com.google.copybara.exception.ValidationException.checkCondition;
import static com.google.copybara.git.github.util.GitHubUtil.asGithubUrl;
import static com.google.copybara.git.github.util.GitHubUtil.asHeadRef;
import static com.google.copybara.git.github.util.GitHubUtil.asMergeRef;
import static com.google.copybara.git.github.util.GitHubUtil.getProjectNameFromUrl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.copybara.BaselinesWithoutLabelVisitor;
import com.google.copybara.Change;
import com.google.copybara.Endpoint;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Origin;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.checks.Checker;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitOrigin.ReaderImpl;
import com.google.copybara.git.GitOrigin.SubmoduleStrategy;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.git.github.api.AuthorAssociation;
import com.google.copybara.git.github.api.GitHubApi;
import com.google.copybara.git.github.api.Issue;
import com.google.copybara.git.github.api.Issue.Label;
import com.google.copybara.git.github.api.PullRequest;
import com.google.copybara.git.github.api.Review;
import com.google.copybara.git.github.api.User;
import com.google.copybara.git.github.util.GitHubUtil;
import com.google.copybara.git.github.util.GitHubUtil.GitHubPrUrl;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.transform.patch.PatchTransformation;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * A class for reading GitHub Pull Requests
 */
public class GitHubPROrigin implements Origin<GitRevision> {

  static final int RETRY_COUNT = 3;

  public static final String GITHUB_PR_NUMBER_LABEL = "GITHUB_PR_NUMBER";
  public static final String GITHUB_BASE_BRANCH = "GITHUB_BASE_BRANCH";
  public static final String GITHUB_BASE_BRANCH_SHA1 = "GITHUB_BASE_BRANCH_SHA1";
  public static final String GITHUB_PR_TITLE = "GITHUB_PR_TITLE";
  public static final String GITHUB_PR_URL = "GITHUB_PR_URL";
  public static final String GITHUB_PR_BODY = "GITHUB_PR_BODY";
  public static final String GITHUB_PR_USER = "GITHUB_PR_USER";
  public static final String GITHUB_PR_ASSIGNEE = "GITHUB_PR_ASSIGNEE";
  public static final String GITHUB_PR_REVIEWER_APPROVER = "GITHUB_PR_REVIEWER_APPROVER";
  public static final String GITHUB_PR_REVIEWER_OTHER = "GITHUB_PR_REVIEWER_OTHER";
  public static final String GITHUB_PR_REQUESTED_REVIEWER = "GITHUB_PR_REQUESTED_REVIEWER";
  private static final String LOCAL_PR_HEAD_REF = "refs/PR_HEAD";
  public static final String GITHUB_PR_HEAD_SHA = "GITHUB_PR_HEAD_SHA";
  private static final String LOCAL_PR_MERGE_REF = "refs/PR_MERGE";
  private static final String LOCAL_PR_BASE_BRANCH = "refs/PR_BASE_BRANCH";


  private final String url;
  private final boolean useMerge;
  private final GeneralOptions generalOptions;
  private final GitOptions gitOptions;
  private final GitOriginOptions gitOriginOptions;
  private final GitHubOptions gitHubOptions;
  private final Set<String> requiredLabels;
  private final Set<String> retryableLabels;
  private final SubmoduleStrategy submoduleStrategy;
  private final Console console;
  private final boolean baselineFromBranch;
  private final Boolean firstParent;
  private final StateFilter requiredState;
  @Nullable private final ReviewState reviewState;
  private final ImmutableSet<AuthorAssociation> reviewApprovers;
  @Nullable private final Checker endpointChecker;
  @Nullable private final PatchTransformation patchTransformation;
  @Nullable private final String branch;
  private final boolean describeVersion;

  GitHubPROrigin(String url, boolean useMerge, GeneralOptions generalOptions,
      GitOptions gitOptions, GitOriginOptions gitOriginOptions, GitHubOptions gitHubOptions,
      Set<String> requiredLabels, Set<String> retryableLabels, SubmoduleStrategy submoduleStrategy,
      boolean baselineFromBranch, Boolean firstParent, StateFilter requiredState,
      @Nullable ReviewState reviewState, ImmutableSet<AuthorAssociation> reviewApprovers,
      @Nullable Checker endpointChecker,
      @Nullable PatchTransformation patchTransformation,
      @Nullable String branch,
      boolean describeVersion) {
    this.url = Preconditions.checkNotNull(url);
    this.useMerge = useMerge;
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
    this.gitOriginOptions = Preconditions.checkNotNull(gitOriginOptions);
    this.gitHubOptions = gitHubOptions;
    this.requiredLabels = Preconditions.checkNotNull(requiredLabels);
    this.retryableLabels = Preconditions.checkNotNull(retryableLabels);
    this.submoduleStrategy = Preconditions.checkNotNull(submoduleStrategy);
    console = generalOptions.console();
    this.baselineFromBranch = baselineFromBranch;
    this.firstParent = firstParent;
    this.requiredState = Preconditions.checkNotNull(requiredState);
    this.reviewState = reviewState;
    this.reviewApprovers = Preconditions.checkNotNull(reviewApprovers);
    this.endpointChecker = endpointChecker;
    this.patchTransformation = patchTransformation;
    this.branch = branch;
    this.describeVersion = describeVersion;
  }

  @Override
  public GitRevision resolve(String reference) throws RepoException, ValidationException {
    checkCondition(reference != null, ""
        + "A pull request reference is expected as argument in the command line."
        + " Invoke copybara as:\n"
        + "    copybara copy.bara.sky workflow_name 12345");
    console.progress("GitHub PR Origin: Resolving reference " + reference);

    // A whole https pull request url
    Optional<GitHubPrUrl> githubPrUrl = GitHubUtil.maybeParseGithubPrUrl(reference);
    String configProjectName = getProjectNameFromUrl(url);
    if (githubPrUrl.isPresent()) {
      checkCondition(
          githubPrUrl.get().getProject().equals(configProjectName),
          "Project name should be '%s' but it is '%s' instead", configProjectName,
              githubPrUrl.get().getProject());

      return getRevisionForPR(configProjectName, githubPrUrl.get().getPrNumber());
    }
    // A Pull request number
    if (CharMatcher.digit().matchesAllOf(reference)) {
      return getRevisionForPR(getProjectNameFromUrl(url), Integer.parseInt(reference));
    }

    // refs/pull/12345/head
    Optional<Integer> prNumber = GitHubUtil.maybeParseGithubPrFromHeadRef(reference);
    if (prNumber.isPresent()) {
      return getRevisionForPR(configProjectName, prNumber.get());
    }
    String sha1Part = Splitter.on(" ").split(reference).iterator().next();
    Matcher matcher = GitRevision.COMPLETE_SHA1_PATTERN.matcher(sha1Part);
    // The only valid use case for this is to resolve previous ref.  Because we fetch the head of
    // the base branch when resolving the PR, it should exist at this point. If it doesn't then it
    // is a non-valid reference.
    // Note that this might not work if the PR is for a different branch than the imported to
    // the destination. But in this case we cannot do that much apart from --force.
    if (matcher.matches()) {
      return new GitRevision(getRepository(), getRepository().parseRef(sha1Part));
    }
    throw new CannotResolveRevisionException(
        String.format("'%s' is not a valid reference for a GitHub Pull Request. Valid formats:"
                + "'https://github.com/project/pull/1234', 'refs/pull/1234/head' or '1234'",
            reference));
  }

  private GitRevision getRevisionForPR(String project, int prNumber)
      throws RepoException, ValidationException {
    GitHubApi api = gitHubOptions.newGitHubApi(project);
    if (!requiredLabels.isEmpty()) {
      int retryCount = 0;
      Set<String> requiredButNotPresent;
      do {
        Issue issue;
        try (ProfilerTask ignore = generalOptions.profiler().start("github_api_get_issue")) {
          issue = api.getIssue(project, prNumber);
        }

        requiredButNotPresent = Sets.newHashSet(requiredLabels);
        requiredButNotPresent.removeAll(Collections2.transform(issue.getLabels(), Label::getName));
        // If we got all the labels we want or none of the ones we didn't get are retryable, return.
        if (requiredButNotPresent.isEmpty()
            || Collections.disjoint(requiredButNotPresent, retryableLabels)) {
          break;
        }
        Uninterruptibles.sleepUninterruptibly(2, TimeUnit.SECONDS);
        retryCount++;
      } while (retryCount < RETRY_COUNT);
      if (!requiredButNotPresent.isEmpty()) {
        throw new EmptyChangeException(String.format(
            "Cannot migrate http://github.com/%s/pull/%d because it is missing the following"
                + " labels: %s",
            project,
            prNumber,
            requiredButNotPresent));
      }
    }

    ImmutableListMultimap.Builder<String, String> labels = ImmutableListMultimap.builder();

    PullRequest prData;
    try (ProfilerTask ignore = generalOptions.profiler().start("github_api_get_pr")) {
      prData = api.getPullRequest(project, prNumber);
    }

    if (branch != null && !Objects.equals(prData.getBase().getRef(), branch)) {
      throw new EmptyChangeException(String.format(
          "Cannot migrate http://github.com/%s/pull/%d because its base branch is '%s', but"
              + " the workflow is configured to only migrate changes for branch '%s'",
          project,
          prNumber,
          prData.getBase().getRef(),
          branch));
    }
    if (reviewState != null) {
      ImmutableList<Review> reviews = api.getReviews(project, prNumber);
      if (!reviewState.shouldMigrate(reviews, reviewApprovers, prData.getHead().getSha())) {
        throw new EmptyChangeException(String.format(
            "Cannot migrate http://github.com/%s/pull/%d because it is missing the required"
                + " approvals (origin is configured as %s)",
            project, prNumber, reviewState));
      }
      Set<String> approvers = new HashSet<>();
      Set<String> others = new HashSet<>();
      for (Review review : reviews) {
        if (reviewApprovers.contains(review.getAuthorAssociation())) {
          approvers.add(review.getUser().getLogin());
        } else {
          others.add(review.getUser().getLogin());
        }
      }
      labels.putAll(GITHUB_PR_REVIEWER_APPROVER, approvers);
      labels.putAll(GITHUB_PR_REVIEWER_OTHER, others);
    }

    if (requiredState == StateFilter.OPEN && !prData.isOpen()) {
      throw new EmptyChangeException(String.format("Pull Request %d is not open", prNumber));
    }

    if (requiredState == StateFilter.CLOSED && prData.isOpen()) {
      throw new EmptyChangeException(String.format("Pull Request %d is open", prNumber));
    }

    // Fetch also the baseline branch. It is almost free and doing a roundtrip later would hurt
    // latency.
    console.progressFmt("Fetching Pull Request %d and branch '%s'",
        prNumber, prData.getBase().getRef());
    try(ProfilerTask ignore = generalOptions.profiler().start("fetch")) {
      ImmutableList.Builder<String> refSpecBuilder = ImmutableList.<String>builder()
          .add(String.format("%s:%s", asHeadRef(prNumber), LOCAL_PR_HEAD_REF))
          // Prefix the branch name with 'refs/heads/' since some implementations of
          // GitRepository need the whole reference name.
          .add(String.format("refs/heads/%s:" + LOCAL_PR_BASE_BRANCH, prData.getBase().getRef()));
      if (useMerge) {
        refSpecBuilder.add(String.format("%s:%s", asMergeRef(prNumber), LOCAL_PR_MERGE_REF));
      }
      ImmutableList<String> refspec = refSpecBuilder.build();
      getRepository().fetch(asGithubUrl(project),/*prune=*/false,/*force=*/true, refspec);
    } catch (CannotResolveRevisionException e) {
      if (useMerge) {
        throw new CannotResolveRevisionException(
            String.format("Cannot find a merge reference for Pull Request %d."
                + " It might have a conflict with head.", prNumber), e);
      } else {
        throw new CannotResolveRevisionException(
            String.format("Cannot find Pull Request %d.", prNumber), e);
      }
    }

    String refForMigration = useMerge ? LOCAL_PR_MERGE_REF : LOCAL_PR_HEAD_REF;
    GitRevision gitRevision = getRepository().resolveReference(refForMigration);

    String headPrSha1 = getRepository().resolveReference(LOCAL_PR_HEAD_REF).getSha1();
    String integrateLabel = new GitHubPRIntegrateLabel(getRepository(), generalOptions,
        project, prNumber,
        prData.getHead().getLabel(),
        // The integrate SHA has to be HEAD of the PR not the merge ref, even if use_merge = True
        headPrSha1)
        .toString();

    labels.putAll(GITHUB_PR_REQUESTED_REVIEWER, prData.getRequestedReviewers().stream()
        .map(User::getLogin)
        .collect(ImmutableList.toImmutableList()));
    labels.put(GITHUB_PR_NUMBER_LABEL, Integer.toString(prNumber));
    labels.put(GitModule.DEFAULT_INTEGRATE_LABEL, integrateLabel);
    labels.put(GITHUB_BASE_BRANCH, prData.getBase().getRef());
    labels.put(GITHUB_PR_HEAD_SHA, headPrSha1);

    String mergeBase = getRepository().mergeBase(refForMigration, LOCAL_PR_BASE_BRANCH);
    labels.put(GITHUB_BASE_BRANCH_SHA1, mergeBase);

    labels.put(GITHUB_PR_TITLE, prData.getTitle());
    labels.put(GITHUB_PR_BODY, prData.getBody());
    labels.put(GITHUB_PR_URL, prData.getHtmlUrl());
    labels.put(GITHUB_PR_USER, prData.getUser().getLogin());
    labels.putAll(GITHUB_PR_ASSIGNEE, prData.getAssignees().stream()
        .map(User::getLogin)
        .collect(Collectors.toList()));

    GitRevision result = new GitRevision(
        getRepository(),
        gitRevision.getSha1(),
        // TODO(malcon): Decide the format to use here:
        /*reviewReference=*/null,
        useMerge ? asMergeRef(prNumber) : asHeadRef(prNumber),
        labels.build(),
        url);

    return describeVersion ? getRepository().addDescribeVersion(result) : result;
  }

  @VisibleForTesting
  public GitRepository getRepository() throws RepoException {
    return gitOptions.cachedBareRepoForUrl(url);
  }

  @Override
  public Reader<GitRevision> newReader(Glob originFiles, Authoring authoring)
      throws ValidationException {
    return new ReaderImpl(url, originFiles, authoring, gitOptions, gitOriginOptions,
        generalOptions, /*includeBranchCommitLogs=*/false, submoduleStrategy, firstParent,
        patchTransformation, describeVersion) {

      /**
       * Disable rebase since this is controlled by useMerge field.
       */
      @Override
      protected void maybeRebase(GitRepository repo, GitRevision ref, Path workdir)
          throws RepoException, CannotResolveRevisionException {
      }

      @Override
      public Optional<Baseline<GitRevision>> findBaseline(GitRevision startRevision, String label)
          throws RepoException, ValidationException {
        if (!baselineFromBranch) {
          return super.findBaseline(startRevision, label);
        }
        return findBaselinesWithoutLabel(startRevision, /*limit=*/1).stream()
            .map(e -> new Baseline<>(e.getSha1(), e))
            .findFirst();
      }

      @Override
      public ImmutableList<GitRevision> findBaselinesWithoutLabel(GitRevision startRevision,
          int limit)
          throws RepoException, ValidationException {
        String baseline = Iterables.getLast(
            startRevision.associatedLabels().get(GITHUB_BASE_BRANCH_SHA1), null);
        Preconditions.checkNotNull(baseline, "%s label should be present in %s",
                                   GITHUB_BASE_BRANCH_SHA1, startRevision);

        GitRevision baselineRev = getRepository().resolveReference(baseline);
        // Don't skip the first change as it is already the baseline
        BaselinesWithoutLabelVisitor<GitRevision> visitor =
            new BaselinesWithoutLabelVisitor<>(originFiles, limit, /*skipFirst=*/ false);
        visitChanges(baselineRev, visitor);
        return visitor.getResult();
      }

      @Override
      public Endpoint getFeedbackEndPoint(Console console) throws ValidationException {
        gitHubOptions.validateEndpointChecker(endpointChecker);
        return new GitHubEndPoint(gitHubOptions.newGitHubApiSupplier(url, endpointChecker), url,
            console);
      }

      /**
       * Deal with the case of useMerge. We have a new commit (the merge) and first-parent from that
       * commit doesn't work for this case.
       */
      @Override
      public ChangesResponse<GitRevision> changes(@Nullable GitRevision fromRef,
          GitRevision toRef) throws RepoException {
        if (!useMerge) {
          return super.changes(fromRef, toRef);
        }
        GitLogEntry merge = Iterables.getOnlyElement(getRepository()
            .log(toRef.getSha1())
            .withLimit(1)
            .run());
        // Fast-forward merge
        if (merge.getParents().size() == 1) {
          return super.changes(fromRef, toRef);
        }
        // HEAD of the Pull Request
        GitRevision gitRevision = merge.getParents().get(1);
        ChangesResponse<GitRevision> prChanges = super.changes(fromRef, gitRevision);
        // Merge might have an effect, but we are not interested on it if the PR doesn't touch
        // origin_files
        if (prChanges.isEmpty()){
            return prChanges;
        }
        try {
          return ChangesResponse.forChanges(
              ImmutableList.<Change<GitRevision>>builder()
                  .addAll(prChanges.getChanges())
                  .add(change(merge.getCommit()))
                  .build());
        } catch (EmptyChangeException e) {
          throw new RepoException("Error getting the merge commit information: " + merge, e);
        }
      }
    };
  }

  @Override
  public String getLabelName() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  @Override
  public String getType() {
    return "git.github_pr_origin";
  }

  @VisibleForTesting
  public ReviewState getReviewState() {
    return reviewState;
  }

  @VisibleForTesting
  public Set<String> getRequiredLabels() {
    return requiredLabels;
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(Glob originFiles) {
    ImmutableSetMultimap.Builder<String, String> builder =
        new ImmutableSetMultimap.Builder<String, String>()
            .put("type", getType())
            .put("url", url);
    if (reviewState != null) {
      builder.put("review_state", reviewState.name());
      builder.putAll("review_approvers",
          reviewApprovers.stream().map(Enum::name).collect(ImmutableList.toImmutableList()));
    }
    return builder.build();
  }

  /**
   * Only migrate PR in one of the following states:
   */
  enum StateFilter {
    OPEN,
    CLOSED,
    ALL
  }

  @VisibleForTesting
  public enum ReviewState {
    /**
     * Requires that the current head commit has at least one valid approval
     */
    HEAD_COMMIT_APPROVED {
      @Override
      boolean shouldMigrate(ImmutableList<Review> reviews, String sha) {
        return reviews.stream()
            .filter(e -> e.getCommitId().equals(sha))
            .anyMatch(Review::isApproved);
      }
    },
    /**
     * Any valid approval, even for old commits is good.
     */
    ANY_COMMIT_APPROVED {
      @Override
      boolean shouldMigrate(ImmutableList<Review> reviews, String sha) {
        return reviews.stream().anyMatch(Review::isApproved);
      }
    },
    /**
     * There are reviewers in the change that have commented, asked for changes or approved
     */
    HAS_REVIEWERS {
      @Override
      boolean shouldMigrate(ImmutableList<Review> reviews, String sha) {
        return !reviews.isEmpty();
      }
    },
    /**
     * Import the change regardless of the the review state. It will populate the appropriate
     * labels if found
     */
    ANY {
      @Override
      boolean shouldMigrate(ImmutableList<Review> reviews, String sha) {
        return true;
      }
    };

    boolean shouldMigrate(ImmutableList<Review> reviews,
        ImmutableSet<AuthorAssociation> approvers, String sha) {
      return shouldMigrate(reviews.stream()
              // Only take into acccount reviews by valid approverTypes
              .filter(e -> approvers.contains(e.getAuthorAssociation()))
              .collect(ImmutableList.toImmutableList()),
          sha);
    }

    abstract boolean shouldMigrate(ImmutableList<Review> reviews, String sha);
  }
}
