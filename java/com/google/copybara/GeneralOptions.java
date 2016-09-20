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
import com.google.common.collect.ImmutableMap;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * General options available for all the program classes.
 */
public final class GeneralOptions implements Option {

  public static final String NOANSI = "--noansi";
  public static final String CONFIG_ROOT_FLAG = "--config-root";

  private final Map<String, String> environment;
  private final FileSystem fileSystem;
  private final boolean verbose;
  private final Console console;
  private final boolean validate;
  private final boolean disableReversibleCheck;
  @Nullable
  private final Path configRoot;

  @VisibleForTesting
  public GeneralOptions(FileSystem fileSystem, boolean verbose, Console console) {
    this(System.getenv(), fileSystem, verbose, console, /*validate=*/false, /*configRoot=*/null,
        /*disableReversibleCheck=*/false);
  }

  @VisibleForTesting
  public GeneralOptions(
      Map<String, String> environment, FileSystem fileSystem, boolean verbose, Console console) {
    this(environment, fileSystem, verbose, console, /*validate=*/false, /*configRoot=*/null,
        /*disableReversibleCheck=*/false);
  }

  @VisibleForTesting
  public GeneralOptions(Map<String, String> environment, FileSystem fileSystem, boolean verbose,
      Console console, boolean validate, @Nullable Path configRoot, boolean disableReversibleCheck)
  {
    this.environment = ImmutableMap.copyOf(Preconditions.checkNotNull(environment));
    this.console = Preconditions.checkNotNull(console);
    this.fileSystem = Preconditions.checkNotNull(fileSystem);
    this.verbose = verbose;
    this.validate = validate;
    this.configRoot = configRoot;
    this.disableReversibleCheck = disableReversibleCheck;
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

  public boolean isValidate() {
    return validate;
  }

  public boolean isDisableReversibleCheck() {
    return disableReversibleCheck;
  }

  /**
   * Returns current working directory
   */
  public Path getCwd() {
    return fileSystem.getPath(environment.get("PWD"));
  }

  /**
   * Returns home directory
   */
  public Path getHomeDir() {
    return fileSystem.getPath(environment.get("HOME"));
  }

  @Nullable
  public Path getConfigRoot() {
    return configRoot;
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

    @Parameter(names = "--validate",
        description = "Validate that the config is correct")
    boolean validate = false;

    @Parameter(names = CONFIG_ROOT_FLAG,
        description = "Configuration root path to be used for resolving absolute config labels"
            + " like '//foo/bar'")
    String configRoot;

    @Parameter(names = "--disable-reversible-check",
        description = "If set, all workflows will be executed without reversible_check, overriding"
            + " the  workflow config and the normal behavior for CHANGE_REQUEST mode.")
    boolean disableReversibleCheck = false;

    /**
     * This method should be called after the options have been set but before are used by any class.
     */
    public GeneralOptions init(
        Map<String, String> environment, FileSystem fileSystem, Console console)
        throws IOException {
      Path root = configRoot != null ? fileSystem.getPath(configRoot) : null;
      return new GeneralOptions(environment, fileSystem, verbose, console, validate, root,
          disableReversibleCheck);
    }
  }
}
