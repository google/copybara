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
import static com.google.copybara.git.github.util.GitHubUtil.getUserNameFromUrl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.ChangeMessage;
import com.google.copybara.Destination;
import com.google.copybara.DestinationEffect;
import com.google.copybara.Endpoint;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LazyResourceLoader;
import com.google.copybara.Revision;
import com.google.copybara.TransformResult;
import com.google.copybara.WriterContext;
import com.google.copybara.checks.Checker;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitDestination.WriterImpl;
import com.google.copybara.git.GitDestination.WriterImpl.WriteHook;
import com.google.copybara.git.GitDestination.WriterState;
import com.google.copybara.git.github.api.CreatePullRequest;
import com.google.copybara.git.github.api.GitHubApi;
import com.google.copybara.git.github.api.GitHubApi.PullRequestListParams;
import com.google.copybara.git.github.api.PullRequest;
import com.google.copybara.git.github.api.UpdatePullRequest;
import com.google.copybara.git.github.util.GitHubUtil;
import com.google.copybara.templatetoken.LabelTemplate;
import com.google.copybara.templatetoken.LabelTemplate.LabelNotFoundException;
import com.google.copybara.util.Glob;
import com.google.copybara.util.Identity;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * A destination for creating/updating Github Pull Requests.
 */
public class GitHubPrDestination implements Destination<GitRevision> {
  private static final String CANNOT_INFER_FORK_URL_MESSAGE =
      "Could not infer fork url. Please set 'fork_url'.";

  private final String url;
  private final boolean pushToFork;
  private final Optional<String> forkUrl;
  private final String destinationRef;
  private final String prBranch;
  private final GeneralOptions generalOptions;
  private final GitHubOptions gitHubOptions;
  private final GitDestinationOptions destinationOptions;
  private final GitHubDestinationOptions gitHubDestinationOptions;
  private final GitOptions gitOptions;
  private final WriteHook writeHook;
  private final Iterable<GitIntegrateChanges> integrates;
  @Nullable private final String title;
  @Nullable private final String body;
  private final boolean updateDescription;
  private final LazyResourceLoader<GitRepository> localRepo;
  private final ConfigFile mainConfigFile;
  @Nullable private final Checker endpointChecker;

  GitHubPrDestination(
      String url,
      String destinationRef,
      boolean pushToFork,
      Optional<String> forkUrl,
      @Nullable String prBranch,
      GeneralOptions generalOptions,
      GitHubOptions gitHubOptions,
      GitDestinationOptions destinationOptions,
      GitHubDestinationOptions gitHubDestinationOptions,
      GitOptions gitOptions,
      WriteHook writeHook,
      Iterable<GitIntegrateChanges> integrates,
      @Nullable String title,
      @Nullable String body,
      ConfigFile mainConfigFile,
      @Nullable Checker endpointChecker,
      boolean updateDescription) {
    this.url = Preconditions.checkNotNull(url);
    this.pushToFork = pushToFork;
    this.forkUrl = Preconditions.checkNotNull(forkUrl);
    this.destinationRef = Preconditions.checkNotNull(destinationRef);
    this.prBranch = prBranch;
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
    this.localRepo = memoized(ignored -> destinationOptions.localGitRepo(getFetchUrl()));
    this.mainConfigFile = Preconditions.checkNotNull(mainConfigFile);
    this.endpointChecker = endpointChecker;
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
            .put("url", getFetchUrl())
            .put("push_to_fork", (pushToFork || forkUrl.isPresent()) ? "True" : "False");
    if (forkUrl.isPresent()) {
      builder.put("fork_url", forkUrl.get());
    }
    builder.put("destination_ref", destinationRef);
    return builder.build();
  }

  @Override
  public Writer<GitRevision> newWriter(WriterContext writerContext) throws ValidationException {
    LazyResourceLoader<GitHubApi> githubApi =
        memoized(gitHubOptions.newGitHubApiSupplier(getFetchUrl(), null));
    Optional<String> pushUrl = pushUrl(githubApi);
    PrBranch prBranch =
        new PrBranch(
            writerContext.getOriginalRevision(),
            writerContext.getWorkflowName(),
            writerContext.getWorkflowIdentityUser(),
            url,
            pushUrl);

    GitHubWriterState state =
        new GitHubWriterState(
            localRepo,
            destinationOptions.localRepoPath != null
                ? prBranch.getLocalName()
                : "copybara/push-"
                    + UUID.randomUUID()
                    + (writerContext.isDryRun() ? "-dryrun" : ""));

    return new WriterImpl<GitHubWriterState>(
        writerContext.isDryRun(),
        getFetchUrl(),
        pushUrl.orElse(getFetchUrl()),
        destinationRef,
        prBranch.getLocalName(),
        /*tagName*/ null,
        /*tagMsg*/ null,
        generalOptions,
        writeHook,
        state,
        /*nonFastForwardPush=*/ true,
        integrates,
        destinationOptions.lastRevFirstParent,
        destinationOptions.ignoreIntegrationErrors,
        destinationOptions.localRepoPath,
        destinationOptions.committerName,
        destinationOptions.committerEmail,
        destinationOptions.rebaseWhenBaseline(),
        gitOptions.visitChangePageSize,
        gitOptions.gitTagOverwrite) {
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
              asGithubHttpsUrl(pushUrl), destinationRef, prBranch.getPrRef());
          state.pullRequestNumber = -1L;
          return result.build();
        }

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

