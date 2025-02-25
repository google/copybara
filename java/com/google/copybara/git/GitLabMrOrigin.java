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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.copybara.BaselinesWithoutLabelVisitor;
import com.google.copybara.Endpoint;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Origin;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitOrigin.ReaderImpl;
import com.google.copybara.git.GitOrigin.SubmoduleStrategy;
import com.google.copybara.git.gitlab.api.GitLabApi;
import com.google.copybara.git.gitlab.api.entities.MergeRequest;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.transform.patch.PatchTransformation;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * An {@link Origin} that reads {@link GitRevision}s from Merge Requests of a given GitLab Project.
 */
public class GitLabMrOrigin implements Origin<GitRevision> {
  protected static final String GITLAB_BASE_BRANCH_REF = "GITLAB_BASE_BRANCH_REF";
  private final GitLabApi gitLabApi;
  private final Console console;
  private final URI repoUrl;
  private final GitOptions gitOptions;
  private final GitOriginOptions gitOriginOptions;
  private final GeneralOptions generalOptions;
  private final Optional<CredentialFileHandler> credentialFileHandler;
  private final SubmoduleStrategy submoduleStrategy;
  private final ImmutableList<String> excludedSubmodules;
  private final Optional<PatchTransformation> patchTransformation;
  private final boolean partialFetch;
  private final boolean describeVersion;
  private final boolean firstParent;
  private final boolean useMergeCommit;

  private GitLabMrOrigin(Builder builder) {
    gitLabApi = checkNotNull(builder.gitLabApi);
    console = checkNotNull(builder.console);
    repoUrl = checkNotNull(builder.repoUrl);
    gitOptions = checkNotNull(builder.gitOptions);
    gitOriginOptions = checkNotNull(builder.gitOriginOptions);
    generalOptions = checkNotNull(builder.generalOptions);
    credentialFileHandler = checkNotNull(builder.credentialFileHandler);
    submoduleStrategy = checkNotNull(builder.submoduleStrategy);
    excludedSubmodules = checkNotNull(builder.excludedSubmodules);
    patchTransformation = checkNotNull(builder.patchTransformation);
    partialFetch = builder.partialFetch;
    describeVersion = builder.describeVersion;
    firstParent = builder.firstParent;
    useMergeCommit = builder.useMergeCommit;
  }

  public static Builder builder() {
    return new GitLabMrOrigin.Builder();
  }

  @Override
  public GitRevision resolve(String reference) throws RepoException, ValidationException {
    ValidationException.checkCondition(
        reference != null,
        """
        A merge request reference is expected as argument in the command line.
        Example:
           copybara path/to/copy.bara.sky workflow_name merge_request_number
        """);

    // TODO: b/393384198 - Ensure we don't handle closed merge requests, and have a way to filter
    // MRs with missing approvals, etc.
    console.progressFmt("Parsing Merge Request reference %s at %s", reference, repoUrl.toString());
    int mergeRequestId = parseReference(reference);

    String urlEncodedProjectPath = getUrlEncodedProjectPath(repoUrl);
    console.progressFmt("Resolving numeric Project ID for %s", urlEncodedProjectPath);
    int projectId =
        gitLabApi
            .getProject(urlEncodedProjectPath)
            .orElseThrow(
                () ->
                    new ValidationException(
                        String.format(
                            "Could not find Project %s in %s.", urlEncodedProjectPath, repoUrl)))
            .getId();

    console.progressFmt("Resolving Merge Request %s for Project id %s", mergeRequestId, projectId);
    MergeRequest mergeRequest =
        gitLabApi
            .getMergeRequest(projectId, mergeRequestId)
            .orElseThrow(
                () ->
                    new RepoException(
                        String.format(
                            "Could not get Merge Request info for ID %s.", mergeRequestId)));
    console.progressFmt("Fetching Merge Request %s from origin %s", mergeRequest.getIid(), repoUrl);
    return getRevisionForMr(mergeRequest);
  }

  private static int parseReference(String reference) throws ValidationException {
    // For now, we just support the numeric ID as a reference. If we realize that we need to support
    // SHA1s or a full ref path, we should change the below.
    try {
      return Integer.parseInt(reference);
    } catch (NumberFormatException e) {
      throw new ValidationException(
          String.format(
              "The merge request reference %s is not a valid numeric identifier.", reference),
          e);
    }
  }

