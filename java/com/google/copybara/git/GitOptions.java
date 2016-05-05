package com.google.copybara.git;

import com.google.copybara.Option;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Arguments for {@link GitDestination}, {@link GitOrigin}, and other Git components.
 */
@Parameters(separators = "=")
public final class GitOptions implements Option {

  @Parameter(names = "--git-committer-name",
      description = "If set, overrides the committer name for the generated commits.")
  String gitCommitterName = "";

  @Parameter(names = "--git-committer-email",
      description = "If set, overrides the committer e-mail for the generated commits.")
  String gitCommitterEmail = "";

  @Parameter(names = "--git-repo-storage",
      description = "Location of the storage path for git repositories")
  String gitRepoStorage = System.getProperty("user.home") + "/.copybara/repos";

  @Parameter(names = "--git-first-commit",
      description = "Ignore that the fetch reference doesn't exist when pushing to destination")
  boolean gitFirstCommit = false;

  @Parameter(names = "--git-previous-ref", description = "Previous SHA-1 reference used for the migration.")
  String gitPreviousRef = "";
}
