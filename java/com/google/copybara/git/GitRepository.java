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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.copybara.util.CommandUtil.executeCommand;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.net.PercentEscaper;
import com.google.copybara.CannotResolveRevisionException;
import com.google.copybara.EmptyChangeException;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.AuthorParser;
import com.google.copybara.authoring.InvalidAuthorException;
import com.google.copybara.util.BadExitStatusWithOutputException;
import com.google.copybara.util.CommandOutput;
import com.google.copybara.util.CommandOutputWithStatus;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.TempDirectoryFactory;
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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * A class for manipulating Git repositories
 */
public class GitRepository {
  private static final Logger logger = Logger.getLogger(GitRepository.class.getName());

  private static final java.util.regex.Pattern SPACES = java.util.regex.Pattern.compile("( |\t)+");

  // TODO(malcon): Make this generic (Using URIish.java)
  private static final Pattern FULL_URI = Pattern.compile(
      "([a-z][a-z0-9+-]+@github.com.*|^[a-z][a-z0-9+-]+://.*)$");

  private static final Pattern LS_TREE_ELEMENT = Pattern.compile(
      "([0-9]{6}) (commit|tag|tree|blob) ([a-f0-9]{40})\t(.*)");

  private static final Pattern LS_REMOTE_OUTPUT_LINE = Pattern.compile("([a-f0-9]{40})\t(.+)");

  private static final Pattern SHA1_PATTERN = Pattern.compile("[a-f0-9]{7,40}");

  private static final Pattern FAILED_REBASE = Pattern.compile("Failed to merge in the changes");
  private static final ImmutableList<Pattern> REF_NOT_FOUND_ERRORS =
      ImmutableList.of(
          Pattern.compile("pathspec '(.+)' did not match any file"),
          Pattern.compile(
              "ambiguous argument '(.+)': unknown revision or path not in the working tree"));

  private static final Pattern FETCH_CANNOT_RESOLVE_ERRORS =
      Pattern.compile(""
          // When fetching a ref like 'refs/foo' fails.
          + "(fatal: Couldn't find remote ref"
          // When fetching a SHA-1 ref
          + "|no such remote ref"
          // Gerrit when fetching
          + "|ERR want .+ not valid)");
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

  private static final Map<Character, StatusCode> CHAR_TO_STATUS_CODE =
      Arrays.stream(StatusCode.values())
          .collect(Collectors.toMap(StatusCode::getCode, Function.identity()));