  private GitRevision getRevisionForMr(MergeRequest mergeRequest)
      throws RepoException, ValidationException {
    // GitLab produces a merge commit for us, which is the merge result of the MR head and the
    // target branch.
    // If the user wants to use this merge commit, use the appropriate ref.
    String refToUse =
        useMergeCommit ? getMrMergeFullRef(mergeRequest) : getMrHeadFullRef(mergeRequest);
    ImmutableList.Builder<String> refspecs = ImmutableList.builder();
    refspecs.add(refToUse + ":" + refToUse);
    // Fetch the source ref as well, which will allow the revision reader to find the baseline later
    // using git merge, if needed.
    refspecs.add(
        "refs/heads/" + mergeRequest.getSourceBranch() + ":" + getMrBaseLocalFullRef(mergeRequest));
    GitRepository repository = getRepository();
    try (ProfilerTask ignore = generalOptions.profiler().start("fetch")) {
      repository.fetch(
          repoUrl.toString(),
          /* prune= */ false,
          generalOptions.isForced(),
          refspecs.build(),
          partialFetch,
          Optional.empty(),
          /* tags= */ false);
    }

    return repository.resolveReference(refToUse).withLabels(generateLabels(mergeRequest));
  }

  private ImmutableListMultimap<String, String> generateLabels(MergeRequest mergeRequest) {
    ImmutableListMultimap.Builder<String, String> labels = ImmutableListMultimap.builder();
    return labels.put(GITLAB_BASE_BRANCH_REF, getMrBaseLocalFullRef(mergeRequest)).build();
  }

  private String getMrHeadFullRef(MergeRequest mergeRequest) {
    return "refs/merge-requests/" + mergeRequest.getIid() + "/head";
  }

  private String getMrMergeFullRef(MergeRequest mergeRequest) {
    return "refs/merge-requests/" + mergeRequest.getIid() + "/merge";
  }

  private String getMrBaseLocalFullRef(MergeRequest mergeRequest) {
    return "refs/merge-requests/" + mergeRequest.getIid() + "/base";
  }

  private GitRepository getRepository() throws RepoException {
    GitRepository repo = gitOptions.cachedBareRepoForUrl(repoUrl.toString());

    if (credentialFileHandler.isEmpty()) {
      console.info("No credentials provided.");
      return repo;
    }

    try {
      credentialFileHandler.get().install(repo, gitOptions.getConfigCredsFile(generalOptions));
    } catch (IOException e) {
      throw new RepoException("Unable to store credentials.", e);
    }
    return repo;
  }

