/*
 * Copyright (C) 2026 Google LLC
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

package com.google.copybara.jj;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.exception.RepoException;
import com.google.copybara.util.BadExitStatusWithOutputException;
import com.google.copybara.util.CommandOutputWithStatus;
import com.google.copybara.util.CommandRunner;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.FileUtil.CopySymlinkStrategy;
import com.google.copybara.util.Glob;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** A Jujutsu ({@code jj}) repository CLI wrapper. */
public class JjRepository {

  /** A commit identifier in a Jujutsu (jj) repository. */
  public record CommitId(String id) {
    public CommitId {
      checkNotNull(id, "Commit ID cannot be null");
      checkArgument(!id.isBlank(), "Commit ID cannot be blank");
    }
  }

  /**
   * Result of a 3-way rebase operation in a Jujutsu (jj) repository.
   *
   * @param hasConflicts whether any commit in the rebased stack contains conflicted files
   * @param tipCommitId the new commit identifier of the tip of the rebased stack
   */
  public record RebaseResult(boolean hasConflicts, CommitId tipCommitId) {
    public RebaseResult {
      checkNotNull(tipCommitId, "tipCommitId cannot be null");
    }
  }

  /** Repository metadata directories that are never copied into or cleared from a working tree. */
  private static final ImmutableSet<String> METADATA_DIRS = ImmutableSet.of(".jj", ".git");

  private static final Glob NON_METADATA_GLOB =
      Glob.createGlob(
          ImmutableList.of("**"),
          METADATA_DIRS.stream().map(dir -> dir + "/**").collect(toImmutableList()));

  private static final Splitter LINE_SPLITTER = Splitter.on('\n').omitEmptyStrings().trimResults();

  private final Path repoDir;
  private final String jjBin;
  private final ImmutableMap<String, String> environment;
  private final boolean verbose;
  private final Duration repoTimeout;

  public JjRepository(
      Path repoDir,
      String jjBin,
      Map<String, String> environment,
      boolean verbose,
      Duration repoTimeout) {
    this.repoDir = checkNotNull(repoDir);
    this.jjBin = checkNotNull(jjBin);
    this.environment = ImmutableMap.copyOf(checkNotNull(environment));
    this.verbose = verbose;
    this.repoTimeout = checkNotNull(repoTimeout);
  }

  /** Returns the root directory of this {@code jj} repository. */
  public Path getRepoDir() {
    return repoDir;
  }

  /**
   * Initializes a non-colocated {@code jj} repository backed by an internal Git store ({@code jj
   * git init --no-colocate}).
   */
  public void init() throws RepoException {
    createRepoDir();
    executeJj(repoDir, ImmutableList.of("git", "init", "--no-colocate"));
  }

  /**
   * Initializes a Git-colocated {@code jj} repository ({@code jj git init --colocate}) with a
   * top-level {@code .git} directory alongside {@code .jj}.
   */
  public void initGitColocated() throws RepoException {
    createRepoDir();
    executeJj(repoDir, ImmutableList.of("git", "init", "--colocate"));
  }

  /**
   * Snapshots the file tree in {@code sourceDir} as a new commit on top of the current working-copy
   * parent ({@code @-}).
   */
  @CanIgnoreReturnValue
  public CommitId commitTree(Path sourceDir, String message) throws RepoException, IOException {
    return commitTree(sourceDir, message, /* parent= */ null);
  }

  /**
   * Snapshots the file tree in {@code sourceDir} as a new commit in the {@code jj} repository.
   *
   * <p>Every file in {@code sourceDir} is committed, including files matched by {@code .gitignore}
   * and files larger than jj's new-file size limit.
   *
   * @param sourceDir directory containing the files to snapshot
   * @param message commit description message
   * @param parent optional parent commit to branch from before snapshotting; if {@code null},
   *     commits on top of the current working-copy parent
   * @return the {@link CommitId} of the newly created commit
   */
  @CanIgnoreReturnValue
  public CommitId commitTree(Path sourceDir, String message, @Nullable CommitId parent)
      throws RepoException, IOException {
    checkNotNull(sourceDir);
    checkNotNull(message);
    if (parent != null) {
      executeJj(repoDir, ImmutableList.of("new", parent.id()));
    }
    clearAndCopyWorkingTree(sourceDir, repoDir);
    // Snapshots skip ignored files (.gitignore, git excludes) and new files over
    // snapshot.max-new-file-size, so track every path explicitly.
    executeJj(repoDir, ImmutableList.of("file", "track", "--include-ignored", "."));
    executeJj(repoDir, ImmutableList.of("commit", "-m", message));
    return new CommitId(getOnlyElement(logCommitIds("@-")));
  }

