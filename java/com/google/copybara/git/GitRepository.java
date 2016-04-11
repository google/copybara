package com.google.copybara.git;

import static com.google.copybara.util.CommandUtil.executeCommand;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.PercentEscaper;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;
import com.google.copybara.util.BadExitStatusWithOutputException;
import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandException;
import com.google.devtools.build.lib.shell.CommandResult;

import com.beust.jcommander.internal.Nullable;

import java.io.IOException;
import java.nio.file.FileSystems;
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

  private static final PercentEscaper PERCENT_ESCAPER = new PercentEscaper(
      "-_", /*plusForSpace=*/ true);

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

  /**
   * Url of the repository
   */
  private final String repoUrl;

  private final boolean verbose;

  private GitRepository(
      String gitExecPath, Path gitDir, @Nullable Path workTree, String repoUrl, boolean verbose) {
    this.gitExecPath = Preconditions.checkNotNull(gitExecPath);
    this.gitDir = Preconditions.checkNotNull(gitDir);
    this.workTree = workTree;
    this.repoUrl = Preconditions.checkNotNull(repoUrl);
    this.verbose = verbose;
  }

  /**
   * Constructs a new instance which represents a clone of some repository.
   *
   * @param repoUrl the URL of the repository which this one is a clone of
   */
  public static GitRepository withRepoUrl(String repoUrl, Options options) {
    GitOptions gitConfig = options.getOption(GitOptions.class);

    Path gitRepoStorage = FileSystems.getDefault().getPath(gitConfig.gitRepoStorage);
    Path gitDir = gitRepoStorage.resolve(PERCENT_ESCAPER.escape(repoUrl));

    return new GitRepository(
        gitConfig.gitExecutable,
        gitDir,
        /*workTree=*/null,
        repoUrl,
        options.getOption(GeneralOptions.class).isVerbose());
  }

  /**
   * Initializes a new repository in a temporary directory. The new repo is not bare.
   */
  public static GitRepository initScratchRepo(
      String repoUrl, GitOptions gitOptions, boolean verbose) throws RepoException {
    Path scratchWorkTree;
    try {
      scratchWorkTree = Files.createTempDirectory("copybara-makeScratchClone");
    } catch (IOException e) {
      throw new RepoException("Could not make temporary directory for scratch repo: " + repoUrl, e);
    }

    GitRepository repository = new GitRepository(gitOptions.gitExecutable,
        scratchWorkTree.resolve(".git"), scratchWorkTree, repoUrl, verbose);
    repository.git(scratchWorkTree, "init", ".");
    return repository;
  }

  public String getRepoUrl() {
    return repoUrl;
  }

  /**
   * Returns an instance equivalent to this one but with a different work tree. This does not
   * initialize or alter the given work tree.
   */
  public GitRepository withWorkTree(Path workTree) {
    return new GitRepository(gitExecPath, gitDir, workTree, repoUrl, verbose);
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
   * Creates a worktree with the contents of the ref {@code ref} for the repository {@code repoUrl}
   *
   * <p>Any content in the workdir is removed/overwritten.
   */
  public void checkoutReference(String ref, Path workdir) throws RepoException {
    if (!Files.exists(gitDir)) {
      try {
        Files.createDirectories(gitDir);
      } catch (IOException e) {
        throw new RepoException("Cannot create git directory '" + gitDir + "': " + e.getMessage(), e);
      }

      git(gitDir, "init", "--bare");
      git(gitDir, "remote", "add", "origin", repoUrl);
    }

    git(gitDir, "fetch", "-f", "origin");
    // We don't allow creating local branches tracking remotes. This doesn't work without
    // merging.
    checkRefExists(ref);
    git(workdir, "--git-dir=" + gitDir, "--work-tree=" + workdir, "checkout", "-f", ref);
  }

  private void checkRefExists(String ref) throws RepoException {
    try {
      simpleCommand("rev-parse", "--verify", ref);
    } catch (RepoException e) {
      if (e.getMessage().contains("Needed a single revision")) {
        throw new CannotFindReferenceException("Ref '" + ref + "' does not exist."
            + " If you used a ref like 'master' you should be using 'origin/master' instead");
      }
      throw e;
    }
  }

  /**
   * Invokes {@code git} in the directory given by {@code cwd} against this repository.
   *
   * @param cwd the directory in which to execute the command
   * @param params the argv to pass to Git, excluding the initial {@code git}
   */
  public CommandResult git(Path cwd, String... params) throws RepoException {
    return git(cwd, Arrays.asList(params));
  }

  /**
   * Invokes {@code git} in the directory given by {@code cwd} against this repository. See also
   * {@link #git(Path, String[])}.
   */
  public CommandResult git(Path cwd, List<String> params) throws RepoException {
    List<String> allParams = new ArrayList<>();
    allParams.add(gitExecPath);
    allParams.addAll(params);
    try {
      CommandResult result = executeCommand(
          new Command(
              allParams.toArray(new String[0]),
              ImmutableMap.<String, String>of(),
              cwd.toFile()),
          verbose);
      if (result.getTerminationStatus().success()) {
        return result;
      }
      throw new RepoException("Error on git command: " + new String(result.getStderr()));
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
        ", repoUrl='" + repoUrl + '\'' +
        '}';
  }
}