  @VisibleForTesting
  public static String getUrlEncodedProjectPath(URI repoUrl) {
    String path = repoUrl.getPath().trim().toLowerCase(Locale.ROOT);
    // Remove any leading '/'
    while (path.startsWith("/")) {
      path = path.substring(1);
    }
    // Remove any trailing .git
    if (path.endsWith(".git")) {
      path = path.substring(0, path.length() - ".git".length());
    }
    return URLEncoder.encode(path, UTF_8);
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(@Nullable Glob originFiles) {
    ImmutableSetMultimap.Builder<String, String> options = ImmutableSetMultimap.builder();
    options
        .putAll(Origin.super.describe(originFiles))
        .put("url", repoUrl.toString())
        .put("submoduleStrategy", submoduleStrategy.toString())
        .put("excludedSubmodules", excludedSubmodules.toString())
        .put("firstParent", Boolean.toString(firstParent))
        .put("partialFetch", Boolean.toString(partialFetch))
        .put("describeVersion", Boolean.toString(describeVersion))
        .put("useMergeCommit", Boolean.toString(useMergeCommit));
    if (!originFiles.roots().isEmpty() && !originFiles.roots().contains("")) {
      options.putAll("root", originFiles.roots());
    }

    return options.build();
  }

  @Override
  public ImmutableList<ImmutableSetMultimap<String, String>> describeCredentials() {
    return credentialFileHandler
        .map(CredentialFileHandler::describeCredentials)
        .orElse(ImmutableList.of());
  }

  @Override
  public Reader<GitRevision> newReader(Glob originFiles, Authoring authoring)
      throws ValidationException {
    return new ReaderImpl(
        repoUrl.toString(),
        originFiles,
        authoring,
        gitOptions,
        gitOriginOptions,
        generalOptions,
        false,
        submoduleStrategy,
        excludedSubmodules,
        firstParent,
        partialFetch,
        patchTransformation.orElse(null),
        describeVersion,
        null,
        null,
        credentialFileHandler.orElse(null)) {
      @Override
      protected void maybeRebase(GitRepository repo, GitRevision ref, Path workdir) {
        // Disable rebase, as this is controlled by useMergeCommit field (GitLab does this for us
        // automatically with the merge commit).
      }

      @Override
      public Optional<Baseline<GitRevision>> findBaseline(GitRevision startRevision, String label)
          throws RepoException, ValidationException {
        return super.findBaseline(startRevision, label);
      }

      @Override
      public ImmutableList<GitRevision> findBaselinesWithoutLabel(
          GitRevision startRevision, int limit) throws RepoException, ValidationException {
        GitRepository repository = getRepository();
        // Contrary to the name of this function, we have to look at the labels of the revision
        // in order to get the base branch ref. This is because the ref at which it is stored is
        // generated based on the merge request number, and that context isn't preserved in a
        // GitRevision object, hence, the use of a label to hold this information.
        String baseBranchRef =
            Iterables.getLast(startRevision.associatedLabel(GITLAB_BASE_BRANCH_REF), null);
        checkNotNull(
            baseBranchRef,
            "%s label should be present in %s.",
            GITLAB_BASE_BRANCH_REF,
            startRevision);

        String mergeBase =
            repository.mergeBase(
                startRevision.getSha1(), repository.resolveReference(baseBranchRef).getSha1());
        GitRevision baseline = repository.resolveReference(mergeBase);
        BaselinesWithoutLabelVisitor<GitRevision> visitor =
            new BaselinesWithoutLabelVisitor<>(originFiles, limit, Optional.empty(), false);
        visitChanges(baseline, visitor);
        return visitor.getResult();
      }

      @Override
      public Endpoint getFeedbackEndPoint(Console console) throws ValidationException {
        // TODO - b/393561422: Implement feedback endpoint for GitLab MR Origin.
        return super.getFeedbackEndPoint(console);
      }
    };
  }

  @Override
  public String getLabelName() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  /** A builder class for {@code GitLabMrOrigin}. */
  public static final class Builder {

    private GitLabApi gitLabApi;
    private Console console;
    private URI repoUrl;
    private GitOptions gitOptions;
    private GitOriginOptions gitOriginOptions;
    private GeneralOptions generalOptions;
    private Optional<CredentialFileHandler> credentialFileHandler = Optional.empty();
    private SubmoduleStrategy submoduleStrategy;
    private ImmutableList<String> excludedSubmodules = ImmutableList.of();
    private Optional<PatchTransformation> patchTransformation = Optional.empty();
    private boolean partialFetch;
    private boolean describeVersion;
    private boolean firstParent;
    private boolean useMergeCommit;

    /** Returns a Builder for {@code GitLabMrOrigin}. */
    public Builder() {}

    /**
     * Sets the {@link GitLabApi} to be used for constructing the origin.
     *
     * @param val the {@link GitLabApi}, to be used for communicating with the GitLab instance's API
     * @return a reference to this Builder
     */
    @CanIgnoreReturnValue
    public Builder setGitLabApi(GitLabApi val) {
      gitLabApi = val;
      return this;
    }

    /**
     * Sets the {@link Console} to be used for constructing the origin.
     *
     * @param val the {@link Console} to set, to be used for logging
     * @return a reference to this Builder
     */
    @CanIgnoreReturnValue
    public Builder setConsole(Console val) {
      console = val;
      return this;
    }

    /**
     * Sets the repo URL to be used for constructing the origin.
     *
     * @param val the {@link URI} to use as the GitLab repo URL
     * @return a reference to this Builder
     */
    @CanIgnoreReturnValue
    public Builder setRepoUrl(URI val) {
      repoUrl = val;
      return this;
    }

    /**
     * Sets the {@link GitOptions} to be used for constructing the origin.
     *
     * @param val the {@link GitOptions}, to be used to configure fetch and checkout operations
     * @return a reference to this Builder
     */
    @CanIgnoreReturnValue
    public Builder setGitOptions(GitOptions val) {
      gitOptions = val;
      return this;
    }

    /**
     * Sets the {@link GitOriginOptions} to be used for constructing the origin.
     *
     * @param val the {@link GitOriginOptions}, to be used to configure fetch and checkout
     *     operations
     * @return a reference to this Builder
     */
    @CanIgnoreReturnValue
    public Builder setGitOriginOptions(GitOriginOptions val) {
      gitOriginOptions = val;
      return this;
    }

    /**
     * Sets the {@link GeneralOptions} to be used for constructing the origin.
     *
     * @param val the {@link GeneralOptions} to be used to configure the origin's behavior
     * @return a reference to this Builder
     */
    @CanIgnoreReturnValue
    public Builder setGeneralOptions(GeneralOptions val) {
      generalOptions = val;
      return this;
    }

    /**
     * Sets the {@link CredentialFileHandler} to be used in the origin, when authenticating with
     * Git.
     *
     * @param val the {@link CredentialFileHandler}, to be used for supplying Git with credentials
     * @return a reference to this Builder
     */
    @CanIgnoreReturnValue
    public Builder setCredentialFileHandler(Optional<CredentialFileHandler> val) {
      credentialFileHandler = val;
      return this;
    }

    /**
     * Sets the {@link SubmoduleStrategy} to be used for constructing the origin.
     *
     * @param val the {@link SubmoduleStrategy}, which determines how Git submodules are handled
     * @return a reference to this Builder
     */
    @CanIgnoreReturnValue
    public Builder setSubmoduleStrategy(SubmoduleStrategy val) {
      submoduleStrategy = val;
      return this;
    }

    /**
     * Sets the submodules to exclude in the origin.
     *
     * @param val the list of submodules. This should be a list of submodule names, not paths.
     * @return a reference to this Builder
     */
    @CanIgnoreReturnValue
    public Builder setExcludedSubmodules(ImmutableList<String> val) {
      excludedSubmodules = val;
      return this;
    }

    /**
     * Sets the {@link PatchTransformation} to apply when checking out changes.
     *
     * @param val the {@link PatchTransformation} to apply to the obtained changes on checkout
     * @return a reference to this Builder
     */
    @CanIgnoreReturnValue
    public Builder setPatchTransformation(PatchTransformation val) {
      checkNotNull(val);
      patchTransformation = Optional.of(val);
      return this;
    }

    /**
     * Sets whether this origin should use partial fetch, which limits the checkout to only the
     * files affected by the origin files glob.
     *
     * @param val whether to use partial fetch
     * @return a reference to this Builder
     */
    @CanIgnoreReturnValue
    public Builder setPartialFetch(boolean val) {
      partialFetch = val;
      return this;
    }

    /**
     * Sets whether to run {@code git describe} on {@link GitRevision} objects returned from this
     * origin.
     *
     * @param val whether to run {@code git describe} on revisions
     * @return a reference to this Builder
     */
    @CanIgnoreReturnValue
    public Builder setDescribeVersion(boolean val) {
      describeVersion = val;
      return this;
    }

    /**
     * Sets whether to use the first parent when this origin's {@link
     * com.google.copybara.Origin.Reader} is looking for changes.
     *
     * @param val whether to use the first parent
     * @return a reference to this Builder
     */
    @CanIgnoreReturnValue
    public Builder setFirstParent(boolean val) {
      firstParent = val;
      return this;
    }

    /**
     * Sets whether to use the merge commit instead of the head commit when checking out a merge
     * request.
     *
     * @param val whether to use the merge commit
     * @return a reference to this Builder
     */
    @CanIgnoreReturnValue
    public Builder setUseMergeCommit(boolean val) {
      useMergeCommit = val;
      return this;
    }

    /**
     * Returns a {@link GitLabMrOrigin} built from the parameters previously set.
     *
     * @return the {@link GitLabMrOrigin}
     */
    public GitLabMrOrigin build() {
      return new GitLabMrOrigin(this);
    }
  }
}
