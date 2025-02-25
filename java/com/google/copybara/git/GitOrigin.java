/*
 * Copyright (C) 2016 Google LLC
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
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.copybara.Origin.Reader.ChangesResponse.noChanges;
import static com.google.copybara.exception.ValidationException.checkCondition;
import static com.google.copybara.git.GitModule.PRIMARY_BRANCHES;
import static com.google.copybara.util.Glob.affectsRoots;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.Origin.Reader.ChangesResponse.EmptyReason;
import com.google.copybara.approval.ApprovalsProvider;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.git.GitRepository.Submodule;
import com.google.copybara.git.GitRepository.TreeElement;
import com.google.copybara.git.version.RefspecVersionList;
import com.google.copybara.revision.Change;
import com.google.copybara.templatetoken.Token;
import com.google.copybara.templatetoken.Token.TokenType;
import com.google.copybara.transform.patch.PatchTransformation;
import com.google.copybara.util.Glob;
import com.google.copybara.util.InsideGitDirException;
import com.google.copybara.util.console.Console;
import com.google.copybara.version.VersionSelector;
import com.google.copybara.version.VersionSelector.SearchPattern;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A class for manipulating Git repositories
 */
public class GitOrigin implements Origin<GitRevision> {

  /**
   * A temporary ref used locally, for Git commands that need one (like rebase).
   */
  private static final String COPYBARA_TMP_REF = "refs/heads/copybara_dont_use_internal";

  private static final ImmutableSet<String> REF_PREFIXES =
      ImmutableSet.of("refs/heads/", "refs/tags/");

  /** How downloading submodules should be handled by Git origins. */
  public enum SubmoduleStrategy {
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

  private String resolvedRef = null;

  @Nullable
  private final String configRef;
  private final Console console;
  private final GeneralOptions generalOptions;
  private final GitRepoType repoType;
  private final GitOptions gitOptions;
  private final GitOriginOptions gitOriginOptions;
  private final SubmoduleStrategy submoduleStrategy;
  private final List<String> excludedSubmodules;
  private final boolean includeBranchCommitLogs;
  boolean firstParent;
  private final boolean partialFetch;
  @Nullable
  private final PatchTransformation patchTransformation;
  protected final boolean describeVersion;
  @Nullable
  private final VersionSelector versionSelector;
  @Nullable
  private final String configPath;
  @Nullable
  private final String workflowName;
  protected final boolean primaryBranchMigrationMode;
  private final ApprovalsProvider approvalsProvider;
  private final boolean enableLfs;
  @Nullable private final CredentialFileHandler credentials;

  GitOrigin(
      GeneralOptions generalOptions,
      String repoUrl,
      @Nullable String configRef,
      GitRepoType repoType,
      GitOptions gitOptions,
      GitOriginOptions gitOriginOptions,
      SubmoduleStrategy submoduleStrategy,
      List<String> excludedSubmodules,
      boolean includeBranchCommitLogs,
      boolean firstParent,
      boolean partialClone,
      @Nullable PatchTransformation patchTransformation,
      boolean describeVersion,
      @Nullable VersionSelector versionSelector,
      @Nullable String configPath,
      @Nullable String workflowName,
      boolean primaryBranchMigrationMode,
      ApprovalsProvider approvalsProvider,
      boolean enableLfs,
      @Nullable CredentialFileHandler credentials) {
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
    this.excludedSubmodules = excludedSubmodules;
    this.includeBranchCommitLogs = includeBranchCommitLogs;
    this.firstParent = firstParent;
    this.partialFetch = partialClone;
    this.patchTransformation = patchTransformation;
    this.describeVersion = describeVersion;
    this.versionSelector = versionSelector;
    this.configPath = configPath;
    this.workflowName = workflowName;
    this.primaryBranchMigrationMode = primaryBranchMigrationMode;
    this.approvalsProvider = approvalsProvider;
    this.enableLfs = enableLfs;
    this.credentials = credentials;
  }

  @VisibleForTesting
  public GitRepository getRepository() throws RepoException {
    GitRepository repo;
    if (partialFetch) {
      String prefixedRepoUrl = String.format("%s:%s%s", configPath, workflowName, repoUrl);
      repo = gitOptions.cachedBareRepoForUrl(prefixedRepoUrl).enablePartialFetch();
    } else {
      repo = gitOptions.cachedBareRepoForUrl(repoUrl);
    }
    if (enableLfs) {
      repo.setRemoteOriginUrl(repoUrl);
    }
    if (credentials != null) {
      try {
        credentials.install(repo, gitOptions.getConfigCredsFile(generalOptions));
      } catch (IOException e) {
        throw new RepoException("Unable to store credentials", e);
      }
    }
    return repo;
  }

