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
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.exception.CommandLineException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Arguments which are unnamed (i.e. positional) or must be evaluated inside {@link Main}.
 */
@NotThreadSafe
@Parameters(separators = "=")
public final class MainArguments {
  private final Logger logger = Logger.getLogger(this.getClass().getName());

  public static final String COPYBARA_SKYLARK_CONFIG_FILENAME = "copy.bara.sky";

  // TODO(danielromero): Annotate the subcommands with the documentation and generate this
  // automatically.
  @Parameter(description =
      ""
          + "[subcommand] [config_path [migration_name [source_ref]...]]\n"
          + "\n"
          + (""
          + "subcommand: Optional, defaults to 'migrate'. The type of task to be performed by "
          + "Copybara. Available subcommands:\n"
          + "  - help: Shows the help.\n"
          + "  - info: Reads the last migrated revision in the origin and destination.\n"
          + "  - migrate: Executes the migration for the given config.\n"
          + "  - validate: Validates that the configuration is correct.\n"
          + "  - version: Shows the version of Copybara.\n"
          + "")
          + "\n"
          + "config_path: Required. Relative or absolute path to the main Copybara config file.\n"
          + "\n"
          + "migration_name: Optional, defaults to 'default'. The name of the migration that the "
          + "subcommand will be applied to.\n"
          + "\n"
          + "source_ref: Optional. The reference(s) to be resolved in the origin. Most of the "
          + "times this argument is not needed, as Copybara can infer the last migrated reference "
          + "in the destination. Different subcommands might require this argument, use only one "
          + "source_ref or use all the list.\n"
  )
  List<String> unnamed = new ArrayList<>();

  @Parameter(names = "--work-dir", description = "Directory where all the transformations"
      + " will be performed. By default a temporary directory.")
  String baseWorkdir;

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

  CommandWithArgs parseCommand(ImmutableMap<String, ? extends CopybaraCmd> commands,
      CopybaraCmd defaultCmd) throws CommandLineException {
    if (unnamed.isEmpty()) {
      return new CommandWithArgs(defaultCmd, ImmutableList.of());
    }
    String firstArg = unnamed.get(0);
    // Default command might take a config file as param.
    if (firstArg.endsWith(COPYBARA_SKYLARK_CONFIG_FILENAME)) {
      return new CommandWithArgs(defaultCmd, ImmutableList.copyOf(unnamed));
    }
    if (firstArg.contains(COPYBARA_SKYLARK_CONFIG_FILENAME + ':')) {
      return new CommandWithArgs(defaultCmd, ImmutableList.<String>builder()
          .addAll(splitConfigArg(firstArg)).addAll(unnamed.subList(1, unnamed.size())).build());
    }
    if (!commands.containsKey(firstArg.toLowerCase())) {
      throw new CommandLineException(
          String.format("Invalid subcommand '%s'. Available commands: %s", firstArg,
              new TreeSet<>(commands.keySet())));
    }
    if (unnamed.size() == 1) {
      return new CommandWithArgs(commands.get(firstArg.toLowerCase()), ImmutableList.of());
    }
    ImmutableList.Builder<String> args = ImmutableList.<String>builder().
        addAll(splitConfigArg(unnamed.get(1))).addAll(unnamed.subList(2, unnamed.size()));
    return new CommandWithArgs(commands.get(firstArg.toLowerCase()), args.build());
  }

  private static ImmutableList<String> splitConfigArg(String arg) {
    List<String> split =
        ImmutableList.copyOf(Splitter.on("copy.bara.sky:").limit(2).splitToList(arg));
    if (split.size() == 1) {
      return ImmutableList.copyOf(split);
    }
    return ImmutableList.of(split.get(0) + "copy.bara.sky", split.get(1));
  }

  static class CommandWithArgs {

    private final CopybaraCmd subcommand;
    private final ImmutableList<String> args;

    private CommandWithArgs(CopybaraCmd subcommand, ImmutableList<String> args) {
      this.subcommand = Preconditions.checkNotNull(subcommand);
      this.args = Preconditions.checkNotNull(args);
    }

    CopybaraCmd getSubcommand() {
      return subcommand;
    }

    ImmutableList<String> getArgs() {
      return args;
    }
  }
}
