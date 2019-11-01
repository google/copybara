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
import static com.google.copybara.Origin.Reader.ChangesResponse.noChanges;
import static com.google.copybara.exception.ValidationException.checkCondition;
import static com.google.copybara.util.Glob.affectsRoots;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.copybara.Change;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.Origin.Reader.ChangesResponse.EmptyReason;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitRepository.Submodule;
import com.google.copybara.git.GitRepository.TreeElement;
import com.google.copybara.transform.patch.PatchTransformation;
import com.google.copybara.util.Glob;
import com.google.copybara.util.InsideGitDirException;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * A class for manipulating Git repositories
 */
public class GitOrigin implements Origin<GitRevision> {

  /**
   * A temporary ref used locally, for Git commands that need one (like rebase).
   */
  private static final String COPYBARA_TMP_REF = "refs/heads/copybara_dont_use_internal";

  enum SubmoduleStrategy {
    /** Don't download any submodule. */
    NO,
    /** Download just the first level of submodules, but don't download recursively */
    YES,
    /** Download all the submodules recursively */
    RECURSIVE
  }

  /**
   * Url of the repository
   */
  final String repoUrl;

  /**
   * Default reference to track
   */
  @Nullable
  private final String configRef;
  private final Console console;
  private final GeneralOptions generalOptions;
  private final GitRepoType repoType;
  private final GitOptions gitOptions;
  private final GitOriginOptions gitOriginOptions;
  private final SubmoduleStrategy submoduleStrategy;
  private final boolean includeBranchCommitLogs;
  boolean firstParent;
  @Nullable private final PatchTransformation patchTransformation;
  protected final boolean describeVersion;
  @Nullable private final LatestVersionSelector versionSelector;

  GitOrigin(GeneralOptions generalOptions, String repoUrl,
      @Nullable String configRef, GitRepoType repoType, GitOptions gitOptions,
      GitOriginOptions gitOriginOptions, SubmoduleStrategy submoduleStrategy,
      boolean includeBranchCommitLogs, boolean firstParent,
      @Nullable PatchTransformation patchTransformation, boolean describeVersion,
      @Nullable LatestVersionSelector versionSelector) {
    this.generalOptions = generalOptions;
    this.console = generalOptions.console();
    // Remove a possible trailing '/' so that the url is normalized.
    this.repoUrl = checkNotNull(repoUrl).endsWith("/")
        ? repoUrl.substring(0, repoUrl.length() - 1)
        : repoUrl;
    this.configRef = configRef;
    this.repoType = checkNotNull(repoType);
    this.gitOptions = checkNotNull(gitOptions);
    this.gitOriginOptions = checkNotNull(gitOriginOptions);
    this.submoduleStrategy = submoduleStrategy;
    this.includeBranchCommitLogs = includeBranchCommitLogs;
    this.firstParent = firstParent;
    this.patchTransformation = patchTransformation;
    this.describeVersion = describeVersion;
    this.versionSelector = versionSelector;
  }

  @VisibleForTesting
  public GitRepository getRepository() throws RepoException {
    return gitOptions.cachedBareRepoForUrl(repoUrl);
  }

  @Override
  public Reader<GitRevision> newReader(Glob originFiles, Authoring authoring) {
    return new ReaderImpl(repoUrl, originFiles, authoring,
        gitOptions, gitOriginOptions, generalOptions, includeBranchCommitLogs, submoduleStrategy,
        firstParent, patchTransformation, describeVersion);
  }

  @Override
  public GitRevision resolve(@Nullable String reference)
      throws RepoException, ValidationException {
    console.progress("Git Origin: Initializing local repo");
    String ref;
    if (gitOriginOptions.useGitVersionSelector() && versionSelector != null) {
      ref = versionSelector.selectVersion(reference, getRepository(), repoUrl, console);
      checkCondition(ref != null, "Cannot find any matching version for latest_version");
    } else if (Strings.isNullOrEmpty(reference)) {
      checkCondition(configRef != null, "No reference was passed as a command line argument for"
              + " %s and no default reference was configured in the config file", repoUrl);
      ref = configRef;
    } else {
      ref = reference;
    }

    GitRevision gitRevision = repoType.resolveRef(getRepository(), repoUrl, ref, generalOptions,
        describeVersion);
    return describeVersion ? getRepository().addDescribeVersion(gitRevision) : gitRevision;
  }

