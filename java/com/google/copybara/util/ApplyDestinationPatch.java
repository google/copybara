/*
 * Copyright (C) 2024 Google LLC
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

import com.google.common.collect.ImmutableList;
import com.google.copybara.util.MergeImportTool.MergeResult;
import com.google.copybara.util.MergeImportTool.MergeResultCode;
import com.google.copybara.util.MergeImportTool.MergeRunner;
import com.google.copybara.util.console.Console;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Implements merge behavior for creating and applying a destination patch on the origin. */
public class ApplyDestinationPatch implements MergeRunner {

  String patchBin;
  Map<String, String> environment;
  Console console;

  public ApplyDestinationPatch(Console console, String patchBin, Map<String, String> environment) {
    this.console = console;
    this.patchBin = patchBin;
    this.environment = environment;
  }

  @Override
  public MergeResult merge(Path lhs, Path rhs, Path baseline, Path workdir) throws IOException {
    byte[] diffContents;
    try {
      diffContents =
          DiffUtil.diffFileWithIgnoreCrAtEol(
              workdir.getParent(), baseline, rhs, false, environment);
    } catch (InsideGitDirException e) {
      throw new IOException("Error diffing from baseline", e);
    }
    if (diffContents.length == 0) {
      InputStream in = Files.newInputStream(lhs);
      // no diff
      return MergeResult.create(ByteString.readFrom(in), MergeResultCode.SUCCESS);
    }

    // patch does not support stdout as output, create a temp file to capture output
    Path tempDir = Files.createTempDirectory(workdir, "patch-");
    Path tempPath = tempDir.resolve(lhs.getFileName());

    // create a temp path to capture rejected patches
    Path rejPath = tempDir.resolve(lhs.getFileName() + ".rej");

    // attempt the patch
    ImmutableList<String> patchCmdParts =
        ImmutableList.of(
            patchBin,
            "--ignore-whitespace",
            "--no-backup-if-mismatch",
            "--merge",
            "--input=-", // read from stdin
            "-r",
            rejPath.toString(),
            "-o",
            tempPath.toString(),
            lhs.toString());

    Command patchCmd =
        new Command(patchCmdParts.toArray(new String[0]), environment, tempDir.toFile());
    try {
      var unused = new CommandRunner(patchCmd).withInput(diffContents).execute();

      // all patches applied successfully
      InputStream in = Files.newInputStream(tempPath);
      ByteString patchOutput = ByteString.readFrom(in);

      return MergeResult.create(patchOutput, MergeResultCode.SUCCESS);
    } catch (BadExitStatusWithOutputException e) {
      if (e.getOutput().getTerminationStatus().getExitCode() == 1) {
        InputStream in = Files.newInputStream(tempPath);
        ByteString patchOutput = ByteString.readFrom(in);
        return MergeResult.create(patchOutput, MergeResultCode.MERGE_CONFLICT);
      } else {
        throw new IOException("Unexpected exit code from patch", new CommandException(patchCmd, e));
      }
    } catch (CommandException e) {
      throw new IOException("Error while executing patch", e);
    }
  }
}
