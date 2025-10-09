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

import com.google.auto.value.AutoBuilder;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.copybara.ChangeMessage;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LabelFinder;
import com.google.copybara.TransformResult;
import com.google.copybara.WriterContext;
import com.google.copybara.checks.Checker;
import com.google.copybara.effect.DestinationEffect;
import com.google.copybara.effect.DestinationEffect.DestinationRef;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitDestination.WriterImpl;
import com.google.copybara.git.GitLabMrDestination.GitLabWriterState;
import com.google.copybara.git.gitlab.api.GitLabApi;
import com.google.copybara.git.gitlab.api.GitLabApiException;
import com.google.copybara.git.gitlab.api.entities.CreateMergeRequestParams;
import com.google.copybara.git.gitlab.api.entities.ListProjectMergeRequestParams;
import com.google.copybara.git.gitlab.api.entities.ListUsersParams;
import com.google.copybara.git.gitlab.api.entities.MergeRequest;
import com.google.copybara.git.gitlab.api.entities.MergeRequest.State;
import com.google.copybara.git.gitlab.api.entities.Project;
import com.google.copybara.git.gitlab.api.entities.UpdateMergeRequestParams;
import com.google.copybara.git.gitlab.api.entities.UpdateMergeRequestParams.StateEvent;
import com.google.copybara.git.gitlab.api.entities.User;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.stream.Collectors;

/** A {@link WriterImpl} that writes to GitLab merge requests. */
public class GitLabMrWriter extends WriterImpl<GitLabMrDestination.GitLabWriterState> {
  private final GitLabMrWriterParams params;

  private GitLabMrWriter(GitLabMrWriterParams params) {
    super(
        params.skipPush(),
        params.repoUrl().toString(),
        params.targetBranch(),
        params.sourceBranch(),
        params.partialFetch(),
        /* tagNameTemplate= */ null,
        /* tagMsgTemplate= */ null,
        params.generalOptions(),
        params.gitOptions(),
        params.writeHook(),
        params.state(),
        /* nonFastForwardPush= */ true,
        params.integrates(),
        params.destinationOptions().lastRevFirstParent,
        params.destinationOptions().ignoreIntegrationErrors,
        params.destinationOptions().localRepoPath,
        params.destinationOptions().committerName,
        params.destinationOptions().committerEmail,
        params.destinationOptions().rebaseWhenBaseline(),
        params.gitOptions().visitChangePageSize,
        params.gitOptions().gitTagOverwrite,
        params.checker().orElse(null),
        params.destinationOptions(),
        params.credentials(),
        /* lfsSource= */ null);
    this.params = params;
  }

  /**
   * Uploads the fully transformed repository to GitLab, and creates/updates a merge request
   * associated with the changes.
   *
   * @param transformResult what to write to the destination
   * @param destinationFiles the glob to use for write. This glob might be different from the one
   *     received in {@link #getDestinationStatus} due to read config from change configuration
   * @param console console to be used for printing messages
   * @return one or more destination effects detailing what was done to the destination by this
   *     writer
   * @throws ValidationException if there is an issue resolving the title, body, assignee labels, or
   *     incorrect parameters were passed to the {@link GitLabApi} object
   * @throws RepoException if there is an issue writing to the Git repos or interacting with the
   *     GitLab API
   * @throws IOException if there is an issue with the super method walking the file tree
   */
  @Override
  public ImmutableList<DestinationEffect> write(
      TransformResult transformResult, Glob destinationFiles, Console console)
      throws ValidationException, RepoException, IOException {
    ImmutableList.Builder<DestinationEffect> result =
        ImmutableList.<DestinationEffect>builder()
            .addAll(super.write(transformResult, destinationFiles, console));

    if (params.writerContext().isDryRun()) {
      console.warnFmt("Not writing MR to GitLab as we are running in --dry-run mode.");
      return result.build();
    }
    if (state.getMergeRequestNumber().isPresent()) {
      console.warnFmt(
          "Not writing MR to GitLab as a merge request has already been written by this"
              + " destination.");
      return result.build();
    }

    GitLabApi gitLabApi = params.gitLabApi();
    ChangeMessage msg = ChangeMessage.parseMessage(transformResult.getSummary().trim());
    String title = getTitle(transformResult, msg);
    String mrBody = getMrBody(transformResult, msg);

    ImmutableList<String> assignees =
        LabelFinder.mapLabels(transformResult.getLabelFinder(), params.assigneeTemplates());

    ImmutableList<MergeRequest> mergeRequests =
        gitLabApi.getProjectMergeRequests(
            params.project().getId(),
            new ListProjectMergeRequestParams(Optional.of(params.sourceBranch())));

    if (mergeRequests.isEmpty()) {
      console.progress("Creating new MR");
      result.addAll(createMergeRequests(title, mrBody, assignees, transformResult, console));
    } else {
      String mrIids =
          mergeRequests.stream()
              .map(mr -> Integer.toString(mr.getIid()))
              .collect(Collectors.joining(", "));
      if (mergeRequests.size() > 1) {
        console.warnFmt("Found more than one MR! IIDs: %s", mrIids);
      }
      console.progressFmt("Updating existing MRs: %s", mrIids);
      result.addAll(
          updateExistingMergeRequests(
              mergeRequests, title, mrBody, assignees, transformResult, console));
    }

    return result.build();
  }

