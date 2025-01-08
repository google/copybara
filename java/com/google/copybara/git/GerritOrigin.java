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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.copybara.exception.ValidationException.checkCondition;
import static com.google.copybara.git.gerritapi.IncludeResult.DETAILED_ACCOUNTS;
import static com.google.copybara.git.gerritapi.IncludeResult.DETAILED_LABELS;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.BaselinesWithoutLabelVisitor;
import com.google.copybara.Endpoint;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.Origin.Reader.ChangesResponse.EmptyReason;
import com.google.copybara.approval.ApprovalsProvider;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.checks.Checker;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.gerritapi.AccountInfo;
import com.google.copybara.git.gerritapi.ChangeInfo;
import com.google.copybara.git.gerritapi.GerritApi;
import com.google.copybara.git.gerritapi.GetChangeInput;
import com.google.copybara.revision.Change;
import com.google.copybara.transform.patch.PatchTransformation;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A {@link Origin} that can read Gerrit reviews TODO(malcon): Implement Reader/getChanges to detect
 * already migrated patchets
 */
public class GerritOrigin extends GitOrigin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GeneralOptions generalOptions;
  private final GitOptions gitOptions;
  private final GitOriginOptions gitOriginOptions;
  private final GerritOptions gerritOptions;
  private final SubmoduleStrategy submoduleStrategy;
  private final List<String> excludedSubmodules;
  private final boolean includeBranchCommitLogs;
  private final boolean partialFetch;
  @Nullable private final Checker endpointChecker;
  @Nullable private final PatchTransformation patchTransformation;
  @Nullable private final String branch;
  private final boolean ignoreGerritNoop;

  private GerritOrigin(
      GeneralOptions generalOptions,
      String repoUrl,
      @Nullable String configRef,
      GitOptions gitOptions,
      GitOriginOptions gitOriginOptions,
      GerritOptions gerritOptions,
      SubmoduleStrategy submoduleStrategy,
      List<String> excludedSubmodules,
      boolean includeBranchCommitLogs,
      boolean firstParent,
      boolean partialFetch,
      @Nullable Checker endpointChecker,
      @Nullable PatchTransformation patchTransformation,
      @Nullable String branch,
      boolean describeVersion,
      boolean ignoreGerritNoop,
      boolean primaryBranchMigrationMode,
      ApprovalsProvider approvalsProvider) {
    super(
        generalOptions,
        repoUrl,
        configRef,
        GitRepoType.GERRIT,
        gitOptions,
        gitOriginOptions,
        submoduleStrategy,
        excludedSubmodules,
        includeBranchCommitLogs,
        firstParent,
        partialFetch,
        patchTransformation,
        describeVersion,
        /* versionSelector= */ null,
        /* configPath= */ null,
        /* workflowName= */ null,
        primaryBranchMigrationMode,
        approvalsProvider,
        /* enableLfs= */ false,
        /* credentials= */ null);
    this.generalOptions = checkNotNull(generalOptions);
    this.gitOptions = checkNotNull(gitOptions);
    this.gitOriginOptions = checkNotNull(gitOriginOptions);
    this.gerritOptions = checkNotNull(gerritOptions);
    this.submoduleStrategy = checkNotNull(submoduleStrategy);
    this.excludedSubmodules = excludedSubmodules;
    this.includeBranchCommitLogs = includeBranchCommitLogs;
    this.endpointChecker = endpointChecker;
    this.patchTransformation = patchTransformation;
    this.branch = branch;
    this.partialFetch = partialFetch;
    this.ignoreGerritNoop = ignoreGerritNoop;
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(Glob originFiles) {
    ImmutableSetMultimap.Builder<String, String> builder = ImmutableSetMultimap.builder();
    builder.putAll(super.describe(originFiles));
    if (branch != null) {
      builder.put("branch", branch);
    }
    return builder.build();
  }

  @Override
  public GitRevision resolve(@Nullable String reference) throws RepoException, ValidationException {
    generalOptions.console().progress("Gerrit Origin: Initializing local repo");

    checkCondition(!Strings.isNullOrEmpty(reference), "Expecting a change number as reference");

    GerritChange change = GerritChange.resolve(getRepository(), repoUrl, reference,
        this.generalOptions);
    if (change == null) {
      GitRevision gitRevision =
          GitRepoType.GIT.resolveRef(
              getRepository(),
              repoUrl,
              reference,
              this.generalOptions,
              describeVersion,
              false,
              partialFetch,
              Optional.empty());
      return describeVersion ? getRepository().addDescribeVersion(gitRevision) : gitRevision;
    }
    GerritApi api = gerritOptions.newGerritApi(repoUrl);

    ChangeInfo response = api.getChange(Integer.toString(change.getChange()),
        new GetChangeInput(ImmutableSet.of(DETAILED_ACCOUNTS, DETAILED_LABELS)));

    if (branch != null && !branch.equals(response.getBranch())) {
      throw new EmptyChangeException(String.format(
          "Skipping import of change %s for branch %s. Only tracking changes for branch %s",
          change.getChange(), response.getBranch(), branch));
    }

    ImmutableMultimap.Builder<String, String> labels = ImmutableMultimap.builder();

    labels.put(GerritChange.GERRIT_CHANGE_BRANCH, response.getBranch());
    if (response.getTopic() != null) {
      labels.put(GerritChange.GERRIT_CHANGE_TOPIC, response.getTopic());
    }
    labels.put(GerritChange.GERRIT_COMPLETE_CHANGE_ID_LABEL, response.getId());
    for (Entry<String, List<AccountInfo>> e : response.getReviewers().entrySet()) {
      for (AccountInfo info : e.getValue()) {
        if (info.getEmail() != null) {
          labels.put("GERRIT_" + e.getKey() + "_EMAIL", info.getEmail());
        }
      }
    }

    if (response.getOwner().getEmail() != null) {
      labels.put(GerritChange.GERRIT_OWNER_EMAIL_LABEL, response.getOwner().getEmail());
    }
    try {
      GitRevision gitRevision = change.fetch(labels.build());
      return describeVersion ? getRepository().addDescribeVersion(gitRevision) : gitRevision;
    } catch (CannotResolveRevisionException unexpected) {
      // We got the change via the API so it is unexpected to fail now.
      throw new RepoException("Unable to fetch change content.", unexpected);
    }
  }

  /** Builds a new {@link GerritOrigin}. */
  static GerritOrigin newGerritOrigin(
      Options options,
      String url,
      SubmoduleStrategy submoduleStrategy,
      List<String> excludedSubmodules,
      boolean firstParent,
      boolean partialFetch,
      @Nullable Checker endpointChecker,
      @Nullable PatchTransformation patchTransformation,
      @Nullable String branch,
      boolean describeVersion,
      boolean ignoreGerritNoop,
      boolean primaryBranchMigrationMode,
      ApprovalsProvider approvalsProvider) {

    return new GerritOrigin(
        options.get(GeneralOptions.class),
        url,
        /* configRef= */ null,
        options.get(GitOptions.class),
        options.get(GitOriginOptions.class),
        options.get(GerritOptions.class),
        submoduleStrategy,
        excludedSubmodules,
        /* includeBranchCommitLogs= */ false,
        firstParent,
        partialFetch,
        endpointChecker,
        patchTransformation,
        branch,
        describeVersion,
        ignoreGerritNoop,
        primaryBranchMigrationMode,
        approvalsProvider);
  }

  @Override
  public Reader<GitRevision> newReader(Glob originFiles, Authoring authoring) {
    return new GitOrigin.ReaderImpl(
        repoUrl,
        originFiles,
        authoring,
        gitOptions,
        gitOriginOptions,
        generalOptions,
        includeBranchCommitLogs,
        submoduleStrategy,
        excludedSubmodules,
        firstParent,
        partialFetch,
        patchTransformation,
        describeVersion,
        /* configPath= */ null,
        /* workflowName= */ null,
        /* credentials= */ null) {

      @Override
      public ImmutableList<GitRevision> findBaselinesWithoutLabel(
          GitRevision startRevision, int limit) throws RepoException, ValidationException {

        // Skip the initial change as it might be the Gerrit review change
        BaselinesWithoutLabelVisitor<GitRevision> visitor =
            new BaselinesWithoutLabelVisitor<>(
                originFiles, limit, Optional.of(startRevision), /* skipFirst= */ false);
        visitChanges(startRevision, visitor);
        return visitor.getResult();
      }

      @Override
      public Endpoint getFeedbackEndPoint(Console console) throws ValidationException {
        gerritOptions.validateEndpointChecker(endpointChecker, repoUrl);
        return new GerritEndpoint(
            gerritOptions.newGerritApiSupplier(repoUrl, endpointChecker),
            repoUrl,
            console,
            // We disallow submitting to the origin, but this has feasible use cases and we can
            // revisit
            /*allowSubmitChange*/ false);
      }

      @Override
      public ChangesResponse<GitRevision> changes(@Nullable GitRevision fromRef, GitRevision toRef)
          throws RepoException, ValidationException {
        ChangesResponse<GitRevision> result = super.changes(fromRef, toRef);
        Change<GitRevision> change = change(toRef);
        if (!ignoreGerritNoop
            || change.getChangeFiles() == null
            || !toRef
                .associatedLabels()
                .containsKey(GerritChange.GERRIT_COMPLETE_CHANGE_ID_LABEL)) {
          return result;
        }
        PathMatcher pathMatcher = originFiles.relativeTo(Paths.get("/"));
        if (change.getChangeFiles().stream()
            .noneMatch(x -> pathMatcher.matches(Paths.get("/", x)))) {
          logger.atInfo().log("Skipping a Gerrit noop change with ref: %s", toRef.getSha1());
          return ChangesResponse.noChanges(EmptyReason.NO_CHANGES);
        }
        return result;
      }
    };
  }
}
