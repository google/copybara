/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.hg;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static com.google.copybara.util.RepositoryUtil.validateNotHttp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.BadExitStatusWithOutputException;
import com.google.copybara.util.CommandOutput;
import com.google.copybara.util.CommandOutputWithStatus;
import com.google.copybara.util.CommandRunner;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A class for manipulating Hg (Mercurial) repositories
 */
public class HgRepository {

  /**
   * Label to mark the original revision id (Hg SHA-1) for migrated commits.
   */
  static final String HG_ORIGIN_REV_ID = "HgOrigin-RevId";

  private static final Pattern INVALID_HG_REPOSITORY =
      Pattern.compile("abort: repository .+ not found");

  private static final Pattern UNKNOWN_REVISION =
      Pattern.compile("abort: unknown revision '.+'");

  private static final Pattern INVALID_REF_EXPRESSION =
      Pattern.compile("syntax error in revset '.+'");

  /**
   * The location of the {@code .hg} directory.
   */
  private final Path hgDir;
  private final boolean verbose;
  private final Duration fetchTimeout;

  public HgRepository(Path hgDir, boolean verbose, Duration fetchTimeout) {
    this.hgDir = checkNotNull(hgDir);
    this.verbose = verbose;
    this.fetchTimeout = checkNotNull(fetchTimeout);
  }

  /**
   * Initializes a new hg repository
   * @return the new HgRepository
   * @throws RepoException if the directory cannot be created
   */
  public HgRepository init() throws RepoException {
    try {
      Files.createDirectories(hgDir);
    } catch (IOException e) {
      throw new RepoException("Cannot create directory: " + e.getMessage(), e);
    }
    hg(hgDir, ImmutableList.of("init"), DEFAULT_TIMEOUT);
    return this;
  }

  /**
   * Finds all changes from a repository at {@code url} and adds to the current repository.
   * Defaults to forced pull.
   *
   * <p>This does not update the copy of the project in the working directory.
   *
   * @param url remote hg repository url
   */
  void pullAll(String url) throws RepoException, ValidationException {
    pull(url, /*force*/ true, /*ref*/ null);
  }

  /**
   * Finds a single reference from a repository at {@code url} and adds to the current repository.
   * Defaults to forced pull.
   *
   * <p>This does not update the copy of the project in the working directory.
   *
   * @param url remote hg repository url
   * @param ref the remote revision to add
   */
  void pullFromRef(String url, String ref) throws RepoException, ValidationException {
    pull(url, /*force*/ true, /*ref*/ ref);
  }


  public void pull(String url, boolean force, @Nullable String ref)
      throws RepoException, ValidationException {
    ImmutableList.Builder<String> builder = ImmutableList.builder();

    builder.add("pull", validateNotHttp(url));

    if (force) {
      builder.add("--force");
    }

    if (!Strings.isNullOrEmpty(ref)) {
      builder.add("--rev", ref);
    }

    try {
      hg(hgDir, builder.build(), fetchTimeout);
    } catch (RepoException e) {
      if (INVALID_HG_REPOSITORY.matcher(e.getMessage()).find()){
        throw new ValidationException("Repository not found: " + e.getMessage());
      }
      if (UNKNOWN_REVISION.matcher(e.getMessage()).find()) {
        throw new ValidationException("Unknown revision: " + e.getMessage());
      }
      throw e;
    }
  }

  /**
   * Updates the working directory to the revision given at {@code ref} in the repository
   * and discards local changes.
   */
  CommandOutput cleanUpdate(String ref) throws RepoException {
      return hg(hgDir, "update", ref, "--clean");
  }

  /**
   * Returns a revision object given a reference.
   *
   * <p> A reference can be any of the following:
   * <ul>
   *   <li> A global identifier for a revision. Example: f4e0e692208520203de05557244e573e981f6c72
   *   <li> A local identifier for a revision in the repository. Example: 1
   *   <li> A bookmark in the repository.
   *   <li> A branch in the repository, which returns the tip of that branch. Example: default
   *   <li> A tag in the repository. Example: tip
   * </ul>
   *
   */
  public HgRevision identify(String reference)
      throws RepoException, CannotResolveRevisionException {
    try {
      CommandOutput commandOutput =
          hg(hgDir, "identify", "--template", "{node}\n", "--id", "--rev", reference);

      String globalId = commandOutput.getStdout().trim();
      return new HgRevision(globalId, reference);
    } catch (RepoException e) {
      if (UNKNOWN_REVISION.matcher(e.getMessage()).find()){
        throw new CannotResolveRevisionException(
            String.format("Unknown revision: %s", e.getMessage()));
      }
      throw e;
    }
  }