  @Override
  @Nullable
  public String showDiff(GitRevision revisionFrom, GitRevision revisionTo) throws RepoException {
    return getRepository().showDiff(revisionFrom.getSha1(), revisionTo.getSha1());
  }

  static class ReaderImpl implements Reader<GitRevision> {

    private final String repoUrl;
    final Glob originFiles;
    final Authoring authoring;
    private final GitOptions gitOptions;
    private final GitOriginOptions gitOriginOptions;
    private final GeneralOptions generalOptions;
    private final boolean includeBranchCommitLogs;
    private final SubmoduleStrategy submoduleStrategy;
    private final boolean firstParent;
    @Nullable private final PatchTransformation patchTransformation;
    private final boolean describeVersion;

    ReaderImpl(String repoUrl, Glob originFiles, Authoring authoring,
        GitOptions gitOptions,
        GitOriginOptions gitOriginOptions,
        GeneralOptions generalOptions,
        boolean includeBranchCommitLogs,
        SubmoduleStrategy submoduleStrategy,
        boolean firstParent,
        @Nullable PatchTransformation patchTransformation,
        boolean describeVersion) {
      this.repoUrl = checkNotNull(repoUrl);
      this.originFiles = checkNotNull(originFiles, "originFiles");
      this.authoring = checkNotNull(authoring, "authoring");
      this.gitOptions = checkNotNull(gitOptions);
      this.gitOriginOptions = gitOriginOptions;
      this.generalOptions = checkNotNull(generalOptions);
      this.includeBranchCommitLogs = includeBranchCommitLogs;
      this.submoduleStrategy = checkNotNull(submoduleStrategy);
      this.firstParent = firstParent;
      this.patchTransformation = patchTransformation;
      this.describeVersion = describeVersion;
    }

    private ChangeReader.Builder changeReaderBuilder(String repoUrl) throws RepoException {
      return ChangeReader.Builder.forOrigin(authoring, getRepository(), generalOptions.console())
          .setVerbose(generalOptions.isVerbose())
          .setIncludeBranchCommitLogs(includeBranchCommitLogs)
          .setRoots(originFiles.roots())
          .setUrl(repoUrl);
    }

    protected GitRepository getRepository() throws RepoException {
      return gitOptions.cachedBareRepoForUrl(repoUrl);
    }

    /**
     * Creates a worktree with the contents of the git reference
     *
     * <p>Any content in the workdir is removed/overwritten.
     */
    @Override
    public void checkout(GitRevision ref, Path workdir) throws RepoException, ValidationException {
      checkoutRepo(getRepository(), repoUrl, workdir, submoduleStrategy, ref,
          /*topLevelCheckout=*/true);
      gitOriginOptions.maybeRunCheckoutHook(workdir, generalOptions);
      if (patchTransformation != null) {
        generalOptions.console().progress("Patching the checkout directory");
        try {
          patchTransformation.patch(generalOptions.console(), workdir, getRepository().getGitDir());
        } catch (InsideGitDirException e) {
          throw new IllegalStateException(
              "This shouldn't happen. Patching always happens inside a git directory here", e);
        }
      }
    }
    
