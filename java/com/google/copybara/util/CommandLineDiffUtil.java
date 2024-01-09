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
import com.google.copybara.util.MergeImportTool.MergeResult;
import com.google.copybara.util.MergeImportTool.MergeResultCode;
import com.google.copybara.util.MergeImportTool.MergeRunner;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import com.google.protobuf.ByteString;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import javax.annotation.Nullable;

/** Diff utilities that shell out to a diffing commandline tool */
public final class CommandLineDiffUtil implements MergeRunner {

  final String diff3Bin;
  private final Map<String, String> environmentVariables;
  @Nullable
  private final Pattern debugPattern;

  public CommandLineDiffUtil(
      String diff3Bin, Map<String, String> environmentVariables, @Nullable Pattern debugPattern) {
    this.diff3Bin = diff3Bin;
    this.environmentVariables = environmentVariables;
    this.debugPattern = debugPattern;
  }

  // Remove the workdir path prefix from the diff3 merge marker label
  private String label(Path file, Path workdir) {
    return workdir.getParent().relativize(file).toString();
  }

  @Override
  public MergeResult merge(Path lhs, Path rhs, Path baseline, Path workDir) throws IOException {
    boolean debug = debugPattern != null && debugPattern.matcher(lhs.toString()).matches();

    // myfile oldfile yourfile
    String mArg = "-m";
    String labelFlag = "--label";
    ArrayList<String> argv =
        Lists.newArrayList(
            diff3Bin,
            lhs.toString(),
            labelFlag,
            label(lhs, workDir),
            baseline.toString(),
            labelFlag,
            label(baseline, workDir),
            rhs.toString(),
            labelFlag,
            label(rhs, workDir),
            mArg);
    Command cmd =
        new Command(
            argv.toArray(new String[0]), environmentVariables, workDir.toFile());
    CommandOutputWithStatus output;
    try {
      if (debug) {
        output = new CommandRunner(cmd).execute();
      } else {
        output = new CommandRunner(cmd).withVerbose(false).withMaxStdOutLogLines(0).execute();
      }
    } catch (BadExitStatusWithOutputException e) {
      if (e.getOutput().getTerminationStatus().getExitCode() == 1) {
        return MergeResult.create(
            ByteString.copyFrom(e.getOutput().getStdoutBytes()), MergeResultCode.MERGE_CONFLICT);
      }
      if (e.getOutput().getTerminationStatus().getExitCode() == 2) {
        return MergeResult.create(
            ByteString.copyFrom(e.getOutput().getStdoutBytes()), MergeResultCode.TROUBLE);
      }
      throw new IOException("Unexpected exit code from diff3", new CommandException(cmd, e));
    } catch (CommandException e) {
      throw new IOException("Error while executing diff3", e);
    }

    return MergeResult.create(
        ByteString.copyFrom(output.getStdoutBytes()), MergeResultCode.SUCCESS);
  }
}
