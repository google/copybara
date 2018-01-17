/*
 * Copyright (C) 2016 Google Inc.
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

import static com.google.copybara.git.LazyGitRepository.memoized;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSetMultimap.Builder;
import com.google.copybara.ChangeMessage;
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.RepoException;
import com.google.copybara.TransformResult;
import com.google.copybara.ValidationException;
import com.google.copybara.git.GitDestination.CommitGenerator;
import com.google.copybara.git.GitDestination.ProcessPushOutput;
import com.google.copybara.git.GitDestination.WriterImpl;
import com.google.copybara.git.GitDestination.WriterState;
import com.google.copybara.git.github_api.CreatePullRequest;
import com.google.copybara.git.github_api.GithubApi;
import com.google.copybara.git.github_api.PullRequest;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * A destination for creating/updating Github Pull Requests.
 */
public class GithubPrDestination implements Destination<GitRevision> {

  private final String url;
  private final String destinationRef;
  private final GeneralOptions generalOptions;
  private final GithubOptions githubOptions;
  private final GitDestinationOptions destinationOptions;
  private final GithubDestinationOptions githubDestinationOptions;
  private final GitOptions gitOptions;
  private final CommitGenerator commitGenerator;
  private final ProcessPushOutput processPushOutput;
  private final Iterable<GitIntegrateChanges> integrates;
  private final boolean effectiveSkipPush;
  private final LazyGitRepository localRepo;

  public GithubPrDestination(String url, String destinationRef, GeneralOptions generalOptions,
      GithubOptions githubOptions,
      GitDestinationOptions destinationOptions, GithubDestinationOptions githubDestinationOptions,
      GitOptions gitOptions,
      boolean skipPush, CommitGenerator commitGenerator, ProcessPushOutput processPushOutput,
      Iterable<GitIntegrateChanges> integrates) {
    this.url = Preconditions.checkNotNull(url);
    this.destinationRef = Preconditions.checkNotNull(destinationRef);
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.githubOptions = Preconditions.checkNotNull(githubOptions);
    this.destinationOptions = Preconditions.checkNotNull(destinationOptions);
    this.githubDestinationOptions = Preconditions.checkNotNull(githubDestinationOptions);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
    this.commitGenerator = Preconditions.checkNotNull(commitGenerator);
    this.processPushOutput = Preconditions.checkNotNull(processPushOutput);
    this.integrates = Preconditions.checkNotNull(integrates);
    this.effectiveSkipPush = skipPush || destinationOptions.skipPush;
    this.localRepo = memoized(ignored -> destinationOptions.localGitRepo(url));
  }

  @Override
  public String getType() {
    return "git.github_pr_destination";
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(Glob originFiles) {
    ImmutableSetMultimap.Builder<String, String> builder =
        new ImmutableSetMultimap.Builder<String, String>()
            .put("type", getType())
            .put("name", url)
            .put("destination_ref", destinationRef);
    if (effectiveSkipPush) {
      builder.put("skip_push", "True");
    }
    return builder.build();
  }

  @Override
  public Writer<GitRevision> newWriter(Glob destinationFiles, boolean dryRun,
      @Nullable String groupId, @Nullable Writer<GitRevision> oldWriter)
      throws ValidationException {
    WriterImpl gitOldWriter = (WriterImpl) oldWriter;

    boolean effectiveSkipPush = GithubPrDestination.this.effectiveSkipPush || dryRun;

    GithubWriterState state;
    String pushBranchName = branchFromGroupId(groupId);
    if (oldWriter != null && gitOldWriter.skipPush == effectiveSkipPush) {
      state = (GithubWriterState) ((WriterImpl) oldWriter).state;
    } else {
      state = new GithubWriterState(localRepo,
          destinationOptions.localRepoPath != null
              ? pushBranchName
              : "copybara/push-" + UUID.randomUUID() + (dryRun ? "-dryrun" : ""));
    }

    return new WriterImpl<GithubWriterState>(destinationFiles, effectiveSkipPush, url,
        destinationRef, pushBranchName,
        generalOptions, commitGenerator, processPushOutput,
        state, /*nonFastForwardPush=*/true, integrates,
        destinationOptions.lastRevFirstParent,
        destinationOptions.ignoreIntegrationErrors,
        destinationOptions.localRepoPath,
        destinationOptions.committerName,
        destinationOptions.committerEmail,
        destinationOptions.rebaseWhenBaseline(),
        gitOptions.visitChangePageSize) {
      @Override
      public WriterResult write(TransformResult transformResult, Console console)
          throws ValidationException, RepoException, IOException {
        WriterResult result = super.write(transformResult, console);

        if (effectiveSkipPush || state.pullRequestNumber != null) {
          return result;
        }

        if (!githubDestinationOptions.createPullRequest) {
          console.infoFmt("Please create a PR manually following this link: %s/compare/%s...%s"
                  + " (Only needed once)",
              asHttpsUrl(), destinationRef, pushBranchName);
          state.pullRequestNumber = -1L;
          return result;
        }

        GithubApi api = githubOptions.getApi(GithubUtil.getProjectNameFromUrl(url));
        for (PullRequest pr : api.getPullRequests(getProjectName())) {
          if (pr.isOpen() && pr.getHead().getRef().equals(pushBranchName)) {
            console.infoFmt("Pull request for branch %s already exists as %s/pull/%s",
                pushBranchName, asHttpsUrl(), pr.getNumber());
            if (!pr.getBase().getRef().equals(destinationRef)) {
              // TODO(malcon): Update PR or create a new one?
              console.warnFmt("Current base branch '%s' is different from the PR base branch '%s'",
                  destinationRef, pr.getBase().getRef());
            }
            return result;
          }
        }
        ChangeMessage msg = ChangeMessage.parseMessage(transformResult.getSummary());
        PullRequest pr = api.createPullRequest(getProjectName(),
            new CreatePullRequest(msg.firstLine(), msg.getText(),
                pushBranchName, destinationRef));
        console.infoFmt("Pull Request %s/pull/%s created using branch '%s'.", asHttpsUrl(),
            pr.getNumber(), pushBranchName);
        state.pullRequestNumber = pr.getNumber();
        return result;
      }
    };
  }


  private String asHttpsUrl() throws ValidationException {
    return "https://github.com/" + getProjectName();
  }

  @VisibleForTesting
  String getProjectName() throws ValidationException {
    return GithubUtil.getProjectNameFromUrl(url);
  }

  private String branchFromGroupId(@Nullable String groupId) throws ValidationException {
    if (!Strings.isNullOrEmpty(githubDestinationOptions.destinationPrBranch)) {
      return githubDestinationOptions.destinationPrBranch;
    }
    // We could do more magic here with the change identity. But this is already complex so we
    // require  a group identity either provided by the origin or the workflow (Will be implemented
    // later.
    ValidationException.checkCondition(groupId != null,
        "git.github_pr_destination is incompatible with the current origin. Origin has to be"
            + " able to provide the group identity or use '%s' flag",
        GithubDestinationOptions.GITHUB_DESTINATION_PR_BRANCH);
    return groupId.replaceAll("[^A-Za-z0-9_-]", "_");
  }

  @Override
  public String getLabelNameWhenOrigin() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  private static class GithubWriterState extends WriterState {

    @Nullable
    Long pullRequestNumber;

    GithubWriterState(LazyGitRepository localRepo, String localBranch) {
      super(localRepo, localBranch);
    }
  }
}
