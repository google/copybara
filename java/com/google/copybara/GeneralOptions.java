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

package com.google.copybara;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.copybara.exception.ValidationException.checkCondition;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.monitor.EventMonitor;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.util.DirFactory;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/**
 * General options available for all the program classes.
 */
public final class GeneralOptions implements Option {

  public static final String NOANSI = "--noansi";
  public static final String FORCE = "--force";
  public static final String CONFIG_ROOT_FLAG = "--config-root";
  public static final String OUTPUT_ROOT_FLAG = "--output-root";
  public static final String OUTPUT_LIMIT_FLAG = "--output-limit";

  private final Map<String, String> environment;
  private final FileSystem fileSystem;
  private final boolean verbose;
  private final Console console;
  private final boolean noCleanup;
  private final boolean disableReversibleCheck;
  private final boolean force;
  private final int outputLimit;
  @Nullable
  private final Path configRoot;
  @Nullable
  private final Path outputRoot;

  private Profiler profiler = new Profiler(Ticker.systemTicker());

  // Default implementation does not show up in the console (unless verbose is used)
  private EventMonitor eventMonitor =
      new EventMonitor() {
        @Override
        public void onMigrationFinished(MigrationFinishedEvent event) {
          console().verboseFmt("Migration finished: %s", event);
        }
      };

  @VisibleForTesting
  public GeneralOptions(FileSystem fileSystem, boolean verbose, Console console) {
    this(System.getenv(), fileSystem, verbose, console, /*configRoot=*/null, /*outputRoot=*/null,
        /*noCleanup*/ true, /*disableReversibleCheck=*/false, /*force=*/false,
        /*outputLimit*/ 0);
  }

  @VisibleForTesting
  public GeneralOptions(Map<String, String> environment, FileSystem fileSystem, boolean verbose,
      Console console, @Nullable Path configRoot, @Nullable Path outputRoot,
      boolean noCleanup, boolean disableReversibleCheck, boolean force, int outputLimit) {
    this.environment = ImmutableMap.copyOf(Preconditions.checkNotNull(environment));
    this.console = Preconditions.checkNotNull(console);
    this.fileSystem = Preconditions.checkNotNull(fileSystem);
    this.verbose = verbose;
    this.configRoot = configRoot;
    this.outputRoot = outputRoot;
    this.noCleanup = noCleanup;
    this.disableReversibleCheck = disableReversibleCheck;
    this.force = force;
    this.outputLimit = outputLimit;
  }

  public GeneralOptions withForce(boolean force) {
    return new GeneralOptions(environment, fileSystem, verbose, console, configRoot, outputRoot,
                              noCleanup, disableReversibleCheck, force, outputLimit);
  }

  public GeneralOptions withConsole(Console console) {
    return new GeneralOptions(environment, fileSystem, verbose, console, configRoot, outputRoot,
                              noCleanup, disableReversibleCheck, force, outputLimit);
  }

  public Map<String, String> getEnvironment() {
    return environment;
  }

  public boolean isVerbose() {
    return verbose;
  }

  public Console console() {
    return console;
  }

  public FileSystem getFileSystem() {
    return fileSystem;
  }

  public boolean isNoCleanup() {
    return noCleanup;
  }

  public boolean isDisableReversibleCheck() {
    return disableReversibleCheck;
  }

  public boolean isForced() {
    return force;
  }

  /**
   * Returns current working directory
   */
  public Path getCwd() {
    return fileSystem.getPath(environment.get("PWD"));
  }

  /**
   * Returns the root absolute path to use for config.
   */
  @Nullable
  public Path getConfigRoot() {
    return configRoot;
  }

  /**
   * Returns the output root directory, or null if not set.
   *
   * <p>This method is exposed mainly for tests and it's probably not what you're looking for. Try
   * {@link #getDirFactory()} instead.
   */
  @VisibleForTesting
  @Nullable
  public Path getOutputRoot() {
    return outputRoot;
  }

  /**
   * Returns the output limit.
   *
   * <p>Each subcommand can use this value differently.
   */
  public int getOutputLimit() {
    return outputLimit > 0 ? outputLimit : Integer.MAX_VALUE;
  }

  public Profiler profiler() {
    return profiler;
  }

  public EventMonitor eventMonitor() {
    return eventMonitor;
  }

