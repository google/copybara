package com.google.copybara.git;

import static com.google.copybara.util.CommandUtil.executeCommand;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.PercentEscaper;
import com.google.copybara.RepoException;
import com.google.copybara.util.BadExitStatusWithOutputException;
import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandException;
import com.google.devtools.build.lib.shell.CommandResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class for manipulating Git repositories
 */
public class GitRepository {

  private static final Pattern RefNotFoundError =
      Pattern.compile("pathspec '(.+)' did not match any file");

  private static final PercentEscaper PERCENT_ESCAPER = new PercentEscaper(
      "-_", /*plusForSpace=*/ true);

  /**
   * Base directory where all the repositories will be stored. Note that this is only for storing
   * the repositories, not the work trees.
   */
  private final Path baseReposDir;
  /**
   * Git tool path. A String on pourpose so that we can use the 'git' on PATH.
   */
  private final String gitExecPath;

  private final boolean verbose;

  public GitRepository(Path baseReposDir, String gitExecPath, boolean verbose) {
    this.baseReposDir = Preconditions.checkNotNull(baseReposDir);
    this.gitExecPath = Preconditions.checkNotNull(gitExecPath);
    this.verbose = verbose;
  }

  /**
   * Creates a worktree with the contents of the ref {@code ref} for the repository {@code repoUrl}
   *
   * <p>Any content in the workdir is removed/overwritten.
   */
  public void prepare(String repoUrl, String ref, Path workdir) throws RepoException {
    String dirName = PERCENT_ESCAPER.escape(repoUrl);
    Path repoDir = baseReposDir.resolve(dirName);
    if (!Files.exists(repoDir)) {
      git(baseReposDir, "init", "--bare", repoDir.toString());
      git(repoDir, "remote", "add", "origin", repoUrl);
    }

    git(repoDir, "fetch", "-f", "origin");
    // We don't allow creating local branches tracking remotes. This doesn't work without
    // merging.
    checkRefExists(repoDir, ref);
    git(workdir, "--git-dir=" + repoDir, "--work-tree=" + workdir, "checkout", "-f", ref);
  }

  private void checkRefExists(Path repoDir, String ref) throws RepoException {
    try {
      git(repoDir, "rev-parse", "--verify", ref);
    } catch (RepoException e) {
      if (e.getMessage().contains("Needed a single revision")) {
        throw new RepoException("Ref '" + ref + "' does not exist."
            + " If you used a ref like 'master' you should be using 'origin/master' instead");
      }
      throw e;
    }
  }

  @VisibleForTesting
  CommandResult git(Path cwd, String... params) throws RepoException {
    String[] cmd = new String[params.length + 1];
    cmd[0] = gitExecPath;
    System.arraycopy(params, 0, cmd, 1, params.length);
    try {
      CommandResult result = executeCommand(
          new Command(cmd, ImmutableMap.of(), cwd.toFile()), verbose);
      if (result.getTerminationStatus().success()) {
        return result;
      }
      throw new RepoException("Error on git command: " + new String(result.getStderr()));
    } catch (BadExitStatusWithOutputException e) {
      Matcher matcher = RefNotFoundError.matcher(e.getStdErr());
      if (matcher.find()) {
        throw new RepoException("Cannot find reference '" + matcher.group(1) + "'", e);
      }
      throw new RepoException("Error executing '" + gitExecPath + "': " + e.getMessage()
          + ". Stderr: \n" + e.getStdErr(), e);
    } catch (CommandException e) {
      throw new RepoException("Error executing '" + gitExecPath + "': " + e.getMessage(), e);
    }
  }
}
