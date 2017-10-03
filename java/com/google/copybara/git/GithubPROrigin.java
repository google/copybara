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

import static com.google.copybara.git.GithubUtil.asGithubUrl;
import static com.google.copybara.git.GithubUtil.getProjectNameFromUrl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.copybara.CannotResolveRevisionException;
import com.google.copybara.Change;
import com.google.copybara.EmptyChangeException;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.git.GitOrigin.ReaderImpl;
import com.google.copybara.git.GitOrigin.SubmoduleStrategy;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.git.GithubUtil.GithubPrUrl;
import com.google.copybara.git.github_api.Issue;
import com.google.copybara.git.github_api.Issue.Label;
import com.google.copybara.git.github_api.PullRequest;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import javax.annotation.Nullable;

/**
 * A class for reading GitHub Pull Requests
 */
public class GithubPROrigin implements Origin<GitRevision> {


  static final String GITHUB_PR_NUMBER_LABEL = "GITHUB_PR_NUMBER";
  public static final String GITHUB_BASE_BRANCH = "GITHUB_BASE_BRANCH";

  private final String url;
  private final boolean useMerge;
  private final GeneralOptions generalOptions;
  private final GitOptions gitOptions;
  private final GitOriginOptions gitOriginOptions;
  private final GithubOptions githubOptions;
  private final Set<String> requiredLabels;
  private final SubmoduleStrategy submoduleStrategy;
  private final Console console;

  GithubPROrigin(String url, boolean useMerge, GeneralOptions generalOptions,
      GitOptions gitOptions, GitOriginOptions gitOriginOptions, GithubOptions githubOptions,
      Set<String> requiredLabels, SubmoduleStrategy submoduleStrategy) {
    this.url = Preconditions.checkNotNull(url);
    this.useMerge = useMerge;
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
    this.gitOriginOptions = Preconditions.checkNotNull(gitOriginOptions);
    this.githubOptions = githubOptions;
    this.requiredLabels = Preconditions.checkNotNull(requiredLabels);
    this.submoduleStrategy = Preconditions.checkNotNull(submoduleStrategy);
    console = generalOptions.console();
  }

  @Override
  public GitRevision resolve(String reference) throws RepoException, ValidationException {
    console.progress("GitHub PR Origin: Resolving reference " + reference);

    // A whole https pull request url
    Optional<GithubPrUrl> githubPrUrl = GithubUtil.maybeParseGithubPrUrl(reference);
    String configProjectName = GithubUtil.getProjectNameFromUrl(url);
    if (githubPrUrl.isPresent()) {
      ValidationException.checkCondition(
          githubPrUrl.get().getProject().equals(configProjectName),
          String.format("Project name should be '%s' but it is '%s' instead", configProjectName,
              githubPrUrl.get().getProject()));

      return getRevisionForPR(configProjectName, githubPrUrl.get().getPrNumber());
    }
    // A Pull request number
    if (CharMatcher.digit().matchesAllOf(reference)) {
      return getRevisionForPR(getProjectNameFromUrl(url), Integer.parseInt(reference));
    }

    // refs/pull/12345/head
    Optional<Integer> prNumber = GithubUtil.maybeParseGithubPrFromHeadRef(reference);
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
    if (!requiredLabels.isEmpty()) {
      Issue issue;
      try (ProfilerTask ignore = generalOptions.profiler().start("github_api_get_issue")) {
        issue = githubOptions.getApi(project).getIssue(project, prNumber);
      }

      Set<String> required = Sets.newHashSet(requiredLabels);
      required.removeAll(Collections2.transform(issue.getLabels(), Label::getName));

      // TODO(malcon): Find a better exception for this.
      ValidationException.checkCondition(
          required.isEmpty(),
          String.format("Cannot migrate http://github.com/%s/%d because it is missing the following"
              + " labels: %s", project, prNumber, required));
    }
    PullRequest prData;
    try (ProfilerTask ignore = generalOptions.profiler().start("github_api_get_pr")) {
      prData = githubOptions.getApi(project).getPullRequest(project, prNumber);
    }
    String stableRef = useMerge ? GithubUtil.asMergeRef(prNumber) : GithubUtil.asHeadRef(prNumber);

    // Fetch also the baseline branch. It is almost free and doing a roundtrip later would hurt
    // latency.
    console.progressFmt("Fetching Pull Request %d and branch '%s'",
        prNumber, prData.getBase().getRef());
    try {
      getRepository().fetch(asGithubUrl(project),/*prune=*/false,/*force=*/true,
          ImmutableList.of(stableRef + ":PR_HEAD", prData.getBase().getRef() + ":PR_BASE_BRANCH"));
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

    GitRevision gitRevision = getRepository().resolveReference("PR_HEAD", /*contextRef=*/null);

    String integrateLabel = new GithubPRIntegrateLabel(getRepository(), generalOptions,
        project, prNumber,
        prData.getHead().getLabel(), gitRevision.getSha1()).toString();

    return new GitRevision(
        getRepository(),
        gitRevision.getSha1(),
        // TODO(malcon): Decide the format to use here:
        /*reviewReference=*/null,
        stableRef,
        ImmutableMap.of(GITHUB_PR_NUMBER_LABEL, Integer.toString(prNumber),
            GitModule.DEFAULT_INTEGRATE_LABEL, integrateLabel,
            GITHUB_BASE_BRANCH, prData.getBase().getRef()));
  }

  @VisibleForTesting
  public GitRepository getRepository() throws RepoException {
    return gitOptions.cachedBareRepoForUrl(url);
  }

  @Override
  public Reader<GitRevision> newReader(Glob originFiles, Authoring authoring)
      throws ValidationException {
    return new ReaderImpl(url, originFiles, authoring, gitOptions, gitOriginOptions,
        generalOptions, /*includeBranchCommitLogs=*/false, submoduleStrategy) {

      /**
       * Disable rebase since this is controlled by useMerge field.
       */
      @Override
      protected void maybeRebase(GitRepository repo, GitRevision ref, Path workdir)
          throws RepoException, CannotResolveRevisionException {
      }

      /**
       * Deal with the case of useMerge. We have a new commit (the merge) and first-parent from that
       * commit doesn't work for this case.
       */
      @Override
      public ImmutableList<Change<GitRevision>> changes(@Nullable GitRevision fromRef,
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
        ImmutableList<Change<GitRevision>> prChanges = super.changes(fromRef, gitRevision);
        try {
          return ImmutableList.<Change<GitRevision>>builder()
              .addAll(prChanges)
              .add(change(merge.getCommit()))
              .build();
        } catch (EmptyChangeException e) {
          throw new RepoException("Error getting the merge commit information: " + merge, e);
        }
      }

      @Nullable
      @Override
      public String getGroupIdentity(GitRevision rev) throws RepoException {
        return rev.associatedLabels().get(GITHUB_PR_NUMBER_LABEL);
      }
    };
  }

  @Override
  public String getLabelName() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(@Nullable Glob originFiles) {
    ImmutableSetMultimap.Builder<String, String> builder =
        new ImmutableSetMultimap.Builder<String, String>()
            .put("type", "git.github_pr_origin")
            .put("url", url);
    return builder.build();
  }
}