  private String getMrBody(TransformResult transformResult, ChangeMessage msg)
      throws ValidationException {
    return params.bodyTemplate().isPresent()
        ? LabelFinder.mapLabels(
            transformResult.getLabelFinder(), params.bodyTemplate().get(), "body")
        : msg.toString();
  }

  private String getTitle(TransformResult transformResult, ChangeMessage msg)
      throws ValidationException {
    String title =
        params.titleTemplate().isPresent()
            ? LabelFinder.mapLabels(
                transformResult.getLabelFinder(), params.titleTemplate().get(), "title")
            : msg.firstLine();
    ValidationException.checkCondition(
        !Strings.isNullOrEmpty(title), "Merge request title can not be empty.");
    return title;
  }

  private ImmutableList<DestinationEffect> createMergeRequests(
      String title,
      String description,
      ImmutableList<String> assignees,
      TransformResult transformResult,
      Console console)
      throws ValidationException, RepoException {
    console.progressFmt(
        "Creating MR for project %s, source branch %s, target branch %s, and assignees %s",
        params.project().getId(), params.sourceBranch(), params.targetBranch(), assignees);
    Optional<MergeRequest> newMr =
        params
            .gitLabApi()
            .createMergeRequest(
                new CreateMergeRequestParams(
                    params.project().getId(),
                    params.sourceBranch(),
                    params.targetBranch(),
                    title,
                    description,
                    mapAssigneeUsernamesToIds(assignees, console)));
    if (newMr.isPresent()) {
      int mergeRequestIid = newMr.get().getIid();
      params.state().setMrNumber(mergeRequestIid);
      console.progressFmt("Created merge request at %s", newMr.get().getWebUrl());
      return ImmutableList.of(
          new DestinationEffect(
              DestinationEffect.Type.CREATED,
              String.format("Merge Request %s created", newMr.get().getWebUrl()),
              transformResult.getChanges().getCurrent(),
              new DestinationRef(
                  Integer.toString(mergeRequestIid), "merge_request", newMr.get().getWebUrl())));
    } else {
      throw new RepoException(
          "Attempted to create a new merge request, but the API did not respond with information"
              + " about the new merge request");
    }
  }

  private ImmutableList<Integer> mapAssigneeUsernamesToIds(
      ImmutableList<String> assignees, Console console)
      throws ValidationException, GitLabApiException {
    ImmutableList.Builder<Integer> assigneeIds = ImmutableList.builder();
    for (String assignee : assignees) {
      ImmutableList<User> user = params.gitLabApi().getListUsers(new ListUsersParams(assignee));
      if (user.size() == 1) {
        assigneeIds.add(Iterables.getOnlyElement(user).getId());
      } else if (user.size() > 1) {
        throw new ValidationException(
            String.format(
                "Found more than 1 user for %s. This should not happen, as a username maps to one"
                    + " user. Please report this to the Copybara team",
                assignee));
      } else {
        console.warnFmt("Could not find a user for the username %s, skipping", assignee);
      }
    }
    return assigneeIds.build();
  }

