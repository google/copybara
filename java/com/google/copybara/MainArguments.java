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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Arguments which are unnamed (i.e. positional) or must be evaluated inside {@link Main}.
 */
@NotThreadSafe
@Parameters(separators = "=")
public final class MainArguments {
  private final Logger logger = Logger.getLogger(this.getClass().getName());

  static final String COPYBARA_SKYLARK_CONFIG_FILENAME = "copy.bara.sky";

  @Parameter(description =
      ""
          + "[subcommand] config_path [workflow_name [source_ref]]\n"
          + "\n"
          + (""
          + "subcommand: Optional, defaults to 'migrate'. The type of task to be performed by "
          + "Copybara. Available subcommands:\n"
          + "  - migrate: Executes the migration for the given config.\n"
          + "  - validate: Validates that the configuration is correct.\n"
          + "  - info: Reads the last migrated revision in the origin and destination.\n")
          + "\n"
          + "config_path: Required. Relative or absolute path to the main Copybara config file.\n"
          + "\n"
          + "workflow_name: Optional, defaults to 'default'. The name of the workflow in the "
          + "configuration to be used by Copybara.\n"
          + "\n"
          + "source_ref: Optional. The reference to be resolved in the origin. Most of the times "
          + "this argument is not needed, as Copybara keeps track of the last migrated reference "
          + "in the destination.\n"
  )
  List<String> unnamed = new ArrayList<>();

  @Parameter(names = "--help", help = true, description = "Shows this help text")
  boolean help;

  @Parameter(names = "--version", description = "Shows the version of the binary")
  boolean version;

  @Parameter(names = "--work-dir", description = "Directory where all the transformations"
      + " will be performed. By default a temporary directory.")
  String baseWorkdir;

  @Nullable
  private ArgumentHolder argumentHolder;

  private final ImmutableList<String> originalArgs;

  /**
   * A list containing the original invocation arguments. Solely meant for debugging/logging.
   */
  public ImmutableList<String> getOriginalArgsForLogging() {
    return originalArgs;
  }

  public CopybaraCmd getSubcommand() {
    return getArgs().subcommand;
  }

  public String getConfigPath() {
    return getArgs().configPath;
  }

  @Nullable
  public String getWorkflowName() {
    return getArgs().workflowName;
  }

  @Nullable
  public String getSourceRef() {
    return getArgs().sourceRef;
  }

  private ArgumentHolder getArgs() {
    Preconditions.checkNotNull(argumentHolder, "parseUnnamedArgs() should be invoked first. "
        + "This is probably a bug.");
    return argumentHolder;
  }

  public MainArguments(String[] args) {
    this.originalArgs = ImmutableList.copyOf(Preconditions.checkNotNull(args));
  }

  /**
   * Returns the base working directory. This method should not be accessed directly by any other
   * class but Main.
   */
  public Path getBaseWorkdir(GeneralOptions generalOptions, FileSystem fs)
      throws IOException {
    Path workdirPath;

    workdirPath = baseWorkdir == null
        ? generalOptions.getDirFactory().newTempDir("workdir")
        : fs.getPath(baseWorkdir).normalize();
    logger.log(Level.INFO, String.format("Using workdir: %s", workdirPath.toAbsolutePath()));

    if (Files.exists(workdirPath) && !Files.isDirectory(workdirPath)) {
      // Better being safe
      throw new IOException(
          "'" + workdirPath + "' exists and is not a directory");
    }
    if (!isDirEmpty(workdirPath)) {
      System.err.println("WARNING: " + workdirPath + " is not empty");
    }
    return workdirPath;
  }

  private static boolean isDirEmpty(final Path directory) throws IOException {
    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
      return !dirStream.iterator().hasNext();
    }
  }

  void parseUnnamedArgs(ImmutableMap<String, ? extends CopybaraCmd> commands,
      CopybaraCmd defaultCmd) throws CommandLineException {
    if (unnamed.isEmpty()) {
      throw new CommandLineException("Expected at least a configuration file.");
    } else if (unnamed.size() > 4) {
      throw new CommandLineException(
          String.format("Expected at most four arguments: %s. Note that flag values that contain "
              + "whitespaces must be between quotes: --some-flag \"Some Value\"", unnamed));
    }

    CopybaraCmd subcommand = defaultCmd;
    int argumentId = 0;
    String firstArg = unnamed.get(argumentId);
    // This should be enough for now
    if (!firstArg.endsWith(COPYBARA_SKYLARK_CONFIG_FILENAME)) {
      if (!commands.containsKey(firstArg.toLowerCase())) {
        throw new CommandLineException(
            String.format("Invalid subcommand '%s'. Available commands: %s", firstArg, commands));
      }
      subcommand = commands.get(firstArg.toLowerCase());
      argumentId++;
    }

    if (argumentId >= unnamed.size()) {
      throw new CommandLineException(
          String.format("Configuration file missing for '%s' subcommand.", subcommand.name()));
    }
    String configPath = unnamed.get(argumentId);
    argumentId++;

    String workflowName = "default";
    if (argumentId < unnamed.size()) {
      workflowName = unnamed.get(argumentId);
      argumentId++;
    }

    String sourceRef = null;
    if (argumentId < unnamed.size()) {
      // TODO(malcon): Move this to the commands
      if (subcommand.name().equals("info") || subcommand.name().equals("validate")) {
        throw new CommandLineException(
            String.format("Too many arguments for subcommand '%s'", subcommand.name()));
      }
      sourceRef = unnamed.get(argumentId);
    }
    argumentHolder = new ArgumentHolder(subcommand, configPath, workflowName, sourceRef);
  }

  private static class ArgumentHolder {

    private final CopybaraCmd subcommand;
    private final String configPath;
    @Nullable private final String workflowName;
    @Nullable private final String sourceRef;

    private ArgumentHolder(CopybaraCmd subcommand, String configPath,
        @Nullable  String workflowName, @Nullable String sourceRef) {
      this.subcommand = subcommand;
      this.configPath = configPath;
      this.workflowName = workflowName;
      this.sourceRef = sourceRef;
    }
  }
}
