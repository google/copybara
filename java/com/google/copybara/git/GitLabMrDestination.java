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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LazyResourceLoader;
import com.google.copybara.WriterContext;
import com.google.copybara.checks.Checker;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitDestination.WriterState;
import com.google.copybara.git.GitLabMrWriteHook.GitLabMrWriteHookParams;
import com.google.copybara.git.GitLabMrWriter.GitLabMrWriterParams;
import com.google.copybara.git.gitlab.GitLabUtil;
import com.google.copybara.git.gitlab.api.GitLabApi;
import com.google.copybara.git.gitlab.api.GitLabApiException;
import com.google.copybara.git.gitlab.api.entities.MergeRequest.DetailedMergeStatus;
import com.google.copybara.git.gitlab.api.entities.Project;
import com.google.copybara.revision.Revision;
import com.google.copybara.templatetoken.LabelTemplate;
import com.google.copybara.templatetoken.LabelTemplate.LabelNotFoundException;
import com.google.copybara.util.Glob;
import com.google.copybara.util.Identity;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A destination for creating/updating GitLab Merge Requests.
 *
 * <p>It will either create new merge requests or update existing ones based on the source branch
 * name provided.
 */
public class GitLabMrDestination implements Destination<GitRevision> {
  private final GitLabMrDestinationParams params;
  private final LazyResourceLoader<GitRepository> localRepo;

  public GitLabMrDestination(GitLabMrDestinationParams params) {
    this.params = params;
    this.localRepo =
        LazyResourceLoader.memoized(
            unused ->
                params
                    .destinationOptions()
                    .localGitRepo(params.repoUrl().toString(), params.credentialFileHandler()));
  }

  @Override
  public String getType() {
    return "git.gitlab_mr_destination";
  }

  @Override
  public GitLabMrWriter newWriter(WriterContext writerContext) throws ValidationException {
    String mrBranch =
        getMergeRequestBranchName(
            Optional.ofNullable(writerContext.getOriginalRevision()),
            writerContext.getWorkflowName(),
            writerContext.getWorkflowIdentityUser());

    GitLabMrWriteHook writeHook =
        new GitLabMrWriteHookParams(
                params.allowEmptyDiff(),
                params.gitLabApi(),
                params.repoUrl(),
                mrBranch,
                params.generalOptions(),
                params.partialFetch(),
                params.allowEmptyDiffMergeStatuses())
            .createWriteHook();
    GitLabWriterState state =
        new GitLabWriterState(
            localRepo,
            String.format(
                "copybara/push-%s%s",
                UUID.randomUUID(), writerContext.isDryRun() ? "-dryrun" : ""));

    Project project;
    try {
      project =
          params
              .gitLabApi()
              .getProject(GitLabUtil.getUrlEncodedProjectPath(params.repoUrl()))
              .orElseThrow(
                  () ->
                      new ValidationException(
                          "GitLab API did not return a Project response for " + params.repoUrl()));

    } catch (GitLabApiException e) {
      throw new ValidationException(
          String.format("Failed to query for GitLab Project status. Cause: %s", e.getMessage()), e);
    }

    return GitLabMrWriterParams.builder()
        .setGitLabApi(params.gitLabApi())
        .setTitleTemplate(params.titleTemplate())
        .setBodyTemplate(params.bodyTemplate())
        .setAssigneeTemplates(params.assigneeTemplates())
        .setProject(project)
        .setWriterContext(writerContext)
        .setSkipPush(writerContext.isDryRun())
        .setRepoUrl(params.repoUrl())
        .setSourceBranch(mrBranch)
        .setTargetBranch(params.targetBranch())
        .setPartialFetch(params.partialFetch())
        .setGeneralOptions(params.generalOptions())
        .setGitOptions(params.gitOptions())
        .setWriteHook(writeHook)
        .setState(state)
        .setIntegrates(params.integrates())
        .setChecker(params.checker())
        .setDestinationOptions(params.destinationOptions())
        .setCredentials(params.credentialFileHandler())
        .build()
        .createWriter();
  }

