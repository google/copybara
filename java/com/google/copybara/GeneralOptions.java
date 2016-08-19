// Copyright 2016 Google Inc. All Rights Reserved.
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

/**
 * General options available for all the program classes.
 */
public final class GeneralOptions implements Option {

  public static final String NOANSI = "--noansi";

  private final Map<String, String> environment;
  private final FileSystem fileSystem;
  private final boolean verbose;
  private final Console console;
  private final boolean validate;

  @VisibleForTesting
  public GeneralOptions(FileSystem fileSystem, boolean verbose, Console console) {
    this(System.getenv(), fileSystem, verbose, console, /*validate=*/false);
  }

  @VisibleForTesting
  public GeneralOptions(
      Map<String, String> environment, FileSystem fileSystem, boolean verbose, Console console) {
    this(environment, fileSystem, verbose, console, /*validate=*/false);
  }

  @VisibleForTesting
  public GeneralOptions(Map<String, String> environment, FileSystem fileSystem, boolean verbose,
      Console console, boolean validate) {
    this.environment = ImmutableMap.copyOf(Preconditions.checkNotNull(environment));
    this.console = Preconditions.checkNotNull(console);
    this.fileSystem = Preconditions.checkNotNull(fileSystem);
    this.verbose = verbose;
    this.validate = validate;
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

    /**
     * This method should be called after the options have been set but before are used by any class.
     */
    public GeneralOptions init(
        Map<String, String> environment, FileSystem fileSystem, Console console)
        throws IOException {
      return new GeneralOptions(environment, fileSystem, verbose, console, validate);
    }
  }
}