  /**
   * Rebases the linear commit stack {@code stackRoot::stackTip} onto {@code onto} and inspects all
   * rebased commits for conflicts.
   *
   * @param stackRoot the first commit of the patch stack to rebase
   * @param stackTip the last commit of the patch stack to rebase
   * @param onto the target base commit to rebase onto
   * @return the {@link RebaseResult} containing conflict status and the new tip {@link CommitId}
   */
  @VisibleForTesting
  RebaseResult rebase(CommitId stackRoot, CommitId stackTip, CommitId onto) throws RepoException {
    checkNotNull(stackRoot);
    checkNotNull(stackTip);
    checkNotNull(onto);
    // Rebasing rewrites every commit in the stack, but change IDs survive the rewrite, so record
    // the tip's change ID to find the rebased tip afterwards.
    String tipChangeId = getOnlyElement(runJjLog(stackTip.id(), "change_id ++ \"\\n\""));
    executeJj(
        repoDir,
        ImmutableList.of(
            "rebase",
            "-r",
            String.format("%s::%s", stackRoot.id(), stackTip.id()),
            "-d",
            onto.id()));

    // change_id() returns every match (or none) rather than failing on divergent or missing
    // changes.
    ImmutableList<String> newTips = logCommitIds(String.format("change_id(%s)", tipChangeId));
    if (newTips.size() != 1) {
      throw new RepoException(
          String.format(
              "Expected change %s to resolve to exactly one commit after rebasing onto %s,"
                  + " found %s",
              tipChangeId, onto.id(), newTips));
    }
    CommitId newTip = new CommitId(newTips.get(0));
    boolean hasConflicts =
        !logCommitIds(String.format("conflicts() & (%s..%s)", onto.id(), newTip.id())).isEmpty();
    return new RebaseResult(hasConflicts, newTip);
  }

  /**
   * Snapshots {@code dir} as a new base commit, rebases the patch stack {@code stackRoot::stackTip}
   * onto it, and, if the rebase has no conflicts, writes the resulting tip tree back into {@code
   * dir}.
   *
   * <p>The new base commit is created on top of the original base (the parent of {@code
   * stackRoot}), so {@code jj diff} on the new base shows only the upstream changes. Each patch is
   * 3-way merged against its original parent.
   *
   * <p>If no rebased commit has conflicts, {@code dir} holds the full tree of the rebased tip on
   * return. Files that are not in the tip are removed from {@code dir}, and empty directories are
   * not preserved.
   *
   * <p>If any rebased commit has conflicts, {@code dir} is left unchanged: it still holds the new
   * base and does not contain the patches. Callers must check {@link RebaseResult#hasConflicts()}
   * before using {@code dir}. The conflicted stack stays in the repository, ending at {@link
   * RebaseResult#tipCommitId()}.
   *
   * @param dir directory holding the new base; overwritten with the rebased tip only if there are
   *     no conflicts
   * @param message description of the new base commit
   * @param stackRoot the first commit of the patch stack to rebase
   * @param stackTip the last commit of the patch stack to rebase
   * @return the {@link RebaseResult} with conflict status and the new tip {@link CommitId}
   * @throws RepoException if {@code stackRoot} is not an ancestor of {@code stackTip}, the stack
   *     contains a merge commit, or a {@code jj} command fails
   */
  public RebaseResult rebaseOnto(Path dir, String message, CommitId stackRoot, CommitId stackTip)
      throws RepoException, IOException {
    checkNotNull(dir);
    checkNotNull(message);
    checkNotNull(stackRoot);
    checkNotNull(stackTip);

    String stack = String.format("%s::%s", stackRoot.id(), stackTip.id());
    // root::tip is empty unless root is an ancestor of (or equal to) tip. Without this check, the
    // rebase would move nothing and silently return the original tip.
    if (logCommitIds(stack).isEmpty()) {
      throw new RepoException(
          String.format(
              "Stack root %s is not an ancestor of stack tip %s", stackRoot.id(), stackTip.id()));
    }
    ImmutableList<String> merges = logCommitIds(String.format("merges() & (%s)", stack));
    if (!merges.isEmpty()) {
      throw new RepoException(
          String.format("Cannot rebase stack %s: it contains merge commits %s", stack, merges));
    }
    ImmutableList<String> parents = logCommitIds(stackRoot.id() + "-");
    if (parents.size() != 1) {
      throw new RepoException(
          String.format(
              "Expected stack root %s to have exactly one parent, found %s",
              stackRoot.id(), parents));
    }

    // Create a new base commit on the parent of the stack root.
    CommitId newBase = commitTree(dir, message, new CommitId(parents.get(0)));
    RebaseResult result = rebase(stackRoot, stackTip, newBase);

    if (!result.hasConflicts()) {
      checkout(result.tipCommitId(), dir);
    }
    return result;
  }

