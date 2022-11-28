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

import static com.google.copybara.LazyResourceLoader.memoized;
import static com.google.copybara.exception.ValidationException.checkCondition;
import static com.google.copybara.git.GitModule.PRIMARY_BRANCHES;
import static com.google.copybara.git.github.api.UpdatePullRequest.State.OPEN;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.ChangeMessage;
import com.google.copybara.Destination;
import com.google.copybara.Endpoint;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LazyResourceLoader;
import com.google.copybara.TransformResult;
import com.google.copybara.WriterContext;
import com.google.copybara.checks.Checker;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.effect.DestinationEffect;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitDestination.WriterImpl;
import com.google.copybara.git.GitDestination.WriterState;
import com.google.copybara.git.github.api.CreatePullRequest;
import com.google.copybara.git.github.api.GitHubApi;
import com.google.copybara.git.github.api.GitHubApi.PullRequestListParams;
import com.google.copybara.git.github.api.PullRequest;
import com.google.copybara.git.github.api.UpdatePullRequest;
import com.google.copybara.git.github.util.GitHubHost;
import com.google.copybara.git.github.util.GitHubUtil;
import com.google.copybara.revision.Revision;
import com.google.copybara.templatetoken.LabelTemplate;
import com.google.copybara.templatetoken.LabelTemplate.LabelNotFoundException;
import com.google.copybara.util.Glob;
import com.google.copybara.util.Identity;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * A destination for creating/updating Github Pull Requests.
 */
public class GitHubPrDestination implements Destination<GitRevision> {

  private final String url;
  private final String destinationRef;
  private final String prBranch;
  private final boolean partialFetch;
  private final boolean draft;
  private final boolean primaryBranchMigrationMode;

  private final GeneralOptions generalOptions;
  private final GitHubOptions gitHubOptions;
  private final GitDestinationOptions destinationOptions;
  private final GitHubDestinationOptions gitHubDestinationOptions;
  private final GitOptions gitOptions;
  private final GitHubPrWriteHook writeHook;
  private final Iterable<GitIntegrateChanges> integrates;
  @Nullable private final String title;
  @Nullable private final String body;
  private final boolean updateDescription;
  private final GitHubHost ghHost;
  @Nullable private final Checker checker;
  private final LazyResourceLoader<GitRepository> localRepo;
  private final ConfigFile mainConfigFile;
  @Nullable private final Checker endpointChecker;

  @Nullable private String resolvedDestinationRef;

  GitHubPrDestination(
      String url,
      String destinationRef,
      @Nullable String prBranch,
      boolean partialFetch,
      boolean draft,
      GeneralOptions generalOptions,
      GitHubOptions gitHubOptions,
      GitDestinationOptions destinationOptions,
      GitHubDestinationOptions gitHubDestinationOptions,
      GitOptions gitOptions,
      GitHubPrWriteHook writeHook,
      Iterable<GitIntegrateChanges> integrates,
      @Nullable String title,
      @Nullable String body,
      ConfigFile mainConfigFile,
      @Nullable Checker endpointChecker,
      boolean updateDescription,
      GitHubHost ghHost,
      boolean primaryBranchMigrationMode,
      @Nullable Checker checker) {
    this.url = Preconditions.checkNotNull(url);
    this.destinationRef = Preconditions.checkNotNull(destinationRef);
    this.prBranch = prBranch;
    this.partialFetch = partialFetch;
    this.draft = draft;
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.gitHubOptions = Preconditions.checkNotNull(gitHubOptions);
    this.destinationOptions = Preconditions.checkNotNull(destinationOptions);
    this.gitHubDestinationOptions = Preconditions.checkNotNull(gitHubDestinationOptions);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
    this.writeHook = Preconditions.checkNotNull(writeHook);
    this.integrates = Preconditions.checkNotNull(integrates);
    this.title = title;
    this.body = body;
    this.updateDescription = updateDescription;
    this.ghHost = Preconditions.checkNotNull(ghHost);
    this.checker = checker;
    this.localRepo = memoized(ignored -> destinationOptions.localGitRepo(url));
    this.mainConfigFile = Preconditions.checkNotNull(mainConfigFile);
    this.endpointChecker = endpointChecker;
    this.primaryBranchMigrationMode = primaryBranchMigrationMode;
  }

