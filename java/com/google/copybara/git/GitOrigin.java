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
import static com.google.copybara.util.console.Consoles.logLines;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.copybara.CannotResolveRevisionException;
import com.google.copybara.Change;
import com.google.copybara.EmptyChangeException;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.git.ChangeReader.GitChange;
import com.google.copybara.git.GitRepository.Submodule;
import com.google.copybara.git.GitRepository.TreeElement;
import com.google.copybara.util.BadExitStatusWithOutputException;
import com.google.copybara.util.CommandOutputWithStatus;
import com.google.copybara.util.CommandUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.Consoles;
import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
  protected final String repoUrl;

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
  private final boolean verbose;
  @Nullable
  private final Map<String, String> environment;
  private final SubmoduleStrategy submoduleStrategy;
  private final boolean includeBranchCommitLogs;

  GitOrigin(GeneralOptions generalOptions, String repoUrl,
      @Nullable String configRef, GitRepoType repoType, GitOptions gitOptions,
      GitOriginOptions gitOriginOptions, boolean verbose,
      @Nullable Map<String, String> environment, SubmoduleStrategy submoduleStrategy,
      boolean includeBranchCommitLogs) {
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
    this.verbose = verbose;
    this.environment = environment;
    this.submoduleStrategy = submoduleStrategy;
    this.includeBranchCommitLogs = includeBranchCommitLogs;
  }

  @VisibleForTesting
  public GitRepository getRepository() throws RepoException {
    return gitOptions.cachedBareRepoForUrl(repoUrl);
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

    ReaderImpl(String repoUrl, Glob originFiles, Authoring authoring,
        GitOptions gitOptions,
        GitOriginOptions gitOriginOptions,
        GeneralOptions generalOptions,
        boolean includeBranchCommitLogs,
        SubmoduleStrategy submoduleStrategy) {
      this.repoUrl = checkNotNull(repoUrl);
      this.originFiles = checkNotNull(originFiles, "originFiles");
      this.authoring = checkNotNull(authoring, "authoring");
      this.gitOptions = checkNotNull(gitOptions);
      this.gitOriginOptions = gitOriginOptions;
      this.generalOptions = checkNotNull(generalOptions);
      this.includeBranchCommitLogs = includeBranchCommitLogs;
      this.submoduleStrategy = checkNotNull(submoduleStrategy);
    }

    private ChangeReader.Builder changeReaderBuilder() throws RepoException {
      return ChangeReader.Builder.forOrigin(authoring,
          getRepository(), generalOptions.console(), originFiles)
          .setVerbose(generalOptions.isVerbose())
          .setIncludeBranchCommitLogs(includeBranchCommitLogs);
    }

    private GitRepository getRepository() throws RepoException {
      return gitOptions.cachedBareRepoForUrl(repoUrl);
    }

    /**
     * Creates a worktree with the contents of the git reference
     *
     * <p>Any content in the workdir is removed/overwritten.
     */
    @Override
    public void checkout(GitRevision ref, Path workdir)
        throws RepoException, CannotResolveRevisionException {
      checkoutRepo(getRepository(), repoUrl, workdir, submoduleStrategy, ref,
          /*topLevelCheckout=*/true);
      if (!Strings.isNullOrEmpty(gitOriginOptions.originCheckoutHook)) {
        runCheckoutOrigin(workdir);
      }
    }

    private void runCheckoutOrigin(Path workdir) throws RepoException {
      try {
        CommandOutputWithStatus result = CommandUtil.executeCommand(
            new Command(new String[]{gitOriginOptions.originCheckoutHook},
                generalOptions.getEnvironment(), workdir.toFile()), generalOptions.isVerbose());
        logLines(generalOptions.console(), "git.origin hook (Stdout): ", result.getStdout());
        logLines(generalOptions.console(), "git.origin hook (Stderr): ", result.getStderr());
      } catch (BadExitStatusWithOutputException e) {
        logLines(generalOptions.console(), "git.origin hook (Stdout): ", e.getOutput().getStdout());
        logLines(generalOptions.console(), "git.origin hook (Stderr): ", e.getOutput().getStderr());
        throw new RepoException(
            "Error executing the git checkout hook: " + gitOriginOptions.originCheckoutHook, e);
      } catch (CommandException e) {
        throw new RepoException(
            "Error executing the git checkout hook: " + gitOriginOptions.originCheckoutHook, e);
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
    private void checkoutRepo(GitRepository repository, String currentRemoteUrl, Path workdir,
        SubmoduleStrategy submoduleStrategy, GitRevision ref, boolean topLevelCheckout)
        throws RepoException, CannotResolveRevisionException {
      GitRepository repo = checkout(repository, workdir, ref);
      if(topLevelCheckout) {
        maybeRebase(repo);
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

        GitRepository subRepo = gitOptions.cachedBareRepoForUrl(submodule.getUrl());
        subRepo.fetchSingleRef(submodule.getUrl(), submodule.getBranch());
        GitRevision submoduleRef = subRepo.resolveReference(element.getRef(), submodule.getName());

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

    protected void maybeRebase(GitRepository repo)
        throws RepoException, CannotResolveRevisionException {
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
    public ImmutableList<Change<GitRevision>> changes(@Nullable GitRevision fromRef,
        GitRevision toRef) throws RepoException {

      String refRange = fromRef == null
          ? toRef.getSha1()
          : fromRef.getSha1() + ".." + toRef.getSha1();
      ChangeReader changeReader = changeReaderBuilder().build();
      return asChanges(changeReader.run(refRange));
    }

    @Override
    public Change<GitRevision> change(GitRevision ref) throws RepoException, EmptyChangeException {
      // The limit=1 flag guarantees that only one change is returned
      ChangeReader changeReader = changeReaderBuilder()
          .setLimit(1)
          .build();
      ImmutableList<Change<GitRevision>> changes = asChanges(changeReader.run(ref.getSha1()));
      if (changes.isEmpty()) {
        throw new EmptyChangeException(
            String.format(
                "'%s' revision cannot be found in the origin or it didn't affect the origin paths.",
                ref.asString()));
      }
      Change<GitRevision> rev = Iterables.getOnlyElement(changes);

      // Keep the original revision since it might have context information like code review
      // info. The difference with changes method is that here we know exactly what we've
      // requested (One SHA-1 revision) while in the other we get a result for a range. That
      // means that extensions of GitOrigin need to implement changes if they want to provide
      // additional information.
      return new Change<>(ref, rev.getAuthor(), rev.getMessage(), rev.getDateTime(),
                          rev.getLabels(), rev.getChangeFiles());
    }

    @Override
    public void visitChanges(GitRevision start, ChangesVisitor visitor)
        throws RepoException, CannotResolveRevisionException {
      ChangeReader queryChanges = changeReaderBuilder()
          .setLimit(1)
          .build();

      ImmutableList<GitChange> result = queryChanges.run(start.asString());
      if (result.isEmpty()) {
        throw new CannotResolveRevisionException("Cannot resolve reference " + start.asString());
      }
      GitChange current = Iterables.getOnlyElement(result);
      while (current != null) {
        if (visitor.visit(current.getChange()) == VisitResult.TERMINATE
            || current.getParents().isEmpty()) {
          break;
        }
        String parentRef = current.getParents().get(0).asString();
        ImmutableList<GitChange> changes = queryChanges.run(parentRef);
        if (changes.isEmpty()) {
          throw new CannotResolveRevisionException(String.format(
              "'%s' revision cannot be found in the origin. But it is referenced as parent of"
                  + " revision '%s'", parentRef, current.getChange().getRevision().asString()));
        }
        current = Iterables.getOnlyElement(changes);
      }
    }
  }

  @Override
  public Reader<GitRevision> newReader(Glob originFiles, Authoring authoring) {
    return new ReaderImpl(repoUrl, originFiles, authoring,
        gitOptions, gitOriginOptions, generalOptions, includeBranchCommitLogs, submoduleStrategy);
  }

  @Override
  public GitRevision resolve(@Nullable String reference)
      throws RepoException, ValidationException {
    console.progress("Git Origin: Initializing local repo");
    String ref;
    if (Strings.isNullOrEmpty(reference)) {
      if (configRef == null) {
        throw new ValidationException("No reference was passed as an command line argument for "
            + repoUrl + " and no default reference was configured in the config file");
      }
      ref = configRef;
    } else {
      ref = reference;
    }
    return repoType.resolveRef(getRepository(), repoUrl, ref, generalOptions);
  }

  private static ImmutableList<Change<GitRevision>> asChanges(ImmutableList<GitChange> gitChanges) {
    ImmutableList.Builder<Change<GitRevision>> result = ImmutableList.builder();
    for (GitChange gitChange : gitChanges) {
      result.add(gitChange.getChange());
    }
    return result.build();
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
      SubmoduleStrategy submoduleStrategy, boolean includeBranchCommitLogs) {
    boolean verbose = options.get(GeneralOptions.class).isVerbose();
    Map<String, String> environment = options.get(GeneralOptions.class).getEnvironment();
    return new GitOrigin(
        options.get(GeneralOptions.class),
        url, ref, type, options.get(GitOptions.class), options.get(GitOriginOptions.class),
        verbose, environment, submoduleStrategy, includeBranchCommitLogs);
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(@Nullable Glob originFiles) {
    ImmutableSetMultimap.Builder<String, String> builder =
        new ImmutableSetMultimap.Builder<String, String>()
            .put("type", "git.origin")
            .put("repoType", repoType.name())
            .put("url", repoUrl)
            .put("submodules", submoduleStrategy.name());
    if (configRef != null) {
      builder.put("ref", configRef);
    }
    return builder.build();
  }
}
