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

import static com.google.copybara.util.CommandUtil.executeCommand;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.net.PercentEscaper;
import com.google.copybara.EmptyChangeException;
import com.google.copybara.RepoException;
import com.google.copybara.util.BadExitStatusWithOutputException;
import com.google.copybara.util.CommandOutput;
import com.google.copybara.util.CommandOutputWithStatus;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandException;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A class for manipulating Git repositories
 */
public class GitRepository {

  private static final Pattern FULL_URI = Pattern.compile("^[a-z][a-z0-9+-]+://.*$");

  private static final Pattern SHA1_PATTERN = Pattern.compile("[a-f0-9]{7,40}");

  private static final Pattern FAILED_REBASE = Pattern.compile("Failed to merge in the changes");
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
  private static final PercentEscaper PERCENT_ESCAPER = new PercentEscaper(
      "-_", /*plusForSpace=*/ true);

  // Git exits with 128 in several circumstances. For example failed rebase.
  private static final ImmutableRangeSet<Integer> NON_CRASH_ERROR_EXIT_CODES =
      ImmutableRangeSet.<Integer>builder().add(
          Range.closed(1, 10)).add(Range.singleton(128)).build();

  /**
   * The location of the {@code .git} directory. The is also the value of the {@code --git-dir}
   * flag.
   */
  private final Path gitDir;

  private final @Nullable Path workTree;

  private final boolean verbose;
  private final Map<String, String> environment;

  GitRepository(
      Path gitDir, @Nullable Path workTree, boolean verbose, Map<String, String> environment) {
    this.gitDir = Preconditions.checkNotNull(gitDir);
    this.workTree = workTree;
    this.verbose = verbose;
    this.environment = Preconditions.checkNotNull(environment);
  }

  public static GitRepository bareRepo(Path gitDir, Map<String, String> environment,
      boolean verbose) {
    return new GitRepository(gitDir,/*workTree=*/null, verbose, environment);
  }

  /**
   * Create a bare repo in the cache of repos so that it can be reused between migrations.
   */
  static GitRepository bareRepoInCache(String url, Map<String, String> environment,
      boolean verbose, String repoStorage) {
    Path gitRepoStorage = FileSystems.getDefault().getPath(repoStorage);
    Path gitDir = gitRepoStorage.resolve(PERCENT_ESCAPER.escape(url));
    return bareRepo(gitDir, environment, verbose);
  }

  /**
   * Initializes a new repository in a temporary directory using the given environment vars.
   *
   * <p>The new repo is not bare.
   */
  public static GitRepository initScratchRepo(boolean verbose, Map<String, String> environment)
      throws RepoException {
    Path scratchWorkTree;
    try {
      scratchWorkTree = Files.createTempDirectory("copybara-makeScratchClone");
    } catch (IOException e) {
      throw new RepoException("Could not make temporary directory for scratch repo", e);
    }

    return initScratchRepo(verbose, scratchWorkTree, environment);
  }

  /**
   * Initializes a new repository in the given directory. The new repo is not bare.
   */
  @VisibleForTesting
  public static GitRepository initScratchRepo(
      boolean verbose, Path path, Map<String, String> environment) throws RepoException {
    GitRepository repository =
        new GitRepository(path.resolve(".git"), path, verbose, environment);
    repository.git(path, ImmutableList.of("init", "."));
    return repository;
  }

  /**
   * Validate that a refspec is valid.
   *
   * @throws EvalException if the refspec is not valid
   */
  static void validateRefSpec(Location location, Map<String, String> env, Path cwd,
      String refspec) throws EvalException {
    try {
      executeGit(cwd,
          ImmutableList.of("check-ref-format", "--allow-onelevel", "--refspec-pattern", refspec),
          env,
          /*verbose=*/false);
    } catch (BadExitStatusWithOutputException e) {
      throw new EvalException(location, "Invalid refspec: " + refspec);
    } catch (CommandException e) {
      throw new RuntimeException("Error validating refspec", e);
    }
  }

