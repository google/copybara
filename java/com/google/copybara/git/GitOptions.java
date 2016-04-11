package com.google.copybara.git;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Arguments for GitRepository
 */
@Parameters(separators = "=")
public final class GitOptions {

  @Parameter(names = "--git-executable", description = "Location of the git executable")
  String gitExecutable = "git";

  @Parameter(names = "--git-repo-storage",
      description = "Location of the storage path for git repositories")
  String gitRepoStorage = System.getProperty("user.home") + "/.copybara/repos";

  @Parameter(names = "--git-first-commit",
      description = "Ignore that the fetch reference doesn't exist when pushing to destination")
  boolean gitFirstCommit = false;
}