  /**
   * Creates an unversioned archive of the current working directory and subrepositories
   * in the location {@code archivePath}.
   */
  void archive(String archivePath) throws RepoException {
    hg(hgDir, "archive", archivePath, "--type", "files", "--subrepos");
  }

  /**
   * Creates a log command.
   */
  public LogCmd log() {
    return LogCmd.create(this);
  }

  /**
   * Invokes {@code hg} in the directory given by {@code cwd} against this repository and returns
   * the {@link CommandOutput} if the command execution was successful.
   *
   * <p>Only to be used externally for testing.
   *
   * @param cwd the directory in which to execute the command
   * @param params the argv to pass to Hg, excluding the initial {@code hg}
   */
  @VisibleForTesting
  public CommandOutput hg(Path cwd, String... params) throws RepoException {
    return hg(cwd, Arrays.asList(params), DEFAULT_TIMEOUT);
  }

  private CommandOutput hg(Path cwd, Iterable<String> params, Duration timeout)
      throws RepoException {
    try {
      return executeHg(cwd, params, -1, timeout);
    } catch (BadExitStatusWithOutputException e) {
      throw new RepoException(String.format("Error executing hg: %s", e.getOutput().getStderr()));
    } catch (CommandException e) {
      throw new RepoException(String.format("Error executing hg: %s", e.getMessage()));
    }
  }

  public Path getHgDir() {
    return hgDir;
  }

  private CommandOutputWithStatus executeHg(Path cwd, Iterable<String> params,
      int maxLogLines, Duration timeout) throws CommandException {
    List<String> allParams = new ArrayList<>(Iterables.size(params) + 1);
    allParams.add("hg"); //TODO(jlliu): resolve Hg binary here
    Iterables.addAll(allParams, params);
    Command cmd = new Command(
        Iterables.toArray(allParams, String.class), null, cwd.toFile());
        //TODO(jlliu): have environment vars
    CommandRunner runner = new CommandRunner(cmd, timeout).withVerbose(verbose);
    return
        maxLogLines >= 0 ? runner.withMaxStdOutLogLines(maxLogLines).execute() : runner.execute();
  }

  /**
   * An object that can perform a "hg log" operation on a repository and returns a list of
   * {@link HgLogEntry}.
   */
  public static class LogCmd {

    private final HgRepository repo;

    private final int limit;

    /** Branch to limit the query from. Defaults to all branches if null.*/
    @Nullable
    private final String branch;

    @Nullable
    private final String referenceExpression;

    @Nullable
    private final String keyword;

    private LogCmd(HgRepository repo, int limit, @Nullable String branch,
        @Nullable String referenceExpression, @Nullable String keyword) {
      this.repo = repo;
      this.limit = limit;
      this.branch = branch;
      this.referenceExpression = referenceExpression;
      this.keyword = keyword;
    }

    static LogCmd create(HgRepository repo) {
      return new LogCmd(
          checkNotNull(repo), /*limit*/0, /*branch*/null,
          /*referenceExpression*/ null, /*keyword*/ null);
    }

    /**
     * Limit the query to references that fit the {@code referenceExpression}.
     *
     * <p>The expression must be in a format that is supported by Mercurial. Mercurial supports a
     * number of operators: for example, x::y represents all revisions that are descendants of x and
     * ancestors of y, including x and y.
     */
    LogCmd withReferenceExpression(String referenceExpression) throws RepoException {
      if (Strings.isNullOrEmpty(referenceExpression.trim())){
        throw new RepoException("Cannot log null or empty reference");
      }
      return new LogCmd(repo, limit, branch, referenceExpression.trim(), keyword);
    }