  /**
   * Fetch a reference from a git url.
   *
   * <p>Note that this method doesn't support fetching refspecs that contain local ref path
   * locations. IOW
   * "refs/foo" is allowed but not "refs/foo:remote/origin/foo". Wildcards are also not allowed.
   */
  public GitReference fetchSingleRef(String url, String ref) throws RepoException {
    if (ref.contains(":") || ref.contains("*")) {
      throw new CannotFindReferenceException("Fetching refspecs that"
          + " contain local ref path locations or wildcards is not supported. Invalid ref: " + ref);
    }
    // This is not strictly necessary for some Git repos that allow fetching from any sha1 ref, like
    // servers configured with 'git config uploadpack.allowReachableSHA1InWant true'. Unfortunately,
    // Github doesn't support it. So what we do is fetch the default refspec (see the comment
    // bellow) and hope the sha1 is reachable from heads.
    if (isSha1Reference(ref)) {
      // TODO(copybara-team): For now we get the default refspec, but we should make this
      // configurable. Otherwise it is not going to work with Gerrit.
      fetch(url, /*prune=*/false, /*force=*/true, ImmutableList.of());
      return resolveReference(ref);
    }
    fetch(url, /*prune=*/false, /*force=*/true, ImmutableList.of(ref));
    return resolveReference("FETCH_HEAD");
  }

  /**
   * Fetch zero or more refspecs in the local repository
   *
   * @param url remote git repository url
   * @param prune if remotely non-present refs should be deleted locally
   * @param force force updates even for non fast-forward updates
   * @param refspecs a set refspecs in the form of 'foo' for branches, 'refs/some/ref' or
   * 'refs/foo/bar:refs/bar/foo'.
   * @return the set of fetched references and what action was done ( rejected, new reference,
   * updated, etc.)
   */
  FetchResult fetch(String url, boolean prune, boolean force, Iterable<String> refspecs)
      throws RepoException {

    List<String> args = Lists.newArrayList("fetch", validateUrl(url));
    args.add("--verbose");
    if (prune) {
      args.add("-p");
    }
    if (force) {
      args.add("-f");
    }
    for (String ref : refspecs) {
      try {
        // Validate refspec
        Refspec.create(environment, gitDir, ref,/*location=*/null);
      } catch (EvalException e) {
        throw new RepoException("Invalid refspec passed to fetch: " + e);
      }
      args.add(ref);
    }

    ImmutableMap<String, GitReference> before = showRef();
    git(getCwd(), addGitDirAndWorkTreeParams(args));
    ImmutableMap<String, GitReference> after = showRef();
    return new FetchResult(before, after);
  }

  // TODO(team): Use JGit URIish.java
  private String validateUrl(String url) {
    Preconditions.checkState(FULL_URI.matcher(url).matches(), "URL '%s' is not valid", url);
    return url;
  }