    /**
     * Checks out the repository, and rebases to a ref if necessary.
     *
     * <p>If {@code rebaseToRef != null}, then the repo will be rebased to the given ref.
     *
     * <p>In the case of submodules, {@code rebaseToRef} is always null, because rebasing on the
     * submodule repo doesn't apply.
     */
    void checkoutRepo(GitRepository repository, String currentRemoteUrl, Path workdir,
        SubmoduleStrategy submoduleStrategy, GitRevision ref, boolean topLevelCheckout)
        throws RepoException, ValidationException {
      // TODO(malcon): Remove includeBranchCommitLogs from the code after 2017-12-31
      if (includeBranchCommitLogs) {
        generalOptions.console().warnFmt("'include_branch_commit_logs' is deprecated. Use"
            + " first_parent = False instead. metadata.squash_notes and metadata.use_last_change"
            + " don't include merge commits by default");
      }
      GitRepository repo = checkout(repository, workdir, ref);
      if (topLevelCheckout) {
        maybeRebase(repo, ref, workdir);
      }

      if (submoduleStrategy == SubmoduleStrategy.NO) {
        return;
      }
      for (Submodule submodule : repo.listSubmodules(currentRemoteUrl)) {
        ImmutableList<TreeElement> elements = repo.lsTree(ref, submodule.getPath());
        if (elements.size() != 1) {
          throw new RepoException(String
              .format("Cannot find one tree element for submodule %s."
                  + " Found the following elements: %s", submodule.getPath(), elements));
        }
        TreeElement element = Iterables.getOnlyElement(elements);
        Preconditions.checkArgument(element.getPath().equals(submodule.getPath()));

        generalOptions.console()
            .verboseFmt(
                "Checking out submodule '%s' with reference '%s'", submodule, element.getRef());

        GitRepository subRepo = gitOptions.cachedBareRepoForUrl(submodule.getUrl());

        // TODO(danielromero): Remove temporary feature after 2019-10-30
        if (generalOptions.isTemporaryFeature("SUBMODULES_FETCH_ALL", true)) {
          if (submodule.getBranch() != null) {
            subRepo.fetchSingleRef(submodule.getUrl(), submodule.getBranch());
          } else {
            subRepo.fetch(
                submodule.getUrl(), /*prune*/
                true, /*force*/
                true,
                ImmutableList.of("refs/heads/*:refs/heads/*", "refs/tags/*:refs/tags/*"));
          }
        } else {
          subRepo.fetchSingleRef(submodule.getUrl(), submodule.getBranch());
        }
        GitRevision submoduleRef =
            subRepo.resolveReferenceWithContext(
                element.getRef(), submodule.getName(), submodule.getUrl());

        Path subdir = workdir.resolve(submodule.getPath());
        try {
          Files.createDirectories(workdir.resolve(submodule.getPath()));
        } catch (IOException e) {
          throw new RepoException(String.format(
              "Cannot create subdirectory %s for submodule: %s", subdir, submodule));
        }

        checkoutRepo(subRepo, submodule.getUrl(), subdir,
            submoduleStrategy == SubmoduleStrategy.RECURSIVE
                ? SubmoduleStrategy.RECURSIVE
                : SubmoduleStrategy.NO, submoduleRef, /*topLevelCheckout*/ false);
      }
    }

    private GitRepository checkout(GitRepository repository, Path workdir, GitRevision ref)
        throws RepoException {
      GitRepository repo = repository.withWorkTree(workdir);
      repo.forceCheckout(ref.getSha1());
      return repo;
    }

    protected void maybeRebase(GitRepository repo, GitRevision ref, Path workdir)
        throws RepoException, ValidationException {
      String rebaseToRef = gitOriginOptions.originRebaseRef;
      if (rebaseToRef == null) {
        return;
      }
      generalOptions.console().info(String.format("Rebasing %s to %s", rebaseToRef, rebaseToRef));
      GitRevision rebaseRev = repo.fetchSingleRef(repoUrl, rebaseToRef);
      repo.simpleCommand("update-ref", COPYBARA_TMP_REF, rebaseRev.getSha1());
      repo.rebase(COPYBARA_TMP_REF);
    }

    @Override
    public ChangesResponse<GitRevision> changes(@Nullable GitRevision fromRef, GitRevision toRef)
        throws RepoException {

      String refRange = fromRef == null
          ? toRef.getSha1()
          : fromRef.getSha1() + ".." + toRef.getSha1();
      ChangeReader changeReader = changeReaderBuilder(repoUrl)
          .setFirstParent(firstParent)
          .build();
      ImmutableList<Change<GitRevision>> gitChanges = changeReader.run(refRange);
      if (!gitChanges.isEmpty()) {
        if (!describeVersion) {
          return ChangesResponse.forChangesWithMerges(gitChanges);
        }
        return ChangesResponse.forChangesWithMerges(gitChanges);
      }
      if (fromRef == null) {
        return noChanges(EmptyReason.NO_CHANGES);
      }
      if (fromRef.getSha1().equals(toRef.getSha1())
          || getRepository().isAncestor(toRef.getSha1(), fromRef.getSha1())) {
        return noChanges(EmptyReason.TO_IS_ANCESTOR);
      }
      if (getRepository().isAncestor(fromRef.getSha1(), toRef.getSha1())) {
        return noChanges(EmptyReason.NO_CHANGES);
      }
      return noChanges(EmptyReason.UNRELATED_REVISIONS);
    }