        ImmutableList<PullRequest> pullRequests =
            githubApi
                .load(console)
                .getPullRequests(
                    getProjectName(),
                    PullRequestListParams.DEFAULT.withHead(prBranch.getQualifiedPrRef()));
        for (PullRequest pr : pullRequests) {
          if (pr.isOpen() && pr.getHead().getRef().equals(prBranch.getLocalName())) {
            console.infoFmt(
                "Pull request for branch %s already exists as %s/pull/%s",
                prBranch.getPrRef(), asGithubHttpsUrl(pushUrl), pr.getNumber());
            if (!pr.getBase().getRef().equals(destinationRef)) {
              // TODO(malcon): Update PR or create a new one?
              console.warnFmt(
                  "Current base branch '%s' is different from the PR base branch '%s'",
                  destinationRef, pr.getBase().getRef());
            }
            if (updateDescription) {
              checkCondition(
                  !Strings.isNullOrEmpty(title),
                  "Pull Request title cannot be empty. Either use 'title' field in"
                      + " git.github_pr_destination or modify the message to not be empty");
              githubApi
                  .load(console)
                  .updatePullRequest(
                      getProjectName(),
                      pr.getNumber(),
                      new UpdatePullRequest(title, prBody, /*state=*/ null));
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
            githubApi
                .load(console)
                .createPullRequest(
                    getProjectName(),
                    new CreatePullRequest(title, prBody, prBranch.getPrRef(), destinationRef));
        console.infoFmt(
            "Pull Request %s/pull/%s created using branch '%s'.",
            asGithubHttpsUrl(pushUrl), pr.getNumber(), prBranch.getPrRef());
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
        String url = pushUrl.orElse(getFetchUrl());
        return new GitHubEndPoint(
            gitHubOptions.newGitHubApiSupplier(url, endpointChecker), url, console);
      }
    };
  }

  @VisibleForTesting
  Optional<String> pushUrl(LazyResourceLoader<GitHubApi> api) throws ValidationException {
    if (forkUrl.isPresent()) {
      return forkUrl;
    }
    if (pushToFork) {
      String repoName = GitHubUtil.getRepoNameFromUrl(getFetchUrl());
      if (!Strings.isNullOrEmpty(repoName)) {
        try {
          String username = api.load(generalOptions.console()).getAuthenticatedUser().getLogin();
          return Optional.of(asGithubHttpsUrl(username + "/" + repoName));
        } catch (RepoException e) {
          throw new ValidationException(CANNOT_INFER_FORK_URL_MESSAGE, e);
        }
      }
      throw new ValidationException(CANNOT_INFER_FORK_URL_MESSAGE);
    }
    return Optional.empty();
  }

  private String getFetchUrl() {
    return url;
  }

  private String asGithubHttpsUrl(String project) {
    return "https://github.com/" + project;
  }

  private String asGithubHttpsUrl(Optional<String> pushUrl) throws ValidationException {
    return asGithubHttpsUrl(GitHubUtil.getProjectNameFromUrl(pushUrl.orElse(getFetchUrl())));
  }

  @VisibleForTesting
  String getProjectName() throws ValidationException {
    return GitHubUtil.getProjectNameFromUrl(getFetchUrl());
  }

  @VisibleForTesting
  public boolean isUpdateDescription() {
    return updateDescription;
  }

  @VisibleForTesting
  public Iterable<GitIntegrateChanges> getIntegrates() {
    return integrates;
  }

  private class PrBranch {
    private final String name;
    private final String url;
    private final Optional<String> forkUrl;

    public PrBranch(
        @Nullable Revision changeRevision,
        String workflowName,
        String workflowIdentityUser,
        String url,
        Optional<String> forkUrl)
        throws ValidationException {
      this.name = getPullRequestBranchName(changeRevision, workflowName, workflowIdentityUser);
      this.url = Preconditions.checkNotNull(url);
      this.forkUrl = Preconditions.checkNotNull(forkUrl);
    }

    private String getPullRequestBranchName(
        @Nullable Revision changeRevision, String workflowName, String workflowIdentityUser)
        throws ValidationException {
      if (!Strings.isNullOrEmpty(gitHubDestinationOptions.destinationPrBranch)) {
        return gitHubDestinationOptions.destinationPrBranch;
      }
      String contextReference = changeRevision.contextReference();
      // We could do more magic here with the change identity. But this is already complex so we
      // require  a group identity either provided by the origin or the workflow (Will be
      // implemented
      // later.
      checkCondition(
          contextReference != null,
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

    public String getUpstreamUrl() {
      return url;
    }

    public String getLocalName() {
      return name;
    }

    private String qualifiedName(String url) throws ValidationException {
      return String.format("%s:%s", getUserNameFromUrl(url), getLocalName());
    }

    public String getPrRef() throws ValidationException {
      if (!forkUrl.isPresent()) {
        return getLocalName();
      }
      return qualifiedName(forkUrl.get());
    }

    public String getQualifiedPrRef() throws ValidationException {
      return qualifiedName(forkUrl.orElse(url));
    }
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
}