  private ImmutableList<DestinationEffect> updateExistingMergeRequests(
      ImmutableList<MergeRequest> mergeRequests,
      String title,
      String description,
      ImmutableList<String> assignees,
      TransformResult transformResult,
      Console console)
      throws ValidationException, RepoException {
    ImmutableList.Builder<DestinationEffect> results = ImmutableList.builder();

    for (MergeRequest mergeRequest : mergeRequests) {
      console.progressFmt(
          "Updating MR %d for project %s, source branch %s, target branch %s, and assignees %s",
          mergeRequest.getIid(),
          params.project().getId(),
          params.sourceBranch(),
          params.targetBranch(),
          assignees);
      StateEvent newState = null;
      if (mergeRequest.getState() == State.CLOSED) {
        console.warnFmt("Existing MR %s is closed, reopening.", mergeRequest.getIid());
        newState = StateEvent.REOPEN;
      }
      Optional<MergeRequest> updatedMr =
          params
              .gitLabApi()
              .updateMergeRequest(
                  new UpdateMergeRequestParams(
                      params.project().getId(),
                      mergeRequest.getIid(),
                      title,
                      description,
                      mapAssigneeUsernamesToIds(assignees, console),
                      newState));
      if (updatedMr.isPresent()) {
        console.progressFmt("Updated MR located at %s", updatedMr.get().getWebUrl());
        results.add(
            new DestinationEffect(
                DestinationEffect.Type.UPDATED,
                String.format("Merge Request %s updated", updatedMr.get().getWebUrl()),
                transformResult.getChanges().getCurrent(),
                new DestinationRef(
                    Integer.toString(updatedMr.get().getIid()),
                    "merge_request",
                    updatedMr.get().getWebUrl())));
      } else {
        throw new RepoException(
            "Attempted to create a new merge request, but the API did not respond with information"
                + " about the new merge request");
      }
    }

    return results.build();
  }