  @Override
  public ApprovalsProvider getApprovalsProvider() {
    return approvalsProvider;
  }

  @Override
  public Reader<GitRevision> newReader(Glob originFiles, Authoring authoring) {
    return new ReaderImpl(
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
        configPath,
        workflowName,
        credentials);
  }

  @Override
  public GitRevision resolve(@Nullable String reference)
      throws RepoException, ValidationException {
    console.progress("Git Origin: Initializing local repo");
    String ref;
    boolean canUseResolverOnCliRef =
        this.generalOptions.isVersionSelectorUseCliRef() || this.generalOptions.isForced();

    if (gitOriginOptions.useGitVersionSelector() && versionSelector != null) {
      if (canUseResolverOnCliRef && !Strings.isNullOrEmpty(reference)) {
        console.warnFmt(
            "Ignoring git.version_selector as %s or %s is being used. Using cli ref %s"
                + " instead.",
            GeneralOptions.FORCE, "--version-selector-use-cli-ref", reference);
        ref = reference;
      } else {
        GitRepository repository = getRepository();
        ImmutableList<Refspec> specs = getVersionSelectorRefspec(repository);
        RefspecVersionList list = new RefspecVersionList(repository, specs, repoUrl);
        for (String prefix : REF_PREFIXES) {
          if (list.list().contains(prefix + reference)) {
            reference = prefix + reference;
          }
        }
        Optional<String> res = versionSelector.select(list, reference, console);
        checkCondition(
            res.isPresent(),
            String.format(
                "Cannot find any matching version for latest_version expression %s.\n\n"
                    + "Please run 'git ls-remote %s' to obtain a list of references that are"
                    + " present in the remote repo.\n",
                versionSelector, repoUrl));
        ref = res.get();
        // It is rare that a branch and a tag has the same name. The reason for this is that
        // destinations expect that the context_reference is a non-full reference. Also it is
        // more readable when we use it in transformations.
        for (String prefix : REF_PREFIXES) {
          if (ref.startsWith(prefix)) {
            ref = ref.substring(prefix.length());
          }
        }
      }
    } else if (Strings.isNullOrEmpty(reference)) {
      checkCondition(getConfigRef() != null, "No reference was passed as a command line argument "
          + "for %s and no default reference was configured in the config file", repoUrl);
      ref = getConfigRef();
    } else {
      ref = reference;
    }

    return resolveStringRef(ref);
  }

  @Override
  public GitRevision resolveAncestorRef(String ancestorRef, GitRevision descendantRev)
      throws ValidationException, RepoException {
    return resolveAncestorRef(this, this.getRepository(), ancestorRef, descendantRev);
  }

  /**
   * Resolves a reference into a revision, but only if the provided descendantRev is an ancestor of
   * ancestorRef.
   *
   * @param gitOrigin the Git origin to check.
   * @param gitRepository the Git repository to check.
   * @param ancestorRef The ancestor reference.
   * @param descendantRev The descendant {@link R}.
   * @return A {@link R} that represents the ancestor reference.
   * @throws ValidationException If descendantRev is not a descendant of ancestorRef, or this
   *     operation is unsupported by the origin.
   * @throws RepoException If there is an error that occurs during the resolve operation.
   */
  public static GitRevision resolveAncestorRef(
      Origin<GitRevision> gitOrigin,
      GitRepository gitRepository,
      String ancestorRef,
      GitRevision descendantRev)
      throws RepoException, ValidationException {
    if (!gitRepository.isAncestor(ancestorRef, descendantRev.fixedReference())) {
      throw new ValidationException(
          String.format("%s is not an ancestor of %s.", ancestorRef, descendantRev.asString()));
    }

    GitRevision resolvedRev = gitOrigin.resolve(ancestorRef);
    if (!Strings.isNullOrEmpty(descendantRev.contextReference())) {
      resolvedRev = resolvedRev.withContextReference(descendantRev.contextReference());
    }

    return resolvedRev;
  }

