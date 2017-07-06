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
import com.google.copybara.RepoException;
import com.google.copybara.authoring.Author;
import com.google.copybara.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Arguments for {@link GitDestination}, {@link GitOrigin}, and other Git components.
 */
@Parameters(separators = "=")
public final class GitDestinationOptions implements Option {
  private final Logger logger = Logger.getLogger(GitDestinationOptions.class.getName());

  private final Supplier<GeneralOptions> generalOptions;

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

  public GitDestinationOptions(Supplier<GeneralOptions> generalOptions) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
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
  @Parameter(names = "--git-destination-path",
      description = "If set, the tool will use this directory for the local repository."
          + " Note that the directory will be deleted each time Copybara is run.")
  String localRepoPath = null;

  @Parameter(names = "--git-destination-skip-push",
      description = "If set, the tool will not push to the remote destination")
  boolean skipPush = false;

  @Parameter(names = "--git-destination-last-rev-first-parent",
      description = "Use git --first-parent flag when looking for last-rev in previous commits")
  boolean lastRevFirstParent = false;


  public GitRepository localGitRepo() throws RepoException {
    generalOptions.get().getDirFactory();
    Path path;
    try {
      if (Strings.isNullOrEmpty(localRepoPath)) {
        path = createTempDirectory();
      } else {
        path = Paths.get(localRepoPath);
        if (Files.exists(path)) {
          FileUtil.deleteRecursively(path);
        }
        Files.createDirectories(path);
      }
    } catch (IOException e) {
      throw new RepoException("Cannot create local repository", e);
    }
    return GitRepository.initScratchRepo(
            generalOptions.get().isVerbose(), path, generalOptions.get().getEnvironment());
  }

  private Path createTempDirectory() throws IOException {
    Path dir = generalOptions.get().getDirFactory()
        .newTempDir("copybara-makeScratchClone");
    logger.info(
        String.format("Created temporary folder for scratch repo: %s", dir.toAbsolutePath()));
    return dir;
  }
}
