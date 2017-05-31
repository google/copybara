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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.copybara.CannotResolveRevisionException;
import com.google.copybara.Change;
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

  enum SubmoduleStrategy {
    /** Don't download any submodule. */
    NO,
    /** Download just the first level of submodules, but don't download recursively */
    YES,
    /** Download all the submodules recursively */
    RECURSIVE
  }

  final GitRepository repository;

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
  private final boolean verbose;
  @Nullable
  private final Map<String, String> environment;
  private final SubmoduleStrategy submoduleStrategy;
  private final boolean includeBranchCommitLogs;

  GitOrigin(GeneralOptions generalOptions, GitRepository repository, String repoUrl,
      @Nullable String configRef, GitRepoType repoType, GitOptions gitOptions, boolean verbose,
      @Nullable Map<String, String> environment, SubmoduleStrategy submoduleStrategy,
      boolean includeBranchCommitLogs) {
    this.generalOptions = generalOptions;
    this.console = generalOptions.console();
    this.repository = checkNotNull(repository);
    // Remove a possible trailing '/' so that the url is normalized.
    this.repoUrl = checkNotNull(repoUrl).endsWith("/")
        ? repoUrl.substring(0, repoUrl.length() - 1)
        : repoUrl;
    this.configRef = configRef;
    this.repoType = checkNotNull(repoType);
    this.gitOptions = gitOptions;
    this.verbose = verbose;
    this.environment = environment;
    this.submoduleStrategy = submoduleStrategy;
    this.includeBranchCommitLogs = includeBranchCommitLogs;
  }

  public GitRepository getRepository() {
    return repository;
  }

  private class ReaderImpl implements Reader<GitRevision> {

    final Glob originFiles;
    final Authoring authoring;

    ReaderImpl(Glob originFiles, Authoring authoring) {
      this.originFiles = checkNotNull(originFiles, "originFiles");
      this.authoring = checkNotNull(authoring, "authoring");
    }

    private ChangeReader.Builder changeReaderBuilder() {
      return ChangeReader.Builder.forOrigin(authoring, repository, console, originFiles)
          .setVerbose(verbose)
          .setIncludeBranchCommitLogs(includeBranchCommitLogs);
    }

    /**
     * Creates a worktree with the contents of the git reference
     *
     * <p>Any content in the workdir is removed/overwritten.
     */
    @Override
    public void checkout(GitRevision ref, Path workdir)
        throws RepoException, CannotResolveRevisionException {
      checkoutRepo(repository, repoUrl, workdir, submoduleStrategy, ref);
      if (!Strings.isNullOrEmpty(gitOptions.originCheckoutHook)) {
        runCheckoutOrigin(workdir);
      }
    }

    private void checkoutRepo(GitRepository repository, String currentRemoteUrl, Path workdir,
        SubmoduleStrategy submoduleStrategy, GitRevision ref)
        throws RepoException, CannotResolveRevisionException {

      GitRepository repo = repository.withWorkTree(workdir);
      repo.simpleCommand("checkout", "-q", "-f", ref.getSha1());
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

        GitRepository subRepo = GitRepository.bareRepoInCache(
            submodule.getUrl(), environment, verbose, gitOptions.repoStorage);
        subRepo.initGitDir();
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
                : SubmoduleStrategy.NO, submoduleRef);
      }
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
    public Change<GitRevision> change(GitRevision ref) throws RepoException {
      // The limit=1 flag guarantees that only one change is returned
      ChangeReader changeReader = changeReaderBuilder()
          .setLimit(1)
          .build();
      Change<GitRevision> rev = Iterables.getOnlyElement(
          asChanges(changeReader.run(ref.getSha1())));

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
    return new ReaderImpl(originFiles, authoring);
  }

  private void runCheckoutOrigin(Path workdir) throws RepoException {
    try {
      CommandOutputWithStatus result = CommandUtil.executeCommand(
          new Command(new String[]{gitOptions.originCheckoutHook},
              environment, workdir.toFile()), verbose);
      Consoles.logLines(console, "git.origin hook (Stdout): ", result.getStdout());
      Consoles.logLines(console, "git.origin hook (Stderr): ", result.getStderr());
    } catch (BadExitStatusWithOutputException e) {
      Consoles.logLines(console, "git.origin hook (Stdout): ", e.getOutput().getStdout());
      Consoles.logLines(console, "git.origin hook (Stderr): ", e.getOutput().getStderr());
      throw new RepoException(
          "Error executing the git checkout hook: " + gitOptions.originCheckoutHook, e);
    } catch (CommandException e) {
      throw new RepoException(
          "Error executing the git checkout hook: " + gitOptions.originCheckoutHook, e);
    }
  }

  @Override
  public GitRevision resolve(@Nullable String reference)
      throws RepoException, ValidationException {
    console.progress("Git Origin: Initializing local repo");
    repository.initGitDir();
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
    return repoType.resolveRef(repository, repoUrl, ref, generalOptions);
  }

  private ImmutableList<Change<GitRevision>> asChanges(ImmutableList<GitChange> gitChanges) {
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
  static GitOrigin newGitOrigin(Options options, String url, String ref, GitRepoType type, SubmoduleStrategy submoduleStrategy,
      boolean includeBranchCommitLogs) {

    GitOptions gitConfig = options.get(GitOptions.class);
    boolean verbose = options.get(GeneralOptions.class).isVerbose();
    Map<String, String> environment = options.get(GeneralOptions.class).getEnvironment();
    return new GitOrigin(
        options.get(GeneralOptions.class),
        GitRepository.bareRepoInCache(url, environment, verbose, gitConfig.repoStorage),
        url, ref, type, options.get(GitOptions.class), verbose, environment, submoduleStrategy,
        includeBranchCommitLogs);
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
