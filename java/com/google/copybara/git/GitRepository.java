package com.google.copybara.git;

import static com.google.copybara.util.CommandUtil.executeCommand;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;
import com.google.copybara.util.BadExitStatusWithOutputException;
import com.google.copybara.util.CommandOutput;
import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandException;

import javax.annotation.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class for manipulating Git repositories
 */
public final class GitRepository {

  private static final ImmutableList<Pattern> REF_NOT_FOUND_ERRORS =
      ImmutableList.of(
          Pattern.compile("pathspec '(.+)' did not match any file"),
          Pattern.compile("fatal: Couldn't find remote ref ([^\n]+)\n"));

  /**
   * Git tool path. A String on pourpose so that we can use the 'git' on PATH.
   */
  private final String gitExecPath;

  /**
   * The location of the {@code .git} directory. The is also the value of the {@code --git-dir}
   * flag.
   */
  private final Path gitDir;

  private final @Nullable Path workTree;

  private final boolean verbose;

  public GitRepository(String gitExecPath, Path gitDir, @Nullable Path workTree, boolean verbose) {
    this.gitExecPath = Preconditions.checkNotNull(gitExecPath);
    this.gitDir = Preconditions.checkNotNull(gitDir);
    this.workTree = workTree;
    this.verbose = verbose;
  }

  public static GitRepository bareRepo(Path gitDir, Options options) {
    GitOptions gitConfig = options.getOption(GitOptions.class);

    return new GitRepository(
        gitConfig.gitExecutable,
        gitDir,
        /*workTree=*/null,
        options.getOption(GeneralOptions.class).isVerbose());
  }

  /**
   * Initializes a new repository in a temporary directory. The new repo is not bare.
   */
  public static GitRepository initScratchRepo(
      GitOptions gitOptions, boolean verbose) throws RepoException {
    Path scratchWorkTree;
    try {
      scratchWorkTree = Files.createTempDirectory("copybara-makeScratchClone");
    } catch (IOException e) {
      throw new RepoException("Could not make temporary directory for scratch repo", e);
    }

    GitRepository repository = new GitRepository(
        gitOptions.gitExecutable, scratchWorkTree.resolve(".git"), scratchWorkTree, verbose);
    repository.git(scratchWorkTree, "init", ".");
    return repository;
  }

  /**
   * Returns an instance equivalent to this one but with a different work tree. This does not
   * initialize or alter the given work tree.
   */
  public GitRepository withWorkTree(Path newWorkTree) {
    return new GitRepository(this.gitExecPath, this.gitDir, newWorkTree, this.verbose);
  }

  /**
   * Runs a {@code git} command with the {@code --git-dir} and (if non-bare) {@code --work-tree}
   * args set.
   *
   * @param argv the arguments to pass to {@code git}, starting with the sub-command name
   */
  public void simpleCommand(String... argv) throws RepoException {
    List<String> allArgv = new ArrayList<String>();

    allArgv.add("--git-dir=" + gitDir);
    Path cwd = gitDir;
    if (workTree != null) {
      cwd = workTree;
      allArgv.add("--work-tree=" + workTree);
    }

    allArgv.addAll(Arrays.asList(argv));

    git(cwd, allArgv);
  }

  /**
   * Initializes the {@code .git} directory of this repository as a new repository with zero
   * commits.
   */
  public void initGitDir() throws RepoException {
    try {
      Files.createDirectories(gitDir);
    } catch (IOException e) {
      throw new RepoException("Cannot create git directory '" + gitDir + "': " + e.getMessage(), e);
    }

    git(gitDir, "init", "--bare");
  }

  /**
   * Invokes {@code git} in the directory given by {@code cwd} against this repository.
   *
   * @param cwd the directory in which to execute the command
   * @param params the argv to pass to Git, excluding the initial {@code git}
   */
  public CommandOutput git(Path cwd, String... params) throws RepoException {
    return git(cwd, Arrays.asList(params));
  }

  /**
   * Invokes {@code git} in the directory given by {@code cwd} against this repository. See also
   * {@link #git(Path, String[])}.
   */
  public CommandOutput git(Path cwd, Iterable<String> params) throws RepoException {
    List<String> allParams = new ArrayList<>();
    allParams.add(gitExecPath);
    Iterables.addAll(allParams, params);
    try {
      CommandOutput result = executeCommand(
          new Command(
              allParams.toArray(new String[0]),
              ImmutableMap.<String, String>of(),
              cwd.toFile()),
          verbose);
      if (result.getTerminationStatus().success()) {
        return result;
      }
      throw new RepoException("Error on git command: " + result.getStderr());
    } catch (BadExitStatusWithOutputException e) {
      String stderr = e.getStdErr();

      for (Pattern error : REF_NOT_FOUND_ERRORS) {
        Matcher matcher = error.matcher(stderr);
        if (matcher.find()) {
          throw new CannotFindReferenceException(
              "Cannot find reference '" + matcher.group(1) + "'", e);
        }
      }

      throw new RepoException("Error executing '" + gitExecPath + "': " + e.getMessage()
          + ". Stderr: \n" + e.getStdErr(), e);
    } catch (CommandException e) {
      throw new RepoException("Error executing '" + gitExecPath + "': " + e.getMessage(), e);
    }
  }

  @Override
  public String toString() {
    return "GitRepository{" +
        "gitExecPath='" + gitExecPath + '\'' +
        ", gitDir='" + gitDir + '\'' +
        ", workTree='" + workTree + '\'' +
        ", verbose=" + verbose +
        '}';
  }
}
