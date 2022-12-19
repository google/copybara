/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.util;

import com.google.common.collect.Lists;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

/** Diff utilities that shell out to a diffing commandline tool */
public final class CommandLineDiffUtil {

  final String diffBin;
  private final Map<String, String> environmentVariables;

  public CommandLineDiffUtil(String diffBin, Map<String, String> environmentVariables) {
    this.diffBin = diffBin;
    this.environmentVariables = environmentVariables;
  }

  public CommandOutputWithStatus diff(Path lhs, Path rhs, Path baseline, Path workDir)
      throws CommandException, IOException {
    // myfile oldfile yourfile
    String mArg = "-m";
    ArrayList<String> argv =
        Lists.newArrayList(diffBin, lhs.toString(), baseline.toString(), rhs.toString(), mArg);
    Command cmd =
        new Command(
            argv.toArray(new String[0]), environmentVariables, workDir.toFile());
    CommandOutputWithStatus output;
    try {
      output = new CommandRunner(cmd).withVerbose(false).execute();
    } catch (BadExitStatusWithOutputException e) {
      if (e.getOutput().getTerminationStatus().getExitCode() == 1
          || e.getOutput().getTerminationStatus().getExitCode() == 2) {
        return new CommandOutputWithStatus(
            e.getOutput().getTerminationStatus(),
            e.getOutput().getStdout().getBytes(StandardCharsets.UTF_8),
            e.getOutput().getStderr().getBytes(StandardCharsets.UTF_8));
      }
      throw new CommandException(cmd, e);
    }
    return output;
  }
}