  @Override
  public String getLabelNameWhenOrigin() throws ValidationException {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(Glob destinationFiles) {
    ImmutableSetMultimap.Builder<String, String> builder =
        new ImmutableSetMultimap.Builder<String, String>()
            .put("type", getType())
            .put("url", params.repoUrl().toString())
            .put("title_template", params.titleTemplate().orElse(""))
            .put("source_branch_template", params.sourceBranchTemplate().orElse(""))
            .put("target_branch", params.targetBranch())
            .put("allow_empty_diff", Boolean.toString(params.allowEmptyDiff()))
            .put("partial_fetch", Boolean.toString(params.partialFetch()));

    params.checker().ifPresent(checker -> builder.put("checker", checker.getClass().getName()));
    if (!destinationFiles.roots().isEmpty() && !destinationFiles.roots().contains("")) {
      builder.putAll("root", destinationFiles.roots());
    }
    return builder.build();
  }

  @Override
  public ImmutableList<ImmutableSetMultimap<String, String>> describeCredentials() {
    return params.credentialFileHandler().describeCredentials();
  }

  private String getMergeRequestBranchName(
      Optional<Revision> revision, String workflowName, String workflowIdentityUser)
      throws ValidationException {
    String contextReference =
        revision
            .map(Revision::contextReference)
            .orElseThrow(
                () ->
                    new ValidationException(
                        getType()
                            + " is incompatible with the current origin. Origin"
                            + " has to be able to provide the context reference."));

    if (params.sourceBranchTemplate().isPresent()) {
      return getCustomMrBranchName(params.sourceBranchTemplate().get(), contextReference);
    }

    return Identity.computeIdentity(
        "OriginGroupIdentity",
        contextReference,
        workflowName,
        params.configFile().getIdentifier(),
        workflowIdentityUser);
  }

  private String getCustomMrBranchName(String template, String contextReference)
      throws ValidationException {
    ImmutableMap<String, String> supportedLabels =
        ImmutableMap.of("CONTEXT_REFERENCE", contextReference);

    try {
      return new LabelTemplate(template).resolve(supportedLabels::get);
    } catch (LabelNotFoundException e) {
      throw new ValidationException(
          "Can not resolve labels in the GitHub MR branch name template: " + e.getMessage(), e);
    }
  }

  static class GitLabWriterState extends WriterState {
    private final AtomicReference<Optional<Long>> mergeRequestNumber;

    GitLabWriterState(LazyResourceLoader<GitRepository> localRepo, String localBranch) {
      this(localRepo, localBranch, Optional.empty());
    }

    private GitLabWriterState(
        LazyResourceLoader<GitRepository> localRepo,
        String localBranch,
        Optional<Long> mergeRequestNumber) {
      super(localRepo, localBranch);
      this.mergeRequestNumber = new AtomicReference<>(mergeRequestNumber);
    }

    void setMrNumber(long mergeRequestNumber) {
      this.mergeRequestNumber.set(Optional.of(mergeRequestNumber));
    }

    Optional<Long> getMergeRequestNumber() {
      return mergeRequestNumber.get();
    }
  }

  /**
   * A value class containing params for {@link GitLabMrDestination}.
   *
   * <p>This class serves as a container for all the necessary parameters required to construct a
   * {@link GitLabMrDestination} instance. It encapsulates the various configurations and settings
   * related to the destination, such as API access, repository URL, template configurations, and
   * options for handling merge requests.
   *
   * <p>It is recommended to construct this class using {@link #builder()}.
   *
   * <p>To construct a new {@link GitLabMrDestination} instance using these params, use {@link
   * #createDestination()}
   *
   * @param gitLabApi the GitLab API client
   * @param repoUrl the URL of the GitLab repository
   * @param titleTemplate The template for the merge request title, which can contain labels
   * @param bodyTemplate The template for the merge request body, which can contain labels
   * @param assigneeTemplates the templates for the merge request assignees, which can contain
   *     labels
   * @param credentialFileHandler the credentials used for Git authentication with GitLab, required
   *     to push to the target branch
   * @param sourceBranchTemplate the template for the source branch name, which can contain labels
   * @param targetBranch the target branch for the merge request
   * @param configFile the config file for the workflow
   * @param allowEmptyDiff whether to allow uploading even if the result is an empty diff
   * @param allowEmptyDiffMergeStatuses The merge statuses for which the destination will still
   *     upload a change despite an empty diff result
   * @param generalOptions the general options to be used by the destination object
   * @param gitOptions the git options to be used by the destination object
   * @param destinationOptions the git destination options to be used by the destination object
   * @param partialFetch whether to use partial fetch when fetching the baseline from the
   *     destination repo
   * @param integrates the collection of changes to be integrated into the code migration
   * @param checker the checker used on the migrated code
   */
  public record GitLabMrDestinationParams(
      GitLabApi gitLabApi,
      URI repoUrl,
      Optional<String> titleTemplate,
      Optional<String> bodyTemplate,
      ImmutableList<String> assigneeTemplates,
      CredentialFileHandler credentialFileHandler,
      Optional<String> sourceBranchTemplate,
      String targetBranch,
      ConfigFile configFile,
      boolean allowEmptyDiff,
      ImmutableSet<DetailedMergeStatus> allowEmptyDiffMergeStatuses,
      GeneralOptions generalOptions,
      GitOptions gitOptions,
      GitDestinationOptions destinationOptions,
      boolean partialFetch,
      Iterable<GitIntegrateChanges> integrates,
      Optional<Checker> checker) {

    /** A builder for {@link GitLabMrDestinationParams}. */
    @AutoBuilder
    public abstract static class Builder {

      /**
       * Sets the GitLab API client to be used by the destination and its {@link GitLabMrWriter}
       *
       * @param gitLabApi the GitLab API client
       * @return a reference to this builder
       */
      public abstract Builder setGitLabApi(GitLabApi gitLabApi);

      /**
       * Sets the URL of the GitLab repository to push to
       *
       * @param repoUrl the URL
       * @return a reference to this builder
       */
      public abstract Builder setRepoUrl(URI repoUrl);

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
       * Sets the credentials used for Git authentication with GitLab, required to push to the
       * target branch
       *
       * @param credentialFileHandler the credentials
       * @return a reference to this builder
       */
      public abstract Builder setCredentialFileHandler(CredentialFileHandler credentialFileHandler);

      /**
       * Sets the template for the source branch name, which can contain labels
       *
       * @param sourceBranchTemplate the source branch template
       * @return a reference to this builder
       */
      public abstract Builder setSourceBranchTemplate(Optional<String> sourceBranchTemplate);

      /**
       * Sets the target branch for the merge request
       *
       * @param targetBranch the target branch
       * @return a reference to this builder
       */
      public abstract Builder setTargetBranch(String targetBranch);

      /**
       * Sets the config file for the workflow
       *
       * @param configFile the config file
       * @return a reference to this builder
       */
      public abstract Builder setConfigFile(ConfigFile configFile);

      /**
       * Sets whether to allow uploading even if the result is an empty diff
       *
       * @param allowEmptyDiff whether to allow uploading even if the result is an empty diff
       * @return a reference to this builder
       */
      public abstract Builder setAllowEmptyDiff(boolean allowEmptyDiff);

      /**
       * Sets the merge statuses for which the destination will still upload a change despite an
       * empty diff result
       *
       * @param allowEmptyDiffMergeStatuses the merge statuses
       * @return a reference to this builder
       */
      public abstract Builder setAllowEmptyDiffMergeStatuses(
          ImmutableSet<DetailedMergeStatus> allowEmptyDiffMergeStatuses);

      /**
       * Sets the general options to be used by the destination object
       *
       * @param generalOptions the general options
       * @return a reference to this builder
       */
      public abstract Builder setGeneralOptions(GeneralOptions generalOptions);

      /**
       * Sets the git options to be used by the destination object
       *
       * @param gitOptions the git options
       * @return a reference to this builder
       */
      public abstract Builder setGitOptions(GitOptions gitOptions);

      /**
       * Sets the git destination options to be used by the destination object
       *
       * @param destinationOptions the git destination options
       * @return a reference to this builder
       */
      public abstract Builder setDestinationOptions(GitDestinationOptions destinationOptions);

      /**
       * Sets whether to use partial fetch when fetching the baseline from the destination repo
       *
       * @param partialFetch whether to use partial fetch
       * @return a reference to this builder
       */
      public abstract Builder setPartialFetch(boolean partialFetch);

      /**
       * Sets the collection of changes to be integrated into the code migration
       *
       * @param integrates the collection of changes
       * @return a reference to this builder
       */
      public abstract Builder setIntegrates(Iterable<GitIntegrateChanges> integrates);

      /**
       * Sets the checker used on the migrated code
       *
       * @param checker the checker
       * @return a reference to this builder
       */
      public abstract Builder setChecker(Optional<Checker> checker);

      /**
       * Builds the {@link GitLabMrDestinationParams} object
       *
       * @return the built object
       */
      public abstract GitLabMrDestinationParams build();
    }

    /**
     * Returns a builder for this param class.
     *
     * @return the builder
     */
    public static Builder builder() {
      return new AutoBuilder_GitLabMrDestination_GitLabMrDestinationParams_Builder()
          .setTitleTemplate(Optional.empty())
          .setBodyTemplate(Optional.empty())
          .setAssigneeTemplates(ImmutableList.of())
          .setSourceBranchTemplate(Optional.empty())
          .setAllowEmptyDiff(false)
          .setAllowEmptyDiffMergeStatuses(ImmutableSet.of())
          .setPartialFetch(false)
          .setIntegrates(ImmutableList.of())
          .setChecker(Optional.empty());
    }

    /**
     * Creates a new instance of {@link GitLabMrDestination} using the parameters from this object.
     */
    public GitLabMrDestination createDestination() {
      return new GitLabMrDestination(this);
    }
  }
}