    @Override
    public Change<GitRevision> change(GitRevision ref) throws RepoException, EmptyChangeException {
      // The limit=1 flag guarantees that only one change is returned
      ChangeReader changeReader = changeReaderBuilder(repoUrl)
          .setLimit(1)
          .setFirstParent(firstParent)
          .build();
      ImmutableList<Change<GitRevision>> changes = changeReader.run(ref.getSha1());

      if (changes.isEmpty()) {
        throw new EmptyChangeException(
            String.format(
                "'%s' revision cannot be found in the origin or it didn't affect the origin paths.",
                ref.asString()));
      }
      // 'git log -1 -m' for a merge commit returns two entries :(
      Change<GitRevision> rev = changes.get(0);

      // Keep the original revision since it might have context information like code review
      // info. The difference with changes method is that here we know exactly what we've
      // requested (One SHA-1 revision) while in the other we get a result for a range. That
      // means that extensions of GitOrigin need to implement changes if they want to provide
      // additional information.
      return new Change<>(ref, rev.getAuthor(), rev.getMessage(), rev.getDateTime(),
          rev.getLabels(), rev.getChangeFiles(), rev.isMerge(), rev.getParents());
    }

    /**
     * Visit changes using git --skip and -n for pagination.
     *
     * <p>We only visit files in the roots. The reason is that there can be different project
     * imports from the same git repository. Using origin_files glob directly would be more
     * accurate, but that doesn't work well with changes on origin_files over time. Roots OTOH is
     * a good compromise.
     */
    @Override
    public void visitChanges(GitRevision start, ChangesVisitor visitor)
        throws RepoException, CannotResolveRevisionException {
      ChangeReader.Builder queryChanges = changeReaderBuilder(repoUrl).setFirstParent(firstParent);
      ImmutableSet<String> roots = originFiles.roots();

      GitVisitorUtil.visitChanges(
          start, input -> affectsRoots(roots, input.getChangeFiles())
              ? visitor.visit(input)
              : VisitResult.CONTINUE,
          queryChanges, generalOptions, "origin", gitOptions.visitChangePageSize);
    }
  }

  @Override
  public String getLabelName() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("repoUrl", repoUrl)
        .add("ref", configRef)
        .add("repoType", repoType)
        .toString();
  }

  /**
   * Builds a new {@link GitOrigin}.
   */
  static GitOrigin newGitOrigin(Options options, String url, String ref, GitRepoType type,
      SubmoduleStrategy submoduleStrategy, boolean includeBranchCommitLogs, boolean firstParent,
      @Nullable PatchTransformation patchTransformation, boolean describeVersion,
      @Nullable LatestVersionSelector versionSelector) {
    return new GitOrigin(
        options.get(GeneralOptions.class),
        url, ref, type, options.get(GitOptions.class), options.get(GitOriginOptions.class),
        submoduleStrategy, includeBranchCommitLogs, firstParent, patchTransformation,
        describeVersion, versionSelector);
  }

  @Override
  public String getType() {
    return "git.origin";
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(Glob originFiles) {
    ImmutableSetMultimap.Builder<String, String> builder =
        new ImmutableSetMultimap.Builder<String, String>()
            .put("type", getType())
            .put("repoType", repoType.name())
            .put("url", repoUrl)
            .put("submodules", submoduleStrategy.name());
    if (configRef != null) {
      builder.put("ref", configRef);
    }
    if (versionSelector != null) {
      builder.put("refspec", versionSelector.asGitRefspec());
    }
    return builder.build();
  }
}
