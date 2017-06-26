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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.util.StructuredOutput;
import com.google.copybara.util.OutputDirFactory;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.FileSystem;
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
  public static final String DISABLE_REVERSIBLE_CHECK_FLAG = "--disable-reversible-check";

  private final Map<String, String> environment;
  private final FileSystem fileSystem;
  private final boolean verbose;
  private final Console console;
  private final StructuredOutput structuredOutput = new StructuredOutput();
  private final boolean reuseOutputDirs;
  private final boolean disableReversibleCheck;
  private final boolean force;
  @Nullable
  private final Path configRoot;
  @Nullable
  private final Path outputRoot;

  private final Profiler profiler = new Profiler(Ticker.systemTicker());

  @VisibleForTesting
  public GeneralOptions(FileSystem fileSystem, boolean verbose, Console console) {
    this(System.getenv(), fileSystem, verbose, console, /*configRoot=*/null, /*outputRoot=*/null,
        /*reuseOutputDirs*/ true, /*disableReversibleCheck=*/false, /*force=*/false);
  }

  @VisibleForTesting
  public GeneralOptions(Map<String, String> environment, FileSystem fileSystem, boolean verbose,
      Console console, @Nullable Path configRoot, @Nullable Path outputRoot,
      boolean reuseOutputDirs, boolean disableReversibleCheck, boolean force)
  {
    this.environment = ImmutableMap.copyOf(Preconditions.checkNotNull(environment));
    this.console = Preconditions.checkNotNull(console);
    this.fileSystem = Preconditions.checkNotNull(fileSystem);
    this.verbose = verbose;
    this.configRoot = configRoot;
    this.outputRoot = outputRoot;
    this.reuseOutputDirs = reuseOutputDirs;
    this.disableReversibleCheck = disableReversibleCheck;
    this.force = force;
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

  public StructuredOutput getStructuredOutput() {
    return structuredOutput;
  }

  public FileSystem getFileSystem() {
    return fileSystem;
  }

  public boolean isReuseOutputDirs() {
    return reuseOutputDirs;
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

  @Nullable
  public Path getConfigRoot() {
    return configRoot;
  }

  /**
   * Returns the output root directory, or null if not set.
   *
   * <p>This method is exposed mainly for tests and it's probably not what you're looking for. Try
   * {@link #getOutputDirFactory()} instead.
   */
  @VisibleForTesting
  @Nullable
  public Path getOutputRoot() {
    return outputRoot;
  }

  public Profiler profiler() {
    return profiler;
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
   * Returns a {@link OutputDirFactory} capable of creating directories in a self contained
   * location in the filesystem.
   *
   * <p>By default, the directories are created under {@code $HOME/copybara/out}, but it can be
   * overridden with the flag --output-root.
   */
  public OutputDirFactory getOutputDirFactory() {
    Path rootPath = outputRoot != null
        ? outputRoot
        : fileSystem.getPath(environment.get("HOME")).resolve("copybara/out/");
    return new OutputDirFactory(rootPath, reuseOutputDirs);
  }

  @Parameters(separators = "=")
  public static final class Args {
    @Parameter(names = "-v", description = "Verbose output.")
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

    @Parameter(names = DISABLE_REVERSIBLE_CHECK_FLAG,
        description = "If set, all workflows will be executed without reversible_check, overriding"
            + " the  workflow config and the normal behavior for CHANGE_REQUEST mode.")
    boolean disableReversibleCheck = false;

    @Parameter(
      names = "--output-root",
      description =
          "The root directory where to generate output files. If not set, ~/copybara/out is used "
              + "by default. Use with care, Copybara might remove files inside this root if "
              + "necessary.")
    String outputRoot = null;

    @Parameter(
        names = "--reuse-output-dirs",
        description =
            "Reuse the output directories. This includes the workdir, scratch clones of Git repos,"
                + " etc. By default is set to true and directories will be cleaned prior to the "
                + "execution and reused. If set to false, different directories will be used."
                + " Keep in mind that this might consume a lot of disk.",
        arity = 1)
    boolean reuseOutputDirs = false;

    /**
     * This method should be called after the options have been set but before are used by any class.
     */
    public GeneralOptions init(
        Map<String, String> environment, FileSystem fileSystem, Console console)
        throws IOException {
      Path configRoot = this.configRoot != null ? fileSystem.getPath(this.configRoot) : null;
      Path outputRoot = this.outputRoot != null ? fileSystem.getPath(this.outputRoot) : null;
      return new GeneralOptions(
          environment, fileSystem, verbose, console, configRoot, outputRoot, reuseOutputDirs,
          disableReversibleCheck, force);
    }
  }
}