  private ImmutableList<Refspec> getVersionSelectorRefspec(GitRepository repository)
      throws ValidationException {
    checkNotNull(versionSelector, "version selector presence should be checked outside of"
        + " the method call");
    ImmutableList.Builder<Refspec> specs = ImmutableList.builder();
    ImmutableSet<String> refspecs = versionSelector.searchPatterns().stream()
        .anyMatch(SearchPattern::isAll)
        ? ImmutableSet.of("refs/*")
        : toRefspec();
    for (String prefix : refspecs) {
      specs.add(repository.createRefSpec(prefix));
    }
    return specs.build();
  }

  private GitRevision resolveStringRef(String ref) throws RepoException, ValidationException {
    GitRevision gitRevision = repoType.resolveRef(getRepository(), repoUrl, ref, generalOptions,
        describeVersion, partialFetch, gitOptions.getFetchDepth());
    if (!describeVersion) {
      return gitRevision;
    }

    String describeAsTag =
        generalOptions.isTemporaryFeature("SHA1_AS_TAG", true)
            ? getRepository().describeExactMatch(gitRevision)
            : null;
    return gitRevision.contextReference() == null
        ? getRepository().addDescribeVersion(gitRevision).withContextReference(describeAsTag)
        : getRepository().addDescribeVersion(gitRevision);
  }

