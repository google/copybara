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
import static com.google.copybara.exception.ValidationException.checkCondition;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static com.google.copybara.util.CommandRunner.NO_INPUT;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.AuthorParser;
import com.google.copybara.authoring.InvalidAuthorException;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitCredential.UserPassword;
import com.google.copybara.util.BadExitStatusWithOutputException;
import com.google.copybara.util.CommandOutput;
import com.google.copybara.util.CommandOutputWithStatus;
import com.google.copybara.util.CommandRunner;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.RepositoryUtil;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * A class for manipulating Git repositories
 */
public class GitRepository {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final Duration DEFAULT_FETCH_TIMEOUT = Duration.ofMinutes(15);

  // TODO(malcon): Make this generic (Using URIish.java)
  private static final Pattern FULL_URI = Pattern.compile(
      "([a-z][a-z0-9+-]+@[a-zA-Z0-9_.-]+(:.+)?|^[a-z][a-z0-9+-]+://.*)$");

  private static final Pattern LS_TREE_ELEMENT = Pattern.compile(
      "([0-9]{6}) (commit|tag|tree|blob) ([a-f0-9]{40})\t(.*)");

  private static final Pattern LS_REMOTE_OUTPUT_LINE = Pattern.compile("([a-f0-9]{40})\t(.+)");

  private static final Pattern SHA1_PATTERN = Pattern.compile("[a-f0-9]{6,40}");

  private static final Pattern FAILED_REBASE = Pattern.compile("Failed to merge in the changes");
  private static final ImmutableList<Pattern> REF_NOT_FOUND_ERRORS =
      ImmutableList.of(
          Pattern.compile("pathspec '(.+)' did not match any file"),
          Pattern.compile(
              "ambiguous argument '(.+)': unknown revision or path not in the working tree"));

  private static final Pattern FETCH_CANNOT_RESOLVE_ERRORS =
      Pattern.compile(
          ""
              // When fetching a ref like 'refs/foo' fails.
              + "(fatal: [Cc]ouldn't find remote ref"
              // When fetching a SHA-1 ref
              + "|no such remote ref"
              // New output for fetching (git version 2.17)
              + "|fatal: no matching remote head"
              // Local fetch for a SHA-1 fails
              + "|upload-pack: not our ref"
              // Gerrit when fetching
              + "|ERR want .+ not valid)");
  private static final Pattern NO_GIT_REPOSITORY =
      Pattern.compile("does not appear to be a git repository");
  private static final Pattern PROTECTED_BRANCH =
      Pattern.compile(
          ""
              // Protected brach errors in GitHub
              + "([Pp]rotected branch hook declined)");


  /**
   * Label to be used for marking the original revision id (Git SHA-1) for migrated commits.
   */
  public static final String GIT_ORIGIN_REV_ID = "GitOrigin-RevId";

  // Git exits with 128 in several circumstances. For example failed rebase.
  private static final ImmutableRangeSet<Integer> NON_CRASH_ERROR_EXIT_CODES =
      ImmutableRangeSet.<Integer>builder().add(
          Range.closed(1, 10)).add(Range.singleton(128)).build();

  /**
   * We cannot control the repo storage location, but we can limit the number of characters of the
   * repo folder name.
   */
  private static final int  DEFAULT_MAX_LOG_LINES = 4_000;
  public static final String GIT_DESCRIBE_REQUESTED_VERSION = "GIT_DESCRIBE_REQUESTED_VERSION";
  public static final String GIT_DESCRIBE_CHANGE_VERSION = "GIT_DESCRIBE_CHANGE_VERSION";

  /**
   * The location of the {@code .git} directory. The is also the value of the {@code --git-dir}
   * flag.
   */
  private final Path gitDir;

  @Nullable
  private final Path workTree;

  private final boolean verbose;
  private final GitEnvironment gitEnv;
  private final Duration fetchTimeout;

  private static final Map<Character, StatusCode> CHAR_TO_STATUS_CODE =
      Arrays.stream(StatusCode.values())
          .collect(Collectors.toMap(StatusCode::getCode, Function.identity()));

  protected GitRepository(
      Path gitDir, @Nullable Path workTree, boolean verbose, GitEnvironment gitEnv,
      Duration fetchTimeout) {
    this.gitDir = checkNotNull(gitDir);
    this.workTree = workTree;
    this.verbose = verbose;
    this.gitEnv = checkNotNull(gitEnv);
    this.fetchTimeout = checkNotNull(fetchTimeout);
  }

  /** Creates a new repository in the given directory. The new repo is not bare. */
  public static GitRepository newRepo(boolean verbose, Path path, GitEnvironment gitEnv,
      Duration fetchTimeout) {
    return new GitRepository(path.resolve(".git"), path, verbose, gitEnv, fetchTimeout);
  }

  /**
   * Creates a new repository in the given directory with a default fetch timeout. The new repo is
   * not bare.
   */
  public static GitRepository newRepo(boolean verbose, Path path, GitEnvironment gitEnv) {
    return newRepo(verbose, path, gitEnv, DEFAULT_FETCH_TIMEOUT);
  }

  /** Create a new bare repository */
  public static GitRepository newBareRepo(Path gitDir, GitEnvironment gitEnv, boolean verbose,
      Duration fetchTimeout) {
    return new GitRepository(gitDir, /*workTree=*/ null, verbose, gitEnv, fetchTimeout);
  }

