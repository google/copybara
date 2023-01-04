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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.copybara.exception.ValidationException.checkCondition;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.StackSize;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.jcommander.DurationConverter;
import com.google.copybara.jcommander.MapConverter;
import com.google.copybara.monitor.ConsoleEventMonitor;
import com.google.copybara.monitor.EventMonitor;
import com.google.copybara.monitor.EventMonitor.EventMonitors;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.util.CommandRunner;
import com.google.copybara.util.DirFactory;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.StarlarkMode;
import com.google.copybara.shell.Command;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/**
 * General options available for all the program classes.
 */
@Parameters(separators = "=")
public final class GeneralOptions implements Option {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String NOANSI = "--noansi";
  public static final String FORCE = "--force";
  public static final String CONFIG_ROOT_FLAG = "--config-root";
  public static final String OUTPUT_ROOT_FLAG = "--output-root";
  public static final String OUTPUT_LIMIT_FLAG = "--output-limit";
  public static final String DRY_RUN_FLAG = "--dry-run";
  public static final String SQUASH_FLAG = "--squash";
  static final Duration DEFAULT_CONSOLE_FILE_FLUSH_INTERVAL = Duration.ofSeconds(30);
  private static final String DEFAULT_MONITOR = "default";

  private Map<String, String> environment;
  private FileSystem fileSystem;
  private Console console;
  private final HashMap<String, EventMonitor> eventMonitors;
  private Path configRootPath;
  private Path outputRootPath;

  private Profiler profiler = new Profiler(Ticker.systemTicker());

  public GeneralOptions(Map<String, String> environment, FileSystem fileSystem, Console console) {
    this.environment = environment;
    this.fileSystem = Preconditions.checkNotNull(fileSystem);
    this.console = Preconditions.checkNotNull(console);
    this.eventMonitors = Maps.newHashMap(ImmutableMap.of(
        DEFAULT_MONITOR, new ConsoleEventMonitor(console, EventMonitor.EMPTY_MONITOR)));
  }

  @VisibleForTesting
  public GeneralOptions(Map<String, String> environment, FileSystem fileSystem, boolean verbose,
      Console console, @Nullable Path configRoot, @Nullable Path outputRoot,
      boolean noCleanup, boolean disableReversibleCheck, boolean force, int outputLimit) {
    this(environment, fileSystem, console);
    this.verbose = verbose;
    this.configRootPath = configRoot;
    this.outputRootPath = outputRoot;
    this.noCleanup = noCleanup;
    this.disableReversibleCheck = disableReversibleCheck;
    this.force = force;
    this.outputLimit = outputLimit;
  }

  public GeneralOptions withForce(boolean force) throws ValidationException {
    return new GeneralOptions(environment, fileSystem, verbose, console, getConfigRoot(),
        getOutputRoot(), noCleanup, disableReversibleCheck, force, outputLimit);
  }

