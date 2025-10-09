/*
 * Copyright (C) 2016 Google LLC
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

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
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
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.AuthorParser;
import com.google.copybara.authoring.InvalidAuthorException;
import com.google.copybara.exception.AccessValidationException;
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
import com.google.copybara.util.Glob;
import com.google.copybara.util.RepositoryUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * A class for manipulating Git repositories
 */
public class GitRepository {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final Duration DEFAULT_REPO_TIMEOUT = Duration.ofMinutes(15);

  // TODO(malcon): Make this generic (Using URIish.java)
  private static final Pattern FULL_URI = Pattern.compile(
      "([a-z][a-z0-9+-]+@[a-zA-Z0-9_.-]+(:.+)?|^[a-z][a-z0-9+-]+://.*)$");

  private static final Pattern LS_TREE_ELEMENT = Pattern.compile(
      "([0-9]{6}) (commit|tag|tree|blob) ([a-f0-9]{40})\t(.*)");

  private static final Pattern LS_REMOTE_OUTPUT_LINE =
      Pattern.compile("([a-f0-9]{40}|ref: refs/heads/\\w+)\t(.+)");

  private static final Pattern SHA1_PATTERN = Pattern.compile("[a-f0-9]{6,40}");

  private static final Pattern DEFAULT_BRANCH_PATTERN =
      Pattern.compile("(?s)ref: (refs/heads/(\\w+)).*");

  private static final Pattern FAILED_REBASE =
      Pattern.compile("(Failed to merge in the changes|Could not apply.*)");
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
  protected static final int DEFAULT_MAX_LOG_LINES = 4_000;
  public static final String GIT_DESCRIBE_REQUESTED_VERSION = "GIT_DESCRIBE_REQUESTED_VERSION";
  public static final String GIT_DESCRIBE_CHANGE_VERSION = "GIT_DESCRIBE_CHANGE_VERSION";
  public static final String GIT_DESCRIBE_FIRST_PARENT = "GIT_DESCRIBE_FIRST_PARENT";
  public static final String GIT_SEQUENTIAL_REVISION_NUMBER = "GIT_SEQUENTIAL_REVISION_NUMBER";
  // Closest tag, if any
  public static final String GIT_DESCRIBE_ABBREV = "GIT_DESCRIBE_ABBREV";
  public static final String GIT_TAG_POINTS_AT = "GIT_TAG_POINTS_TO";
  public static final String HTTP_PERMISSION_DENIED = "The requested URL returned error: 403";
  public static final String FULL_REF_NAMESPACE = "_copybara_full_ref";
  public static final String COPYBARA_FETCH_NAMESPACE = "refs/copybara_fetch";

  /**
   * The location of the {@code .git} directory. The is also the value of the {@code --git-dir}
   * flag.
   */
  private final Path gitDir;

  @Nullable
  private final Path workTree;

  private final boolean verbose;
  protected final GitEnvironment gitEnv;
  private final Duration repoTimeout;
  protected final PushOptionsValidator pushOptionsValidator;
  protected final boolean noVerify;

  private static final Map<Character, StatusCode> CHAR_TO_STATUS_CODE =
      Arrays.stream(StatusCode.values())
          .collect(Collectors.toMap(StatusCode::getCode, Function.identity()));

  protected GitRepository(
      Path gitDir,
      @Nullable Path workTree,
      boolean verbose,
      GitEnvironment gitEnv,
      Duration repoTimeout,
      boolean noVerify,
      PushOptionsValidator pushOptionsValidator) {
    this.gitDir = checkNotNull(gitDir);
    this.workTree = workTree;
    this.verbose = verbose;
    this.gitEnv = checkNotNull(gitEnv);
    this.repoTimeout = checkNotNull(repoTimeout);
    this.noVerify = noVerify;
    this.pushOptionsValidator = checkNotNull(pushOptionsValidator);
  }

  /** Creates a new repository in the given directory. The new repo is not bare. */
  public static GitRepository newRepo(
      boolean verbose, Path path, GitEnvironment gitEnv, Duration repoTimeout, boolean noVerify) {
    return new GitRepository(
        path.resolve(".git"),
        path,
        verbose,
        gitEnv,
        repoTimeout,
        noVerify,
        /* pushOptionsValidator= */ new PushOptionsValidator(Optional.empty()));
  }

  /** Creates a new repository in the given directory. The new repo is not bare. */
  public static GitRepository newRepo(
      boolean verbose,
      Path path,
      GitEnvironment gitEnv,
      Duration repoTimeout,
      boolean noVerify,
      PushOptionsValidator pushOptionsValidator) {
    return new GitRepository(
        path.resolve(".git"), path, verbose, gitEnv, repoTimeout, noVerify, pushOptionsValidator);
  }

  /**
   * Creates a new repository in the given directory with a default repo timeout. The new repo is
   * not bare.
   */
  public static GitRepository newRepo(boolean verbose, Path path, GitEnvironment gitEnv) {
    return newRepo(verbose, path, gitEnv, DEFAULT_REPO_TIMEOUT, /* noVerify= */ false);
  }

  /** Create a new bare repository with a push options validator passes validation for all flags */
  public static GitRepository newBareRepo(
      Path gitDir, GitEnvironment gitEnv, boolean verbose, Duration repoTimeout, boolean noVerify) {
    return new GitRepository(
        gitDir,
        /* workTree= */ null,
        verbose,
        gitEnv,
        repoTimeout,
        noVerify,
        /* pushOptionsValidator= */ new PushOptionsValidator(Optional.empty()));
  }

  /** Create a new bare repository with push options validator */
  public static GitRepository newBareRepo(
      Path gitDir,
      GitEnvironment gitEnv,
      boolean verbose,
      Duration repoTimeout,
      boolean noVerify,
      PushOptionsValidator pushOptionsValidator) {
    return new GitRepository(
        gitDir,
        /* workTree= */ null,
        verbose,
        gitEnv,
        repoTimeout,
        noVerify,
        /* pushOptionsValidator= */ pushOptionsValidator);
  }