  /**
   * Get the version of git that will be used for running migrations. Returns empty if git cannot be
   * found.
   *
   * @param gitEnv
   */
  private static Optional<String> version(GitEnvironment gitEnv) {
    try {
      String version =
          executeGit(
                  Paths.get(StandardSystemProperty.USER_DIR.value()),
                  ImmutableList.of("version"),
                  gitEnv,
                  /*verbose=*/ false)
              .getStdout();
      return Optional.of(version);
    } catch (CommandException e) {
      return Optional.empty();
    }
  }

  /**
   * Validate that a refspec is valid.
   *
   * @throws InvalidRefspecException if the refspec is not valid
   */
  static void validateRefSpec(GitEnvironment gitEnv, Path cwd, String refspec)
      throws InvalidRefspecException {
    try {
      executeGit(
          cwd,
          ImmutableList.of("check-ref-format", "--allow-onelevel", "--refspec-pattern", refspec),
          gitEnv,
          /*verbose=*/ false);
    } catch (CommandException e) {
      Optional<String> version = version(gitEnv);
      throw new InvalidRefspecException(
          version
              .map(s -> "Invalid refspec: " + refspec)
              .orElseGet(
                  () ->
                      String.format("Cannot find git binary at '%s'", gitEnv.resolveGitBinary())));
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
      throws RepoException, ValidationException {
    return fetchSingleRefWithTags(url, ref, /*fetchTags=*/false);
  }

  public GitRevision fetchSingleRefWithTags(String url, String ref, boolean fetchTags)
      throws RepoException, ValidationException {
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
      // Tags are fetched by the default refspec
      fetch(url, /*prune=*/false, /*force=*/true, ImmutableList.of());
      try {
        return resolveReferenceWithContext(ref, /*contextRef=*/ref, url);
      } catch (RepoException | CannotResolveRevisionException ignore) {
        // Ignore, the fetch below will attempt using the SHA-1.
      }
    }

    if (fetchTags) {
      fetch(url, /*prune=*/false, /*force=*/true,
          ImmutableList.of(ref + ":refs/copybara_fetch/" + ref, "refs/tags/*:refs/tags/*"));
      return resolveReferenceWithContext("refs/copybara_fetch/" + ref, /*contextRef=*/ref, url);
    } else {
      fetch(
          url,
          /*prune=*/ false,
          /*force=*/ true,
          ImmutableList.of(ref + ":refs/copybara_fetch/" + ref));
      return resolveReferenceWithContext("refs/copybara_fetch/" + ref, /*contextRef=*/ref, url);
    }
  }

  public GitRevision addDescribeVersion(GitRevision rev) throws RepoException {
    return rev.withLabels(ImmutableListMultimap.of(GIT_DESCRIBE_REQUESTED_VERSION, describe(rev)));
  }

  public String describe(GitRevision rev) throws RepoException {
    try {
      return simpleCommand("describe", "--", rev.getSha1()).getStdout().trim();
    } catch (RepoException e) {
      logger.atWarning()
          .withCause(e).log("Cannot get describe version for commit " + rev.getSha1());
      return simpleCommand("describe", "--always", "--", rev.getSha1()).getStdout().trim();
    }
  }

  public String showDiff(String referenceFrom, String referenceTo) throws RepoException{
      Preconditions.checkNotNull(referenceFrom, "Parameter referenceFrom should not be null");
      Preconditions.checkNotNull(referenceTo, "Parameter referenceTo should not be null");
      return simpleCommand("diff", referenceFrom, referenceTo).getStdout();
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
  public FetchResult fetch(String url, boolean prune, boolean force, Iterable<String> refspecs)
      throws RepoException, ValidationException {

    List<String> args = Lists.newArrayList("fetch", validateUrl(url));
    args.add("--verbose");
    // This shows progress in the log if not attached to a terminal
    args.add("--progress");
    if (prune) {
      args.add("-p");
    }
    if (force) {
      args.add("-f");
    }

    List<String> requestedRefs = new ArrayList<>();
    for (String ref : refspecs) {
      // Validates refspec:
      Refspec refSpec = createRefSpec(ref);
      requestedRefs.add(refSpec.getOrigin());
      args.add(ref);
    }

    ImmutableMap<String, GitRevision> before = showRef();
    CommandOutputWithStatus output = gitAllowNonZeroExit(NO_INPUT, args, fetchTimeout);
    if (output.getTerminationStatus().success()) {
      ImmutableMap<String, GitRevision> after = showRef();
      return new FetchResult(before, after);
    }
    if (output.getStderr().isEmpty()
        || FETCH_CANNOT_RESOLVE_ERRORS.matcher(output.getStderr()).find()) {
      throw new CannotResolveRevisionException("Cannot find reference(s): " + requestedRefs);
    }
    if (NO_GIT_REPOSITORY.matcher(output.getStderr()).find()) {
      throw new CannotResolveRevisionException(
          String.format("Invalid Git repository: %s. Error: %s", url, output.getStderr()));
    }
    if (output.getStderr().contains(
        "Server does not allow request for unadvertised object")) {
      throw new CannotResolveRevisionException(
          String.format("%s: %s", url, output.getStderr().trim()));
    }
    checkCondition(!output.getStderr().contains("Repository not found"),
        "%s: %s", url, output.getStderr());
    throw throwUnknownGitError(output, args);
  }

  /**
   * Create a refspec from a string
   */
  public Refspec createRefSpec(String ref) throws ValidationException {
    // Validate refspec
    return Refspec.create(gitEnv, gitDir, ref);
  }

  @CheckReturnValue
  public LogCmd log(String referenceExpr) {
    return LogCmd.create(this, referenceExpr);
  }

  @CheckReturnValue
  public PushCmd push() {
    return new PushCmd(this, /*url=*/null, ImmutableList.of(), /*prune=*/false);
  }

  @CheckReturnValue
  public TagCmd tag(String tagName) {
    return new TagCmd(this, tagName, /*tagMessage=*/null, false);
  }

  /**
   * Runs a git ls-remote from the current directory for a repository url. Assumes the path to the
   * git binary is already set. You don't have to be in a git repository to run this command. Does
   * not work with remote names.
   *
   * @param url - see <repository> in git help ls-remote
   * @param refs - see <refs> in git help ls-remote
   * @param gitEnv - determines where the Git binaries are
   * @param maxLogLines - Limit log lines to the number specified. -1 for unlimited
   * @return - a map of refs to sha1 from the git ls-remote output.
   * @throws RepoException if the operation fails
   */
  public static Map<String, String> lsRemote(
      String url, Collection<String> refs, GitEnvironment gitEnv, int maxLogLines)
      throws RepoException {

    ImmutableMap.Builder<String, String> result = ImmutableMap.builder();
    List<String> args;
    try {
      args = Lists.newArrayList("ls-remote", validateUrl(url));
    } catch (ValidationException e) {
      throw new RepoException("Invalid url: " + url, e);
    }
    args.addAll(refs);

    CommandOutputWithStatus output;
    try {
      output = executeGit(FileSystems.getDefault().getPath("."), args, gitEnv, false, maxLogLines);
    } catch (BadExitStatusWithOutputException e) {
      throw new RepoException(
          String.format("Error running ls-remote for '%s' and refs '%s': Exit code %s, Output:\n%s",
              url, refs, e.getOutput().getTerminationStatus().getExitCode(),
              e.getOutput().getStderr()), e);
    } catch (CommandException e) {
      throw new RepoException(
          String.format("Error running ls-remote for '%s' and refs '%s'", url, refs), e);
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

  /**
   * Same as {@link #lsRemote(String, Collection, GitEnvironment, int)} but using this repository
   * environment and {@link #DEFAULT_MAX_LOG_LINES} as max number of log lines.
   *
   * @param refs - see <refs> in git help ls-remote
   * @return - a map of refs to sha1 from the git ls-remote output.
   * @throws RepoException if the operation fails
   */
  public Map<String, String> lsRemote(String url, Collection<String> refs) throws RepoException {
    return lsRemote(url, refs, gitEnv, DEFAULT_MAX_LOG_LINES);
  }

  /**
   * Same as {@link #lsRemote(String, Collection, GitEnvironment, int)} but using this repository
   * environment and explicit max number of log lines.
   *
   * @param refs - see <refs> in git help ls-remote
   * @param maxLogLines - Limit log lines to the number specified. -1 for unlimited
   * @return - a map of refs to sha1 from the git ls-remote output.
   * @throws RepoException if the operation fails
   */
  public Map<String, String> lsRemote(String url, Collection<String> refs, int maxLogLines)
      throws RepoException {
    return lsRemote(url, refs, gitEnv, maxLogLines);
  }

  @CheckReturnValue
  static String validateUrl(String url) throws RepoException, ValidationException {
    RepositoryUtil.validateNotHttp(url);
    if (FULL_URI.matcher(url).matches()) {
      return url;
    }

    // Support local folders
    if (Files.isDirectory(Paths.get(url))) {
      return url;
    }
    throw new RepoException(String.format("URL '%s' is not valid", url));
  }

  /**
   * Execute show-ref git command in the local repository and returns a map from reference name to
   * GitReference(SHA-1).
   */
  private ImmutableMap<String, GitRevision> showRef(Iterable<String> refs)
      throws RepoException {
    ImmutableMap.Builder<String, GitRevision> result = ImmutableMap.builder();
    CommandOutput commandOutput = gitAllowNonZeroExit(NO_INPUT,
        ImmutableList.<String>builder().add("show-ref").addAll(refs).build(),
        DEFAULT_TIMEOUT);

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
   * Execute show-ref git command in the local repository and returns a map from reference name to
   * GitReference(SHA-1).
   */
  ImmutableMap<String, GitRevision> showRef() throws RepoException {
    return showRef(ImmutableList.of());
  }

  String mergeBase(String commit1, String commit2) throws RepoException {
    return simpleCommand("merge-base", commit1, commit2).getStdout().trim();
  }

  boolean isAncestor(String ancestor, String commit) throws RepoException {
    CommandOutputWithStatus result =
        gitAllowNonZeroExit(
            NO_INPUT,
            ImmutableList.of("merge-base", "--is-ancestor", "--", ancestor, commit),
            DEFAULT_TIMEOUT);
    if (result.getTerminationStatus().success()) {
      return true;
    }
    if (result.getTerminationStatus().getExitCode() == 1) {
      return false;
    }
    throw new RepoException("Error executing git merge-base --is-ancestor:\n" + result.getStderr());
  }

  /**
   * Returns an instance equivalent to this one but with a different work tree. This does not
   * initialize or alter the given work tree.
   */
  public GitRepository withWorkTree(Path newWorkTree) {
    return new GitRepository(this.gitDir, newWorkTree, this.verbose, this.gitEnv, fetchTimeout);
  }

  /**
   * The Git work tree - in a typical Git repo, this is the directory containing the {@code .git}
   * directory. Returns {@code null} for bare repos.
   */
  @Nullable
  public Path getWorkTree() {
    return workTree;
  }

  public Path getGitDir() {
    return gitDir;
  }

  /**
   * Can be overwriten to add custom behavior.
   */
  protected String runPush(PushCmd pushCmd) throws RepoException, ValidationException {
    List<String> cmd = Lists.newArrayList("push");

    // This shows progress in the log if not attached to a terminal
    cmd.add("--progress");

    if (pushCmd.prune) {
      cmd.add("--prune");
    }

    if (pushCmd.url != null) {
      cmd.add(validateUrl(pushCmd.url));
      for (Refspec refspec : pushCmd.refspecs) {
        cmd.add(refspec.toString());
      }
    }

    return simpleCommand(cmd.toArray(new String[0])).getStderr();
  }

  /**
   * An add command bound to the repo that can be configured and then executed with {@link #run()}.
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
      return new AddCmd(force, /*all=*/true, files);
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
   * Get a field from a configuration {@code configFile} relative to {@link #getWorkTree()}.
   *
   * <p>If {@code configFile} is null it uses configuration (local or global).
   * TODO(malcon): Refactor this to work similar to LogCmd.
   */
  @Nullable
  private String getConfigField(String field, @Nullable String configFile) throws RepoException {
    ImmutableList.Builder<String> params = ImmutableList.builder();
    params.add("config");
    if (configFile != null) {
      params.add("-f", configFile);
    }
    params.add("--get");
    params.add(field);
    CommandOutputWithStatus out = gitAllowNonZeroExit(NO_INPUT, params.build(),
        DEFAULT_TIMEOUT);
    if (out.getTerminationStatus().success()) {
      return out.getStdout().trim();
    } else if (out.getTerminationStatus().getExitCode() == 1 && out.getStderr().isEmpty()) {
      return null;
    }
    throw new RepoException("Error executing git config:\n" + out.getStderr());
  }

  private ImmutableSet<String> getSubmoduleNames() throws RepoException {
    // No submodules
    if (!Files.exists(getCwd().resolve(".gitmodules"))) {
      return ImmutableSet.of();
    }
    ImmutableList.Builder<String> params = ImmutableList.builder();
    params.add("config", "-f", ".gitmodules", "-l", "--name-only");
    CommandOutputWithStatus out = gitAllowNonZeroExit(NO_INPUT, params.build(), DEFAULT_TIMEOUT);
    if (out.getTerminationStatus().success()) {
      Set<String> modules = new LinkedHashSet<>();
      for (String line : Splitter.on('\n').omitEmptyStrings().trimResults().split(
          out.getStdout().trim())) {
        if (!line.startsWith("submodule.")) {
          continue;
        }
        modules.add(Splitter.on('.').splitToList(line).get(1));
      }
      return ImmutableSet.copyOf(modules);
    } else if (out.getTerminationStatus().getExitCode() == 1 && out.getStderr().isEmpty()) {
      return ImmutableSet.of();
    }
    throw new RepoException("Error executing git config:\n" + out.getStderr());
  }

  /**
   * Resolves a git reference to the SHA-1 reference
   */
  public String parseRef(String ref) throws RepoException, CannotResolveRevisionException {
    // Runs rev-list on the reference and remove the extra newline from the output.
    CommandOutputWithStatus result = gitAllowNonZeroExit(
        NO_INPUT, ImmutableList.of("rev-list", "-1", ref, "--"), DEFAULT_TIMEOUT);
    if (!result.getTerminationStatus().success()) {
      throw new CannotResolveRevisionException("Cannot find reference '" + ref + "'");
    }
    String sha1 = result.getStdout().trim();
    Verify.verify(SHA1_PATTERN.matcher(sha1).matches(), "Should be resolved to a SHA-1: %s", sha1);
    return sha1;
  }

  boolean refExists(String ref) throws RepoException {
    try {
      parseRef(ref);
      return true;
    } catch (CannotResolveRevisionException e) {
      return false;
    }
  }

  public void rebase(String newBaseline) throws RepoException, RebaseConflictException {
    CommandOutputWithStatus output = gitAllowNonZeroExit(
        NO_INPUT, ImmutableList.of("rebase", checkNotNull(newBaseline)),
        DEFAULT_TIMEOUT);

    if (output.getTerminationStatus().success()) {
      return;
    }

    if (FAILED_REBASE.matcher(output.getStderr()).find()) {
      throw new RebaseConflictException(
          String.format(
              ""
                  + "Conflict detected while rebasing %s to %s. Please sync or update the change "
                  + "in the origin and retry. Git output was:\n%s",
              workTree, newBaseline, output.getStdout()));
    }
    throw new RepoException(output.getStderr());
  }

  /**
   * Checks out the given ref in the repo, quietly and throwing away local changes.
   */
  public CommandOutput forceCheckout(String ref) throws RepoException {
    return simpleCommand("checkout", "-q", "-f", checkNotNull(ref));
  }

  // DateTimeFormatter.ISO_OFFSET_DATE_TIME might include subseconds, but Git's ISO8601 format does
  // not deal with subseconds (see https://git-scm.com/docs/git-commit#git-commit-ISO8601).
  // We still want to stick to the default ISO format in Git, but don't add the subseconds.
  private static final DateTimeFormatter ISO_OFFSET_DATE_TIME_NO_SUBSECONDS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssZ");
  // The effective bytes that can be used for command-line arguments is ~128k. Setting an arbitrary
  // max for the description of 64k
  private static final int ARBITRARY_MAX_ARG_SIZE = 64_000;

  public void commit(String author, ZonedDateTime timestamp, String message)
      throws RepoException, ValidationException {
    commit(checkNotNull(author), /*amend=*/false, checkNotNull(timestamp), checkNotNull(message));
  }

  // TODO(malcon): Create a CommitCmd object builder
  public void commit(@Nullable String author, boolean amend, @Nullable ZonedDateTime timestamp,
      String message)
      throws RepoException, ValidationException {
    if (isEmptyStaging() && !amend) {
      throw new EmptyChangeException("Migration of the revision resulted in an empty change. "
          + "Is the change already migrated?");
    }

    ImmutableList.Builder<String> params = ImmutableList.<String>builder().add("commit");
    if (author != null) {
      params.add("--author", author);
    }
    if (timestamp != null) {
      params.add("--date", timestamp.format(ISO_OFFSET_DATE_TIME_NO_SUBSECONDS));
    }
    if (amend) {
      params.add("--amend");
    }
    Path descriptionFile = null;
    try {
      if (message.getBytes(StandardCharsets.UTF_8).length > ARBITRARY_MAX_ARG_SIZE) {
        descriptionFile = getCwd().resolve(UUID.randomUUID().toString() + ".desc");
        Files.write(descriptionFile, message.getBytes(StandardCharsets.UTF_8));
        params.add("-F", descriptionFile.toAbsolutePath().toString());
      } else {
        params.add("-m", message);
      }
      git(getCwd(), addGitDirAndWorkTreeParams(params.build()));
    } catch (IOException e) {
      throw new RepoException(
          "Could not commit change: Failed to write file " + descriptionFile, e);
    } finally {
      try {
        if (descriptionFile != null) {
          Files.deleteIfExists(descriptionFile);
        }
      } catch (IOException e) {
        logger.atWarning().log("Could not delete description file: %s", descriptionFile);
      }
    }
  }

  /**
   * Check if staging is empty. That means that a commit would fail with EmptyCommitException.
   */
  private boolean isEmptyStaging() throws RepoException {
    CommandOutput status = simpleCommand("diff", "--staged", "--stat");
    return status.getStdout().trim().isEmpty();
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
    for (String submoduleName : getSubmoduleNames()) {
      String path = getSubmoduleField(submoduleName, "path");

      if (path == null) {
        throw new RepoException("Path is required for submodule " + submoduleName);
      }
      String url = getSubmoduleField(submoduleName, "url");
      if (url == null) {
        throw new RepoException("Url is required for submodule " + submoduleName);
      }
      String branch = getSubmoduleField(submoduleName, "branch");
      if (branch != null && branch.equals(".")) {
        branch = "HEAD";
      }
      FileUtil.checkNormalizedRelative(path);
      // If the url is relative, construct a url using the parent module remote url.
      if (url.startsWith("../")) {
        url = siblingUrl(currentRemoteUrl, submoduleName, url.substring(3));
      } else if (url.startsWith("./")) {
        url = siblingUrl(currentRemoteUrl, submoduleName, url.substring(2));
      }
      try {
        result.add(new Submodule(validateUrl(url), submoduleName, branch, path));
      } catch (ValidationException e) {
        throw new RepoException("Invalid url: " + url, e);
      }
    }
    return result.build();
  }

  ImmutableList<TreeElement> lsTree(GitRevision reference, String treeish)
      throws RepoException {
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

  private String getSubmoduleField(String submoduleName, String field) throws RepoException {
    return getConfigField("submodule." + submoduleName + "." + field, ".gitmodules");
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

  public GitRepository init() throws RepoException {
    try {
      Files.createDirectories(gitDir);
      if (workTree != null) {
        Files.createDirectories(workTree);
      }
    } catch (IOException e) {
      throw new RepoException("Cannot create directories: " + e.getMessage(), e);
    }
    if (workTree != null && workTree.resolve(".git").equals(gitDir)) {
      git(workTree, ImmutableList.of("init", "."));
    } else {
      git(gitDir, ImmutableList.of("init", "--bare"));
    }
    return this;
  }

  public GitRepository withCredentialHelper(String credentialHelper)
      throws RepoException {
    git(gitDir, ImmutableList.of("config", "--local", "credential.helper",
        checkNotNull(credentialHelper)));
    return this;
  }

  public UserPassword credentialFill(String url) throws RepoException, ValidationException {
    return new GitCredential(gitEnv.resolveGitBinary(), Duration.ofMinutes(1), gitEnv)
        .fill(gitDir, url);
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

  CommandOutput simpleCommandNoRedirectOutput(String... argv) throws RepoException {
    Iterable<String> params = addGitDirAndWorkTreeParams(Arrays.asList(argv));
    try {
      // Use maxLoglines 0 and verbose=false to avoid redirection
      return executeGit(getCwd(), params, gitEnv, /*verbose*/ false, /*maxLoglines*/ 0);
    } catch (BadExitStatusWithOutputException e) {
      CommandOutputWithStatus output = e.getOutput();

      for (Pattern error : REF_NOT_FOUND_ERRORS) {
        Matcher matcher = error.matcher(output.getStderr());
        if (matcher.find()) {
          throw new RepoException(
              "Cannot find reference '" + matcher.group(1) + "'");
        }
      }

      throw throwUnknownGitError(output, params);
    } catch (CommandException e) {
      throw new RepoException("Error executing 'git': " + e.getMessage(), e);
    }
  }

  void forceClean() throws RepoException {
    Preconditions.checkNotNull(workTree, "Clean only acts on the worktree. A worktree is needed");
    // Force clean and also untracked directories.
    simpleCommand("clean", "-f", "-d");
  }
  /**
   * Execute git apply.
   *
   * @param index if true we pass --index to the git command
   * @throws RebaseConflictException if it cannot apply the change.
   */
  public void apply(byte[] stdin, boolean index) throws RepoException, RebaseConflictException {
    CommandOutputWithStatus output = gitAllowNonZeroExit(stdin,
        index ? ImmutableList.of("apply", "--index") : ImmutableList.of("apply"),
        DEFAULT_TIMEOUT);
    if (output.getTerminationStatus().success()) {
      return;
    }

    if (output.getTerminationStatus().getExitCode() == 1) {
      throw new RebaseConflictException("Couldn't apply patch:\n" + output.getStderr());
    }

    throw new RepoException("Couldn't apply patch:\n" + output.getStderr());
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
      return executeGit(cwd, params, gitEnv, verbose);
    } catch (BadExitStatusWithOutputException e) {
      CommandOutputWithStatus output = e.getOutput();

      for (Pattern error : REF_NOT_FOUND_ERRORS) {
        Matcher matcher = error.matcher(output.getStderr());
        if (matcher.find()) {
          throw new RepoException(
              "Cannot find reference '" + matcher.group(1) + "'");
        }
      }

      throw throwUnknownGitError(output, params);
    } catch (CommandException e) {
      throw new RepoException("Error executing 'git': " + e.getMessage(), e);
    }
  }

  private RepoException throwUnknownGitError(
      CommandOutputWithStatus output, Iterable<String> params) throws RepoException {
    throw new RepoException(
        String.format(
            "Error executing 'git %s'(exit code %d). Stderr: %s\n",
            Joiner.on(' ').join(params),
            output.getTerminationStatus().getExitCode(),
            output.getStderr()));
  }

  /**
   * Execute git allowing non-zero exit codes. This will only allow program non-zero exit codes
   * (0-10. The upper bound is arbitrary). And will still fail for exit codes like 127 (Command not
   * found).
   */
  private CommandOutputWithStatus gitAllowNonZeroExit(byte[] stdin, Iterable<String> params,
      Duration defaultTimeout)
      throws RepoException {
    try {
      List<String> allParams = new ArrayList<>();
      allParams.add(gitEnv.resolveGitBinary());
      allParams.addAll(addGitDirAndWorkTreeParams(params));
      Command cmd =
          new Command(
              Iterables.toArray(allParams, String.class),
              gitEnv.getEnvironment(),
              getCwd().toFile());
      return new CommandRunner(cmd, defaultTimeout)
          .withVerbose(verbose)
          .withInput(stdin)
          .execute();
    } catch (BadExitStatusWithOutputException e) {
      CommandOutputWithStatus output = e.getOutput();
      int exitCode = e.getOutput().getTerminationStatus().getExitCode();
      if (NON_CRASH_ERROR_EXIT_CODES.contains(exitCode)) {
        return output;
      }
      throw throwUnknownGitError(output, params);
    } catch (CommandException e) {
      throw new RepoException("Error executing 'git': " + e.getMessage(), e);
    }
  }

  private static CommandOutputWithStatus executeGit(
      Path cwd, Iterable<String> params, GitEnvironment gitEnv, boolean verbose)
      throws CommandException {
    return executeGit(cwd, params, gitEnv, verbose, DEFAULT_MAX_LOG_LINES);
  }

  private static CommandOutputWithStatus executeGit(
      Path cwd, Iterable<String> params, GitEnvironment gitEnv, boolean verbose, int maxLogLines)
      throws CommandException {
    List<String> allParams = new ArrayList<>(Iterables.size(params) + 1);
    allParams.add(gitEnv.resolveGitBinary());
    Iterables.addAll(allParams, params);
    Command cmd =
        new Command(
            Iterables.toArray(allParams, String.class), gitEnv.getEnvironment(), cwd.toFile());
    CommandRunner runner = new CommandRunner(cmd).withVerbose(verbose);
    return
        maxLogLines >= 0 ? runner.withMaxStdOutLogLines(maxLogLines).execute() : runner.execute();
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
  GitRevision resolveReferenceWithContext(String reference, @Nullable String contextRef,
      String url)
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
    return new GitRevision(this, parseRef(reference), /*reviewReference=*/null, contextRef,
        ImmutableListMultimap.of(), url);
  }

  /**
   * Resolve a reference
   *
   * @throws CannotResolveRevisionException if it cannot resolve the reference
   */
  public GitRevision resolveReference(String reference)
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
    return new GitRevision(this, parseRef(reference));
  }

  /**
   * Checks if a SHA-1 object exist in the repository
   */
  private boolean checkSha1Exists(String reference) throws RepoException {
    ImmutableList<String> params = ImmutableList.of("cat-file", "-e", reference);
    CommandOutputWithStatus output = gitAllowNonZeroExit(NO_INPUT, params,
        DEFAULT_TIMEOUT);
    if (output.getTerminationStatus().success()) {
      return true;
    }
    if (output.getStderr().isEmpty()) {
      return false;
    }
    throw throwUnknownGitError(output, params);
  }

  GitRevision commitTree(String message, String tree, List<GitRevision> parents)
      throws RepoException {
    ImmutableList.Builder<String> args = ImmutableList.<String>builder().add("commit-tree", tree);
    for (GitRevision parent : parents) {
      args.add("-p", parent.getSha1());
    }
    args.add("-m", message);

    return new GitRevision(this,
        git(getCwd(), addGitDirAndWorkTreeParams(args.build())).getStdout().trim());
  }

  /**
   * Creates a reference from a complete SHA-1 string without any validation that it exists.
   */
  private GitRevision createReferenceFromCompleteSha1(String ref) {
    return new GitRevision(this, ref);
  }

  private boolean isSha1Reference(String ref) {
    return SHA1_PATTERN.matcher(ref).matches();
  }

  /**
   * Information of a submodule of {@code this} repository.
   */
  public static class Submodule {

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

    public String getRef() {
      return ref;
    }

    public String getPath() {
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

    public String getFile() {
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
      return Objects.equals(file, that.file)
          && Objects.equals(newFileName, that.newFileName)
          && indexStatus == that.indexStatus
          && workdirStatus == that.workdirStatus;
    }

    @Override
    public int hashCode() {
      return Objects.hash(file, newFileName, indexStatus, workdirStatus);
    }

    @Override
    public String toString() {
      return Character.toString(indexStatus.getCode())
          + getWorkdirStatus().getCode()
          + " " + file
          + (newFileName != null ? " -> " + newFileName : "");
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
    IGNORED('!'),
    CHANGE_TYPE('T');

    private final char code;

    public char getCode() {
      return code;
    }

    StatusCode(char code) {
      this.code = code;
    }
  }

  /**
   * An object capable of performing a 'git push' operation to a remote repository.
   */
  public static class PushCmd {

    private final GitRepository repo;
    @Nullable
    private final String url;
    private final ImmutableList<Refspec> refspecs;
    private final boolean prune;

    @Nullable
    public String getUrl() {
      return url;
    }

    public ImmutableList<Refspec> getRefspecs() {
      return refspecs;
    }

    public boolean isPrune() {
      return prune;
    }


    @CheckReturnValue
    public PushCmd(GitRepository repo, @Nullable String url, ImmutableList<Refspec> refspecs,
        boolean prune) {
      this.repo = checkNotNull(repo);
      this.url = url;
      this.refspecs = checkNotNull(refspecs);
      Preconditions.checkArgument(refspecs.isEmpty() || url != null, "refspec can only be"
          + " used when a url is passed");
      this.prune = prune;
    }

    @CheckReturnValue
    public PushCmd withRefspecs(String url, Iterable<Refspec> refspecs) {
      return new PushCmd(repo, checkNotNull(url), ImmutableList.copyOf(refspecs),
          prune);
    }

    @CheckReturnValue
    public PushCmd prune(boolean prune) {
      return new PushCmd(repo, url, this.refspecs, prune);
    }

    /**
     * Runs the push command and returns the response from the server.
     */
    public String run() throws RepoException, ValidationException {
      String output = repo.runPush(this);
      checkCondition(
          !PROTECTED_BRANCH.matcher(output).find(),
          "Cannot push to %s refspecs %s. Please request an admin of the repo to verify the "
              + "branch protection rules at %s/settings/branches if you think it's a legit branch.",
          url,
          refspecs,
          url);
      return output;
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
    private static final String TREE_FIELD = "tree";
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
    private final boolean includeMergeDiff;
    private final boolean firstParent;
    private final int skip;

    private final GitRepository repo;

    @Nullable
    private final String grepString;

    @CheckReturnValue
    LogCmd(GitRepository repo, String refExpr, int limit, ImmutableCollection<String> paths,
        boolean firstParent, boolean includeStat, boolean includeBody,
        @Nullable String grepString, boolean includeMergeDiff, int skip) {
      this.limit = limit;
      this.paths = paths;
      this.refExpr = refExpr;
      this.firstParent = firstParent;
      this.includeStat = includeStat;
      this.includeMergeDiff = includeMergeDiff;
      this.includeBody = includeBody;
      this.repo = repo;
      this.grepString = grepString;
      this.skip = skip;
    }

    static LogCmd create(GitRepository repository, String refExpr) {
      return new LogCmd(
          checkNotNull(repository),
          checkNotNull(refExpr),
          0,
          ImmutableList.of(), /*firstParent*/
          true,
          /* includeStat= */ false,
          /*includeBody=*/ true,
          /*grepString=*/ null,
          /*includeMergeDiff=*/ false, /*skip=*/0);
    }

    /**
     * Limit the query to {@code limit} results. Should be > 0.
     */
    @CheckReturnValue
    public LogCmd withLimit(int limit) {
      Preconditions.checkArgument(limit > 0);
      return new LogCmd(repo, refExpr, limit, paths, firstParent, includeStat, includeBody,
          grepString, includeMergeDiff, skip);
    }

    /**
     * Skip the first {@code skip} commits. Should be >= 0.
     */
    @CheckReturnValue
    LogCmd withSkip(int skip) {
      Preconditions.checkArgument(skip >= 0);
      return new LogCmd(repo, refExpr, limit, paths, firstParent, includeStat, includeBody,
          grepString, includeMergeDiff, skip);
    }

    /**
     * Only query for changes in {@code paths} paths.
     */
    @CheckReturnValue
    LogCmd withPaths(ImmutableCollection<String> paths) {
      Preconditions.checkArgument(paths.stream().noneMatch(s -> s.trim().equals("")));
      return new LogCmd(repo, refExpr, limit, paths, firstParent, includeStat, includeBody,
          grepString, includeMergeDiff, skip);
    }

    /**
     * Set if --first-parent should be used in 'git log'.
     */
    @CheckReturnValue
    LogCmd firstParent(boolean firstParent) {
      return new LogCmd(repo, refExpr, limit, paths, firstParent, includeStat, includeBody,
          grepString, includeMergeDiff, skip);
    }

    /**
     * If files affected by the commit should be included in the response.
     */
    @CheckReturnValue
    LogCmd includeFiles(boolean includeStat) {
      return new LogCmd(repo, refExpr, limit, paths, firstParent, includeStat, includeBody,
          grepString, includeMergeDiff, skip);
    }

    /**
     * If file diff should be shown for merges. Equivalent to 'git log -m' command.
     */
    @CheckReturnValue
    LogCmd includeMergeDiff(boolean includeMergeDiff) {
      return new LogCmd(repo, refExpr, limit, paths, firstParent, includeStat, includeBody,
          grepString, includeMergeDiff, skip);
    }

    /**
     * If the body (commit message) should be included in the response.
     */
    @CheckReturnValue
    LogCmd includeBody(boolean includeBody) {
      return new LogCmd(repo, refExpr, limit, paths, firstParent, includeStat, includeBody,
          grepString, includeMergeDiff, skip);
    }

    /**
     * Look only for messages thatMatches grep expression.
     */
    @CheckReturnValue
    public LogCmd grep(@Nullable String grepString) {
      return new LogCmd(repo, refExpr, limit, paths, firstParent, includeStat, includeBody,
          grepString, includeMergeDiff, skip);
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

      if (includeMergeDiff) {
        cmd.add("-m");
      }
      if (skip > 0) {
        cmd.add("--skip");
        cmd.add(Integer.toString(skip));
      }

      if (!Strings.isNullOrEmpty(grepString)) {
        cmd.add("--grep");
        cmd.add(grepString);
      }

      cmd.add(refExpr);

      if (!paths.isEmpty()) {
        cmd.add("--");
        cmd.addAll(paths);
      }

      CommandOutput output = repo.simpleCommand(cmd.toArray(new String[0]));
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
            .withKeyValueSeparator(Splitter.on("=").limit(2))
            .split(groups.get(0));

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

        String tree = getField(fields, TREE_FIELD);
        String commit = getField(fields, COMMIT_FIELD);
        try {
          commits.add(new GitLogEntry(
              repo.createReferenceFromCompleteSha1(commit), parents.build(),
              tree,
              AuthorParser.parse(getField(fields, AUTHOR_FIELD)),
              AuthorParser.parse(getField(fields, COMMITTER_FIELD)),
              tryParseDate(fields, AUTHOR_DATE_FIELD, commit),
              tryParseDate(fields, COMMITTER_DATE, commit),
              body, files));
        } catch (InvalidAuthorException e) {
          throw new RepoException("Error in commit '" + commit + "'. Invalid author.", e);
        }
      }
      return commits.build();
    }

    private ZonedDateTime tryParseDate(Map<String, String> fields, String dateField,
        String commit) {
      String value = getField(fields, dateField);
      try {
        return ZonedDateTime.parse(value);
      } catch (DateTimeParseException e) {
        logger.atSevere().log("Cannot parse date '%s' for commit %s. Using epoch time instead",
            value, commit);
        return ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);
      }
    }

    private String getField(Map<String, String> fields, String field) {
      return checkNotNull(fields.get(field), "%s not present", field);
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
          + TREE_FIELD + "=%T\n"
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
    private final String tree;
    private final Author author;
    private final Author committer;
    private final ZonedDateTime authorDate;
    private final ZonedDateTime commitDate;
    @Nullable
    private final String body;
    @Nullable
    private final ImmutableSet<String> files;

    GitLogEntry(GitRevision commit, ImmutableList<GitRevision> parents,
        String tree, Author author, Author committer, ZonedDateTime authorDate,
        ZonedDateTime commitDate,
        @Nullable String body, @Nullable ImmutableSet<String> files) {
      this.commit = commit;
      this.parents = parents;
      this.tree = tree;
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

    public ImmutableList<GitRevision> getParents() {
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

    ZonedDateTime getCommitDate() {
      return commitDate;
    }

    public String getTree() {
      return tree;
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

  // Used for debugging issues
  @SuppressWarnings("unused")
  public String gitCmd() {
    return "git --git-dir=" + gitDir + (workTree != null ? " --work-tree=" + workTree : "");
  }

  /**
   * An object capable of performing a 'git tag' operation to a remote repository.
   */
  //TODO(huanhuanchen): support deleting tag
  public static class TagCmd {

    private final GitRepository repo;
    private final String tagName;
    @Nullable
    private final String tagMessage;
    private final boolean force;

    TagCmd(GitRepository gitRepository, String tagName, String tagMessage, boolean force) {
      this.repo = Preconditions.checkNotNull(gitRepository);
      this.tagName = Preconditions.checkNotNull(tagName);
      this.tagMessage = tagMessage;
      this.force = force;
    }

    static TagCmd create(GitRepository gitRepository, String tagName) {
      return new TagCmd(gitRepository, tagName, null, false);
    }

    public TagCmd withAnnotatedTag(String tagMessage) {
      return new TagCmd(repo, tagName, tagMessage, force);
    }

    public TagCmd force(boolean force) {
      return new TagCmd(repo, tagName, tagMessage, force);
    }

    public void run() throws RepoException, ValidationException {
      List<String> cmd = Lists.newArrayList("tag");
      if (tagMessage != null) {
        cmd.add("-a");
      }
      cmd.add(tagName);
      if (tagMessage != null) {
        cmd.add("-m");
        cmd.add(tagMessage);
      }
      if (force) {
        cmd.add("--force");
      }
      repo.simpleCommand(cmd.toArray(new String[0]));
    }
  }
}