  public GeneralOptions withConsole(Console console) throws ValidationException {
    return new GeneralOptions(environment, fileSystem, verbose, console, getConfigRoot(),
        getOutputRoot(), noCleanup, disableReversibleCheck, force, outputLimit);
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

  public boolean isVersionSelectorUseCliRef() {
    return versionSelectorUseCliRef;
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
  public Path getConfigRoot() throws ValidationException {
    if (configRootPath == null && this.configRoot != null) {
      configRootPath = fileSystem.getPath(this.configRoot).toAbsolutePath();
      checkCondition(Files.exists(configRootPath), "%s doesn't exist", configRoot);
      checkCondition(Files.isDirectory(configRootPath), "%s isn't a directory", configRoot);
    }
    return configRootPath;
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
    if (outputRootPath == null && this.outputRoot != null) {
      outputRootPath = fileSystem.getPath(this.outputRoot);
    }
    return outputRootPath;
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

  /**
   * Returns the active event monitors
   */
  public EventMonitors eventMonitors() {
    return new EventMonitors(
        eventMonitors.entrySet().stream()
            .filter(e -> enabledEventMonitors.contains(e.getKey()))
            .map(Map.Entry::getValue)
            .collect(toImmutableList()));
  }

  public ImmutableMap<String, String> cliLabels() {
    return labels;
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
    if (getOutputRoot() != null) {
      return new DirFactory(getOutputRoot());
    } else {
      String home = checkNotNull(environment.get("HOME"), "$HOME environment var is not set");
      return new DirFactory(fileSystem.getPath(home).resolve("copybara"));
    }
  }

  @VisibleForTesting
  public void setEnvironmentForTest(Map<String, String> environment) {
    this.environment = environment;
  }

  @VisibleForTesting
  public void setOutputRootPathForTest(Path outputRootPath) {
    this.outputRootPath = outputRootPath;
  }

  @VisibleForTesting
  public void setConsoleForTest(Console console) {
    this.console = console;
  }

  @VisibleForTesting
  public void setForceForTest(boolean force) {
    this.force = force;
  }

  @VisibleForTesting
  public void setVersionSelectorUseCliRefForTest(boolean versionSelectorUseCliRef) {
    this.versionSelectorUseCliRef = versionSelectorUseCliRef;
  }

  @VisibleForTesting
  public void setCliLabelsForTest(ImmutableMap<String, String> labels) {
    this.labels = labels;
  }

  @VisibleForTesting
  public void setFileSystemForTest(FileSystem fileSystem) {
    this.fileSystem = fileSystem;
  }

  public GeneralOptions withProfiler(Profiler profiler) {
    this.profiler = Preconditions.checkNotNull(profiler);
    return this;
  }

  /**
   * Add an EventMonitor to the list of available monitors without activating it. Use this to make
   * a monitor available via the --event-monitor flag
   */
  public GeneralOptions addEventMonitor(String name, EventMonitor eventMonitor) {
    this.eventMonitors.put(name, eventMonitor);
    return this;
  }

  /** Enable an event monitor already on the list of available monitors. */
  public GeneralOptions enableEventMonitor(String name) {
    Preconditions.checkArgument(
        eventMonitors.containsKey(name), "%s is not a known EventMonitor.", name);
    this.enabledEventMonitors.add(name);
    return this;
  }

  /**
   * Add an EventMonitor to the list of available monitors and enable it. Use this to inject new
   * default monitors as required.
   */
  public GeneralOptions enableEventMonitor(String name, EventMonitor eventMonitor) {
    addEventMonitor(name, eventMonitor);
    enableEventMonitor(name);
    return this;
  }

  /** Clear the list of enabled event monitors for changing default behavior.. */
  public GeneralOptions clearEventMonitor() {
    this.enabledEventMonitors.clear();
    return this;
  }

  @Parameter(
      names = {"-v", "--verbose"},
      description = "Verbose output.")
  boolean verbose;

  @Parameter(
      names = {"--repo-timeout"},
      description = "Repository operation timeout duration.",
      converter = DurationConverter.class)
  public Duration repoTimeout = CommandRunner.DEFAULT_TIMEOUT;

  @Parameter(
      names = {"--commands-timeout"},
      description = "Commands timeout",
      converter = DurationConverter.class)
  public Duration commandsTimeout = CommandRunner.DEFAULT_TIMEOUT;

  public CommandRunner newCommandRunner(Command cmd) {
    return new CommandRunner(cmd, commandsTimeout);
  }

  // We don't use JCommander for parsing this flag but we do it manually since
  // the parsing could fail and we need to report errors using one console
  @SuppressWarnings("unused")
  @Parameter(names = NOANSI, description = "Don't use ANSI output for messages")
  boolean noansi = false;

  @Parameter(
      names = FORCE,
      description =
          "Force the migration even if Copybara cannot find in the destination a change that is an"
              + " ancestor of the one(s) being migrated. This should be used with care, as it"
              + " could lose changes when migrating a previous/conflicting change.")
  boolean force = false;

  @Parameter(
      names = "--version-selector-use-cli-ref",
      description =
          "If command line ref is to used with a version selector, pass this flag to tell copybara"
              + " to use it.",
      arity = 1)
  boolean versionSelectorUseCliRef = true;

  @Parameter(
      names = CONFIG_ROOT_FLAG,
      description =
          "Configuration root path to be used for resolving absolute config labels"
              + " like '//foo/bar'")
  String configRoot;

  @Parameter(
      names = "--disable-reversible-check",
      description =
          "If set, all workflows will be executed without reversible_check, overriding"
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
              + "flag differently. Defaults to 0, which shows all the output.")
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

  @Parameter(
      names = "--nologging",
      description =
          "Disable logging of this binary. Note that commands executed by Copybara "
              + "might still log to their own file.", hidden = true)
  boolean noLogging = false;

  @Parameter(
      names = "--labels",
      description =
          "Additional flags. It has been exposed to Feedback Context, "
              + "which can be accessed through ctx.cli_labels. "
              + "Format: --labels=flag1:value1,flag2:value2 "
              + "Or: --labels flag1:value1,flag2:value2 ",
      converter = MapConverter.class,
      hidden = true)
  ImmutableMap<String, String> labels = ImmutableMap.of();

  @Parameter(
      names = "--temporary-features",
      description = "Change guarded features. If set it means that it will return true.",
      converter = MapConverter.class,
      hidden = true)
  private ImmutableMap<String, String> temporaryFeatures = ImmutableMap.of();

  @Parameter(
      names = "--diff-bin",
      description =
          "Command line diff tool bin used in merge import. Defaults to diff3, but users can pass"
              + " in their own diffing tools (along with requisite arg reordering)")
  private String diffBin = "/usr/bin/diff3";

  public String getDiffBin() {
    return diffBin;
  }

  /**
   * Temporary features is mean to be used by Copybara team for guarding new codepaths. Should
   * never be used for user facing flags or longer term experiments. Any caller of this function
   * should have a todo saying when to remove the call.
   *
   * <p>If the flag doesn't have a value it will use defaultVal. If the flag is incorrect (different
   * from true/false) it will use defaultVal (And log at severe).
   */
  public boolean isTemporaryFeature(String name, boolean defaultVal) {
    Preconditions.checkNotNull(name);
    String v = temporaryFeatures.get(name);
    if (v == null) {
      return defaultVal;
    }
    if (Ascii.equalsIgnoreCase(v, "true")) {
      return true;
    }
    if (Ascii.equalsIgnoreCase(v, "false")) {
      return false;
    }
    logger.atSevere()
        .withStackTrace(StackSize.SMALL)
        .log("Invalid boolean value for '%s' in '%s'. Needs to be true or false. Using default: %s",
            name, temporaryFeatures, defaultVal);
    return defaultVal;
  }

  static final String CONSOLE_FILE_PATH = "--console-file-path";

  // This flag is read before we parse the arguments, because of the console lifecycle
  @SuppressWarnings("unused")
  @Parameter(
      names = CONSOLE_FILE_PATH,
      description = "If set, write the console output also to the given file path.")
  String consoleFilePath;

  // This flag is read before we parse the arguments, because of the console lifecycle
  @SuppressWarnings("unused")
  @Deprecated
  @Parameter(
      names = "--console-file-flush-rate",
      description =
          "How often in number of lines to flush the console to the output file. "
              + "If set to 0, console will be flushed only at the end.", hidden = true)
  int consoleFileFlushRateDeprecatedDontUse = -1;

  static final String CONSOLE_FILE_FLUSH_INTERVAL = "--console-file-flush-interval";
 
  // This flag is read before we parse the arguments, because of the console lifecycle
  @SuppressWarnings("unused")
  @Parameter(
      names = CONSOLE_FILE_FLUSH_INTERVAL,
      converter = DurationConverter.class,
      description =
          "How often Copybara should flush the console to the output file. (10s, 1m, etc.)"
              + "If set to 0s, console will be flushed only at the end.")
  Duration consoleFileFlushInterval = DEFAULT_CONSOLE_FILE_FLUSH_INTERVAL;

  @Parameter(names = DRY_RUN_FLAG,
      description = "Run the migration in dry-run mode. Some destination implementations might"
          + " have some side effects (like creating a code review), but never submit to a main"
          + " branch.")
  public boolean dryRunMode = false;

  @Parameter(names = SQUASH_FLAG, description = "Override workflow's mode with 'SQUASH'. This is "
      + "useful mainly for workflows that use 'ITERATIVE' mode, when we want to run a single "
      + "export with 'SQUASH', maybe to fix an issue. Always use " + DRY_RUN_FLAG + " before, to "
      + "test your changes locally.")
  public boolean squash = false;

  @Parameter(
      names = "--validate-starlark",
      description =
          "Starlark should be validated prior to execution, but this might break legacy configs."
              + " Options are LOOSE, STRICT")
  public String starlarkMode = StarlarkMode.LOOSE.name();

  public StarlarkMode getStarlarkMode() {
    return StarlarkMode.valueOf(starlarkMode);
  }

  @Parameter(
      names = "--info-list-only",
      description =
          "When set, the INFO command will print a list of workflows defined in the file.")
  boolean infoListOnly = false;

  @Parameter(
      names = {"--event-monitor"},
      description = "Eventmonitors to enable. These must be in the list of available monitors.")
  public List<String> enabledEventMonitors = new ArrayList<>(ImmutableList.of(DEFAULT_MONITOR));

  @Parameter(
      names = "--allow-empty-diff",
      description =
          "If set to false, Copybara will not write to the destination if the exact same change is"
              + " already pending in the destination. Currently only supported for"
              + " `git.github_pr_destination` and `git.gerrit_destination`.",
      arity = 1)
  public Boolean allowEmptyDiff = null;

  public boolean allowEmptyDiff(boolean configAllowEmptyDiff) {
    return this.allowEmptyDiff == null ? configAllowEmptyDiff : this.allowEmptyDiff;
  }
}