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

package com.google.copybara;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.exception.CommandLineException;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * Environment information for command execution: arguments, workdir, etc.
 */
public class CommandEnv {

  private final Path workdir;
  private final Options options;
  private final ImmutableList<String> args;
  @Nullable
  private ConfigFileArgs configFileArgs;

  @VisibleForTesting
  public CommandEnv(Path workdir, Options options, ImmutableList<String> args) {
    this.workdir = Preconditions.checkNotNull(workdir);
    this.options = Preconditions.checkNotNull(options);
    this.args = Preconditions.checkNotNull(args);
  }

  /**
   * Get the arguments parsed as config [migration [source_ref]...] if the command uses that format.
   */
  @Nullable
  public ConfigFileArgs getConfigFileArgs() {
    return configFileArgs;
  }

  /**
   * Parse the CLI arguments as config [workflow [source_ref]...]
   */
  public ConfigFileArgs parseConfigFileArgs(CopybaraCmd cmd, boolean usesSourceRef)
      throws CommandLineException {
    Preconditions.checkState(this.configFileArgs == null, "parseConfigFileArgs was already"
        + " called. Only one invocation allowed.");
    if (args.isEmpty()) {
      throw new CommandLineException(
          String.format("Configuration file missing for '%s' subcommand.", cmd.name()));
    }

    String configPath = args.get(0);

    if (args.size() < 2) {
      configFileArgs = new ConfigFileArgs(configPath, /*workflowName=*/null);
      return configFileArgs;
    }
    String workflowName = args.get(1);
    if (args.size() < 3) {
      configFileArgs = new ConfigFileArgs(configPath, workflowName);
      return configFileArgs;
    }

    if (!usesSourceRef) {
      throw new CommandLineException(
          String.format("Too many arguments for subcommand '%s'", cmd.name()));
    }
    configFileArgs = new ConfigFileArgs(configPath, workflowName, args.subList(2, args.size()));
    return configFileArgs;
  }


  public Path getWorkdir() {
    return workdir;
  }

  public Options getOptions() {
    return options;
  }

  public ImmutableList<String> getArgs() {
    return args;
  }
}
