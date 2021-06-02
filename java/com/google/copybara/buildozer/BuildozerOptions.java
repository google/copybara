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

package com.google.copybara.buildozer;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.format.BuildifierOptions;
import com.google.copybara.util.BadExitStatusWithOutputException;
import com.google.copybara.util.CommandOutput;
import com.google.copybara.util.CommandOutputWithStatus;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.Consoles;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Specifies how Buildozer is executed. */
@Parameters(separators = "=")
public final class BuildozerOptions implements Option {

  private final GeneralOptions generalOptions;
  private final BuildifierOptions buildifierOptions;
  private final WorkflowOptions workflowOptions;

  public BuildozerOptions(GeneralOptions generalOptions,
      BuildifierOptions buildifierOptions, WorkflowOptions workflowOptions) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.buildifierOptions = Preconditions.checkNotNull(buildifierOptions);
    this.workflowOptions = workflowOptions;
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Pattern targetNotFound =
      Pattern.compile(".*error while executing commands \\[.+\\] on target (?<error>.* not found)");

  @Parameter(names = "--buildozer-bin",
      description = "Binary to use for buildozer (Default is /usr/bin/buildozer)",
      hidden = true)
  public String buildozerBin = "/usr/bin/buildozer";

  private void logError(Console console, CommandOutput output) {
    Consoles.errorLogLines(console, "buildozer stdout: ", output.getStdout());
    Consoles.errorLogLines(console, "buildozer stderr: ", output.getStderr());
  }

  static class BuildozerCommand {

    private final List<String> targets;
    private final String cmd;

    BuildozerCommand(List<String> targets, String cmd) {
      this.targets = Preconditions.checkNotNull(targets);
      this.cmd = Preconditions.checkNotNull(cmd);
    }

    BuildozerCommand(String targets, String cmd) {
      this.targets = ImmutableList.of(Preconditions.checkNotNull(targets));
      this.cmd = Preconditions.checkNotNull(cmd);
    }

    @Override
    public String toString() {
      return cmd + "|" + Joiner.on('|').join(targets);
    }
  }

  /**
   * Runs buildozer with the given commands.
   */
  void run(Console console, Path checkoutDir, Iterable<BuildozerCommand> commands)
      throws ValidationException, TargetNotFoundException {
    List<String> args = Lists.newArrayList(
        buildozerBin, "-buildifier=" + buildifierOptions.buildifierBin);

    // We only use -k in keep going mode because it shows less errors (http://b/69386431)
    if (workflowOptions.ignoreNoop) {
      args.add("-k");
    }
    args.add("-f");
    args.add("-");
    try {
      Command cmd =
          new Command(
              args.toArray(new String[0]), /*environmentVariables*/ null, checkoutDir.toFile());
      CommandOutputWithStatus output = generalOptions.newCommandRunner(cmd)
          .withVerbose(generalOptions.isVerbose())
          .withInput(Joiner.on('\n').join(commands).getBytes(UTF_8))
          .execute();
      if (!output.getStdout().isEmpty()) {
        logger.atInfo().log("buildozer stdout: %s", output.getStdout());
      }
      if (!output.getStderr().isEmpty()) {
        logger.atInfo().log("buildozer stderr: %s", output.getStderr());
      }
    } catch (BadExitStatusWithOutputException e) {
      // Don't print the output for common/known errors.
      if (generalOptions.isVerbose()) {
        logError(console, e.getOutput());
      }
      if (e.getResult().getTerminationStatus().getExitCode() == 3) {
        // Buildozer exits with code == 3 when the build file was not modified and no output
        // was generated. This happens with expressions that match multiple targets, like
        // :%java_library
        throw new TargetNotFoundException(
            commandsMessage("Buildozer could not find a target for", commands));
      }
      if (e.getResult().getTerminationStatus().getExitCode() == 2) {
        ImmutableList<String> errors =
            Splitter.on('\n').splitToList(e.getOutput().getStderr()).stream()
                .filter(s -> !(s.isEmpty() || s.startsWith("fixed ")))
                .collect(ImmutableList.toImmutableList());
        ImmutableList.Builder<String> notFoundMsg = ImmutableList.builder();
        boolean allNotFound = true;
        for (String error : errors) {
          Matcher matcher = targetNotFound.matcher(error);
          if (matcher.matches()) {
            notFoundMsg.add(
                String.format("Buildozer could not find a target for %s", matcher.group(1)));
          } else if (error.contains("no such file or directory")
              || error.contains("not a directory")) {
            notFoundMsg.add("Buildozer could not find build file: " + error);
          } else {
            allNotFound = false;
          }
        }
        if (allNotFound) {
          throw new TargetNotFoundException(Joiner.on("\n").join(notFoundMsg.build()));
        }
      }
      // Otherwise we have already printed above.
      if (!generalOptions.isVerbose()) {
        logError(console, e.getOutput());
      }
      throw new ValidationException(String.format(
          "%s\nCommand stderr:%s",
          commandsMessage("Failed to execute buildozer with args", commands),
          e.getOutput().getStderr()),
          e);
    } catch (CommandException e) {
      String message = String.format("Error '%s' running buildozer command: %s",
          e.getMessage(), e.getCommand().toDebugString());
      console.error(message);
      throw new ValidationException(message, e);
    }
  }

  private static String commandsMessage(final String prefix, Iterable<BuildozerCommand> commands) {
    return prefix + ":\n  " + Joiner.on("\n  ").join(commands);
  }
}
