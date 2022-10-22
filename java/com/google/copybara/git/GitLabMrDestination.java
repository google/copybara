/*
 * Copyright (C) 2022 Google Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
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
import com.google.copybara.git.github.util.GitHubUtil;
import com.google.copybara.git.gitlab.api.GitLabApi;
import com.google.copybara.git.gitlab.api.MergeRequest;
import com.google.copybara.git.gitlab.util.GitLabHost;
import com.google.copybara.revision.Revision;
import com.google.copybara.templatetoken.LabelTemplate;
import com.google.copybara.util.Glob;
import com.google.copybara.util.Identity;
import com.google.copybara.util.console.Console;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.UUID;

import static com.google.copybara.LazyResourceLoader.memoized;
import static com.google.copybara.exception.ValidationException.checkCondition;
import static com.google.copybara.git.GitModule.PRIMARY_BRANCHES;

public class GitLabMrDestination implements Destination<GitRevision> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final String url;
  private final String destinationRef;
  private final String prBranch;
  private final boolean partialFetch;
  private final boolean primaryBranchMigrationMode;

  private final GeneralOptions generalOptions;
  private final GitLabOptions gitLabOptions;
  private final GitDestinationOptions destinationOptions;
  private final GitLabDestinationOptions gitLabDestinationOptions;
  private final GitOptions gitOptions;
  private final GitLabMrWriteHook writeHook;
  private final Iterable<GitIntegrateChanges> integrates;
  @Nullable
  private final String title;
  @Nullable
  private final String body;
  private final boolean updateDescription;
  private final GitLabHost glHost;
  @Nullable
  private final Checker checker;
  private final LazyResourceLoader<GitRepository> localRepo;
  private final ConfigFile mainConfigFile;
  @Nullable
  private final Checker endpointChecker;

  @Nullable
  private String resolvedDestinationRef;

  GitLabMrDestination(
      String url,
      String destinationRef,
      @Nullable String prBranch,
      boolean partialFetch,
      GeneralOptions generalOptions,
      GitLabOptions gitLabOptions,
      GitDestinationOptions destinationOptions,
      GitLabDestinationOptions gitLabDestinationOptions,
      GitOptions gitOptions,
      GitLabMrWriteHook writeHook,
      Iterable<GitIntegrateChanges> integrates,
      @Nullable String title,
      @Nullable String body,
      ConfigFile mainConfigFile,
      @Nullable Checker endpointChecker,
      boolean updateDescription,
      GitLabHost glHost,
      boolean primaryBranchMigrationMode,
      @Nullable Checker checker) {
    this.url = Preconditions.checkNotNull(url);
    this.destinationRef = Preconditions.checkNotNull(destinationRef);
    this.prBranch = prBranch;
    this.partialFetch = partialFetch;
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.gitLabOptions = Preconditions.checkNotNull(gitLabOptions);
    this.destinationOptions = Preconditions.checkNotNull(destinationOptions);
    this.gitLabDestinationOptions = Preconditions.checkNotNull(gitLabDestinationOptions);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
    this.writeHook = Preconditions.checkNotNull(writeHook);
    this.integrates = Preconditions.checkNotNull(integrates);
    this.title = title;
    this.body = body;
    this.updateDescription = updateDescription;
    this.glHost = Preconditions.checkNotNull(glHost);
    this.checker = checker;
    this.localRepo = memoized(ignored -> destinationOptions.localGitRepo(url));
    this.mainConfigFile = Preconditions.checkNotNull(mainConfigFile);
    this.endpointChecker = endpointChecker;
    this.primaryBranchMigrationMode = primaryBranchMigrationMode;
  }

  @Override
  public Writer<GitRevision> newWriter(WriterContext writerContext) throws ValidationException {
    String prBranch =
        getMergeRequestBranchName(
            writerContext.getOriginalRevision(),
            writerContext.getWorkflowName(),
            writerContext.getWorkflowIdentityUser());
    GitLabMrWriteHook gitLabMrWriteHook = writeHook.withUpdatedMrBranch(prBranch);

    GitLabWriterState state = new GitLabWriterState(
        localRepo,
        destinationOptions.localRepoPath != null
            ? prBranch
            : "copybara/push-"
            + UUID.randomUUID()
            + (writerContext.isDryRun() ? "-dryrun" : ""));

    return new GitDestination.WriterImpl<GitLabWriterState>(
        writerContext.isDryRun(),
        url,
        getDestinationRef(),
        prBranch,
        partialFetch,
        /*tagName*/ null,
        /*tagMsg*/ null,
        generalOptions,
        gitLabMrWriteHook,
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
        gitOptions.gitTagOverwrite,
        checker,
        ImmutableList.of()
    ) {
      @Override
      public ImmutableList<DestinationEffect> write(
          TransformResult transformResult, Glob destinationFiles, Console console)
          throws ValidationException, RepoException, IOException {
        ImmutableList.Builder<DestinationEffect> result =
            ImmutableList.<DestinationEffect>builder()
                .addAll(super.write(transformResult, destinationFiles, console));
        if (writerContext.isDryRun() || state.mergeRequestNumber != null) {
          return result.build();
        }

        if (!gitLabDestinationOptions.createMergeRequest) {
          console.infoFmt(
              "Please create a MR manually following this link: %s/compare/%s...%s"
                  + " (Only needed once)",
              asHttpsUrl(), getDestinationRef(), prBranch);
          state.mergeRequestNumber = -1L;
          return result.build();
        }

        GitLabApi api = gitLabOptions.newGitLabApi();

        console.infoFmt("Search MRs for %s", prBranch);

        ImmutableList<MergeRequest> mergeRequests =
            api.getMergeRequests(getProjectName(), prBranch);

        ChangeMessage msg = ChangeMessage.parseMessage(transformResult.getSummary().trim());

        String title =
            GitLabMrDestination.this.title == null
                ? msg.firstLine()
                : SkylarkUtil.mapLabels(
                transformResult.getLabelFinder(), GitLabMrDestination.this.title, "title");

        String mrBody =
            GitLabMrDestination.this.body == null
                ? msg.toString()
                : SkylarkUtil.mapLabels(
                transformResult.getLabelFinder(), GitLabMrDestination.this.body, "body");


        for (MergeRequest mr : mergeRequests) {
          if (mr.getSourceBranch().equals(prBranch)) {
            if (!mr.isOpen()) {
              console.warnFmt(
                  "Merge request for branch %s already exists as %s/-/merge_requests/%s, but is closed - "
                      + "reopening.",
                  prBranch, asHttpsUrl(), mr.getNumber());
              api.updateMergeRequest(
                  getProjectName(), mr.getNumber(), title, mrBody, "reopen");
            } else {
              console.infoFmt(
                  "Merge request for branch %s already exists as %s/-/merge_requests/%s",
                  prBranch, asHttpsUrl(), mr.getNumber());
            }
            if (!mr.getTargetBranch().equals(getDestinationRef())) {
              // TODO(malcon): Update MR or create a new one?
              console.warnFmt(
                  "Current base branch '%s' is different from the MR base branch '%s'",
                  getDestinationRef(), mr.getTargetBranch());
            }
            if (updateDescription) {
              checkCondition(
                  !Strings.isNullOrEmpty(title),
                  "Merge Request title cannot be empty. Either use 'title' field in"
                      + " git.gitlab_mr_destination or modify the message to not be empty");
              api.updateMergeRequest(
                  getProjectName(),
                  mr.getNumber(),
                  title, mrBody, null);
            }
            result.add(
                new DestinationEffect(
                    DestinationEffect.Type.UPDATED,
                    String.format("Merge Request %s updated", mr.getHtmlUrl()),
                    transformResult.getChanges().getCurrent(),
                    new DestinationEffect.DestinationRef(
                        Long.toString(mr.getNumber()), "pull_request", mr.getHtmlUrl())));
            return result.build();
          }
        }

        checkCondition(
            !Strings.isNullOrEmpty(title),
            "Pull Request title cannot be empty. Either use 'title' field in"
                + " git.github_pr_destination or modify the message to not be empty");

        MergeRequest mr =
            api.createMergeRequest(
                getProjectName(),
                title, mrBody, prBranch, getDestinationRef());
        console.infoFmt(
            "Merge Request %s/-/merge_requests/%s created using branch '%s'.",
            asHttpsUrl(), mr.getNumber(), prBranch);
        state.mergeRequestNumber = mr.getNumber();
        result.add(
            new DestinationEffect(
                DestinationEffect.Type.CREATED,
                String.format("Merge Request %s created", mr.getHtmlUrl()),
                transformResult.getChanges().getCurrent(),
                new DestinationEffect.DestinationRef(
                    Long.toString(mr.getNumber()), "merge_request", mr.getHtmlUrl())));
        return result.build();
      }

      @Override
      public Endpoint getFeedbackEndPoint(Console console) throws ValidationException {
        gitLabOptions.validateEndpointChecker(endpointChecker);
        // do not provide any feedback endpoint as for now
        // consider to enhance it later
        return Endpoint.NOOP_ENDPOINT;
      }
    };
  }

  private String getMergeRequestBranchName(
      @Nullable Revision changeRevision, String workflowName, String workflowIdentityUser)
      throws ValidationException {
    if (!Strings.isNullOrEmpty(gitLabDestinationOptions.destinationMrBranch)) {
      return gitLabDestinationOptions.destinationMrBranch;
    }
    String contextReference = changeRevision.contextReference();
    String contextRevision = changeRevision.asString();
    // We could do more magic here with the change identity. But this is already complex so we
    // require  a group identity either provided by the origin or the workflow (Will be implemented
    // later.
    checkCondition(contextReference != null,
        "git.gitlab_mr_destination is incompatible with the current origin. Origin has to be"
            + " able to provide the contextReference or use '%s' flag",
        GitLabDestinationOptions.GITLAB_DESTINATION_MR_BRANCH);
    String branchNameFromUser = getCustomBranchName(contextReference, contextRevision);
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

  private String asHttpsUrl() throws ValidationException {
    return gitLabOptions.getGitlabUrl() + "/" + getProjectName();
  }

  @VisibleForTesting
  String getProjectName() throws ValidationException {
    return glHost.getProjectNameFromUrl(url);
  }

  @Override
  public String getLabelNameWhenOrigin() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  private String getCustomBranchName(String contextReference, String revision) throws ValidationException {
    if (prBranch == null) {
      return null;
    }
    try {
      return new LabelTemplate(prBranch)
          .resolve((Function<String, String>) e -> {
            if (e.equals("CONTEXT_REFERENCE")) {
              return contextReference;
            } else if (e.equals("GITLAB_REVISION")) {
              return revision;
            }
            return prBranch;
          });
    } catch (LabelTemplate.LabelNotFoundException e) {
      throw new ValidationException(
          "Cannot find some labels in the GitLab MR branch name field: " + e.getMessage(), e);
    }
  }

  private static class GitLabWriterState extends GitDestination.WriterState {

    @Nullable
    Long mergeRequestNumber;

    GitLabWriterState(LazyResourceLoader<GitRepository> localRepo, String localBranch) {
      super(localRepo, localBranch);
    }
  }

  @Nullable
  String getDestinationRef() throws ValidationException {
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
