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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import com.google.copybara.util.BadExitStatusWithOutputException;
import com.google.copybara.util.CommandOutput;
import com.google.copybara.util.CommandOutputWithStatus;
import com.google.copybara.util.CommandRunner;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
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
  public static final String HG_ORIGIN_REV_ID = "HgOrigin-RevId";

  private static final Pattern INVALID_HG_REPOSITORY =
      Pattern.compile("abort: repository .+ not found!");
  private static final Pattern UNKNOWN_REVISION =
      Pattern.compile("abort: unknown revision '.+'!");

  private static final Pattern NOTHING_CHANGED = Pattern.compile("(.|\n)*nothing changed(.|\n)*");

  /**
   * The location of the {@code .hg} directory.
   */
  private final Path hgDir;


  protected HgRepository(Path hgDir) {
    this.hgDir = hgDir;
  }


  public static HgRepository newRepository(Path hgDir) {
    return new HgRepository(hgDir);
  }

  /**
   * Initializes a new hg repository
   * @return the new HgRepository
   * @throws RepoException if the directory cannot be created
   */
  public HgRepository init() throws RepoException, ValidationException {
    try {
      Files.createDirectories(hgDir);
    }
    catch (IOException e) {
      throw new RepoException("Cannot create directory: " + e.getMessage(), e);
    }
    hg(hgDir, ImmutableList.of("init"));
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
  public void pullAll(String url) throws RepoException, ValidationException {
    pull(url,/*force*/ true, /*ref*/ null);
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
  public void pullFromRef(String url, String ref) throws RepoException, ValidationException {
    pull(url, /*force*/ true, /*ref*/ ref);
  }


  public void pull(String url, boolean force, String ref)
      throws RepoException, ValidationException {
    ImmutableList.Builder<String> builder = ImmutableList.builder();

    builder.add("pull", url);

    if (force) {
      builder.add("--force");
    }

    if (!Strings.isNullOrEmpty(ref)) {
      builder.add("--rev", ref);
    }

    hg(hgDir, builder.build());
  }

  /**
   * Updates the working directory to the revision given at {@code ref} in the repository
   * and discards local changes.
   */
  public CommandOutput cleanUpdate(String ref) throws RepoException, ValidationException {
    return hg(hgDir, "update", ref, "--clean");
  }

  /**
   * Returns a revision object given a reference.
   *
   * <p> A reference can be any of the following:
   * <ul>
   *   <li> A global identifier for a revision. Example:
   *   <li> A local identifier for a revision in the repository
   *   <li> A bookmark in the repository
   *   <li> A tag in the repository
   * </ul>
   *
   */
  public HgRevision identify(String reference) throws RepoException, ValidationException{
    try {
      CommandOutput commandOutput =
          hg(hgDir, "identify", "--debug", "--id", "--rev", reference);

      String globalId = commandOutput.getStdout().trim();

      return new HgRevision(this, globalId);
    }
    catch (RepoException e) {
      String output = e.getMessage();

      if (UNKNOWN_REVISION.matcher(output).find()) {
        throw new ValidationException(
            String.format("Reference %s is unknown", reference));
      }

      throw e;
    }
  }

  /**
   * Creates an archive of the current working directory in the location {@code archivePath}.
   */
  public void archive(String archivePath) throws RepoException, ValidationException {
    hg(hgDir, "archive", archivePath, "--type", "files");
  }

  /**
   * Creates a log command
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
  public CommandOutput hg(Path cwd, String... params) throws RepoException, ValidationException {
    return hg(cwd, Arrays.asList(params));
  }

  public Path getHgDir() {
    return hgDir;
  }

  private CommandOutput hg(Path cwd, Iterable<String> params)
      throws RepoException, ValidationException {
    try{
      return executeHg(cwd, params, -1);
    }
    catch (BadExitStatusWithOutputException e) {
      CommandOutputWithStatus output = e.getOutput();

      if (INVALID_HG_REPOSITORY.matcher(output.getStderr()).find()) {
        throw new ValidationException(
            String.format("Repository not found: %s", output.getStderr()));
      }
      if (NOTHING_CHANGED.matches(output.getStdout())) {
        throw new EmptyChangeException("Commit is empty");
      }

      throw new RepoException(String.format("Error executing 'hg':\n%s\n%s",
          output.getStdout(), output.getStderr()), e);
    }
    catch(CommandException e) {
      throw new RepoException(String.format("Error executing 'hg': %s", e.getMessage()), e);
    }
  }

  private static CommandOutputWithStatus executeHg(Path cwd, Iterable<String> params,
      int maxLogLines) throws CommandException {
    List<String> allParams = new ArrayList<>(Iterables.size(params) + 1);
    allParams.add("hg"); //TODO(jlliu): resolve Hg binary here
    Iterables.addAll(allParams, params);
    Command cmd = new Command(
        Iterables.toArray(allParams, String.class), null, cwd.toFile());
        //TODO(jlliu): have environment vars
    CommandRunner runner = new CommandRunner(cmd);
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

    LogCmd(HgRepository repo, int limit, @Nullable String branch) {
      this.repo = repo;
      this.limit = limit;
      this.branch = branch;
    }

    static LogCmd create(HgRepository repo) {
      return new LogCmd(
          Preconditions.checkNotNull(repo), /*limit*/0, /*branch*/null);
    }

    /**
     * Limit the query to {@code limit} results. Should be > 0.
     */
    public LogCmd withLimit(int limit) {
      Preconditions.checkArgument(limit > 0);
      return new LogCmd(repo, limit, branch);
    }

    /**
     * Only query for revisions from the branch {@code branch}.
     */
    public LogCmd withBranch(String branch) {
      return new LogCmd(repo, limit, branch);
    }

    /**
     * Run "hg log' and return zero or more {@link HgLogEntry}.
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

      builder.add("-Tjson");
      CommandOutput output = repo.hg(repo.getHgDir(), builder.build());
      return parseLog(output.getStdout());
    }

    private ImmutableList<HgLogEntry> parseLog(String log) throws RepoException{
      if (log.isEmpty()) {
        return ImmutableList.of();
      }

      Gson gson = new Gson();
      Type logListType = new TypeToken<List<HgLogEntry>>() {}.getType();

      try {
        List<HgLogEntry> logEntries = gson.fromJson(log.trim(), logListType);
        return ImmutableList.copyOf(logEntries);
      }
      catch (JsonParseException e) {
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
      this.globalId = Preconditions.checkNotNull(node);
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

    public String getGlobalId() {
      return globalId;
    }

    public ZonedDateTime getZonedDate() {
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
