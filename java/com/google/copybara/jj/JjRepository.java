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

  /** Repository metadata directories that are never copied into or cleared from a working tree. */
  private static final ImmutableSet<String> METADATA_DIRS = ImmutableSet.of(".jj", ".git");

  private static final Glob NON_METADATA_GLOB =
      Glob.createGlob(
          ImmutableList.of("**"),
          METADATA_DIRS.stream().map(dir -> dir + "/**").collect(toImmutableList()));

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
    executeJj(repoDir, ImmutableList.of("commit", "-m", message));
    CommandOutputWithStatus output =
        executeJj(
            repoDir,
            ImmutableList.of(
                "log", "--no-pager", "--color=never", "--no-graph", "-r", "@-", "-T", "commit_id"));
    return new CommitId(output.getStdout().trim());
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
