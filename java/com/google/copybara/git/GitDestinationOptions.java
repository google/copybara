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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.RepoException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Arguments for {@link GitDestination}, {@link GitOrigin}, and other Git components.
 */
@Parameters(separators = "=")
public final class GitDestinationOptions implements Option {

  private final GeneralOptions generalOptions;
  private final GitOptions gitOptions;

  @VisibleForTesting
  @Parameter(names = "--git-committer-name",
      description = "If set, overrides the committer name for the generated commits in git"
          + " destination.")
  public String committerName = "";

  @VisibleForTesting
  @Parameter(names = "--git-committer-email",
      description = "If set, overrides the committer e-mail for the generated commits in git"
          + " destination.")
  public String committerEmail = "";

  public GitDestinationOptions(GeneralOptions generalOptions,
      GitOptions gitOptions) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
  }

  Author getCommitter() {
    return new Author(committerName, committerEmail);
  }

  @Parameter(names = "--git-destination-url",
      description = "If set, overrides the git destination URL.")
  String url = null;

  @Nullable
  @Parameter(names = "--git-destination-fetch",
      description = "If set, overrides the git destination fetch reference.")
  public String fetch = null;

  @Nullable
  @Parameter(names = "--git-destination-push",
      description = "If set, overrides the git destination push reference.")
  public String push = null;

  @Nullable
  @Parameter(
      names = "--git-destination-path",
      description =
          "If set, the tool will use this directory for the local repository. Note that if the"
              + " directory exists it needs to be a git repository. Copybara will revert any"
              + " staged/unstaged changes. For example, you can override destination url with a"
              + " local non-bare repo (or existing empty folder) with this flag.")
  public String localRepoPath = null;

  @Parameter(names = "--git-destination-last-rev-first-parent",
      description = "Use git --first-parent flag when looking for last-rev in previous commits")
  boolean lastRevFirstParent = false;

  @Parameter(names = "--git-destination-non-fast-forward",
      description = "Allow non-fast-forward pushes to the destination. We only allow this when"
          + " used with different push != fetch references.")
  boolean nonFastForwardPush = false;

  @Parameter(names = "--git-destination-ignore-integration-errors",
      description = "If an integration error occurs, ignore it and continue without the integrate")
  boolean ignoreIntegrationErrors = false;

  @Parameter(names = "--nogit-destination-rebase",
      description = "Don't rebase the change automatically for workflows CHANGE_REQUEST mode")
  public boolean noRebase = false;

  boolean rebaseWhenBaseline() {
    return !noRebase;
  }

  /**
   * Returns a non-bare repo. Either because it uses a custom worktree or because it is a user
   * non-bare repo.
   *
   * <p> The git database (git-dir) might be potentially shared between multiple workflows. This
   * means that the users should force fetch and probably create its own local unique references.
   */
  GitRepository localGitRepo(String url) throws RepoException {
    try {
      if (Strings.isNullOrEmpty(localRepoPath)) {
        return gitOptions.cachedBareRepoForUrl(url)
            .withWorkTree(generalOptions.getDirFactory().newTempDir("git_dest"));
      }
      Path path = Paths.get(localRepoPath);

      if (!Files.exists(path) || (Files.isDirectory(path) && isGitRepoOrEmptyDir(path))) {
        Files.createDirectories(path);
        return gitOptions.initRepo(
            GitRepository.newRepo(
                generalOptions.isVerbose(),
                path,
                gitOptions.getGitEnvironment(generalOptions.getEnvironment()),
                generalOptions.fetchTimeout,
                gitOptions.gitNoVerify));
      }
      throw new RepoException(path + " is not empty and is not a git repository");
    } catch (IOException e) {
      throw new RepoException("Cannot create local repository", e);
    }
  }

  private static boolean isGitRepoOrEmptyDir(Path path) throws IOException {
    try (Stream<Path> stream = Files.list(path)) {
      return Files.exists(path.resolve(".git")) || !stream.findAny().isPresent();
    }
  }

  /**
   * Used internally to be able to traverse the local repo once a migration finishes.
   */
  public String customLocalBranch;

  /**
   * Returns the local branch that will be used for working on the change before pushing.
   */
  String getLocalBranch(String resolvedPush, boolean dryRun) {
    return localRepoPath != null
        ? resolvedPush // This is nicer for the user
        : customLocalBranch != null
            ? customLocalBranch
            : "copybara/resolvedPush-" + UUID.randomUUID() + (dryRun ? "-dryrun" : "");
  }

}