  /**
   * Get the version of git that will be used for running migrations. Returns empty if git cannot be
   * found.
   */
  private static Optional<String> version(GitEnvironment gitEnv) {
    try {
      String version =
          executeGit(
                  Paths.get(StandardSystemProperty.USER_DIR.value()),
                  ImmutableList.of("version"),
                  gitEnv,
                  /*verbose=*/ false,
                  /*timeout=*/ Optional.empty())
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

    if (quickRefspecValidation(refspec)) {
      return;
    }
    try {
      executeGit(
          cwd,
          ImmutableList.of("check-ref-format", "--allow-onelevel", "--refspec-pattern", refspec),
          gitEnv,
          /*verbose=*/ false,
          /*timeout=*/Optional.empty());
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

  private static final Pattern BASIC_REFSPEC_COMPONENT =
      Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9_.-]*");

  /**
   * Skip calling CLI for common refspecs that we know are safe. CLI can take 50-100ms.
   * Note that the default is to return false and let Git decide if it is valid. IOW, returning
   * false here is always safe (But less optimal).
   */
  private static boolean quickRefspecValidation(String refspec) {
    if (!refspec.startsWith("refs/")
        || refspec.endsWith(".")
        || refspec.contains("..")
        || refspec.endsWith(".lock")) {
      return false;
    }
    boolean wildcard = false;
    for (String component : Splitter.on('/').split(refspec)) {
      if (component.equals("*")) {
        // Only one asterisk is allowed
        if (wildcard) {
          return false;
        }
        wildcard = true;
        continue;
      }
      if (!BASIC_REFSPEC_COMPONENT.matcher(component).matches()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Fetch a reference from a git url.
   *
   * <p>Note that this method doesn't support fetching refspecs that contain local ref path
   * locations. IOW
   * "refs/foo" is allowed but not "refs/foo:remote/origin/foo". Wildcards are also not allowed.
   */
  @CanIgnoreReturnValue
  public GitRevision fetchSingleRef(String url, String ref, boolean partialFetch,
      Optional<Integer> depth)
      throws RepoException, ValidationException {
    return fetchSingleRefWithTags(url, ref, /* fetchTags= */ false, partialFetch, depth);
  }

  public GitRevision fetchSingleRefWithTags(
      String url, String ref, boolean fetchTags, boolean partialFetch, Optional<Integer> depth)
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
    boolean isSha1Ref = isSha1Reference(ref);
    if (isSha1Ref) {
      boolean tags = !partialFetch && fetchTags;
      try {
        fetch(
            url,
            /* prune= */ false,
            /* force= */ true,
            ImmutableList.of(),
            partialFetch,
            depth,
            tags);
      } catch (CannotResolveRevisionException e) {
        // Some servers are configured without HEAD. That is fine, we'll try fetching the SHA
        // instead.
        logger.atWarning().withCause(e).log(
            "Cannot fetch remote HEAD. Ignoring and fetching SHA-1 directly");
      }
      try {
        return resolveReferenceWithContext(ref, /*contextRef=*/ref, url);
      } catch (RepoException | CannotResolveRevisionException ignore) {
        // Ignore, the fetch below will attempt using the SHA-1.
      }
    }

    ImmutableList.Builder<String> refspec = ImmutableList.builder();
    refspec.add(String.format("%s:%s/%s", ref, COPYBARA_FETCH_NAMESPACE, ref));
    if (fetchTags) {
      refspec.add("refs/tags/*:refs/tags/*");
    }

    if (!ref.startsWith("refs/")) {
      ImmutableList.Builder<String> fullRefspec = ImmutableList.builder();
      fullRefspec.addAll(refspec.build());
      if (!isSha1Ref) {
        // Define a refspec that attempts to obtain the full reference using wildcards, for use in
        // GitRevision's fullReference() method.
        fullRefspec.add(
            String.format(
                "refs/*/%s:%s/refs/*/%s%s",
                ref, COPYBARA_FETCH_NAMESPACE, ref, FULL_REF_NAMESPACE));
      }

      try {
        // If this fails, the fetch below will resolve using a simpler refspec.
        fetch(
            url,
            /* prune= */ false,
            /* force= */ true,
            fullRefspec.build(),
            partialFetch,
            depth,
            false);
        return resolveReferenceWithContext(
            String.format("%s/%s", COPYBARA_FETCH_NAMESPACE, ref), /* contextRef= */ ref, url);
      } catch (RepoException | CannotResolveRevisionException ignore) {
        // Ignore, the fetch below will attempt using a simpler refspec.
      }
    }

    fetch(url, /* prune= */ false, /* force= */ true, refspec.build(), partialFetch, depth, false);
    return resolveReferenceWithContext(
        String.format("%s/%s", COPYBARA_FETCH_NAMESPACE, ref), /* contextRef= */ ref, url);
  }

  public GitRevision addDescribeVersion(GitRevision rev) throws RepoException {
    ImmutableListMultimap.Builder<String, String> describeLabels = ImmutableListMultimap.builder();
    describeLabels.put(GIT_DESCRIBE_REQUESTED_VERSION, describe(rev, false));
    describeLabels.put(GIT_DESCRIBE_FIRST_PARENT, describe(rev, true));
    String describeAbbrev = describeAbbrev(rev);
    // We only want to populate this label if a value exists
    if (describeAbbrev != null) {
      describeLabels.put(GIT_DESCRIBE_ABBREV, describeAbbrev(rev));
    }
    return rev.withLabels(describeLabels.build());
  }

  @Nullable
  String describe(GitRevision rev, boolean fallback, String... arg) throws RepoException {
    try {
      ImmutableList.Builder<String> args = ImmutableList.builder();
      args.add("describe");
      args.add(arg);
      args.add("--").add(rev.getSha1());
      return simpleCommand(args.build()).getStdout().trim();
    } catch (RepoException e) {
      logger.atWarning().withCause(e).log(
          "Cannot get describe version for commit %s", rev.getSha1());
      if (!fallback) {
        return null;
      }
      return simpleCommand("describe", "--always", "--", rev.getSha1()).getStdout().trim();
    }
  }

  public String describe(GitRevision rev, boolean firstParent) throws RepoException {
    return describe(rev, true, firstParent ? new String[]{"--first-parent"} : new String[]{});
  }

  /**
   * Finds a tag that exactly points to the given revision.
   *
   * @param rev the revision to describe
   * @return the describe output of the revision, or null if the revision is not found
   */
  @Nullable
  public String describeExactMatch(GitRevision rev) throws RepoException {
    return describe(rev, false, new String[] {"--exact-match", "--tags"});
  }

  public String describeAbbrev(GitRevision rev) throws RepoException {
    return describe(rev, false, "--tag", "--abbrev=0");
  }

  public ImmutableList<String> tagPointsAt(GitRevision rev) throws RepoException {
    return ImmutableList.copyOf(
        simpleCommand("tag", "--points-at", rev.getSha1()).getStdout().trim().split("\n"));
  }

  public String showDiff(String referenceFrom, String referenceTo) throws RepoException {
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
   *     'refs/foo/bar:refs/bar/foo'.
   * @return the set of fetched references and what action was done ( rejected, new reference,
   *     updated, etc.)
   */
  @CanIgnoreReturnValue
  public FetchResult fetch(
      String url,
      boolean prune,
      boolean force,
      Iterable<String> refspecs,
      boolean partialFetch,
      Optional<Integer> depth,
      boolean tags)
      throws RepoException, ValidationException {

    List<String> args = Lists.newArrayList("fetch", validateUrl(url));
    if (tags) {
      args.add("--tags");
    }
    if (depth.isPresent()) {
      args.add(String.format("--depth=%d", depth.get()));
    }
    if (partialFetch) {
      args.add("--filter=blob:none");
    }
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
    CommandOutputWithStatus output = gitAllowNonZeroExit(NO_INPUT, args, repoTimeout);
    if (output.getTerminationStatus().success()) {
      ImmutableMap<String, GitRevision> after = showRef();
      return new FetchResult(before, after);
    }
    checkFetchError(
        output.getStderr(), url, requestedRefs, output.getTerminationStatus().getExitCode());
    throw throwUnknownGitError(output, args);
  }

  public void checkFetchError(
      String stdErr, String url, List<String> requestedRefs, Integer exitCode)
      throws ValidationException, RepoException {
    if (stdErr.isEmpty()
        || FETCH_CANNOT_RESOLVE_ERRORS.matcher(stdErr).find()) {
      throw new CannotResolveRevisionException("Cannot find reference(s): " + requestedRefs);
    }
    if (NO_GIT_REPOSITORY.matcher(stdErr).find()) {
      throw new CannotResolveRevisionException(
          String.format("Invalid Git repository: %s. Error: %s", url, stdErr));
    }
    if (stdErr.contains(
        "Server does not allow request for unadvertised object")) {
      throw new CannotResolveRevisionException(
          String.format("%s: %s", url, stdErr.trim()));
    }
    if (stdErr.contains("Permission denied")
        || stdErr.contains("Could not read from remote repository")
        || stdErr.contains(HTTP_PERMISSION_DENIED)
        || stdErr.contains("Repository not found")) {
      throw new AccessValidationException(stdErr);
    }
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
    return new PushCmd(
        this,
        /* url= */ null,
        /* refspecs= */ ImmutableList.of(),
        /* prune= */ false,
        /* force= */ false,
        /* forceLease= */ ImmutableMap.of(),
        /* pushOptions= */ ImmutableList.of(),
        /* pushOptionsValidator= */ this.pushOptionsValidator);
  }

  @CheckReturnValue
  public MergeCmd merge(String branch, List<String> commits) {
    return MergeCmd.create(
        this, branch, commits, (Map<String, String> unusedMap) -> true);
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
   * @return - a map of refs to sha1 from the git ls-remote output. Can also contain symbolic refs
   *     if --symref is set.
   * @throws RepoException if the operation fails
   */
  public static Map<String, String> lsRemote(
      String url, Collection<String> refs, GitEnvironment gitEnv, int maxLogLines)
      throws RepoException, ValidationException {
    return lsRemote(FileSystems.getDefault().getPath("."), url, refs, gitEnv, maxLogLines,
        ImmutableList.of());
  }

  /**
   * Runs a git ls-remote from the current directory for a repository url. Assumes the path to the
   * git binary is already set. You don't have to be in a git repository to run this command. Does
   * not work with remote names.
   *
   * @param url - see <repository> in git help ls-remote
   * @param refs - see <refs> in git help ls-remote
   * @param gitEnv - determines where the Git binaries are
   * @param flags - flags to pass to the ls-remote command.
   * @return - a map of refs to sha1 from the git ls-remote output. Can also contain symbolic refs
   *     if --symref is set.
   * @throws RepoException if the operation fails
   */
  public static ImmutableMap<String, String> lsRemote(
      String url,
      Collection<String> refs,
      GitEnvironment gitEnv,
      Collection<String> flags,
      int maxLogLines)
      throws RepoException, ValidationException {
    return lsRemote(FileSystems.getDefault().getPath("."), url, refs, gitEnv, maxLogLines, flags);
  }

  private static ImmutableMap<String, String> lsRemote(
      Path cwd, String url, Collection<String> refs, GitEnvironment gitEnv, int maxLogLines,
      Collection<String> flags) throws RepoException, ValidationException {

    ImmutableMap.Builder<String, String> result = ImmutableMap.builder();
    List<String> args = Lists.newArrayList("ls-remote");
    args.addAll(flags);
    try {
      args.add(validateUrl(url));
    } catch (ValidationException e) {
      throw new RepoException("Invalid url: " + url, e);
    }
    args.addAll(refs);

    CommandOutputWithStatus output;
    try {
      output = executeGit(cwd, args, gitEnv, false, maxLogLines, Optional.empty());
    } catch (BadExitStatusWithOutputException e) {
      if (e.getOutput().getStderr().contains("Please make sure you have the correct access rights")
              || e.getOutput().getStderr().contains(HTTP_PERMISSION_DENIED)) {
        String errMsg = String.format(
                "Permission denied running ls-remote for '%s' and refs '%s': Exit code %s,"
                        + " Output:\n%s",
                url, refs, e.getOutput().getTerminationStatus().getExitCode(),
                e.getOutput().getStderr());
         throw new AccessValidationException(errMsg, e);
      }
      String errMsg = String.format(
              "Error running ls-remote for '%s' and refs '%s': Exit code %s, Output:\n%s",
              url, refs, e.getOutput().getTerminationStatus().getExitCode(),
              e.getOutput().getStderr());
      throw new RepoException(errMsg, e);
    } catch (CommandException e) {
      throw new RepoException(
          String.format("Error running ls-remote for '%s' and refs '%s'", url, refs), e);
    }
    if (output.getTerminationStatus().success()) {
      int rowsAccumulated = 0;
      for (String line :
          Iterables.filter(Splitter.on('\n').split(output.getStdout()), row -> !row.isEmpty())) {
        if (maxLogLines >= 0 && rowsAccumulated >= maxLogLines) {
          break;
        }
        Matcher matcher = LS_REMOTE_OUTPUT_LINE.matcher(line);
        if (!matcher.matches()) {
          throw new RepoException("Unexpected format for ls-remote output: " + line);
        }
        result.put(matcher.group(2), matcher.group(1));
        rowsAccumulated++;
        if (DEFAULT_BRANCH_PATTERN.matches(line)) {
          // we have a ref: line, indicating that we were looking for a symbolic ref. bail.
          break;
        }
      }
    }
    return result.buildOrThrow();
  }

  /**
   * Same as {@link #lsRemote(String, Collection, GitEnvironment, int)} but using this repository
   * environment and {@link #DEFAULT_MAX_LOG_LINES} as max number of log lines.
   *
   * @param refs - see <refs> in git help ls-remote
   * @return - a map of refs to sha1 from the git ls-remote output.
   * @throws RepoException if the operation fails
   */
  public Map<String, String> lsRemote(String url, Collection<String> refs)
      throws RepoException, ValidationException {
    return lsRemote(url, refs, DEFAULT_MAX_LOG_LINES);
  }

  /**
   * Same as {@link #lsRemote(String, Collection, int, Collection)} but using this repository
   * environment and explicit max number of log lines.
   *
   * @param refs - see <refs> in git help ls-remote
   * @param maxLogLines - Limit log lines to the number specified. -1 for unlimited
   * @return - a map of refs to sha1 from the git ls-remote output.
   * @throws RepoException if the operation fails
   */
  public Map<String, String> lsRemote(String url, Collection<String> refs, int maxLogLines)
      throws RepoException, ValidationException {
    return lsRemote(url, refs, maxLogLines, ImmutableList.of());
  }

  /**
   * Same as {@link #lsRemote(String, Collection, GitEnvironment, int)} but using this repository
   * environment and explicit max number of log lines.
   *
   * @param refs - see <refs> in git help ls-remote
   * @param maxLogLines - Limit log lines to the number specified. -1 for unlimited
   * @param flags - additional flags to pass to ls-remote
   * @return - a map of refs to sha1 from the git ls-remote output.
   * @throws RepoException if the operation fails
   */
  public Map<String, String> lsRemote(
      String url, Collection<String> refs, int maxLogLines, Collection<String> flags)
      throws RepoException, ValidationException {
    return lsRemote(getCwd(), url, refs, gitEnv, maxLogLines, flags);
  }

  /**
   * Same as {@link #lsRemote(String, Collection)} but allows you to specify additional flags.
   *
   * @param refs - see <refs> in git help ls-remote
   * @param flags - additional flags to pass to ls-remote
   * @return - a map of refs to sha1 from the git ls-remote output.
   * @throws RepoException if the operation fails
   */
  public Map<String, String> lsRemote(String url, Collection<String> refs, Collection<String> flags)
      throws RepoException, ValidationException {
    return lsRemote(getCwd(), url, refs, gitEnv, DEFAULT_MAX_LOG_LINES, flags);
  }

  @CheckReturnValue
  static String validateUrl(String url) throws RepoException, ValidationException {
    // support remote helper syntax <transport>::<address>
    List<String> parts = Splitter.on("::").splitToList(url);
    if (parts.size() == 2) {
      // run validation on the address portion
      return parts.get(0) + "::" + validateUrl(parts.get(1));
    }

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
   *
   * @param refs the refs to pass to the git show-ref command
   * @return the result of git show-ref
   */
  public ImmutableMap<String, GitRevision> showRef(Iterable<String> refs) throws RepoException {
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
    return result.buildOrThrow();
  }


  /**
   * Execute show-ref git command in the local repository and returns a map from reference name to
   * GitReference(SHA-1).
   */
  public ImmutableMap<String, GitRevision> showRef() throws RepoException {
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
    return new GitRepository(
        this.gitDir,
        newWorkTree,
        this.verbose,
        this.gitEnv,
        repoTimeout,
        this.noVerify,
        this.pushOptionsValidator);
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
   * Can be overwritten to add custom behavior.
   */
  protected String runPush(PushCmd pushCmd) throws RepoException, ValidationException {
    List<String> cmd = Lists.newArrayList("push");

    // This shows progress in the log if not attached to a terminal
    cmd.add("--progress");

    for (String pushOption : pushCmd.pushOptions) {
      cmd.add(String.format("--push-option=%s", pushOption));
    }

    if (pushCmd.prune) {
      cmd.add("--prune");
    }

    if (pushCmd.force) {
      cmd.add("--force");
    }

    for (Entry<String, String> entry : pushCmd.forceLease.entrySet()) {
      cmd.add(String.format("--force-with-lease=%s:%s", entry.getKey(), entry.getValue()));
    }

    if (noVerify) {
      cmd.add("--no-verify");
    }

    if (pushCmd.url != null) {
      cmd.add(validateUrl(pushCmd.url));
      for (Refspec refspec : pushCmd.refspecs) {
        cmd.add(refspec.toString());
      }
    }
    try {
      return simpleCommand(repoTimeout, cmd).getStderr();
    } catch (RepoException e) {
      if (e.getMessage().contains(HTTP_PERMISSION_DENIED)) {
        throw new AccessValidationException("Permission error pushing to " + pushCmd.url, e);
      }
      throw e;
    }
  }

  /**
   * git branch command
   */
  public class BranchCmd {
    private final String name;
    @Nullable private final String startPoint;

    private BranchCmd(String name, @Nullable String startPoint) {
      this.name = checkNotNull(name);
      this.startPoint = startPoint;
    }

    /** Create the branch from this commit. If not set, it uses current HEAD. */
    @CheckReturnValue
    public BranchCmd withStartPoint(String startPoint) {
      return new BranchCmd(name, checkNotNull(startPoint));
    }

    void run() throws RepoException {
      List<String> args = Lists.newArrayList("branch", name);
      if (startPoint != null) {
        args.add(startPoint);
      }
      simpleCommand(args);
    }
  }

  /** Builder of git branch command. */
  public BranchCmd branch(String name) {
    return new BranchCmd(name, null);
  }

  /** A class that represents 'git cherry-pick' command and options */
  public class CherryPickCmd {

    private final ImmutableList<String> commits;
    @Nullable private final Integer parentNumber;
    private final boolean addCommitOriginInfo;
    private final boolean fastForward;
    private final boolean allowEmpty;

    public CherryPickCmd(ImmutableList<String> commit, @Nullable Integer parentNumber,
        boolean addCommitOriginInfo, boolean fastForward,
        boolean allowEmpty) {
      this.commits = commit;
      this.parentNumber = parentNumber;
      this.addCommitOriginInfo = addCommitOriginInfo;
      this.fastForward = fastForward;
      this.allowEmpty = allowEmpty;
    }

    /** git cherry-pick -m parent-number: Allow to cherry-pick merges by specifying the parent */
    @CheckReturnValue
    public CherryPickCmd parentNumber(int parentNumber) {
      return new CherryPickCmd(commits, parentNumber, addCommitOriginInfo, fastForward, allowEmpty);
    }

    /**
     * Include an additional message saying where the commit is coming from. This is -x flag
     * in git cherry-pick.
     */
    @CheckReturnValue
    public CherryPickCmd addCommitOriginInfo(boolean addCommitOriginInfo) {
      return new CherryPickCmd(commits, parentNumber, addCommitOriginInfo, fastForward, allowEmpty);
    }

    /** git cherry-pick --ff */
    @CheckReturnValue
    public CherryPickCmd fastForward(boolean fastForward) {
      return new CherryPickCmd(commits, parentNumber, addCommitOriginInfo, fastForward, allowEmpty);
    }

    /** git cherry-pick --allow-empty */
    @CheckReturnValue
    public CherryPickCmd allowEmpty(boolean allowEmpty) {
      return new CherryPickCmd(commits, parentNumber, addCommitOriginInfo, fastForward, allowEmpty);
    }

    public void run() throws RepoException {
      List<String> args = Lists.newArrayList("cherry-pick");
      if (parentNumber != null) {
        args.add("-m");
        args.add(parentNumber.toString());
      }
      if (addCommitOriginInfo) {
        args.add("-x");
      }
      if (fastForward) {
        args.add("--ff");
      }
      if (allowEmpty) {
        args.add("--allow-empty");
      }
      args.addAll(commits);
      simpleCommand(args);
    }
  }

  @CheckReturnValue
  public CherryPickCmd cherryPick(Iterable<String> commits) {
    return new CherryPickCmd(ImmutableList.copyOf(commits), null, false, false, false);
  }

  public void abortCherryPick() throws RepoException {
    simpleCommand("cherry-pick", "--abort");
  }

  /**
   * An add command bound to the repo that can be configured and then executed with {@link #run()}.
   */
  public class AddCmd {

    private final boolean force;
    private final boolean all;
    private final Iterable<String> files;
    @Nullable private final String pathSpecFromFile;

    private AddCmd(boolean force, boolean all, Iterable<String> files, String pathSpecFromFile) {
      this.force = force;
      this.all = all;
      this.files = checkNotNull(files);
      this.pathSpecFromFile = pathSpecFromFile;
    }

    /** Force the add */
    @CheckReturnValue
    public AddCmd force() {
      return new AddCmd(/*force=*/ true, all, files, pathSpecFromFile);
    }

    /** Add all the unstagged files to the index */
    @CheckReturnValue
    public AddCmd all() {
      Preconditions.checkState(Iterables.isEmpty(files), "'all' and passing files is incompatible");
      Preconditions.checkState(
          pathSpecFromFile == null, "'all' and pathSpecFromFile is incompatible");
      return new AddCmd(force, /*all=*/ true, files, pathSpecFromFile);
    }

    /** Configure the files to add to the index */
    @CheckReturnValue
    public AddCmd files(Iterable<String> files) {
      Preconditions.checkState(!all, "'all' and passing files is incompatible");
      Preconditions.checkState(
          pathSpecFromFile == null, "'pathSpecFromFile' and passing files is incompatible");
      return new AddCmd(force, /*all=*/ false, files, pathSpecFromFile);
    }

    /** Configure the files to add to the index */
    @CheckReturnValue
    public AddCmd pathSpecFromFile(String pathSpecFromFile) {
      Preconditions.checkState(!all, "'pathSpecFromFile' and passing files is incompatible");
      Preconditions.checkState(
          Iterables.isEmpty(files), "'pathSpecFromFile' and passing files is incompatible");
      return new AddCmd(force, /*all=*/ false, files, pathSpecFromFile);
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

      if (pathSpecFromFile != null) {
        params.add("--pathspec-from-file=" + pathSpecFromFile);
      }
      params.add("--");
      Iterables.addAll(params, files);
      git(getCwd(), Optional.empty(), addGitDirAndWorkTreeParams(params));
    }
  }

  /**
   * Create a git add command that can be configured before execution.
   */
  @CheckReturnValue
  public AddCmd add() {
    return new AddCmd(/*force*/ false, /*all*/ false, /*files*/ ImmutableSet.of(), null);
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
    CommandOutputWithStatus out =
        gitAllowNonZeroExit(NO_INPUT, addGitDirAndWorkTreeParams(params.build()), DEFAULT_TIMEOUT);
    if (out.getTerminationStatus().success()) {
      Set<String> modules = new LinkedHashSet<>();
      for (String line : Splitter.on('\n').omitEmptyStrings().trimResults().split(
          out.getStdout().trim())) {
        if (!line.startsWith("submodule.")) {
          continue;
        }
        modules.add(line.substring("submodule.".length(),
            line.lastIndexOf('.') > 0 ? line.lastIndexOf('.') : line.length()));
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

  /** An object capable of performing a 'git rebase' operation on the local repository. */
  public class RebaseCmd {
    private final GitRepository repo;
    @Nullable private final String branch;
    private final String upstream;
    @Nullable private final String into;
    @Nullable private final String errorAdvice;

    @CheckReturnValue
    RebaseCmd(
        GitRepository repo,
        String upstream,
        @Nullable String branch,
        @Nullable String into,
        @Nullable String errorAdvice) {

      this.repo = repo;
      this.branch = branch;
      this.upstream = upstream;
      this.into = into;
      this.errorAdvice = errorAdvice;
    }

    /** Set the branch to rebase */
    @CheckReturnValue
    public RebaseCmd branch(String branch) {
      return new RebaseCmd(this.repo, upstream, branch, into, errorAdvice);
    }

    /** Set --into branch. See git rebase. */
    @CheckReturnValue
    public RebaseCmd into(String into) {
      return new RebaseCmd(this.repo, upstream, branch, into, errorAdvice);
    }

    /**
     * Additional advice to the user in case of error. Can have context on flags or manual
     * intervention that fix the problem.
     */
    @CheckReturnValue
    public RebaseCmd errorAdvice(String errorAdvice) {
      return new RebaseCmd(this.repo, upstream, branch, into, errorAdvice);
    }

    /** Run 'git rebase'. */
    public void run() throws RepoException, RebaseConflictException {
      List<String> cmd = Lists.newArrayList("rebase", upstream);

      if (branch != null) {
        cmd.add(branch);
      }

      if (into != null) {
        cmd.add("--into");
        cmd.add(into);
      }

      CommandOutputWithStatus output = gitAllowNonZeroExit(NO_INPUT, cmd, DEFAULT_TIMEOUT);

      if (output.getTerminationStatus().success()) {
        return;
      }

      if (FAILED_REBASE.matcher(output.getStderr()).find()) {
        throw new RebaseConflictException(
            String.format(
                "Conflict detected while rebasing %s to %s. Please sync or update the change in the"
                    + " origin and retry. Git output was:\n"
                    + "%s%s",
                workTree,
                branch,
                output.getStdout(),
                errorAdvice != null ? ". " + errorAdvice : ""));
      }
      throw new RepoException(output.getStderr());
    }
  }

  /** Create a git rebase command. */
  @CheckReturnValue
  public RebaseCmd rebaseCmd(String upstream) {
    return new RebaseCmd(this, checkNotNull(upstream), null, null, null);
  }

  /** Try to cherry pick a commit. If it fails, cherry-pick will be aborted and return false*/
  public boolean tryToCherryPick(String commit) {
    try {
      this.simpleCommand("cherry-pick", commit);
      return true;
    } catch (RepoException e) {
      logger.atSevere().withCause(e).log("Cherry-pick failed for %s", commit);
      // Abort the cherry-pick
      try {
        this.abortCherryPick();
      } catch (RepoException ex) {
        logger.atWarning().withCause(ex).log("cherry-pick --abort failed.");
      }
      return false;
    }
  }

  /** Return the symbolic-ref (branch name) of HEAD. If it fails, return the sha1 of HEAD*/
  public GitRevision getHeadRef()
      throws RepoException, CannotResolveRevisionException {
    try {
      String reference = getPrimaryBranch();
      return new GitRevision(this, parseRef(reference), null, reference,
          ImmutableListMultimap.of(), null);
    } catch (RepoException e) {
      return new GitRevision(this, this.resolveReference("HEAD").getSha1());
    }
  }

  /** Check whether the remote sha1's tree is the same as repo's HEAD */
  public boolean hasSameTree(String remoteCommit)
      throws RepoException {
    GitLogEntry newChange = Iterables.getLast(this.log("HEAD").withLimit(1).run());
    this.simpleCommand("checkout", "-b", "cherry_pick" + UUID.randomUUID(), "HEAD~1");
    if (tryToCherryPick(remoteCommit)) {
      GitLogEntry oldWithCherryPick = Iterables.getLast(this.log("HEAD").withLimit(1).run());
      return oldWithCherryPick.tree().equals(newChange.tree());
    }
    return false;
  }

  /**
   * Checks out the given ref in the repo, quietly and throwing away local changes. If checkoutPath
   * is empty, it will checkout all files. If not, it will only checkout checkoutPaths.
   *
   * @param ref the ref to checkout
   * @param checkoutPaths the paths to checkout, relative to the repo's root
   * @param commandTimeout the command timeout to use for the checkout operation
   * @return the command output
   * @throws RepoException if there is an issue checking out the ref from the repo
   */
  @CanIgnoreReturnValue
  public CommandOutput forceCheckout(
      String ref, ImmutableSet<String> checkoutPaths, Duration commandTimeout)
      throws RepoException {
    ImmutableList.Builder<String> argv = ImmutableList.builder();
    argv.add("checkout", "-q", "-f", checkNotNull(ref));
    argv.addAll(checkoutPaths.stream().filter(e -> !e.isEmpty()).collect(Collectors.toList()));
    return simpleCommand(commandTimeout, argv.build());
  }

  /** Checks out the given ref in the repo, quietly and throwing away local changes. */
  public CommandOutput forceCheckout(String ref) throws RepoException {
    return forceCheckout(ref, null);
  }

  /**
   * Checks out the given ref in the repo, quietly and throwing away local changes.
   *
   * @param ref the ref to check out
   * @return the command output
   * @throws RepoException if there is an issue checking out the given ref
   */
  @CanIgnoreReturnValue
  public CommandOutput forceCheckout(String ref, @Nullable Duration commandTimeout)
      throws RepoException {
    return simpleCommand(commandTimeout, "checkout", "-q", "-f", checkNotNull(ref));
  }

  /** Set the sparse checkout */
  public CommandOutput setSparseCheckout(ImmutableSet<String> checkoutPaths) throws RepoException {
    ImmutableList.Builder<String> argv = ImmutableList.builder();
    argv.add("sparse-checkout", "set");
    argv.addAll(
        checkoutPaths.stream()
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList()));
    argv.add("--cone");
    return simpleCommand(argv.build());
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
      String baseline = "unknown";
      try {
        baseline = parseRef("HEAD");
      } catch (CannotResolveRevisionException | RepoException e) {
        logger.atWarning().withCause(e).log("Cannot find baseline.");
      }
      throw new EmptyChangeException(
          String.format(
              "Migration of the revision resulted in an empty change from baseline '%s'.\n"
                  + "Is the change already migrated?", baseline));
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
    if (noVerify) {
      params.add("--no-verify");
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
      git(getCwd(), Optional.empty(), addGitDirAndWorkTreeParams(params.build()));
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
    CommandOutput output = git(getCwd(), Optional.empty(),
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
  Iterable<Submodule> listSubmodules(String currentRemoteUrl, GitRevision ref) throws RepoException {
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
        branch = ref.contextReference(); // Either "branch/tag" or null to force fetching all refs
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

  // TODO(malcon): Create a builder like LogCmd, etc.
  ImmutableList<TreeElement> lsTree(GitRevision reference, @Nullable String treeish,
      boolean recursive, boolean fullName)
      throws RepoException {
    ImmutableList.Builder<TreeElement> result = ImmutableList.builder();
    List<String> args = Lists.newArrayList("ls-tree", reference.getSha1());
    if (recursive) {
      args.add("-r");
    }
    if (fullName) {
      args.add("--full-name");
    }

    args.add("-z");

    if (treeish != null) {
      args.add("--");
      args.add(treeish);
    }

    String stdout = simpleCommand(args).getStdout();
    for (String line : Splitter.on('\0').split(stdout)) {
      if (line.isEmpty()) {
        continue;
      }
      Matcher matcher = LS_TREE_ELEMENT.matcher(line);
      if (!matcher.matches()) {
        throw new RepoException("Unexpected format for ls-tree output: " + line);
      }
      String mode = matcher.group(1);
      GitObjectType objectType =
          GitObjectType.valueOf(matcher.group(2).toUpperCase(Locale.getDefault()));
      String sha1 = matcher.group(3);
      String path = matcher.group(4);

      result.add(new TreeElement(objectType, sha1, path, mode));
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
      git(workTree, Optional.empty(), ImmutableList.of("init", "."));
    } else {
      git(gitDir, Optional.empty(), ImmutableList.of("init", "--bare"));
    }
    return this;
  }

  @CanIgnoreReturnValue
  public GitRepository withCredentialHelper(String credentialHelper) throws RepoException {
    replaceLocalConfigField("credential", "helper", checkNotNull(credentialHelper));
    return this;
  }

  public void replaceLocalConfigField(String category, String field, String value)
      throws RepoException {
    this.simpleCommand(
        "config", "--replace-all", "--local", String.format("%s.%s", category, field), value);
  }

  public GitRepository enablePartialFetch() {
    try {
      this.simpleCommand("config", "core.repositoryFormatVersion", "1");
      this.simpleCommand("config", "extensions.partialClone", "origin");
    } catch (Exception e) {
      logger.atInfo().withCause(e).log("Partial Clone %s", e);
    }
    return this;
  }

  public void setRemoteOriginUrl(String url) {
    try {
      this.simpleCommand("config", "remote.origin.url", url);
    } catch (RepoException e) {
      logger.atInfo().withCause(e).log("Remote Origin URL %s", e);
    }
  }

  public UserPassword credentialFill(String url) throws RepoException, ValidationException {
    return new GitCredential(Duration.ofMinutes(1), gitEnv)
        .fill(gitDir, url);
  }

  /**
   * Runs a {@code git} command with the {@code --git-dir} and (if non-bare) {@code --work-tree}
   * args set, and returns the {@link CommandOutput} if the command execution was successful.
   *
   * <p>Git commands usually write to stdout, but occasionally they write to stderr. It's
   * responsibility of the client to consume the output from the correct source.
   *
   * <p>WARNING: Please consider creating a higher level function instead of calling this method. At
   * some point we will deprecate.
   *
   * @param timeout the timeout duration to pass to {@code git} command runner
   * @param argv the arguments to pass to {@code git}, starting with the sub-command name
   */
  @CanIgnoreReturnValue
  public CommandOutput simpleCommand(@Nullable Duration timeout, String... argv)
      throws RepoException {
    return simpleCommand(timeout, Arrays.asList(argv));
  }

  @CanIgnoreReturnValue
  public CommandOutput simpleCommand(String... argv) throws RepoException {
    return simpleCommand(Arrays.asList(argv));
  }

  @CanIgnoreReturnValue
  public CommandOutput simpleCommand(Duration timeout, List<String> argv)
      throws RepoException {
    return git(getCwd(), Optional.ofNullable(timeout), addGitDirAndWorkTreeParams(argv));
  }

  @CanIgnoreReturnValue
  public CommandOutput simpleCommand(List<String> argv) throws RepoException {
    return git(getCwd(), Optional.empty(), addGitDirAndWorkTreeParams(argv));
  }

  /**
   * Pulls Git LFS files into the working tree.
   * 
   * <p>This is necessary when GIT_LFS_SKIP_SMUDGE=1 is set, which causes git operations to
   * work with LFS pointer files instead of actual file content. This method temporarily
   * configures remote.origin.url, pulls the LFS files, then cleans up the configuration.
   *
   * @param remoteUrl The URL to set as remote.origin.url for LFS operations
   * @throws RepoException if LFS operations fail
   */
  public void lfsPull(String remoteUrl) throws RepoException {
    logger.atInfo().log("Pulling Git LFS files from %s", remoteUrl);
    try {
      // Step 1: Add remote.origin.url (required for git lfs pull to work)
      simpleCommand("config", "remote.origin.url", remoteUrl);
      
      // Step 2: Pull LFS files
      simpleCommand("lfs", "pull");
      
      // Step 3: Unset remote.origin.url (cleanup)
      simpleCommand("config", "--unset", "remote.origin.url");
      
      logger.atInfo().log("Successfully pulled Git LFS files");
    } catch (RepoException e) {
      // Attempt cleanup even on error
      try {
        simpleCommand("config", "--unset", "remote.origin.url");
      } catch (RepoException cleanupError) {
        logger.atWarning().withCause(cleanupError).log(
            "Failed to cleanup remote.origin.url after LFS error");
      }
      throw new RepoException("Failed to pull LFS files: " + e.getMessage(), e);
    }
  }

  /**
   * Lists Git LFS files with their object IDs and file paths.
   *
   * <p>Returns output from "git lfs ls-files -l" which includes object IDs and paths.
   * Output format: <OID> - <filepath>
   *
   * @return List of lines from git lfs ls-files -l, or empty list if no LFS files
   * @throws RepoException if the command fails
   */
  public ImmutableList<String> lfsListFiles() throws RepoException {
    try {
      CommandOutput output = simpleCommandNoRedirectOutput("lfs", "ls-files", "-l");
      return ImmutableList.copyOf(
          Splitter.on('\n').omitEmptyStrings().trimResults().split(output.getStdout()));
    } catch (RepoException e) {
      // If LFS is not installed or no LFS files exist, return empty list
      logger.atInfo().withCause(e).log("Could not list LFS files, returning empty list");
      return ImmutableList.of();
    }
  }

  /**
   * Pushes specific Git LFS objects by their object IDs to a remote URL.
   *
   * <p>This is used to proactively push LFS objects to the destination before pushing commits,
   * ensuring that the destination has all necessary LFS file content.
   *
   * @param destinationUrl The destination repository URL
   * @param objectIds List of LFS object IDs to push
   * @throws RepoException if the push operation fails
   */
  public void lfsPushObjects(String destinationUrl, ImmutableList<String> objectIds)
      throws RepoException {
    if (objectIds.isEmpty()) {
      logger.atInfo().log("No LFS objects to push");
      return;
    }

    logger.atInfo().log("Pushing %d LFS object(s) to %s", objectIds.size(), destinationUrl);
    
    for (String objectId : objectIds) {
      try {
        simpleCommand("lfs", "push", destinationUrl, "--object-id", objectId);
      } catch (RepoException e) {
        logger.atWarning().withCause(e).log(
            "Failed to push LFS object %s to %s", objectId, destinationUrl);
        // Continue with other objects even if one fails
      }
    }
    
    logger.atInfo().log("Successfully pushed LFS objects");
  }

  CommandOutput simpleCommandNoRedirectOutput(String... argv) throws RepoException {
    Iterable<String> params = addGitDirAndWorkTreeParams(Arrays.asList(argv));
    try {
      // Use maxLoglines 0 and verbose=false to avoid redirection
      return executeGit(
          getCwd(),
          params,
          gitEnv,
          /* verbose= */ false,
          /* maxLogLines= */ 0,
          /** timeout= */ Optional.empty());
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
    return git(cwd, Optional.empty(), Arrays.asList(params));
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
  @CanIgnoreReturnValue
  protected CommandOutput git(Path cwd, Optional<Duration> timeout, Iterable<String> params)
      throws RepoException {
    try {
      return executeGit(cwd, params, gitEnv, verbose, timeout);
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
  protected CommandOutputWithStatus gitAllowNonZeroExit(byte[] stdin, Iterable<String> params,
      Duration defaultTimeout)
      throws RepoException {
      return gitAllowNonZeroExit(stdin, params, defaultTimeout, -1);
  }

  /**
   * Execute git allowing non-zero exit codes. This will only allow program non-zero exit codes
   * (0-10. The upper bound is arbitrary). And will still fail for exit codes like 127 (Command not
   * found).
   */
  protected CommandOutputWithStatus gitAllowNonZeroExit(byte[] stdin, Iterable<String> params,
      Duration defaultTimeout, int maxLogLines)
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
      CommandRunner runner =  new CommandRunner(cmd, defaultTimeout)
          .withVerbose(verbose)
          .withInput(stdin);
      if (maxLogLines != -1) {
        runner = runner.withMaxStdOutLogLines(maxLogLines);
      }
      return runner.execute();
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

  @CanIgnoreReturnValue
  private static CommandOutputWithStatus executeGit(
      Path cwd,
      Iterable<String> params,
      GitEnvironment gitEnv,
      boolean verbose,
      Optional<Duration> timeout)
      throws CommandException {
    return executeGit(cwd, params, gitEnv, verbose, DEFAULT_MAX_LOG_LINES, timeout);
  }

  @CanIgnoreReturnValue
  private static CommandOutputWithStatus executeGit(
      Path cwd,
      Iterable<String> params,
      GitEnvironment gitEnv,
      boolean verbose,
      int maxLogLines,
      Optional<Duration> timeout)
      throws CommandException {
    List<String> allParams = new ArrayList<>(Iterables.size(params) + 1);
    allParams.add(gitEnv.resolveGitBinary());
    Iterables.addAll(allParams, params);
    
    Command cmd =
        new Command(
            Iterables.toArray(allParams, String.class), gitEnv.getEnvironment(), cwd.toFile());

    CommandRunner runner =
        (timeout.isPresent() ? new CommandRunner(cmd, timeout.get()) : new CommandRunner(cmd))
            .withVerbose(verbose);
    
    CommandOutputWithStatus result = maxLogLines >= 0
        ? runner.withMaxStdOutLogLines(maxLogLines).execute()
        : runner.execute();
    
    return result;
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
        return new GitRevision(this, reference, url);
      }
      throw new CannotResolveRevisionException(
          String.format("Cannot find '%s' object in the repository (%s)", reference, url));
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

  public byte[] readFileBytes(String revision, String path) throws RepoException {
    CommandOutputWithStatus result = gitAllowNonZeroExit(NO_INPUT,
        ImmutableList.of("--no-pager", "show", String.format("%s:%s", revision, path)),
        DEFAULT_TIMEOUT, 0);
    if (!result.getTerminationStatus().success()) {
      throw new RepoException(String.format("Cannot read file '%s' in '%s'", path, revision));
    }
    return result.getStdoutBytes();
  }

  /** Reads a file at the given revision */
  public String readFile(String revision, String path) throws RepoException {
    return new String(readFileBytes(revision, path), StandardCharsets.UTF_8);
  }

  public Path readSymlink(String revision, String path) throws RepoException {
    String symlinkContents = readFile(revision, path);
    return Path.of(symlinkContents);
  }

  /** Returns the commit hash at which the given file was last modified. */
  public String lastModified(String revision, String path) throws RepoException {
    CommandOutputWithStatus result =
        gitAllowNonZeroExit(
            NO_INPUT,
            ImmutableList.of(
                "--no-pager", "log", "--pretty=format:%H", "--max-count=1", revision, "--", path),
            DEFAULT_TIMEOUT,
            0);
    if (!result.getTerminationStatus().success()) {
      throw new RepoException(
          String.format("Cannot get last modified revision of '%s' in '%s'", path, revision));
    }
    return result.getStdout();
  }

  public void checkout(Glob glob, Path destRoot, GitRevision rev) throws RepoException {
    ImmutableList<TreeElement> treeElements = lsTree(rev, null, true, true);
    PathMatcher pathMatcher = glob.relativeTo(destRoot);

    var checkoutFiles = new Object() {
      void run(ImmutableList<String> files) throws RepoException {
        ImmutableList.Builder<String> args = ImmutableList.builder();
        args.add(
          "--git-dir", gitDir.toString(),
          "--work-tree", destRoot.toString(),
          "checkout", rev.getSha1(), "--");
        args.addAll(files);
        git(getCwd(), args.build().toArray(new String[0]));
      }
    };

    ImmutableList.Builder<String> pendingFiles = ImmutableList.builder();
    int pendingFilesLength = 0;
    for (TreeElement file : treeElements) {
      var path = file.path();
      if (pathMatcher.matches(destRoot.resolve(path))) {
        pendingFiles.add(path);
        pendingFilesLength += path.length();
      }

      // Arbitrarily limit the size of the files list. If it exceeds the limit,
      // we do the checkout in multiple batches. This works around "argument
      // list too long" errors.
      if (pendingFilesLength > 128 * 1024) {
        checkoutFiles.run(pendingFiles.build());
        pendingFiles = ImmutableList.builder();
        pendingFilesLength = 0;
      }
    }
    if (pendingFilesLength > 0) checkoutFiles.run(pendingFiles.build());
  }

  GitRevision commitTree(String message, String tree, List<GitRevision> parents)
      throws RepoException {
    ImmutableList.Builder<String> args = ImmutableList.<String>builder().add("commit-tree", tree);
    for (GitRevision parent : parents) {
      args.add("-p", parent.getSha1());
    }
    args.add("-m", message);

    return new GitRevision(
        this,
        git(getCwd(), Optional.empty(), addGitDirAndWorkTreeParams(args.build()))
            .getStdout()
            .trim());
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
   *
   * @param url Resolved submodule URL. Urls like './foo' have been already resolved to its
   *     corresponding absolute one.
   * @param name Name of the submodule.
   * @param branch Branch associated with the submodule. Supported values: null or '.' (HEAD is
   *     used) or a regular reference.
   * @param path Relative path for the checkout of the submodule
   */
  public static record Submodule(String url, String name, String branch, String path) {}

  static record TreeElement(GitObjectType type, String ref, String path, String mode) {
    TreeElement {
      checkNotNull(type);
      checkNotNull(ref);
      checkNotNull(path);
      checkNotNull(mode);
    }
    public static final String SYMLINK_MODE = "120000";
  }

  enum GitObjectType {
    BLOB,
    COMMIT,
    TAG,
    TREE
  }

  static final record StatusFile(
      String file,
      @Nullable String newFileName,
      StatusCode indexStatus,
      StatusCode workdirStatus) {

    public StatusFile {
      checkNotNull(file);
      checkNotNull(indexStatus);
      checkNotNull(workdirStatus);
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
   * Hook to rewrite exceptions thrown by the git invocation, e.g. user error.
   */
  protected void handlePushException(Exception e, PushCmd cmd)
      throws RepoException, ValidationException {
     /* Non-fast-forward errors in git mirror usually means that the destination
        has commits that the origin doesn't. Usually by a user submitting directly
        to the destination instead of using Copybara. */
    if (e.getMessage().contains("(non-fast-forward)") || e.getMessage().contains("(fetch first)")) {
      throw new NonFastForwardRepositoryException(
          String.format(
              "Failed to push to %s %s, because local/origin history is behind destination",
              cmd.url, cmd.refspecs()),
          e);
    }
    if (e.getMessage().contains("(stale info)")) {
      throw new NonFastForwardRepositoryException(
          String.format(
              "Failed to push to %s %s, because destination is not in expected state",
              cmd.url, cmd.refspecs()),
          e);
    }
    Throwables.throwIfInstanceOf(e, RepoException.class);
    Throwables.throwIfInstanceOf(e, ValidationException.class);
  }

  /** An object capable of performing a 'git push' operation to a remote repository. */
  public static record PushCmd(
      GitRepository repo,
      @Nullable String url,
      ImmutableList<Refspec> refspecs,
      boolean prune,
      boolean force,
      ImmutableMap<String, String> forceLease,
      ImmutableList<String> pushOptions,
      PushOptionsValidator pushOptionsValidator) {

    public PushCmd {
      checkNotNull(repo);
      checkNotNull(refspecs);
      Preconditions.checkArgument(
          refspecs.isEmpty() || url != null, "refspec can only be" + " used when a url is passed");
      checkNotNull(forceLease);
      checkNotNull(pushOptions);
    }

    @CheckReturnValue
    public PushCmd withRefspecs(String url, Iterable<Refspec> refspecs) {
      return new PushCmd(
          repo,
          checkNotNull(url),
          ImmutableList.copyOf(refspecs),
          prune,
          force,
          forceLease,
          pushOptions,
          pushOptionsValidator);
    }

    @CheckReturnValue
    public PushCmd withForceLease(Map<String, String> forceLease) {
      return new PushCmd(
          repo,
          url,
          refspecs,
          prune,
          force,
          ImmutableMap.copyOf(forceLease),
          pushOptions,
          pushOptionsValidator);
    }

    @CheckReturnValue
    public PushCmd prune(boolean prune) {
      return new PushCmd(
          repo, url, this.refspecs, prune, force, forceLease, pushOptions, pushOptionsValidator);
    }

    @CheckReturnValue
    public PushCmd force(boolean force) {
      return new PushCmd(
          repo, url, this.refspecs, prune, force, forceLease, pushOptions, pushOptionsValidator);
    }

    /**
     * Returns a new instance of {@code PushCmd} with {@code newPushOptions}
     *
     * @param newPushOptions - the new push options to set
     * @throws ValidationException if {@code newPushOptions} fails validation against a set {@code
     *     this.pushOptionsValidator}
     */
    @CheckReturnValue
    public PushCmd withPushOptions(ImmutableList<String> newPushOptions)
        throws ValidationException {
      pushOptionsValidator.validate(newPushOptions);
      return new PushCmd(
          repo, url, this.refspecs, prune, force, forceLease, newPushOptions, pushOptionsValidator);
    }

    /**
     * Runs the push command and returns the response from the server.
     *
     * @throws NonFastForwardRepositoryException if local repo is behind destination, unless force
     *     is used.
     */
    public String run() throws RepoException, ValidationException {
      String output = null;
      try {
        output = repo.runPush(this);
      } catch (RepoException | ValidationException e) {
        repo.handlePushException(e, this);
      }
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

  /** An object capable of performing a 'git merge' operation to a git repository. */
  public static class MergeCmd {
    protected String branch;
    protected String mergeMessage;
    protected String fastForward;
    protected GitRepository repo;
    protected List<String> commits;
    Function<Map<String, String>, Boolean> validator;

    public MergeCmd(
        GitRepository repo,
        String branch,
        String mergeMessage,
        List<String> commits,
        String fastForward, Function<Map<String, String>, Boolean> validator) {
      Preconditions.checkArgument(
          Arrays.asList("--no-ff", "--ff-only", "--ff").contains(fastForward));
      this.repo = checkNotNull(repo);
      this.validator = validator;
      this.branch = checkNotNull(branch);
      this.mergeMessage = mergeMessage;
      this.fastForward = checkNotNull(fastForward);
      this.commits = checkNotNull(commits);
    }

    public static MergeCmd create(
        GitRepository repo,
        String branch,
        List<String> commits,
        Function<Map<String, String>, Boolean> validator) {
      return new MergeCmd(repo, branch, "", commits, "--ff", validator);
    }

    public MergeCmd withFFMode(String ffMode) {
      return new MergeCmd(repo, branch, mergeMessage, commits, ffMode, validator);
    }

    public MergeCmd withMessage(String message) {
      return new MergeCmd(repo, branch, message, commits, fastForward, validator);
    }

    // TODO(linjordan) add chaining-setters if ever used in future like we do for other *Cmd.

    public void run(Map<String, String> configs) throws RepoException {
      Preconditions.checkArgument(
          validator.apply(configs), "Error could not validate git configs in %s", configs);
      List<String> command = Lists.newArrayList();

      for (Map.Entry<String, String> entry : configs.entrySet()) {
        command.addAll(
            Lists.newArrayList("-c", String.format("%s=%s", entry.getKey(), entry.getValue())));
      }
      command.addAll(Lists.newArrayList("merge", branch));

      if (!Strings.isNullOrEmpty(mergeMessage)) {
        command.addAll(Lists.newArrayList("-m", mergeMessage));
      }

      command.add(fastForward);
      command.addAll(commits);

      if (repo.noVerify) {
        command.add("--no-verify");
      }
      repo.simpleCommand(command);
    }
  }

  /**
   * An object capable of performing a 'git log' operation on a repository and returning a list of
   * {@link GitLogEntry}.
   *
   * <p>By default it returns the body, doesn't include the changed files and does --first-parent.
   */
  public static record LogCmd(
      GitRepository repo,
      String refExpr,
      int limit,
      ImmutableCollection<String> paths,
      boolean firstParent,
      boolean includeStat,
      boolean includeBody,
      @Nullable String grepString,
      boolean includeMergeDiff,
      int skip,
      int batchSize,
      boolean includeTags,
      boolean noWalk,
      boolean topoOrder) {

    private static final String COMMIT_FIELD = "commit";
    private static final String PARENTS_FIELD = "parents";
    private static final String TREE_FIELD = "tree";
    private static final String AUTHOR_FIELD = "author";
    private static final String AUTHOR_DATE_FIELD = "author_date";
    private static final String COMMITTER_FIELD = "committer";
    private static final String COMMITTER_DATE = "committer_date";
    private static final String TAG_FIELD = "tag";
    private static final String BEGIN_BODY = "begin_body";
    private static final String END_BODY = "end_body";
    private static final String COMMIT_SEPARATOR = "\u0001copybara\u0001";
    private static final Pattern UNINDENT = Pattern.compile("\n    ");
    private static final String GROUP = "--\n";

    static LogCmd create(GitRepository repository, String refExpr) {
      return new LogCmd(
          checkNotNull(repository),
          checkNotNull(refExpr),
          0,
          ImmutableList.of(), /*firstParent*/
          true,
          /* includeStat= */ false,
          /* includeBody= */ true,
          /* grepString= */ null,
          /* includeMergeDiff= */ false,
          /* skip= */ 0,
          /* batchSize= */ 0,
          /* includeTags= */ false,
          /* noWalk= */ false,
          /* topoOrder= */ false);
    }

    /**
     * Limit the query to {@code limit} results. Should be > 0.
     */
    @CheckReturnValue
    public LogCmd withLimit(int limit) {
      Preconditions.checkArgument(limit > 0);
      return new LogCmd(
          repo,
          refExpr,
          limit,
          paths,
          firstParent,
          includeStat,
          includeBody,
          grepString,
          includeMergeDiff,
          skip,
          batchSize,
          includeTags,
          noWalk,
          topoOrder);
    }

    /**
     * Skip the first {@code skip} commits. Should be >= 0.
     */
    @CheckReturnValue
    LogCmd withSkip(int skip) {
      Preconditions.checkArgument(skip >= 0);
      return new LogCmd(
          repo,
          refExpr,
          limit,
          paths,
          firstParent,
          includeStat,
          includeBody,
          grepString,
          includeMergeDiff,
          skip,
          batchSize,
          includeTags,
          noWalk,
          topoOrder);
    }

    /** Read in batches of siz {@code batchSize} commits. Should be >= 0. */
    @CheckReturnValue
    LogCmd withBatchSize(int batchSize) {
      Preconditions.checkArgument(batchSize >= 0);
      return new LogCmd(
          repo,
          refExpr,
          limit,
          paths,
          firstParent,
          includeStat,
          includeBody,
          grepString,
          includeMergeDiff,
          skip,
          batchSize,
          includeTags,
          noWalk,
          topoOrder);
    }

    /**
     * Only query for changes in {@code paths} paths.
     */
    @CheckReturnValue
    LogCmd withPaths(ImmutableCollection<String> paths) {
      Preconditions.checkArgument(paths.stream().noneMatch(s -> s.trim().equals("")));
      return new LogCmd(
          repo,
          refExpr,
          limit,
          paths,
          firstParent,
          includeStat,
          includeBody,
          grepString,
          includeMergeDiff,
          skip,
          batchSize,
          includeTags,
          noWalk,
          topoOrder);
    }

    /**
     * Set if --first-parent should be used in 'git log'.
     */
    @CheckReturnValue
    LogCmd firstParent(boolean firstParent) {
      return new LogCmd(
          repo,
          refExpr,
          limit,
          paths,
          firstParent,
          includeStat,
          includeBody,
          grepString,
          includeMergeDiff,
          skip,
          batchSize,
          includeTags,
          noWalk,
          topoOrder);
    }

    /** Set if --topo-order should be used in 'git log'. */
    @CheckReturnValue
    LogCmd topoOrder(boolean topoOrder) {
      return new LogCmd(
          repo,
          refExpr,
          limit,
          paths,
          firstParent,
          includeStat,
          includeBody,
          grepString,
          includeMergeDiff,
          skip,
          batchSize,
          includeTags,
          noWalk,
          topoOrder);
    }

    /** If files affected by the commit should be included in the response. */
    @CheckReturnValue
    public LogCmd includeFiles(boolean includeStat) {
      return new LogCmd(
          repo,
          refExpr,
          limit,
          paths,
          firstParent,
          includeStat,
          includeBody,
          grepString,
          includeMergeDiff,
          skip,
          batchSize,
          includeTags,
          noWalk,
          topoOrder);
    }

    /**
     * If file diff should be shown for merges. Equivalent to 'git log -m' command.
     */
    @CheckReturnValue
    LogCmd includeMergeDiff(boolean includeMergeDiff) {
      return new LogCmd(
          repo,
          refExpr,
          limit,
          paths,
          firstParent,
          includeStat,
          includeBody,
          grepString,
          includeMergeDiff,
          skip,
          batchSize,
          includeTags,
          noWalk,
          topoOrder);
    }

    /**
     * If the body (commit message) should be included in the response.
     */
    @CheckReturnValue
    LogCmd includeBody(boolean includeBody) {
      return new LogCmd(
          repo,
          refExpr,
          limit,
          paths,
          firstParent,
          includeStat,
          includeBody,
          grepString,
          includeMergeDiff,
          skip,
          batchSize,
          includeTags,
          noWalk,
          topoOrder);
    }

    /**
     * Look only for messages thatMatches grep expression.
     */
    @CheckReturnValue
    public LogCmd grep(@Nullable String grepString) {
      return new LogCmd(
          repo,
          refExpr,
          limit,
          paths,
          firstParent,
          includeStat,
          includeBody,
          grepString,
          includeMergeDiff,
          skip,
          batchSize,
          includeTags,
          noWalk,
          topoOrder);
    }

    /** Include tags in the response. */
    @CanIgnoreReturnValue
    public LogCmd includeTags(boolean includeTags) {
      return new LogCmd(
          repo,
          refExpr,
          limit,
          paths,
          firstParent,
          includeStat,
          includeBody,
          grepString,
          includeMergeDiff,
          skip,
          batchSize,
          includeTags,
          noWalk,
          topoOrder);
    }

    /** If the ancestors of commits should be included in the output. */
    @CanIgnoreReturnValue
    public LogCmd noWalk(boolean noWalk) {
      return new LogCmd(
          repo,
          refExpr,
          limit,
          paths,
          firstParent,
          includeStat,
          includeBody,
          grepString,
          includeMergeDiff,
          skip,
          batchSize,
          includeTags,
          noWalk,
          topoOrder);
    }

    /**
     * Run 'git log' and returns zero or more {@link GitLogEntry}.
     */
    public ImmutableList<GitLogEntry> run() throws RepoException {
      List<String> cmd =
          Lists.newArrayList("log", "--no-color", createFormat(includeBody, includeTags));

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
      // Without this flag, non-ascii characters in file names are returned wrapped
      // in quotes and the unicode chars escaped.
      cmd.add("-z");

      if (includeTags) {
        cmd.add("--tags");
      }

      if (noWalk) {
        cmd.add("--no-walk");
      }

      if (topoOrder) {
        cmd.add("--topo-order");
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

      return runGitLog(cmd);
    }

    private ImmutableList<GitLogEntry> runGitLog(List<String> cmd) throws RepoException {
      ImmutableList.Builder<GitLogEntry> res = ImmutableList.builder();
      ImmutableList<GitLogEntry> batchRes;
      int batchSkip = skip;
      int overallLimit = limit;
      do {
        ImmutableList.Builder<String> batchCmdBuilder = ImmutableList.builder();
        batchCmdBuilder.addAll(cmd);

        int batchLimit =
            limit == 0
                ? batchSize
                : (batchSize == 0 ? overallLimit : Math.min(batchSize, overallLimit));
        if (batchSkip > 0) {
          batchCmdBuilder.add("--skip");
          batchCmdBuilder.add(Integer.toString(batchSkip));
        }
        if (batchLimit > 0) {
          batchCmdBuilder.add("-" + batchLimit);
        }
        ImmutableList<String> batchCmd = batchCmdBuilder.build();
        logger.atInfo().log("Executing: %s", batchCmd);
        // Avoid logging since git log can return LOT of entries.
        CommandOutput output =
            limit > 0 && limit < 10
                ? repo.simpleCommand(batchCmd)
                : repo.simpleCommandNoRedirectOutput(batchCmd.toArray(new String[0]));
        batchRes = parseLog(output.getStdout(), includeBody);
        logger.atInfo().log("Log command returned %s entries", batchRes.size());
        if (!batchRes.isEmpty()) {
          logger.atInfo().log("First commit: %s", batchRes.get(0));
          logger.atInfo().log("Last commit: %s", Iterables.getLast(batchRes));
          // Log each commit that was searched for GitOrigin-RevID tracking
          // for (GitLogEntry entry : batchRes) {
          //   // System.out.println(String.format("COPYBARA_SEARCH: Searched commit: SHA=%s, Author=%s, Message=%s", 
          //   //     entry.commit().getSha1(), 
          //   //     entry.author().getName(), 
          //   //     entry.body().length() > 100 ? entry.body().substring(0, 100) + "..." : entry.body()));
          // }
        }
        if (batchSize > 0) {
          // Merge commit shows multiple entries when using -m and --name-only but first parent is
          // disabled. Each entry represents the parent file changes.
          batchSkip =
              (int)
                  (batchSkip + batchRes.stream().map(e -> e.commit().getSha1()).distinct().count());
          overallLimit -= batchSkip;
        }
        res.addAll(batchRes);
      } while (batchSize > 0 && (limit == 0 || overallLimit > 0) && !batchRes.isEmpty());
      return res.build();
    }

    private ImmutableList<GitLogEntry> parseLog(String log, boolean includeBody)
        throws RepoException {
      // No changes. We cannot know until we run git log since fromRef can be null (HEAD)
      if (log.isEmpty()) {
        return ImmutableList.of();
      }

      ImmutableList.Builder<GitLogEntry> commits = ImmutableList.builder();
      for (String msg : Splitter.on("\0" + COMMIT_SEPARATOR).
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

        ImmutableSet<String> files = null;
        if (includeStat) {
          String fileString = groups.get(2);
          if (fileString.startsWith("\0\n")) {
            fileString = fileString.substring(2);
          }
          files = ImmutableSet.copyOf(Splitter.on("\0").omitEmptyStrings().split(fileString));
        }
        ImmutableList.Builder<GitRevision> parents = ImmutableList.builder();
        for (String parent : Splitter.on(" ").omitEmptyStrings()
            .split(getField(fields, PARENTS_FIELD))) {
          parents.add(repo.createReferenceFromCompleteSha1(parent));
        }

        String tree = getField(fields, TREE_FIELD);
        String commit = getField(fields, COMMIT_FIELD);

        String tagString = includeTags ? getField(fields, TAG_FIELD) : null;
        GitRevision tag =
            tagString != null
                ? repo.createReferenceFromCompleteSha1(commit).withContextReference(tagString)
                : null;

        try {
          commits.add(
              new GitLogEntry(
                  repo.createReferenceFromCompleteSha1(commit),
                  parents.build(),
                  tree,
                  AuthorParser.parse(getField(fields, AUTHOR_FIELD)),
                  AuthorParser.parse(getField(fields, COMMITTER_FIELD)),
                  tryParseDate(fields, AUTHOR_DATE_FIELD, commit),
                  tryParseDate(fields, COMMITTER_DATE, commit),
                  body,
                  files,
                  tag));
        } catch (InvalidAuthorException e) {
          throw new RepoException("Error in commit '" + commit + "'. Invalid author.", e);
        }
      }
      return commits.build();
    }

    // Do not change this method since we could have old git commits that have incorrect date
    // formats.
    private ZonedDateTime tryParseDate(
        Map<String, String> fields, String dateField, String commit) {
      String value = getField(fields, dateField);
      try {
        return ZonedDateTime.parse(value);
      } catch (DateTimeParseException e) {
        logger.atSevere().withCause(e).log(
            "Cannot parse date '%s' for commit %s. Using epoch time instead", value, commit);
        return ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);
      }
    }

    private String getField(Map<String, String> fields, String field) {
      return checkNotNull(fields.get(field), "%s not present", field);
    }

    /**
     * We use a custom format that allows us easy parsing and be tolerant to random text in the body
     * (That is the reason why we indent the body).
     *
     * <p>We also use \u0001 as commit separator to prevent a file being confused as the separator.
     */
    private String createFormat(boolean includeBody, boolean includeTags) {
      return ("--format="
              + COMMIT_SEPARATOR
              + COMMIT_FIELD
              + "=%H\n"
              + PARENTS_FIELD
              + "=%P\n"
              + TREE_FIELD
              + "=%T\n"
              + AUTHOR_FIELD
              + "=%an <%ae>\n"
              + AUTHOR_DATE_FIELD
              + "=%aI\n"
              + COMMITTER_FIELD
              + "=%cn <%ce>\n"
              + COMMITTER_DATE
              + "=%cI"
              + (includeTags ? "\n" + TAG_FIELD + "=%S\n" : "\n")
              + GROUP
              // Body is padded by 4 spaces.
              + (includeBody
                  ? BEGIN_BODY + "\n" + "%w(0,4,4)%B%w(0,0,0)\n" + END_BODY + "\n"
                  : "\n")
              + GROUP)
          .replace("\n", "%n")
          .replace("\u0001", "%x01");
    }
  }

  /** An object that represent a commit as returned by 'git log'. */
  public static record GitLogEntry(
      GitRevision commit,
      ImmutableList<GitRevision> parents,
      String tree,
      Author author,
      Author committer,
      ZonedDateTime authorDate,
      ZonedDateTime commitDate,
      @Nullable String body,
      @Nullable ImmutableSet<String> files,
      @Nullable GitRevision tag) {}

  // Used for debugging issues
  @SuppressWarnings("unused")
  public String gitCmd() {
    return "git --git-dir=" + gitDir + (workTree != null ? " --work-tree=" + workTree : "");
  }

  /** An object capable of performing a 'git tag' operation to a remote repository. */
  // TODO(huanhuanchen): support deleting tag
  public static record TagCmd(
      GitRepository repo, String tagName, @Nullable String tagMessage, boolean force) {

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

  /** Returns the repo's primary branch, e.g. "main". */
  public String getPrimaryBranch() throws RepoException {
    // This actually returns the current branch, but in a newly initialized repo the two are the
    // same
    return simpleCommand("symbolic-ref", "--short", "HEAD").getStdout().trim();
  }

  /** Returns the repo's primary branch, e.g. "main". */
  @Nullable
  public String getPrimaryBranch(String uri) throws RepoException {
    Map<String, String> refs;
    try {
       refs = lsRemote(
           uri, ImmutableList.of("HEAD", "main", "master"),
           DEFAULT_MAX_LOG_LINES, ImmutableList.of("--symref"));
    } catch (ValidationException e) {
      throw new RepoException("Error parsing primary branch", e);
    }
    for (String key : refs.values()) {
      Matcher matcher = DEFAULT_BRANCH_PATTERN.matcher(key);
      if (matcher.matches()) {
        return matcher.group(2);
      }
    }
    // Repo has no HEAD, try to guess by testing which branches exist.
    if (refs.containsKey("refs/heads/main") && !refs.containsKey("refs/heads/master")) {
      return "main";
    }
    if (refs.containsKey("refs/heads/master") && !refs.containsKey("refs/heads/main")) {
      return "master";
    }
    return null;
  }

 public String getCurrentBranch() throws RepoException {
    try {
      String rev = simpleCommand("symbolic-ref", "--short", "HEAD").getStdout().trim();
      if (rev.equals("HEAD")) {
        return "";
      }
      return rev;
    } catch (RepoException re) {
      if (re.getMessage().contains("ref HEAD is not a symbolic ref")) {
        return "";
      }
      throw re;
    }
  }

  /** Interface for validating git options and providing userful error messages on failure. */
  public interface OptionsValidator {
    public void validate(List<String> options) throws ValidationException;
  }

  /** A version list that comes from a set of Strings */
  public static record PushOptionsValidator(Optional<ImmutableList<String>> allowedOptions)
      implements OptionsValidator {

    @Override
    public void validate(List<String> options) throws ValidationException {
      if (this.allowedOptions.isPresent() && !allowedOptions.get().containsAll(options)) {
        throw new ValidationException(
            String.format(
                "Push options have failed validation. The allowed push options are %s, but found"
                    + " push options not on the allowlist: %s",
                allowedOptions.get(),
                Sets.difference(new HashSet<>(options), new HashSet<>(allowedOptions.get()))));
      }
    }
  }
}
