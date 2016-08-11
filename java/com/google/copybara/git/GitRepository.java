// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.copybara.util.CommandUtil.executeCommand;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.copybara.EmptyChangeException;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;
import com.google.copybara.util.BadExitStatusWithOutputException;
import com.google.copybara.util.CommandOutput;
import com.google.copybara.util.CommandOutputWithStatus;
import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * A class for manipulating Git repositories
 */
public class GitRepository {

  private static final Pattern SHA1_PATTERN = Pattern.compile("[a-f0-9]{7,40}");

  // When used as the environment parameter in GitRepository construction it will
  // pass the current process environment as-is.
  @Nullable
  static final Map<String, String> CURRENT_PROCESS_ENVIRONMENT = null;

  private static final Pattern FAILED_REBASE = Pattern.compile("Failed to merge in the changes");
  private static final Pattern NOTHING_TO_COMMIT = Pattern.compile(
      "nothing to commit, working directory clean");
  private static final ImmutableList<Pattern> REF_NOT_FOUND_ERRORS =
      ImmutableList.of(
          Pattern.compile("pathspec '(.+)' did not match any file"),
          Pattern.compile(
              "ambiguous argument '(.+)': unknown revision or path not in the working tree"),
          Pattern.compile("fatal: Couldn't find remote ref ([^\n]+)\n"));

  /**
   * Label to be used for marking the original revision id (Git SHA-1) for migrated commits.
   */
  static final String GIT_ORIGIN_REV_ID = "GitOrigin-RevId";

  /**
   * The location of the {@code .git} directory. The is also the value of the {@code --git-dir}
   * flag.
   */
  private final Path gitDir;

  private final @Nullable Path workTree;

  private final boolean verbose;
  // The environment to be passed to git. When the value is null, it will pass the current
  // process environment as-is.
  @Nullable
  private final ImmutableMap<String, String> environment;

  GitRepository(Path gitDir, @Nullable Path workTree, boolean verbose,
      @Nullable Map<String, String> environment) {
    this.gitDir = Preconditions.checkNotNull(gitDir);
    this.workTree = workTree;
    this.verbose = verbose;
    this.environment = environment == null ? null : ImmutableMap.copyOf(environment);
  }

  public static GitRepository bareRepo(Path gitDir, Options options,
      @Nullable Map<String, String> environment) {
    return new GitRepository(
        gitDir,/*workTree=*/null, options.get(GeneralOptions.class).isVerbose(), environment);
  }

  /**
   * Initializes a new repository in a temporary directory. The new repo is not bare.
   */
  public static GitRepository initScratchRepo(boolean verbose) throws RepoException {
    Path scratchWorkTree;
    try {
      scratchWorkTree = Files.createTempDirectory("copybara-makeScratchClone");
    } catch (IOException e) {
      throw new RepoException("Could not make temporary directory for scratch repo", e);
    }

    return initScratchRepo(verbose, scratchWorkTree);
  }

  /**
   * Initializes a new repository in the given directory. The new repo is not bare.
   */
  @VisibleForTesting
  public static GitRepository initScratchRepo(boolean verbose, Path path) throws RepoException {
    GitRepository repository =
        new GitRepository(path.resolve(".git"), path, verbose, CURRENT_PROCESS_ENVIRONMENT);
    repository.git(path, "init", ".");
    return repository;
  }

  /**
   * Fetch a reference from a git url.
   *
   * <p>Note that this method doesn't support fetching refspecs that contain local ref path
   * locations. IOW
   * "refs/foo" is allowed but not "refs/foo:remote/origin/foo". Wildcards are also not allowed.
   */
  public GitReference fetch(String url, String ref) throws RepoException {
    if (ref.contains(":") || ref.contains("*")) {
      throw new CannotFindReferenceException("Fetching refspecs that"
          + " contain local ref path locations or wildcards is not supported. Invalid ref: " + ref);
    }
    // This is not strictly necessary for some Git repos that allow fetching from any sha1 ref, like
    // servers configured with 'git config uploadpack.allowReachableSHA1InWant true'. Unfortunately,
    // Github doesn't support it. So what we do is fetch the default refspec (see the comment
    // bellow) and hope the sha1 is reachable from heads.
    if (isSha1Reference(ref)) {
      // TODO(malcon): For now we get the default refspec, but we should make this
      // configurable. Otherwise it is not going to work with Gerrit.
      simpleCommand("fetch", "-f", url);
      return resolveReference(ref);
    }
    simpleCommand("fetch", "-f", url, ref);
    return resolveReference("FETCH_HEAD");
  }

  /**
   * Returns an instance equivalent to this one but with a different work tree. This does not
   * initialize or alter the given work tree.
   */
  public GitRepository withWorkTree(Path newWorkTree) {
    return new GitRepository(this.gitDir, newWorkTree, this.verbose, this.environment);
  }

  /**
   * The Git work tree - in a typical Git repo, this is the directory containing the {@code .git}
   * directory. Returns {@code null} for bare repos.
   */
  @Nullable public Path getWorkTree() {
    return workTree;
  }