  /**
   * Execute show-ref git command in the local repository and returns a map from reference name to
   * GitReference(SHA-1).
   */
  ImmutableMap<String, GitReference> showRef() throws RepoException {
    ImmutableMap.Builder<String, GitReference> result = ImmutableMap.builder();
    CommandOutput commandOutput = gitAllowNonZeroExit(ImmutableList.of("show-ref"));

    if (!commandOutput.getStderr().isEmpty()) {
      throw new RepoException(String.format(
          "Error executing show-ref on %s git repo:\n%s", getGitDir(), commandOutput.getStderr()));
    }

    for (String line : Splitter.on('\n').split(commandOutput.getStdout())) {
      if (line.isEmpty()) {
        continue;
      }
      List<String> strings = Splitter.on(' ').splitToList(line);
      Preconditions.checkState(strings.size() == 2
          && SHA1_PATTERN.matcher(strings.get(0)).matches(), "Cannot parse line: '%s'", line);
      // Ref -> SHA1
      result.put(strings.get(1), new GitReference(this, strings.get(0)));
    }
    return result.build();
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

  // TODO(malcon): Create a common add method for all 'addX' implementations
  public void addForce(Iterable<String> files) throws RepoException {
    List<String> params = Lists.newArrayList("add", "-f", "--");
    Iterables.addAll(params, files);
    git(getCwd(), addGitDirAndWorkTreeParams(params));
  }

  public void addForceAll() throws RepoException {
    git(getCwd(), addGitDirAndWorkTreeParams(
        Lists.newArrayList("add", "-f", "--all")));
  }

  /**
   * Resolves a git reference to the SHA-1 reference
   */
  public String revParse(String ref) throws RepoException {
    // Runs rev-parse on the reference and remove the extra newline from the output.
    return simpleCommand("rev-parse", ref).getStdout().trim();
  }

  public void rebase(String newBaseline) throws RepoException {
    CommandOutputWithStatus output = gitAllowNonZeroExit(
        ImmutableList.of("rebase", Preconditions.checkNotNull(newBaseline)));

    if (output.getTerminationStatus().success()) {
      return;
    }

    if (FAILED_REBASE.matcher(output.getStderr()).find()) {
      throw new RebaseConflictException(
          "Conflict detected while rebasing " + workTree + " to " + newBaseline
              + ". Git ouput was:\n" + output.getStdout());
    }
    throw new RepoException(output.getStderr());
  }

  void commit(String author, Instant timestamp, String message)
      throws RepoException {
    CommandOutput status = simpleCommand("diff", "--staged");
    if (status.getStdout().trim().isEmpty()) {
      throw new EmptyChangeException("Migration of the revision resulted in an empty change. "
          + "Is the change already migrated?");
    }
    simpleCommand("commit", "--author", author,
        "--date", timestamp.getEpochSecond() + " +0000", "-m", message);
  }

  private Path getCwd() {
    return workTree != null ? workTree : gitDir;
  }

  private List<String> addGitDirAndWorkTreeParams(Iterable<String> argv) {
    Preconditions.checkState(Files.isDirectory(gitDir),
        "git repository dir '%s' doesn't exist or is not a directory", gitDir);

    List<String> allArgv = Lists.newArrayList("--git-dir=" + gitDir);

    if (workTree != null) {
      allArgv.add("--work-tree=" + workTree);
    }
    Iterables.addAll(allArgv, argv);
    return allArgv;
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

    git(gitDir, ImmutableList.of("init", "--bare"));
  }

  /**
   * Runs a {@code git} command with the {@code --git-dir} and (if non-bare) {@code --work-tree}
   * args set, and returns the {@link CommandOutput} if the command execution was successful.
   *
   * <p>Git commands usually write to stdout, but occasionally they write to stderr. It's
   * responsibility of the client to consume the output from the correct source.
   *
   * <p>WARNING: Please consider creating a higher level function instead of calling this method.
   * At some point we will deprecate.
   *
   * @param argv the arguments to pass to {@code git}, starting with the sub-command name
   */
  public CommandOutput simpleCommand(String... argv) throws RepoException {
    return git(getCwd(), addGitDirAndWorkTreeParams(Arrays.asList(argv)));
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
  private CommandOutput git(Path cwd, Iterable<String> params) throws RepoException {
    try {
      return executeGit(cwd, params, environment, verbose);
    } catch (BadExitStatusWithOutputException e) {
      CommandOutput output = e.getOutput();

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

  /**
   * Execute git allowing non-zero exit codes. This will only allow program non-zero exit codes
   * (0-10. The upper bound is arbitrary). And will still fail for exit codes like 127 (Command not
   * found).
   */
  private CommandOutputWithStatus gitAllowNonZeroExit(Iterable<String> params)
      throws RepoException {
    try {
      return executeGit(getCwd(), addGitDirAndWorkTreeParams(params), environment, verbose);
    } catch (BadExitStatusWithOutputException e) {
      CommandOutputWithStatus output = e.getOutput();
      int exitCode = e.getOutput().getTerminationStatus().getExitCode();
      if (NON_CRASH_ERROR_EXIT_CODES.contains(exitCode)) {
        return output;
      }

      throw new RepoException(
          "Error executing 'git': " + e.getMessage() + ". Stderr: \n" + output.getStderr(), e);
    } catch (CommandException e) {
      throw new RepoException("Error executing 'git': " + e.getMessage(), e);
    }
  }

  private static CommandOutputWithStatus executeGit(Path cwd, Iterable<String> params,
      Map<String, String> env, boolean verbose) throws CommandException {
    List<String> allParams = new ArrayList<>(Iterables.size(params) + 1);
    allParams.add(resolveGitBinary(env));
    Iterables.addAll(allParams, params);
    return executeCommand(new Command(
        Iterables.toArray(allParams, String.class), env, cwd.toFile()), verbose);
  }

  /**
   * Returns a String representing the git binary to be executed.
   *
   * <p>The env var {@code GIT_EXEC_PATH} determines where Git looks for its sub-programs, but also
   * the regular git binaries (git, git-upload-pack, etc) are duplicated in {@code GIT_EXEC_PATH}.
   *
   * <p>If the env var is not set, then we will execute "git", that it will be resolved in the path
   * as usual.
   */
  @VisibleForTesting
  static String resolveGitBinary(Map<String, String> environment) {
    if (environment.containsKey("GIT_EXEC_PATH")) {
      return FileSystems.getDefault()
          .getPath(environment.get("GIT_EXEC_PATH"))
          .resolve("git")
          .toString();
    }
    return "git";
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