  @Override
  public GitRevision resolveLastRev(String ref) throws RepoException, ValidationException {
    if (gitOriginOptions.useGitFuzzyLastRev()) {
      FuzzyClosestVersionSelector selector = new FuzzyClosestVersionSelector();
      ref = selector.selectVersion(ref, getRepository(), repoUrl, generalOptions.console());
    }
    return resolveStringRef(ref);
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
    private final List<String> excludedSubmodules;
    private final boolean firstParent;
    private final boolean partialFetch;
    @Nullable private final PatchTransformation patchTransformation;
    private final boolean describeVersion;
    private final String configPath;
    private final String workflowName;
    @Nullable private final CredentialFileHandler credentials;

    ReaderImpl(
        String repoUrl,
        Glob originFiles,
        Authoring authoring,
        GitOptions gitOptions,
        GitOriginOptions gitOriginOptions,
        GeneralOptions generalOptions,
        boolean includeBranchCommitLogs,
        SubmoduleStrategy submoduleStrategy,
        List<String> excludedSubmodules,
        boolean firstParent,
        boolean partialFetch,
        @Nullable PatchTransformation patchTransformation,
        boolean describeVersion,
        String configPath,
        String workflowName,
        @Nullable CredentialFileHandler credentials) {
      this.repoUrl = checkNotNull(repoUrl);
      this.originFiles = checkNotNull(originFiles, "originFiles");
      this.authoring = checkNotNull(authoring, "authoring");
      this.gitOptions = checkNotNull(gitOptions);
      this.gitOriginOptions = gitOriginOptions;
      this.generalOptions = checkNotNull(generalOptions);
      this.includeBranchCommitLogs = includeBranchCommitLogs;
      this.submoduleStrategy = checkNotNull(submoduleStrategy);
      this.excludedSubmodules = excludedSubmodules;
      this.firstParent = firstParent;
      this.partialFetch = partialFetch;
      this.patchTransformation = patchTransformation;
      this.describeVersion = describeVersion;
      this.configPath = configPath;
      this.workflowName = workflowName;
      this.credentials = credentials;
    }

    ChangeReader.Builder changeReaderBuilder(String repoUrl) throws RepoException {
      return ChangeReader.Builder.forOrigin(authoring, getRepository(), generalOptions.console())
          .setIncludeBranchCommitLogs(includeBranchCommitLogs)
          .setRoots(originFiles.roots(/* allowFiles= */ true))
          .setPartialFetch(partialFetch)
          .setBatchSize(gitOriginOptions.gitOriginLogBatchSize)
          .setUrl(repoUrl);
    }

    protected GitRepository getRepository() throws RepoException {
      GitRepository repo;
      if (partialFetch) {
        String prefixedRepoUrl = String.format("%s:%s%s", configPath, workflowName, repoUrl);
        repo = gitOptions.cachedBareRepoForUrl(prefixedRepoUrl).enablePartialFetch();
      } else {
        repo = gitOptions.cachedBareRepoForUrl(repoUrl);
      }
      if (credentials != null) {
        try {
          Path credentialHelper = gitOptions.getConfigCredsFile(generalOptions);
          credentials.install(repo, credentialHelper);
        } catch (IOException e) {
          throw new RepoException("Unable to store credentials", e);
        }
      }
      return repo;
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

    private GitRepository checkout(
        GitRepository repository, Path workdir, GitRevision ref)
        throws RepoException {
      GitRepository repo = repository.withWorkTree(workdir);
      if (partialFetch) {
        repo.setSparseCheckout(originFiles.tips());
        repo.forceCheckout(ref.getSha1(), generalOptions.commandsTimeout);
        return repo;
      }
      repo.forceCheckout(
          ref.getSha1(),
          gitOptions.experimentCheckoutAffectedFiles ? originFiles.roots() : ImmutableSet.of(),
          generalOptions.commandsTimeout);
      return repo;
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
      for (Submodule submodule : repo.listSubmodules(currentRemoteUrl, ref)) {
        if (excludedSubmodules.contains(submodule.getName())) {
          generalOptions
              .console()
              .infoFmt("Submodule '%s' is excluded, skipping checkout", submodule.getName());
          continue;
        }

        ImmutableList<TreeElement> elements = repo.lsTree(ref, submodule.getPath(), false, false);
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
        String submoduleUrl = gitOptions.rewriteSubmoduleUrl(submodule.getUrl());
        GitRepository subRepo = gitOptions.cachedBareRepoForUrl(submoduleUrl);


        if (submodule.getBranch() != null) {
          subRepo.fetchSingleRef(
              submoduleUrl, submodule.getBranch(), partialFetch, Optional.empty());
        } else {
          subRepo.fetch(
              submoduleUrl, /*prune*/
              true, /*force*/
              true,
              ImmutableList.of("refs/heads/*:refs/heads/*", "refs/tags/*:refs/tags/*"),
              partialFetch,
              Optional.empty(),
              false);
        }
        GitRevision submoduleRef =
            subRepo.resolveReferenceWithContext(
                element.getRef(), submodule.getName(), submoduleUrl);

        Path subdir = workdir.resolve(submodule.getPath());
        try {
          Files.createDirectories(workdir.resolve(submodule.getPath()));
        } catch (IOException e) {
          throw new RepoException(String.format(
              "Cannot create subdirectory %s for submodule: %s", subdir, submodule));
        }

        checkoutRepo(subRepo, submoduleUrl, subdir,
            submoduleStrategy == SubmoduleStrategy.RECURSIVE
                ? SubmoduleStrategy.RECURSIVE
                : SubmoduleStrategy.NO, submoduleRef, /*topLevelCheckout*/ false);
      }
    }

    protected void maybeRebase(GitRepository repo, GitRevision ref, Path workdir)
        throws RepoException, ValidationException {
      String rebaseToRef = gitOriginOptions.originRebaseRef;
      if (rebaseToRef == null) {
        return;
      }
      generalOptions.console().info(String.format("Rebasing %s to %s", rebaseToRef, rebaseToRef));
      GitRevision rebaseRev = repo.fetchSingleRef(repoUrl, rebaseToRef, partialFetch,
          Optional.empty());
      repo.simpleCommand("update-ref", COPYBARA_TMP_REF, rebaseRev.getSha1());
      repo.rebaseCmd(COPYBARA_TMP_REF)
          .errorAdvice(
              "Please consider not using the flag --git-origin-rebase-ref as a workaround")
          .run();
    }

    @Override
    public ChangesResponse<GitRevision> changes(@Nullable GitRevision fromRef, GitRevision toRef)
        throws RepoException, ValidationException {

      String refRange = fromRef == null
          ? toRef.getSha1()
          : fromRef.getSha1() + ".." + toRef.getSha1();
      ChangeReader changeReader = changeReaderBuilder(repoUrl)
          .setFirstParent(firstParent)
          .build();
      // toRef might already have labels that we want to maintain in the toRef copy when we return
      // (fromRef, toRef] (that includes toRef).
      ImmutableMap<String, ImmutableListMultimap<String, String>> labelsToPropagate =
          ImmutableMap.of(toRef.getSha1(), toRef.associatedLabels());
      ImmutableList<Change<GitRevision>> gitChanges = changeReader.run(refRange, labelsToPropagate);
      if (!gitChanges.isEmpty()) {
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
    public Change<GitRevision> change(GitRevision ref)
        throws RepoException, ValidationException {
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
          rev.getLabels(), rev.getChangeFiles(), rev.isMerge(), rev.getParents())
          .withLabels(ref.associatedLabels());
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
        throws RepoException, ValidationException {
      ChangeReader.Builder queryChanges = changeReaderBuilder(repoUrl).setFirstParent(firstParent);
      ImmutableSet<String> roots = originFiles.roots();

      GitVisitorUtil.visitChanges(
          start, input -> affectsRoots(roots, input.getChangeFiles())
              ? visitor.visit(input)
              : VisitResult.CONTINUE,
          queryChanges, generalOptions, "origin", gitOptions.visitChangePageSize);
    }

    @Override
    public ImmutableList<Change<GitRevision>> getVersions() throws RepoException {
      ImmutableList.Builder<Change<GitRevision>> result = ImmutableList.builder();

      ImmutableList<GitLogEntry> output =
          getRepository().log("*").includeTags(true).noWalk(true).run();

      for (GitLogEntry entry : output) {
        if (entry.getTag() != null) {
          result.add(
              new Change<>(
                  entry.getTag(),
                  entry.getAuthor(),
                  nullToEmpty(entry.getBody()),
                  entry.getCommitDate(),
                  ImmutableListMultimap.of()));
        }
      }

      return result.build();
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
        .add("primaryBranchMigrationMode", primaryBranchMigrationMode)
        .toString();
  }

  /** Builds a new {@link GitOrigin}. */
  static GitOrigin newGitOrigin(
      Options options,
      String url,
      String ref,
      GitRepoType type,
      SubmoduleStrategy submoduleStrategy,
      List<String> excludedSubmodules,
      boolean includeBranchCommitLogs,
      boolean firstParent,
      boolean partialClone,
      boolean primaryBranchMigrationMode,
      @Nullable PatchTransformation patchTransformation,
      boolean describeVersion,
      @Nullable VersionSelector versionSelector,
      String configPath,
      String workflowName,
      ApprovalsProvider approvalsProvider,
      boolean enableLfs,
      @Nullable CredentialFileHandler credentials) {
    return new GitOrigin(
        options.get(GeneralOptions.class),
        url,
        ref,
        type,
        options.get(GitOptions.class),
        options.get(GitOriginOptions.class),
        submoduleStrategy,
        excludedSubmodules,
        includeBranchCommitLogs,
        firstParent,
        partialClone,
        patchTransformation,
        describeVersion,
        versionSelector,
        configPath,
        workflowName,
        primaryBranchMigrationMode,
        approvalsProvider,
        enableLfs,
        credentials);
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
            .put("submodules", submoduleStrategy.name())
            .put("primaryBranchMigrationMode", "" + primaryBranchMigrationMode);
    if (!originFiles.roots().isEmpty() && !originFiles.roots().contains("")) {
      builder.putAll("root", originFiles.roots());
    }
    if (partialFetch) {
      builder.put("partialFetch", Boolean.toString(partialFetch));
    }
    if (configRef != null) {
      builder.put("ref", configRef);
    }
    if (versionSelector != null) {
      builder.putAll("refspec", toRefspec());
    }
    if (enableLfs) {
      builder.put("enableLfs", Boolean.toString(enableLfs));
    }
    return builder.build();
  }

  private ImmutableSet<String> toRefspec() {
    ImmutableSet<SearchPattern> searchPatterns = versionSelector.searchPatterns();
    if (searchPatterns.stream().anyMatch(SearchPattern::isAll)) {
      return ImmutableSet.of("refs/*");
    }
    ImmutableSet.Builder<String> refspecs = ImmutableSet.builder();
    for (SearchPattern searchPattern : searchPatterns) {
      if (searchPattern.isNone()) {
        continue;
      }
      StringBuilder patternBuilder = new StringBuilder();
      for (Token token : searchPattern.tokens()) {
        if (token.getType() == TokenType.LITERAL) {
          patternBuilder.append(token.getValue());
        } else {
          // Only support prefixes for now.
          patternBuilder.append("*");
          break;
        }
      }
      String pattern = patternBuilder.toString();
      if (!pattern.startsWith("refs/")) {
        pattern = "refs/*";
      }
      refspecs.add(pattern);
    }
    return refspecs.build();
  }

  @Nullable
  private String getConfigRef() throws RepoException {
    if (resolvedRef != null) {
      return resolvedRef;
    }
    if (primaryBranchMigrationMode && PRIMARY_BRANCHES.contains(configRef)) {
      resolvedRef = getRepository().getPrimaryBranch(repoUrl);
      console.infoFmt("Detected primary origin branch '%s'", resolvedRef);
    }
    if (resolvedRef == null) {
      resolvedRef = configRef;
    }
    return resolvedRef;
  }

  @Override
  public ImmutableList<ImmutableSetMultimap<String, String>> describeCredentials() {
    if (credentials == null) {
      return ImmutableList.of();
    }
    return credentials.describeCredentials();
  }
}
