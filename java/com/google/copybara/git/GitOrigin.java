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
import com.google.common.collect.Iterables;
import com.google.copybara.CannotResolveReferenceException;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.Change;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;
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
public final class GitOrigin implements Origin<GitReference> {

  enum SubmoduleStrategy {
    /** Don't download any submodule. */
    NO,
    /** Download just the first level of submodules, but don't download recursively */
    YES,
    /** Download all the submodules recursively */
    RECURSIVE
  }
  static final String GIT_LOG_COMMENT_PREFIX = "    ";
  private final GitRepository repository;

  /**
   * Url of the repository
   */
  private final String repoUrl;

  /**
   * Default reference to track
   */
  @Nullable
  private final String configRef;
  private final Console console;
  private final GitRepoType repoType;
  private final GitOptions gitOptions;
  private final boolean verbose;
  @Nullable
  private final Map<String, String> environment;
  private final SubmoduleStrategy submoduleStrategy;

  private GitOrigin(Console console, GitRepository repository, String repoUrl,
      @Nullable String configRef, GitRepoType repoType, GitOptions gitOptions, boolean verbose,
      @Nullable Map<String, String> environment, SubmoduleStrategy submoduleStrategy) {
    this.console = checkNotNull(console);
    this.repository = checkNotNull(repository);
    // Remove a possible trailing '/' so that the url is normalized.
    this.repoUrl = repoUrl.endsWith("/") ? repoUrl.substring(0, repoUrl.length() - 1) : repoUrl;
    this.configRef = configRef;
    this.repoType = checkNotNull(repoType);
    this.gitOptions = gitOptions;
    this.verbose = verbose;
    this.environment = environment;
    this.submoduleStrategy = submoduleStrategy;
  }

  public GitRepository getRepository() {
    return repository;
  }

  private class ReaderImpl implements Reader<GitReference> {

    final Authoring authoring;

    ReaderImpl(Authoring authoring) {
      this.authoring = checkNotNull(authoring);
    }

    /**
     * Creates a worktree with the contents of the git reference
     *
     * <p>Any content in the workdir is removed/overwritten.
     */
    @Override
    public void checkout(GitReference ref, Path workdir)
        throws RepoException, CannotResolveReferenceException {
      checkoutRepo(repository, repoUrl, workdir, submoduleStrategy, ref);
      if (!Strings.isNullOrEmpty(gitOptions.originCheckoutHook)) {
        runCheckoutOrigin(workdir);
      }
    }

    private void checkoutRepo(GitRepository repository, String currentRemoteUrl, Path workdir,
        SubmoduleStrategy submoduleStrategy, GitReference ref)
        throws RepoException, CannotResolveReferenceException {

      GitRepository repo = repository.withWorkTree(workdir);
      repo.simpleCommand("checkout", "-q", "-f", ref.asString());
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
        GitReference submoduleRef = subRepo.resolveReference(element.getRef());

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
    public ImmutableList<Change<GitReference>> changes(@Nullable GitReference fromRef,
        GitReference toRef) throws RepoException {

      String refRange = fromRef == null
          ? toRef.asString()
          : fromRef.asString() + ".." + toRef.asString();
      ChangeReader changeReader =
          ChangeReader.Builder.forOrigin(authoring, repository, console)
              .setVerbose(verbose)
              .build();
      return asChanges(changeReader.run(refRange));
    }

    @Override
    public Change<GitReference> change(GitReference ref) throws RepoException {
      // The limit=1 flag guarantees that only one change is returned
      ChangeReader changeReader =
          ChangeReader.Builder.forOrigin(authoring, repository, console)
              .setVerbose(verbose)
              .setLimit(1)
              .build();
      return Iterables.getOnlyElement(asChanges(changeReader.run(ref.asString())));
    }

    @Override
    public void visitChanges(GitReference start, ChangesVisitor visitor)
        throws RepoException, CannotResolveReferenceException {
      ChangeReader queryChanges =
          ChangeReader.Builder.forOrigin(authoring, repository, console)
              .setVerbose(verbose)
              .setLimit(1)
              .build();

      ImmutableList<GitChange> result = queryChanges.run(start.asString());
      if (result.isEmpty()) {
        throw new CannotResolveReferenceException("Cannot resolve reference " + start.asString());
      }
      GitChange current = Iterables.getOnlyElement(result);
      while (current != null) {
        if (visitor.visit(current.getChange()) == VisitResult.TERMINATE
            || current.getParents().isEmpty()) {
          break;
        }
        current =
            Iterables.getOnlyElement(queryChanges.run(current.getParents().get(0).asString()));
      }
    }
  }

  @Override
  public Reader<GitReference> newReader(Glob originFiles, Authoring authoring) {
    // TODO(matvore): Use originFiles to determine the depot trees from which to read.
    return new ReaderImpl(checkNotNull(authoring, "authoring"));
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
  public GitReference resolve(@Nullable String reference)
      throws RepoException, CannotResolveReferenceException {
    console.progress("Git Origin: Initializing local repo");
    repository.initGitDir();
    String ref;
    if (Strings.isNullOrEmpty(reference)) {
      if (configRef == null) {
        throw new RepoException("No reference was pass for " + repoUrl
            + " and no default reference was configured in the config file");
      }
      ref = configRef;
    } else {
      ref = reference;
    }
    return repoType.resolveRef(repository, repoUrl, ref, console);
  }

  private ImmutableList<Change<GitReference>> asChanges(ImmutableList<GitChange> gitChanges) {
    ImmutableList.Builder<Change<GitReference>> result = ImmutableList.builder();
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
      Map<String, String> environment, SubmoduleStrategy submoduleStrategy) {

    GitOptions gitConfig = options.get(GitOptions.class);
    boolean verbose = options.get(GeneralOptions.class).isVerbose();

    return new GitOrigin(
        options.get(GeneralOptions.class).console(),
        GitRepository.bareRepoInCache(url, environment, verbose, gitConfig.repoStorage),
        url, ref, type, options.get(GitOptions.class), verbose, environment, submoduleStrategy);
  }
}