    /**
     * Limit the query to {@code limit} results. Should be > 0.
     */
    public LogCmd withLimit(int limit) {
      Preconditions.checkArgument(limit > 0);
      return new LogCmd(repo, limit, branch, referenceExpression, keyword);
    }

    /**
     * Only query for revisions from the branch {@code branch}.
     */
    LogCmd withBranch(String branch) {
      return new LogCmd(repo, limit, branch, referenceExpression, keyword);
    }

    /**
     * Only query for revisions with the keyword {@code keyword}.
     */
    LogCmd withKeyword(String keyword) {
      return new LogCmd(repo, limit, branch, referenceExpression, keyword);
    }

    /**
     * Run "hg log' and return zero or more {@link HgLogEntry}.
     *
     * <p> The order of the log entries is by default in reverse chronological order, where the
     * first element of the list is the most recent commit. However, if a reference expression is
     * provided, log entries will be ordered with respect to the expression. For example, if the
     * expression is "0:tip", entries will be ordered in chronological order, where the first
     * element of the list is the first commit.
     */
    public ImmutableList<HgLogEntry> run() throws RepoException, ValidationException {
      ImmutableList.Builder<String> builder = ImmutableList.builder();
      builder.add("log", "--verbose"); // verbose flag shows files in output

      if (branch != null) {
        builder.add("--branch", branch);
      }

      /* hg requires limit to be a positive integer */
      if (limit > 0) {
        builder.add("--limit", String.valueOf(limit));
      }

      if (referenceExpression != null) {
        builder.add("--rev", referenceExpression);
      }

      if (keyword != null) {
        builder.add("--keyword", keyword);
      }

      builder.add("-Tjson");
      try {
        CommandOutput output = repo.hg(repo.getHgDir(), builder.build(), DEFAULT_TIMEOUT);
        return parseLog(output.getStdout());
      } catch (RepoException e) {
        if (UNKNOWN_REVISION.matcher(e.getMessage()).find()) {
          throw new ValidationException("Unknown revision: " + e.getMessage());
        }
        if (INVALID_REF_EXPRESSION.matcher(e.getMessage()).find()) {
          throw new RepoException("Syntax error in reference expression: " + e.getMessage());
        }
        throw e;
      }
    }

    private ImmutableList<HgLogEntry> parseLog(String log) throws RepoException {
      if (log.isEmpty()) {
        return ImmutableList.of();
      }

      Gson gson = new Gson();
      Type logListType = new TypeToken<List<HgLogEntry>>() {}.getType();

      try {
        List<HgLogEntry> logEntries = gson.fromJson(log.trim(), logListType);
        return ImmutableList.copyOf(logEntries);
      } catch (JsonParseException e) {
        throw new RepoException(String.format("Cannot parse log output: %s", e.getMessage()));
      }
    }
  }

  /**
   * An object that represents a commit as returned by 'hg log'.
   */
  public static class HgLogEntry {
    @SerializedName("node") private final String globalId;
    private final List<String> parents;
    private final String user;
    @SerializedName("date") private final List<String> commitDate;
    private final String branch;
    @SerializedName("desc") private final String description;
    private final List<String> files;

    HgLogEntry(String node, List<String> parents, String user,
        List<String> commitDate, String branch,
        String desc, List<String> files) {
      this.globalId = checkNotNull(node);
      this.parents = ImmutableList.copyOf(parents);
      this.user = user;
      this.commitDate = commitDate;
      this.branch = branch;
      this.description = desc;
      this.files = ImmutableList.copyOf(files);
    }

    public List<String> getParents() {
      return parents;
    }

    public String getUser() {
      return user;
    }

    String getGlobalId() {
      return globalId;
    }

    ZonedDateTime getZonedDate() {
      Instant date = Instant.ofEpochSecond(
          Long.parseLong(commitDate.get(0)));
      ZoneOffset offset = ZoneOffset.ofTotalSeconds(-1 * Integer.parseInt(commitDate.get(1)));
      ZoneId zone = ZoneId.ofOffset("", offset);
      return ZonedDateTime.ofInstant(date, zone);
    }

    public String getBranch() {
      return branch;
    }

    public String getDescription() {
      return description;
    }

    public List<String> getFiles() {
      return files;
    }
  }
}