  /**
   * A value class containing params for {@link GitLabMrWriter}.
   *
   * <p>It is recommended to construct this record using {@link #builder()}.
   *
   * <p>To construct a new instance of {@link GitLabMrWriter} with these params, use {@link
   * #createWriter()}.
   *
   * @param gitLabApi the GitLab API client to be used by the writer, for creating/updating merge
   *     requests
   * @param titleTemplate the template for the title of the merge request, which can contain labels
   * @param bodyTemplate the template for the body of the merge request, which can contain labels
   * @param assigneeTemplates the templates for the assignees of the merge request, which can
   *     contain labels
   * @param project the project to create the merge request for
   * @param writerContext the writer context object
   * @param skipPush whether to skip the push to the remote repository
   * @param repoUrl the URL of the GitLab repository to push to
   * @param sourceBranch the source branch of the merge request
   * @param targetBranch the target branch of the merge request
   * @param partialFetch whether to use partial fetch when fetching the baseline from the
   *     destination repo
   * @param generalOptions the general options to be used by the writer object
   * @param gitOptions the git options to be used by the writer object
   * @param writeHook the GitLab write hook to be used by this writer
   * @param state the writer state, used for tracking the merge request number throughout the
   *     operation
   * @param integrates the collection of changes to be integrated into the code migration
   * @param checker the checker used on the migrated code
   * @param destinationOptions the Git destination options to be used by the writer object
   * @param credentials the credentials used for Git authentication with GitLab, required to push to
   *     the target branch
   */
  public record GitLabMrWriterParams(
      GitLabApi gitLabApi,
      Optional<String> titleTemplate,
      Optional<String> bodyTemplate,
      ImmutableList<String> assigneeTemplates,
      Project project,
      WriterContext writerContext,
      boolean skipPush,
      URI repoUrl,
      String sourceBranch,
      String targetBranch,
      boolean partialFetch,
      GeneralOptions generalOptions,
      GitOptions gitOptions,
      GitLabMrWriteHook writeHook,
      GitLabWriterState state,
      Iterable<GitIntegrateChanges> integrates,
      Optional<Checker> checker,
      GitDestinationOptions destinationOptions,
      CredentialFileHandler credentials) {

    /** A builder for {@link GitLabMrWriterParams}. */
    @AutoBuilder
    public abstract static class Builder {

      /**
       * Sets the GitLab API client to be used by the writer, for creating/updating merge requests
       *
       * @param gitLabApi the GitLab API client
       * @return a reference to this builder
       */
      public abstract Builder setGitLabApi(GitLabApi gitLabApi);

      /**
       * Sets the templates for the title of the merge request, which can contain labels
       *
       * @param titleTemplate the title template
       * @return a reference to this builder
       */
      public abstract Builder setTitleTemplate(Optional<String> titleTemplate);

      /**
       * Sets the templates for the body of the merge request, which can contain labels
       *
       * @param bodyTemplate the body template
       * @return a reference to this builder
       */
      public abstract Builder setBodyTemplate(Optional<String> bodyTemplate);

      /**
       * Sets the templates for the assignees of the merge request, which can contain labels
       *
       * @param assigneeTemplates the assignee templates
       * @return a reference to this builder
       */
      public abstract Builder setAssigneeTemplates(ImmutableList<String> assigneeTemplates);

      /**
       * Sets the project to create the merge request for. This object should be obtained from
       * {@link GitLabApi}
       *
       * @param project the project
       * @return a reference to this builder
       */
      public abstract Builder setProject(Project project);

      /**
       * Sets the writer context object, which is used for some information such as the workflow
       * name and identity user to generate a branch name
       *
       * @param writerContext the writer context
       * @return a reference to this builder
       */
      public abstract Builder setWriterContext(WriterContext writerContext);

      /**
       * Sets whether to skip the push to the remote repository
       *
       * @param skipPush whether to skip the push
       * @return a reference to this builder
       */
      public abstract Builder setSkipPush(boolean skipPush);

      /**
       * Sets the URL of the GitLab repository to push to
       *
       * @param repoUrl the URL
       * @return a reference to this builder
       */
      public abstract Builder setRepoUrl(URI repoUrl);

      /**
       * Sets the source branch of the merge request, i.e., the branch to be merged into the target
       * branch
       *
       * @param sourceBranch the source branch
       * @return a reference to this builder
       */
      public abstract Builder setSourceBranch(String sourceBranch);

      /**
       * Sets the target branch of the merge request, i.e., the branch to merge the source branch
       * into
       *
       * @param targetBranch the target branch
       * @return a reference to this builder
       */
      public abstract Builder setTargetBranch(String targetBranch);

      /**
       * Sets whether to use partial fetch when fetching the baseline from the destination repo
       *
       * @param partialFetch whether to use partial fetch
       * @return a reference to this builder
       */
      public abstract Builder setPartialFetch(boolean partialFetch);

      /**
       * Sets the general options to be used by the writer object
       *
       * @param generalOptions the general options
       * @return a reference to this builder
       */
      public abstract Builder setGeneralOptions(GeneralOptions generalOptions);

      /**
       * Sets the git options to be used by the writer object
       *
       * @param gitOptions the git options
       * @return a reference to this builder
       */
      public abstract Builder setGitOptions(GitOptions gitOptions);

      /**
       * Sets the write hook to be used by this writer, which checks whether the resulting change
       * should be uploaded
       *
       * @param writeHook the write hook
       * @return a reference to this builder
       */
      public abstract Builder setWriteHook(GitLabMrWriteHook writeHook);

      /**
       * Sets the writer state, used for tracking the merge request number throughout the operation
       *
       * @param state the writer state
       * @return a reference to this builder
       */
      public abstract Builder setState(GitLabWriterState state);

      /**
       * Sets the collection of changes to be integrated into the code migration
       *
       * @param integrates the changes to integrate
       * @return a reference to this builder
       */
      public abstract Builder setIntegrates(Iterable<GitIntegrateChanges> integrates);

      /**
       * Sets the checker used on the migrated code before it is pushed to the remote
       *
       * @param checker the checker
       * @return a reference to this builder
       */
      public abstract Builder setChecker(Optional<Checker> checker);

      /**
       * Sets the Git destination options to be used by the writer object
       *
       * @param destinationOptions the destination options
       * @return a reference to this builder
       */
      public abstract Builder setDestinationOptions(GitDestinationOptions destinationOptions);

      /**
       * Sets the credentials used for Git authentication with GitLab, required to push to the
       * target branch
       *
       * @param credentials the credentials
       * @return the builder object with the credentials set
       */
      public abstract Builder setCredentials(CredentialFileHandler credentials);

      /**
       * Returns an instance of {@link GitLabMrWriterParams} with the given parameters.
       *
       * @return the {@link GitLabMrWriterParams} object
       */
      public abstract GitLabMrWriterParams build();
    }

    /**
     * Returns a builder for this param class.
     *
     * @return the builder
     */
    public static Builder builder() {
      return new AutoBuilder_GitLabMrWriter_GitLabMrWriterParams_Builder()
          .setTitleTemplate(Optional.empty())
          .setBodyTemplate(Optional.empty())
          .setIntegrates(ImmutableList.of())
          .setChecker(Optional.empty());
    }

    /** Creates a new instance of {@link GitLabMrWriter} using the parameters from this object. */
    public GitLabMrWriter createWriter() {
      return new GitLabMrWriter(this);
    }
  }
}