  /**
   * Run a repository task with profiling
   */
  public <T> T repoTask(String description, Callable<T> callable)
      throws RepoException, ValidationException {
    try (ProfilerTask ignored = profiler().start(description)) {
      return callable.call();
    } catch (Exception e) {
      Throwables.propagateIfPossible(e, RepoException.class, ValidationException.class);
      throw new RuntimeException("Unexpected exception", e);
    }
  }

  /**
   * Run a repository task that can throw IOException with profiling
   */
  public <T> T ioRepoTask(String description, Callable<T> callable)
      throws RepoException, ValidationException, IOException{
    try (ProfilerTask ignored = profiler().start(description)) {
      return callable.call();
    } catch (Exception e) {
      Throwables.propagateIfPossible(e, RepoException.class, ValidationException.class);
      Throwables.propagateIfPossible(e, IOException.class);
      throw new RuntimeException("Unexpected exception", e);
    }
  }

  /**
   * Returns a {@link DirFactory} capable of creating directories in a self contained location in
   * the filesystem.
   *
   * <p>By default, the directories are created under {@code $HOME/copybara}, but it can be
   * overridden with the flag --output-root.
   */
  public DirFactory getDirFactory() {
    if (outputRoot != null) {
      return new DirFactory(outputRoot);
    } else {
      String home = checkNotNull(environment.get("HOME"), "$HOME environment var is not set");
      return new DirFactory(fileSystem.getPath(home).resolve("copybara"));
    }
  }

  public GeneralOptions withProfiler(Profiler profiler) {
    this.profiler = Preconditions.checkNotNull(profiler);
    return this;
  }

  public GeneralOptions withEventMonitor(EventMonitor eventMonitor) {
    this.eventMonitor = Preconditions.checkNotNull(eventMonitor);
    return this;
  }

  @Parameters(separators = "=")
  public static final class Args {
    @Parameter(names = {"-v", "--verbose"}, description = "Verbose output.")
    boolean verbose;

    // We don't use JCommander for parsing this flag but we do it manually since
    // the parsing could fail and we need to report errors using one console
    @SuppressWarnings("unused")
    @Parameter(names = NOANSI, description = "Don't use ANSI output for messages")
    boolean noansi = false;

    @Parameter(names = FORCE, description = "Force the migration even if Copybara cannot find in"
        + " the destination a change that is an ancestor of the one(s) being migrated. This should"
        + " be used with care, as it could lose changes when migrating a previous/conflicting"
        + " change.")
    boolean force = false;

    @Parameter(names = CONFIG_ROOT_FLAG,
        description = "Configuration root path to be used for resolving absolute config labels"
            + " like '//foo/bar'")
    String configRoot;

    @Parameter(names = "--disable-reversible-check",
        description = "If set, all workflows will be executed without reversible_check, overriding"
            + " the  workflow config and the normal behavior for CHANGE_REQUEST mode.")
    boolean disableReversibleCheck = false;

    @Parameter(
      names = OUTPUT_ROOT_FLAG,
      description =
          "The root directory where to generate output files. If not set, ~/copybara/out is used "
              + "by default. Use with care, Copybara might remove files inside this root if "
              + "necessary.")
    String outputRoot = null;

    @Parameter(
      names = OUTPUT_LIMIT_FLAG,
      description =
          "Limit the output in the console to a number of records. Each subcommand might use this "
              + "flag differently. Defaults to 0, which shows all the output."
    )
    int outputLimit = 0;

    @Parameter(
        names = "--nocleanup",
        description =
            "Cleanup the output directories. This includes the workdir, scratch clones of Git"
                + " repos, etc. By default is set to false and directories will be cleaned prior to"
                + " the execution. If set to true, the previous run output will not be cleaned up."
                + " Keep in mind that running in this mode will lead to an ever increasing disk"
                + " usage.")
    boolean noCleanup = false;

    /**
     * This method should be called after the options have been set but before are used by any
     * class.
     */
    public GeneralOptions init(
        Map<String, String> environment, FileSystem fileSystem, Console console)
        throws ValidationException {
      Path configRoot = null;
      if (this.configRoot != null) {
        configRoot = fileSystem.getPath(this.configRoot).toAbsolutePath();
        checkCondition(Files.exists(configRoot), "%s doesn't exist", configRoot);
        checkCondition(Files.isDirectory(configRoot), "%s isn't a directory", configRoot);
      }

      Path outputRoot = this.outputRoot != null ? fileSystem.getPath(this.outputRoot) : null;
      return new GeneralOptions(
          environment, fileSystem, verbose, console, configRoot, outputRoot, noCleanup,
          disableReversibleCheck, force, outputLimit);
    }
  }
}