  /**
   * Materializes the tree of {@code commitId} into {@code targetDir}, excluding {@code .jj} and
   * {@code .git} metadata directories.
   */
  public void checkout(CommitId commitId, Path targetDir) throws RepoException, IOException {
    checkNotNull(commitId);
    checkNotNull(targetDir);
    executeJj(repoDir, ImmutableList.of("new", commitId.id()));
    Files.createDirectories(targetDir);
    clearAndCopyWorkingTree(repoDir, targetDir);
  }

  /** Runs a {@code jj} command in the repository directory. */
  @CanIgnoreReturnValue
  public CommandOutputWithStatus simpleCommand(String... argv) throws RepoException {
    return executeJj(repoDir, ImmutableList.copyOf(argv));
  }

  /**
   * Executes a {@code jj} command in {@code cwd}.
   *
   * @param cwd working directory for the command execution
   * @param params command-line arguments passed to {@code jj}
   * @return the command output and termination status
   */
  @CanIgnoreReturnValue
  protected CommandOutputWithStatus executeJj(Path cwd, Iterable<String> params)
      throws RepoException {
    checkNotNull(cwd);
    checkNotNull(params);
    ImmutableList<String> allParams =
        ImmutableList.<String>builder().add(jjBin).addAll(params).build();

    Map<String, String> env = new HashMap<>(environment);
    env.put("LANG", "en_US.UTF-8");

    Command cmd = new Command(allParams.toArray(new String[0]), env, cwd.toFile());
    try {
      return new CommandRunner(cmd, repoTimeout).withVerbose(verbose).execute();
    } catch (BadExitStatusWithOutputException e) {
      throw new RepoException(
          String.format("Error executing jj: %s\n%s", e.getMessage(), e.getOutput().getStderr()),
          e);
    } catch (CommandException e) {
      throw new RepoException(String.format("Error executing jj: %s", e.getMessage()), e);
    }
  }

  /** Returns the commit IDs matching {@code revset}, newest first. */
  private ImmutableList<String> logCommitIds(String revset) throws RepoException {
    return runJjLog(revset, "commit_id ++ \"\\n\"");
  }

  /** Returns the non-empty output lines of {@code jj log -r revset -T template}. */
  private ImmutableList<String> runJjLog(String revset, String template) throws RepoException {
    CommandOutputWithStatus output =
        executeJj(
            repoDir,
            ImmutableList.of(
                "log", "--no-pager", "--color=never", "--no-graph", "-r", revset, "-T", template));
    return ImmutableList.copyOf(LINE_SPLITTER.split(output.getStdout()));
  }

  private void createRepoDir() throws RepoException {
    try {
      Files.createDirectories(repoDir);
    } catch (IOException e) {
      throw new RepoException("Cannot create JJ repository directory: " + e.getMessage(), e);
    }
  }

  /**
   * Mirrors the working tree from {@code from} into {@code to} while preserving {@code .jj} and
   * {@code .git} metadata directories.
   *
   * <p>Clears existing non-metadata files and directories in {@code to} before copying so that
   * deleted files are pruned and directory-to-file replacements do not collide, without wiping
   * repository metadata in {@code repoDir} or leaking it into {@code targetDir}.
   */
  private static void clearAndCopyWorkingTree(Path from, Path to) throws IOException {
    checkArgument(
        !from.toAbsolutePath().normalize().equals(to.toAbsolutePath().normalize()),
        "Source and destination directories must be different: %s",
        from);
    try (Stream<Path> entries = Files.list(to)) {
      for (Path entry : entries.toList()) {
        if (!METADATA_DIRS.contains(entry.getFileName().toString())) {
          FileUtil.deleteRecursively(entry);
        }
      }
    }
    FileUtil.copyFilesRecursively(
        from, to, CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS, NON_METADATA_GLOB);
  }
}