  GitRepository(
      Path gitDir, @Nullable Path workTree, boolean verbose, Map<String, String> environment) {
    this.gitDir = checkNotNull(gitDir);
    this.workTree = workTree;
    this.verbose = verbose;
    this.environment = checkNotNull(environment);
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
  public static GitRepository initScratchRepo(
      boolean verbose, Map<String, String> environment, TempDirectoryFactory tempDirectoryFactory)
      throws RepoException {
    Path scratchWorkTree;
    try {
      scratchWorkTree = tempDirectoryFactory.newTempDirectory("copybara-makeScratchClone");
      logger.log(Level.INFO,
          String.format("Created temporary folder for scratch repo: %s",
              scratchWorkTree.toAbsolutePath()));
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
    } catch (CommandException e) {
      throw new EvalException(location, "Invalid refspec: " + refspec);
    }
  }

  /**
   * Fetch a reference from a git url.
   *
   * <p>Note that this method doesn't support fetching refspecs that contain local ref path
   * locations. IOW
   * "refs/foo" is allowed but not "refs/foo:remote/origin/foo". Wildcards are also not allowed.
   */
  public GitRevision fetchSingleRef(String url, String ref)
      throws RepoException, CannotResolveRevisionException {
    if (ref.contains(":") || ref.contains("*")) {
      throw new CannotResolveRevisionException("Fetching refspecs that"
          + " contain local ref path locations or wildcards is not supported. Invalid ref: " + ref);
    }
    // This is not strictly necessary for some Git repos that allow fetching from any sha1 ref, like
    // servers configured with 'git config uploadpack.allowReachableSHA1InWant true'. Unfortunately,
    // Github doesn't support it. So what we do is fetch the default refspec (see the comment
    // below) and hope the sha1 is reachable from heads.
    // If we fail to find the SHA-1 with that fetch we fetch the SHA-1 directly and hope the server
    // allows to download it.
    if (isSha1Reference(ref)) {
      fetch(url, /*prune=*/false, /*force=*/true, ImmutableList.of());
      try {
        return resolveReference(ref, /*contextRef=*/ref);
      } catch (RepoException ignore) {
        // Ignore, the fetch below will attempt using the SHA-1.
      }
    }
    fetch(url, /*prune=*/false, /*force=*/true, ImmutableList.of(ref));
    return resolveReference("FETCH_HEAD", /*contextRef=*/ref);
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
      throws RepoException, CannotResolveRevisionException {

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

    ImmutableMap<String, GitRevision> before = showRef();
    CommandOutputWithStatus output = gitAllowNonZeroExit(args);
    if (output.getTerminationStatus().success()) {
      ImmutableMap<String, GitRevision> after = showRef();
      return new FetchResult(before, after);
    }
    if (output.getStderr().isEmpty()
        || FETCH_CANNOT_RESOLVE_ERRORS.matcher(output.getStderr()).find()) {
      throw new CannotResolveRevisionException("Cannot find references: " + refspecs);
    } else {
      throw throwUnknownGitError(output);
    }
  }

  @CheckReturnValue
  public LogCmd log(String referenceExpr) {
    return LogCmd.create(this, referenceExpr);
  }

  /**
   * Runs a git ls-remote from the current directory for a repository url. Assumes the path to the
   * git binary is already set. You don't have to be in a git repository to run this command. Does
   * not work with remote names.
   *
   * @param url - see <repository> in git help ls-remote
   * @param refs - see <refs> in git help ls-remote
   * @param env - determines where the Git binaries are
   * @return - a map of refs to sha1 from the git ls-remote output.
   */
  public static Map<String, String> lsRemote(
      String url, Collection<String> refs, Map<String, String> env)
      throws RepoException {

    ImmutableMap.Builder<String, String> result = ImmutableMap.<String, String>builder();
    List<String> args = Lists.newArrayList("ls-remote", validateUrl(url));
    args.addAll(refs);

    CommandOutputWithStatus output;
    try {
      output = executeGit(FileSystems.getDefault().getPath("."), args, env, false);
    } catch (CommandException e) {
      throw new RepoException(
          String.format("Error running ls-remote for '%s' and refs '%s'", url, refs));
    }
    if (output.getTerminationStatus().success()) {
      for (String line : Splitter.on('\n').split(output.getStdout())) {
        if (line.isEmpty()) {
          continue;
        }
        Matcher matcher = LS_REMOTE_OUTPUT_LINE.matcher(line);
        if (!matcher.matches()) {
          throw new RepoException("Unexpected format for ls-remote output: " + line);
        }
        result.put(matcher.group(2), matcher.group(1));
      }
    }
    return result.build();
  }

  public static Map<String, String> lsRemote(String url, Collection<String> refs)
      throws RepoException {
    return lsRemote(url, refs, System.getenv());
  }

  // TODO(team): Use JGit URIish.java
  static String validateUrl(String url) throws RepoException {
    if (!FULL_URI.matcher(url).matches()) {
      throw new RepoException(String.format("URL '%s' is not valid", url));
    }
    return url;
  }

  /**
   * Execute show-ref git command in the local repository and returns a map from reference name to
   * GitReference(SHA-1).
   */
  ImmutableMap<String, GitRevision> showRef() throws RepoException {
    ImmutableMap.Builder<String, GitRevision> result = ImmutableMap.builder();
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
      result.put(strings.get(1), new GitRevision(this, strings.get(0)));
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

  /**
   * An add command bound to the repo that can be configured and then executed with
   * @{{@link #run()}}.
   */
  public class AddCmd {

    private final boolean force;
    private final boolean all;
    private final Iterable<String> files;

    private AddCmd(boolean force, boolean all, Iterable<String> files) {
      this.force = force;
      this.all = all;
      this.files = checkNotNull(files);
    }

    /** Force the add */
    @CheckReturnValue
    public AddCmd force() {
      return new AddCmd(/*force=*/true, all, files);
    }

    /** Add all the unstagged files to the index */
    @CheckReturnValue
    public AddCmd all() {
      Preconditions.checkState(Iterables.isEmpty(files), "'all' and passing files is incompatible");
      return new AddCmd(force,/*all=*/ true, files);
    }

    /** Configure the files to add to the index */
    @CheckReturnValue
    public AddCmd files(Iterable<String> files) {
      Preconditions.checkState(!all, "'all' and passing files is incompatible");
      return new AddCmd(force, /*all=*/false, files);
    }

    /** Configure the files to add to the index */
    @CheckReturnValue
    public AddCmd files(String... files) {
      return files(ImmutableList.copyOf(files));
    }

    /** Run the git command */
    public void run() throws RepoException {
      List<String> params = Lists.newArrayList("add");
      if (force) {
        params.add("-f");
      }
      if (all) {
        params.add("--all");
      }
      params.add("--");
      Iterables.addAll(params, files);
      git(getCwd(), addGitDirAndWorkTreeParams(params));
    }
  }

  /**
   * Create a git add command that can be configured before execution.
   */
  @CheckReturnValue
  public AddCmd add() {
    return new AddCmd(/*force*/false, /*all*/false, /*files*/ImmutableSet.of());
  }

  /**
   * Get a field from a configuration {@code file} relative to {@link #getWorkTree()}.
   */
  @Nullable
  private String getConfigField(String file, String field) throws RepoException {
    CommandOutputWithStatus out = gitAllowNonZeroExit(
        ImmutableList.of("config", "-f", file, "--get", field));
    if (out.getTerminationStatus().success()) {
      return out.getStdout().trim();
    } else if (out.getTerminationStatus().getExitCode() == 1 && out.getStderr().isEmpty()) {
      return null;
    }
    throw new RepoException("Error executing git config:\n" + out.getStderr());
  }

  /**
   * Resolves a git reference to the SHA-1 reference
   */
  public String parseRef(String ref) throws RepoException, CannotResolveRevisionException {
    // Runs rev-list on the reference and remove the extra newline from the output.
    CommandOutputWithStatus result = gitAllowNonZeroExit(
        ImmutableList.of("rev-list", "-1", ref, "--"));
    if (!result.getTerminationStatus().success()) {
      throw new CannotResolveRevisionException("Cannot find reference '" + ref + "'");
    }
    String sha1 = result.getStdout().trim();
    Verify.verify(SHA1_PATTERN.matcher(sha1).matches(), "Should be resolved to a SHA-1: %s",sha1);
    return sha1;
  }

  public boolean refExists(String ref) throws RepoException {
    try {
      parseRef(ref);
      return true;
    } catch (CannotResolveRevisionException e) {
      return false;
    }
  }

  public void rebase(String newBaseline) throws RepoException {
    CommandOutputWithStatus output = gitAllowNonZeroExit(
        ImmutableList.of("rebase", checkNotNull(newBaseline)));

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

  // DateTimeFormatter.ISO_OFFSET_DATE_TIME might include subseconds, but Git's ISO8601 format does
  // not deal with subseconds (see https://git-scm.com/docs/git-commit#git-commit-ISO8601).
  // We still want to stick to the default ISO format in Git, but don't add the subseconds.
  private static final DateTimeFormatter ISO_OFFSET_DATE_TIME_NO_SUBSECONDS =
      DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ssZ");

  public void commit(String author, ZonedDateTime timestamp, String message)
      throws RepoException, ValidationException {
    CommandOutput status = simpleCommand("diff", "--staged", "--stat");
    if (status.getStdout().trim().isEmpty()) {
      throw new EmptyChangeException("Migration of the revision resulted in an empty change. "
          + "Is the change already migrated?");
    }
    simpleCommand("commit", "--author", author,
        "--date", timestamp.format(ISO_OFFSET_DATE_TIME_NO_SUBSECONDS), "-m", message);
  }

  public List<StatusFile> status() throws RepoException {
    CommandOutput output = git(getCwd(),
        addGitDirAndWorkTreeParams(ImmutableList.of("status", "--porcelain")));
    ImmutableList.Builder<StatusFile> builder = ImmutableList.builder();
    for (String line : Splitter.on('\n').split(output.getStdout())) {
      if (line.isEmpty()) {
        continue;
      }
      // Format 'XY file (-> file)?'
      List<String> split = Splitter.on(" -> ").limit(2).splitToList(line.substring(3));
      String fileName;
      String newFileName;
      if (split.size() == 1) {
        fileName = split.get(0);
        newFileName = null;
      } else {
        fileName = split.get(0);
        newFileName = split.get(1);
      }
      builder.add(
          new StatusFile(fileName, newFileName, toStatusCode(line.charAt(0)),
              toStatusCode(line.charAt(1))));
    }
    return builder.build();
  }

  private StatusCode toStatusCode(char c) {
    return checkNotNull(CHAR_TO_STATUS_CODE.get(c),
        "Cannot find status code for '%s'", c);
  }

  /**
   * Find submodules information for the current repository.
   *
   * @param currentRemoteUrl remote url associated with the repository. It will be used to
   * resolve relative URLs (for example: url = ../foo).
   */
  Iterable<Submodule> listSubmodules(String currentRemoteUrl) throws RepoException {
    ImmutableList.Builder<Submodule> result = ImmutableList.builder();
    String rawOutput = simpleCommand("submodule--helper", "list").getStdout();
    for (String line : Splitter.on('\n').split(rawOutput)) {
      if (line.isEmpty()) {
        continue;
      }
      List<String> fields = Splitter.on(SPACES).splitToList(line);
      String submoduleName = fields.get(3);
      if (Strings.isNullOrEmpty(submoduleName)) {
        throw new RepoException("Empty submodule name for " + line);
      }

      String path = getSubmoduleField(submoduleName, "path");

      if (path == null) {
        throw new RepoException("Path is required for submodule " + submoduleName);
      }
      String url = getSubmoduleField(submoduleName, "url");
      if (url == null) {
        throw new RepoException("Url is required for submodule " + submoduleName);
      }
      String branch = getSubmoduleField(submoduleName, "branch");
      if (branch == null) {
        branch = "master";
      } else if (branch.equals(".")) {
        branch = "HEAD";
      }
      FileUtil.checkNormalizedRelative(path);
      // If the url is relative, construct a url using the parent module remote url.
      if (url.startsWith("../")) {
        url = siblingUrl(currentRemoteUrl, submoduleName, url.substring(3));
      } else if (url.startsWith("./")) {
        url = siblingUrl(currentRemoteUrl, submoduleName, url.substring(2));
      }
      GitRepository.validateUrl(url);
      result.add(new Submodule(url, submoduleName, branch, path));
    }
    return result.build();
  }

  ImmutableList<TreeElement> lsTree(GitRevision reference, String treeish) throws RepoException {
    ImmutableList.Builder<TreeElement> result = ImmutableList.builder();
    String stdout = simpleCommand("ls-tree", reference.getSha1(), "--", treeish).getStdout();
    for (String line : Splitter.on('\n').split(stdout)) {
      if (line.isEmpty()) {
        continue;
      }
      Matcher matcher = LS_TREE_ELEMENT.matcher(line);
      if (!matcher.matches()) {
        throw new RepoException("Unexpected format for ls-tree output: " + line);
      }
      // We ignore the mode for now
      GitObjectType objectType = GitObjectType.valueOf(matcher.group(2).toUpperCase());
      String sha1 = matcher.group(3);
      String path = matcher.group(4)
          // Per ls-tree documentation. Replace those escaped characters.
          .replace("\\\\", "\\").replace("\\t", "\t").replace("\\n", "\n");

      result.add(new TreeElement(objectType, sha1, path));
    }
    return result.build();
  }

  private String siblingUrl(String currentRemoteUrl, String submoduleName, String relativeUrl)
      throws RepoException {
    int idx = currentRemoteUrl.lastIndexOf('/');
    if (idx == -1) {
      throw new RepoException(String.format(
          "Cannot find the parent url for '%s'. But git submodule '%s' is"
              + " configured with url '%s'", currentRemoteUrl, submoduleName, relativeUrl));
    }
    return currentRemoteUrl.substring(0, idx) + "/" + relativeUrl;
  }

  private String getSubmoduleField(String submoduleName, final String field) throws RepoException {
    return getConfigField(".gitmodules", "submodule." + submoduleName + "." + field);
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
      CommandOutputWithStatus output = e.getOutput();

      for (Pattern error : REF_NOT_FOUND_ERRORS) {
        Matcher matcher = error.matcher(output.getStderr());
        if (matcher.find()) {
          throw new RepoException(
              "Cannot find reference '" + matcher.group(1) + "'");
        }
      }

      throw throwUnknownGitError(output);
    } catch (CommandException e) {
      throw new RepoException("Error executing 'git': " + e.getMessage(), e);
    }
  }

  private RepoException throwUnknownGitError(CommandOutputWithStatus output) throws RepoException {
    throw new RepoException(
        "Error executing 'git'(exit code " + output.getTerminationStatus().getExitCode() + ")"
            + ". Stderr: \n" + output.getStderr());
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
      throw throwUnknownGitError(output);
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
   * @throws CannotResolveRevisionException if it cannot resolve the reference
   */
  GitRevision resolveReference(String reference, @Nullable String contextRef)
      throws RepoException, CannotResolveRevisionException {
    // Nothing needs to be resolved, since it is a complete SHA-1. But we
    // check that the reference exists.
    if (GitRevision.COMPLETE_SHA1_PATTERN.matcher(reference).matches()) {
      if (checkSha1Exists(reference)) {
        return new GitRevision(this, reference);
      }
      throw new CannotResolveRevisionException(
          "Cannot find '" + reference + "' object in the repository");
    }
    return new GitRevision(this, parseRef(reference),
                           /*reviewReference=*/null,
                           contextRef,
                           ImmutableMap.of());
  }

  /**
   * Checks if a SHA-1 object exist in the the repository
   */
  private boolean checkSha1Exists(String reference) throws RepoException {
    CommandOutputWithStatus output = gitAllowNonZeroExit(
        ImmutableList.of("cat-file", "-e", reference));
    if (output.getTerminationStatus().success()) {
      return true;
    }
    if (output.getStderr().isEmpty()) {
      return false;
    }
    throw throwUnknownGitError(output);
  }

  /**
   * Creates a reference from a complete SHA-1 string without any validation that it exists.
   */
  GitRevision createReferenceFromCompleteSha1(String ref) {
    return new GitRevision(this, ref);
  }

  private boolean isSha1Reference(String ref) {
    return SHA1_PATTERN.matcher(ref).matches();
  }

  /**
   * Information of a submodule of {@code this} repository.
   */
  static class Submodule {

    private final String url;
    private final String name;
    @Nullable
    private final String branch;
    private final String path;

    private Submodule(String url, String name, String branch, String path) {
      this.url = url;
      this.name = name;
      this.branch = branch;
      this.path = path;
    }

    /**
     * Resolved submodule URL. Urls like './foo' have been already resolved to its corresponding
     * absolute one.
     */
    public String getUrl() {
      return url;
    }

    /** Name of the submodule. */
    public String getName() {
      return name;
    }

    /**
     * Branch associated with the submodule. Supported values: null or '.' (HEAD is used) or
     * a regular reference.
     */
    @Nullable
    public String getBranch() {
      return branch;
    }

    /** Relative path for the checkout of the submodule */
    public String getPath() {
      return path;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("url", url)
          .add("name", name)
          .add("branch", branch)
          .add("path", path)
          .toString();
    }
  }

  static class TreeElement {

    private final GitObjectType type;
    private final String ref;
    private final String path;

    private TreeElement(GitObjectType type, String ref, String path) {
      this.type = checkNotNull(type);
      this.ref = checkNotNull(ref);
      this.path = checkNotNull(path);
    }

    GitObjectType getType() {
      return type;
    }

    String getRef() {
      return ref;
    }

    String getPath() {
      return path;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("type", type)
          .add("ref", ref)
          .add("file", path)
          .toString();
    }
  }

  enum GitObjectType {
    BLOB,
    COMMIT,
    TAG,
    TREE
  }

  static final class StatusFile {

    private final String file;
    @Nullable
    private final String newFileName;
    private final StatusCode indexStatus;
    private final StatusCode workdirStatus;

    @VisibleForTesting
    StatusFile(String file, @Nullable String newFileName,
        StatusCode indexStatus, StatusCode workdirStatus) {
      this.file = checkNotNull(file);
      this.newFileName = newFileName;
      this.indexStatus = checkNotNull(indexStatus);
      this.workdirStatus = checkNotNull(workdirStatus);
    }

    String getFile() {
      return file;
    }

    @Nullable
    String getNewFileName() {
      return newFileName;
    }

    StatusCode getIndexStatus() {
      return indexStatus;
    }

    StatusCode getWorkdirStatus() {
      return workdirStatus;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      StatusFile that = (StatusFile) o;
      return Objects.equals(file, that.file) &&
          Objects.equals(newFileName, that.newFileName) &&
          indexStatus == that.indexStatus &&
          workdirStatus == that.workdirStatus;
    }

    @Override
    public int hashCode() {
      return Objects.hash(file, newFileName, indexStatus, workdirStatus);
    }

    @Override
    public String toString() {
      return Character.toString(indexStatus.getCode())
          + getWorkdirStatus().getCode()
          + " " + file +
          (newFileName != null ? " -> " + newFileName : "");
    }
  }

  enum StatusCode {
    UNMODIFIED(' '),
    MODIFIED('M'),
    ADDED('A'),
    DELETED('D'),
    RENAMED('R'),
    COPIED('C'),
    UPDATED_BUT_UNMERGED('U'),
    UNTRACKED('?'),
    IGNORED('!'),;

    private final char code;

    public char getCode() {
      return code;
    }

    StatusCode(char code) {
      this.code = code;
    }
  }

  /**
   * An object capable of performing a 'git log' operation on a repository and returning a list
   * of {@link GitLogEntry}.
   *
   * <p>By default it returns the body, doesn't include the changed files and does --first-parent.
   */
  public static class LogCmd {

    private static final String COMMIT_FIELD = "commit";
    private static final String PARENTS_FIELD = "parents";
    private static final String AUTHOR_FIELD = "author";
    private static final String AUTHOR_DATE_FIELD = "author_date";
    private static final String COMMITTER_FIELD = "committer";
    private static final String COMMITTER_DATE = "committer_date";
    private static final String BEGIN_BODY = "begin_body";
    private static final String END_BODY = "end_body";
    private static final String COMMIT_SEPARATOR = "\u0001copybara\u0001";
    private static final Pattern UNINDENT = Pattern.compile("\n    ");
    private static final String GROUP = "--\n";
    private final int limit;
    private final ImmutableCollection<String> paths;
    private final String refExpr;

    private final boolean includeStat;
    private final boolean includeBody;
    private boolean firstParent;

    private final GitRepository repo;

    @Nullable
    private final String grepString;

    @CheckReturnValue
    LogCmd(GitRepository repo, String refExpr, int limit, ImmutableCollection<String> paths,
        boolean firstParent, boolean includeStat, boolean includeBody,
        @Nullable String grepString) {
      this.limit = limit;
      this.paths = paths;
      this.refExpr = refExpr;
      this.firstParent = firstParent;
      this.includeStat = includeStat;
      this.includeBody = includeBody;
      this.repo = repo;
      this.grepString = grepString;
    }

    static LogCmd create(GitRepository repository, String refExpr) {
      return new LogCmd(checkNotNull(repository), checkNotNull(refExpr), 0,
          ImmutableList.of(), /*firstParent*/true,/*includeStats=*/false, /*includeBody=*/true,
          /*grepString=*/null);
    }

    /**
     * Limit the query to {@code limit} results. Should be > 0.
     */
    @CheckReturnValue
    public LogCmd withLimit(int limit) {
      Preconditions.checkArgument(limit > 0);
      return new LogCmd(repo, refExpr, limit, paths, firstParent, includeStat, includeBody,
          grepString);
    }

    /**
     * Only query for changes in {@code paths} paths.
     */
    @CheckReturnValue
    public LogCmd withPaths(ImmutableCollection<String> paths) {
      return new LogCmd(repo, refExpr, limit, paths, firstParent, includeStat, includeBody,
          grepString);
    }

    /**
     * Set if --first-parent should be used in 'git log'.
     */
    @CheckReturnValue
    public LogCmd firstParent(boolean firstParent) {
      return new LogCmd(repo, refExpr, limit, paths, firstParent, includeStat, includeBody,
          grepString);
    }

    /**
     * If files affected by the commit should be included in the response.
     */
    @CheckReturnValue
    public LogCmd includeFiles(boolean includeStat) {
      return new LogCmd(repo, refExpr, limit, paths, firstParent, includeStat, includeBody,
          grepString);
    }

    /**
     * If the body (commit message) should be included in the response.
     */
    @CheckReturnValue
    public LogCmd includeBody(boolean includeBody) {
      return new LogCmd(repo, refExpr, limit, paths, firstParent, includeStat, includeBody,
          grepString);
    }

    /**
     * Look only for messages thatMatches grep expression.
     */
    @CheckReturnValue
    public LogCmd grep(@Nullable String grepString) {
      return new LogCmd(repo, refExpr, limit, paths, firstParent, includeStat, includeBody,
          grepString);
    }

    /**
     * Run 'git log' and returns zero or more {@link GitLogEntry}.
     */
    public ImmutableList<GitLogEntry> run() throws RepoException {
      List<String> cmd = Lists.newArrayList("log", "--no-color", createFormat(includeBody));

      if (limit > 0) {
        cmd.add("-" + limit);
      }

      if (includeStat) {
        cmd.add("--name-only");
        // Don't show changes as renames, otherwise --name-only shows only the new name.
        cmd.add("--no-renames");
      }

      if (firstParent) {
        cmd.add("--first-parent");
      }

      if (!Strings.isNullOrEmpty(grepString) ){
        cmd.add("--grep");
        cmd.add(grepString);
      }

      cmd.add(refExpr);

      if (!paths.isEmpty()) {
        cmd.add("--");
        cmd.addAll(paths);
      }

      CommandOutput output = repo.simpleCommand(cmd.toArray(new String[cmd.size()]));
      return parseLog(output.getStdout(), includeBody);
    }

    private ImmutableList<GitLogEntry> parseLog(String log, boolean includeBody)
        throws RepoException {
      // No changes. We cannot know until we run git log since fromRef can be null (HEAD)
      if (log.isEmpty()) {
        return ImmutableList.of();
      }

      ImmutableList.Builder<GitLogEntry> commits = ImmutableList.builder();
      for (String msg : Splitter.on("\n" + COMMIT_SEPARATOR).
          split(log.substring(COMMIT_SEPARATOR.length()))) {

        List<String> groups = Splitter.on("\n" + GROUP).splitToList(msg);

        Map<String, String> fields = Splitter.on("\n")
            .withKeyValueSeparator("=").split(groups.get(0));

        String body = null;
        if (includeBody) {
          body = UNINDENT.matcher(groups.get(1)).replaceAll("\n");
          body = body.substring(BEGIN_BODY.length() + 1, body.length() - END_BODY.length() - 1);
          // Copybara assumes \n as a separator in many places.
          body = body.replace("\r\n", "\n");
        }

        ImmutableSet<String> files = includeStat
            ? ImmutableSet.copyOf(Splitter.on("\n").omitEmptyStrings().split(groups.get(2)))
            : null;

        ImmutableList.Builder<GitRevision> parents = ImmutableList.builder();
        for (String parent : Splitter.on(" ").omitEmptyStrings()
            .split(getField(fields, PARENTS_FIELD))) {
          parents.add(repo.createReferenceFromCompleteSha1(parent));
        }

        String commit = getField(fields, COMMIT_FIELD);
        try {
          commits.add(new GitLogEntry(
              repo.createReferenceFromCompleteSha1(commit), parents.build(),
              AuthorParser.parse(getField(fields, AUTHOR_FIELD)),
              AuthorParser.parse(getField(fields, COMMITTER_FIELD)),
              ZonedDateTime.parse(getField(fields, AUTHOR_DATE_FIELD)),
              ZonedDateTime.parse(getField(fields, COMMITTER_DATE)),
              body, files));
        } catch (InvalidAuthorException e) {
          throw new RepoException("Error in commit '" + commit + "'. Invalid author.", e);
        }
      }
      return commits.build();
    }

    private String getField(Map<String, String> fields, String field) {
      return Preconditions.checkNotNull(fields.get(field), "%s not present", field);
    }

    /**
     * We use a custom format that allows us easy parsing and be tolerant to random text in the
     * body (That is the reason why we indent the body).
     *
     * <p>We also use \u0001 as commit separator to prevent a file being confused as the separator.
     */
    private String createFormat(boolean includeBody) {
      return ("--format=" + COMMIT_SEPARATOR
          + COMMIT_FIELD + "=%H\n"
          + PARENTS_FIELD + "=%P\n"
          + AUTHOR_FIELD + "=%an <%ae>\n"
          + AUTHOR_DATE_FIELD + "=%aI\n"
          + COMMITTER_FIELD + "=%cn <%ce>\n"
          + COMMITTER_DATE + "=%cI\n"
          + GROUP
          // Body is padded by 4 spaces.
          + (includeBody ? BEGIN_BODY + "\n" + "%w(0,4,4)%B%w(0,0,0)\n" + END_BODY + "\n" : "\n")
          + GROUP)
          .replace("\n", "%n").replace("\u0001", "%x01");
    }
  }

  /**
   * An object that represent a commit as returned by 'git log'.
   */
  public static class GitLogEntry {

    private final GitRevision commit;
    private final ImmutableList<GitRevision> parents;
    private final Author author;
    private final Author committer;
    private final ZonedDateTime authorDate;
    private final ZonedDateTime commitDate;
    @Nullable
    private final String body;
    @Nullable
    private final ImmutableSet<String> files;

    GitLogEntry(GitRevision commit, ImmutableList<GitRevision> parents,
        Author author, Author committer, ZonedDateTime authorDate, ZonedDateTime commitDate,
        @Nullable String body, @Nullable ImmutableSet<String> files) {
      this.commit = commit;
      this.parents = parents;
      this.author = author;
      this.committer = committer;
      this.authorDate = authorDate;
      this.commitDate = commitDate;
      this.body = body;
      this.files = files;
    }

    public GitRevision getCommit() {
      return commit;
    }

    public List<GitRevision> getParents() {
      return parents;
    }

    public Author getAuthor() {
      return author;
    }

    public Author getCommitter() {
      return committer;
    }

    public ZonedDateTime getAuthorDate() {
      return authorDate;
    }

    public ZonedDateTime getCommitDate() {
      return commitDate;
    }

    @Nullable
    public String getBody() {
      return body;
    }

    @Nullable
    public ImmutableSet<String> getFiles() {
      return files;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("commit", commit)
          .add("parents", parents)
          .add("author", author)
          .add("committer", committer)
          .add("authorDate", authorDate)
          .add("commitDate", commitDate)
          .add("body", body)
          .toString();
    }
  }
}