  @Override
  public String getType() {
    return "git.github_pr_destination";
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(Glob destinationFiles) {
    ImmutableSetMultimap.Builder<String, String> builder =
        new ImmutableSetMultimap.Builder<String, String>()
            .put("type", getType())
            .put("url", url)
            .put("destination_ref", destinationRef)
            .put("primaryBranchMigrationMode", "" + primaryBranchMigrationMode);

    if (checker != null) {
      builder.put("checker", checker.getClass().getName());
    }
    if (!destinationFiles.roots().isEmpty() && !destinationFiles.roots().contains("")) {
      builder.putAll("root", destinationFiles.roots());
    }
    return builder.build();
  }

  @Override
  public Writer<GitRevision> newWriter(WriterContext writerContext) throws ValidationException {

    String prBranch =
        getPullRequestBranchName(
            writerContext.getOriginalRevision(),
            writerContext.getWorkflowName(),
            writerContext.getWorkflowIdentityUser());
    GitHubPrWriteHook gitHubPrWriteHook = writeHook.withUpdatedPrBranch(prBranch);

    GitHubWriterState state = new GitHubWriterState(
        localRepo,
        destinationOptions.localRepoPath != null
            ? prBranch
            : "copybara/push-"
                + UUID.randomUUID()
                + (writerContext.isDryRun() ? "-dryrun" : ""));

    return new WriterImpl<GitHubWriterState>(
        writerContext.isDryRun(),
        url,
        getDestinationRef(),
        prBranch,
        partialFetch,
        /*tagName*/ null,
        /*tagMsg*/ null,
        generalOptions,
        gitHubPrWriteHook,
        state,
        /* nonFastForwardPush= */ true,
        integrates,
        destinationOptions.lastRevFirstParent,
        destinationOptions.ignoreIntegrationErrors,
        destinationOptions.localRepoPath,
        destinationOptions.committerName,
        destinationOptions.committerEmail,
        destinationOptions.rebaseWhenBaseline(),
        gitOptions.visitChangePageSize,
        gitOptions.gitTagOverwrite,
        checker) {
      @Override
      public ImmutableList<DestinationEffect> write(
          TransformResult transformResult, Glob destinationFiles, Console console)
          throws ValidationException, RepoException, IOException {
        ImmutableList.Builder<DestinationEffect> result =
            ImmutableList.<DestinationEffect>builder()
                .addAll(super.write(transformResult, destinationFiles, console));
        if (writerContext.isDryRun() || state.pullRequestNumber != null) {
          return result.build();
        }

        if (!gitHubDestinationOptions.createPullRequest) {
          console.infoFmt(
              "Please create a PR manually following this link: %s/compare/%s...%s"
                  + " (Only needed once)",
              asHttpsUrl(), getDestinationRef(), prBranch);
          state.pullRequestNumber = -1L;
          return result.build();
        }

        GitHubApi api = gitHubOptions.newGitHubRestApi(getProjectName());

        ImmutableList<PullRequest> pullRequests =
            api.getPullRequests(
                getProjectName(),
                PullRequestListParams.DEFAULT.withHead(
                    String.format("%s:%s", ghHost.getUserNameFromUrl(url), prBranch)));

        ChangeMessage msg = ChangeMessage.parseMessage(transformResult.getSummary().trim());

        String title =
            GitHubPrDestination.this.title == null
                ? msg.firstLine()
                : SkylarkUtil.mapLabels(
                    transformResult.getLabelFinder(), GitHubPrDestination.this.title, "title");

        String prBody =
            GitHubPrDestination.this.body == null
                ? msg.toString()
                : SkylarkUtil.mapLabels(
                    transformResult.getLabelFinder(), GitHubPrDestination.this.body, "body");

        for (PullRequest pr : pullRequests) {
          if (pr.getHead().getRef().equals(prBranch)) {
            if (!pr.isOpen()) {
              console.warnFmt(
                  "Pull request for branch %s already exists as %s/pull/%s, but is closed - "
                      + "reopening.",
                  prBranch, asHttpsUrl(), pr.getNumber());
              api.updatePullRequest(
                  getProjectName(), pr.getNumber(), new UpdatePullRequest(null, null, OPEN));
            } else {
              console.infoFmt(
                  "Pull request for branch %s already exists as %s/pull/%s",
                  prBranch, asHttpsUrl(), pr.getNumber());
            }
            if (!pr.getBase().getRef().equals(getDestinationRef())) {
              // TODO(malcon): Update PR or create a new one?
              console.warnFmt(
                  "Current base branch '%s' is different from the PR base branch '%s'",
                  getDestinationRef(), pr.getBase().getRef());
            }
            if (updateDescription) {
              checkCondition(
                  !Strings.isNullOrEmpty(title),
                  "Pull Request title cannot be empty. Either use 'title' field in"
                      + " git.github_pr_destination or modify the message to not be empty");
              api.updatePullRequest(
                  getProjectName(),
                  pr.getNumber(),
                  new UpdatePullRequest(title, prBody, /* state= */ null));
            }
            result.add(
                new DestinationEffect(
                    DestinationEffect.Type.UPDATED,
                    String.format("Pull Request %s updated", pr.getHtmlUrl()),
                    transformResult.getChanges().getCurrent(),
                    new DestinationEffect.DestinationRef(
                        Long.toString(pr.getNumber()), "pull_request", pr.getHtmlUrl())));
            return result.build();
          }
        }

        checkCondition(
            !Strings.isNullOrEmpty(title),
            "Pull Request title cannot be empty. Either use 'title' field in"
                + " git.github_pr_destination or modify the message to not be empty");

        PullRequest pr =
            api.createPullRequest(
                getProjectName(),
                new CreatePullRequest(title, prBody, prBranch, getDestinationRef(), draft));
        console.infoFmt(
            "Pull Request %s/pull/%s created using branch '%s'.",
            asHttpsUrl(), pr.getNumber(), prBranch);
        state.pullRequestNumber = pr.getNumber();
        result.add(
            new DestinationEffect(
                DestinationEffect.Type.CREATED,
                String.format("Pull Request %s created", pr.getHtmlUrl()),
                transformResult.getChanges().getCurrent(),
                new DestinationEffect.DestinationRef(
                    Long.toString(pr.getNumber()), "pull_request", pr.getHtmlUrl())));
        return result.build();
      }

      @Override
      public Endpoint getFeedbackEndPoint(Console console) throws ValidationException {
        gitHubOptions.validateEndpointChecker(endpointChecker);
        return new GitHubEndPoint(
            gitHubOptions.newGitHubApiSupplier(url, endpointChecker, ghHost), url, console, ghHost);
      }
    };
  }

  private String asHttpsUrl() throws ValidationException {
    return "https://github.com/" + getProjectName();
  }

  @VisibleForTesting
  String getProjectName() throws ValidationException {
    return ghHost.getProjectNameFromUrl(url);
  }

  @VisibleForTesting
  public boolean isUpdateDescription() {
    return updateDescription;
  }

  @VisibleForTesting
  public Iterable<GitIntegrateChanges> getIntegrates() {
    return integrates;
  }

  private String getPullRequestBranchName(
      @Nullable Revision changeRevision, String workflowName, String workflowIdentityUser)
      throws ValidationException {
    if (!Strings.isNullOrEmpty(gitHubDestinationOptions.destinationPrBranch)) {
      return gitHubDestinationOptions.destinationPrBranch;
    }
    String contextReference = changeRevision.contextReference();
    // We could do more magic here with the change identity. But this is already complex so we
    // require  a group identity either provided by the origin or the workflow (Will be implemented
    // later.
    checkCondition(contextReference != null,
        "git.github_pr_destination is incompatible with the current origin. Origin has to be"
            + " able to provide the contextReference or use '%s' flag",
        GitHubDestinationOptions.GITHUB_DESTINATION_PR_BRANCH);
    String branchNameFromUser = getCustomBranchName(contextReference);
    String branchName =
        branchNameFromUser != null
            ? branchNameFromUser
            : Identity.computeIdentity(
                "OriginGroupIdentity",
                contextReference,
                workflowName,
                mainConfigFile.getIdentifier(),
                workflowIdentityUser);
    return GitHubUtil.getValidBranchName(branchName);
  }

  @Nullable
  private String getCustomBranchName(String contextReference) throws ValidationException {
    if (prBranch == null) {
      return null;
    }
    try {
      return new LabelTemplate(prBranch)
          .resolve(e -> e.equals("CONTEXT_REFERENCE") ? contextReference : prBranch);
    } catch (LabelNotFoundException e) {
      throw new ValidationException(
          "Cannot find some labels in the GitHub PR branch name field: " + e.getMessage(), e);
    }
  }

  @Override
  public String getLabelNameWhenOrigin() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  private static class GitHubWriterState extends WriterState {

    @Nullable
    Long pullRequestNumber;

    GitHubWriterState(LazyResourceLoader<GitRepository> localRepo, String localBranch) {
      super(localRepo, localBranch);
    }
  }

  @Nullable String getDestinationRef() throws ValidationException {
    if (!primaryBranchMigrationMode || !PRIMARY_BRANCHES.contains(destinationRef)) {
      return destinationRef;
    }
    if (resolvedDestinationRef == null) {
      try {
        resolvedDestinationRef = localRepo.load(generalOptions.console()).getPrimaryBranch(url);
      } catch (RepoException e) {
        generalOptions.console().warnFmt("Error detecting primary branch: %s", e);
        return null;
      }
    }
    return resolvedDestinationRef;
  }
}