  public Path getGitDir() {
    return gitDir;
  }

  /**
   * Resolves a git reference to the SHA-1 reference
   */
  public String revParse(String ref) throws RepoException {
    // Runs rev-parse on the reference and remove the extra newline from the output.
    return simpleCommand("rev-parse", ref).getStdout().trim();
  }

  public void rebase(String newBaseline) throws RepoException {
    try {
      simpleCommand("rebase", Preconditions.checkNotNull(newBaseline));
    } catch (RebaseConflictException e) {
      // Improve the message with more context
      throw new RebaseConflictException(
          "Conflict detected while rebasing " + workTree + " to " + newBaseline
              + ". Git ouput was:\n" + e.getMessage());
    }
  }

  /**
   * Runs a {@code git} command with the {@code --git-dir} and (if non-bare) {@code --work-tree}
   * args set, and returns the {@link CommandOutput} if the command execution was successful.
   *
   * <p>Git commands usually write to stdout, but occasionally they write to stderr. It's
   * responsibility of the client to consume the output from the correct source.
   *
   * @param argv the arguments to pass to {@code git}, starting with the sub-command name
   */
  public CommandOutput simpleCommand(String... argv) throws RepoException {
    Preconditions.checkState(Files.isDirectory(gitDir),
        "git repository dir '%s' doesn't exist or is not a directory", gitDir);

    List<String> allArgv = new ArrayList<>();

    allArgv.add("--git-dir=" + gitDir);
    Path cwd = gitDir;
    if (workTree != null) {
      cwd = workTree;
      allArgv.add("--work-tree=" + workTree);
    }

    allArgv.addAll(Arrays.asList(argv));

    return git(cwd, allArgv);
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
   * Invokes {@code git} in the directory given by {@code cwd} against this repository and returns
   * the {@link CommandOutput} if the command execution was successful.
   *
   * <p>Git commands usually write to stdout, but occasionally they write to stderr. It's
   * responsibility of the client to consume the output from the correct source.
   *
   * @param cwd the directory in which to execute the command
   * @param params the argv to pass to Git, excluding the initial {@code git}
   */
  public CommandOutput git(Path cwd, String... params) throws RepoException {
    return git(cwd, Arrays.asList(params));
  }

  /**
   * Invokes {@code git} in the directory given by {@code cwd} against this repository and returns
   * the {@link CommandOutput} if the command execution was successful.
   *
   * <p>Git commands usually write to stdout, but occasionally they write to stderr. It's
   * responsibility of the client to consume the output from the correct source.
   *
   * <p>See also {@link #git(Path, String[])}.
   *
   * @param cwd the directory in which to execute the command
   * @param params params the argv to pass to Git, excluding the initial {@code git}
   */
  public CommandOutput git(Path cwd, Iterable<String> params) throws RepoException {
    List<String> allParams = new ArrayList<>();
    allParams.add("git");
    Iterables.addAll(allParams, params);
    try {
      CommandOutputWithStatus commandOutputWithStatus =
          executeCommand(new Command(allParams.toArray(new String[0]), environment, cwd.toFile()),
              verbose);
      if (commandOutputWithStatus.getTerminationStatus().success()) {
        return commandOutputWithStatus;
      }
      throw new RepoException("Error on git command: " + commandOutputWithStatus.getStderr());
    } catch (BadExitStatusWithOutputException e) {
      CommandOutput output = e.getOutput();

      if (NOTHING_TO_COMMIT.matcher(output.getStdout()).find()) {
        throw new EmptyChangeException("Migration of the revision resulted in an empty change. "
            + "Is the change already migrated?");
      } else if (FAILED_REBASE.matcher(output.getStderr()).find()) {
        System.out.println(output.getStdout());
        throw new RebaseConflictException(output.getStdout());
      }

      for (Pattern error : REF_NOT_FOUND_ERRORS) {
        Matcher matcher = error.matcher(output.getStderr());
        if (matcher.find()) {
          throw new CannotFindReferenceException(
              "Cannot find reference '" + matcher.group(1) + "'", e);
        }
      }

      throw new RepoException(
          "Error executing 'git': " + e.getMessage() + ". Stderr: \n" + output.getStderr(), e);
    } catch (CommandException e) {
      throw new RepoException("Error executing 'git': " + e.getMessage(), e);
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("gitDir", gitDir)
        .add("workTree", workTree)
        .add("verbose", verbose)
        .toString();
  }

  /**
   * Resolve a reference
   *
   * @throws CannotFindReferenceException if it cannot resolve the reference
   */
  GitReference resolveReference(String reference) throws RepoException {
    return new GitReference(this, revParse(reference));
  }

  /**
   * Creates a reference from a complete SHA-1 string without any validation that it exists.
   */
  GitReference createReferenceFromCompleteSha1(String ref) {
    return new GitReference(this, ref);
  }

  boolean isSha1Reference(String ref) {
    return SHA1_PATTERN.matcher(ref).matches();
  }

}
