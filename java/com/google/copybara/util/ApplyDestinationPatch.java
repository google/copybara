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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.copybara.util.MergeImportTool.MergeResult;
import com.google.copybara.util.MergeImportTool.MergeResultCode;
import com.google.copybara.util.MergeImportTool.MergeRunner;
import com.google.copybara.util.console.Console;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import com.google.protobuf.ByteString;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Implements merge behavior for creating and applying a destination patch on the origin. */
public class ApplyDestinationPatch implements MergeRunner {

  String patchBin;
  Map<String, String> environment;
  Console console;

  private static final String FUZZ_TOKEN_PATTERN = "with fuzz (?P<fuzz>\\d+)";
  private static final String OFFSET_TOKEN_PATTERN = "\\(offset (?P<offset>\\d+) lines?\\)";
  private static final String SUCCEEDED_TOKEN_PATTERN = "succeeded at (?P<lineNumber>\\d+)";
  private static final Pattern FUZZ_AND_OFFSET_PATTERN =
      Pattern.compile(
          String.format(
              ".+%s( %s)?( %s)?.*",
              SUCCEEDED_TOKEN_PATTERN, FUZZ_TOKEN_PATTERN, OFFSET_TOKEN_PATTERN));

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
            "--fuzz=3",
            "--ignore-whitespace",
            "--input=-", // read from stdin
            "-r",
            rejPath.toString(),
            "-o",
            tempPath.toString(),
            lhs.toString());

    Command patchCmd =
        new Command(patchCmdParts.toArray(new String[0]), environment, tempDir.toFile());
    try {
      CommandOutputWithStatus out = new CommandRunner(patchCmd).withInput(diffContents).execute();

      // all patches applied successfully
      InputStream in = Files.newInputStream(tempPath);
      ByteString patchOutput = ByteString.readFrom(in);

      emitFuzzOffsetWarnings(tempPath.getFileName().toString(), out.getStdout(), false);
      return MergeResult.create(patchOutput, MergeResultCode.SUCCESS);
    } catch (BadExitStatusWithOutputException e) {
      if (e.getOutput().getTerminationStatus().getExitCode() == 1) {

        emitFuzzOffsetWarnings(tempPath.getFileName().toString(), e.getOutput().getStdout(), true);
        return applyMergePatch(tempDir, tempPath, rejPath);
      } else {
        throw new IOException("Unexpected exit code from patch", new CommandException(patchCmd, e));
      }
    } catch (CommandException e) {
      throw new IOException("Error while executing patch", e);
    }
  }

  private void emitFuzzOffsetWarnings(String filename, String stdout, boolean hasConflicts) {
    // patches requiring fuzz or offset to apply might warrant additional attention
    // TODO(b/282065119) emit full path and not just filename
    List<String> lines = Splitter.on("\n").splitToList(stdout);
    for (String line : lines) {
      Matcher matcher = FUZZ_AND_OFFSET_PATTERN.matcher(line);
      if (matcher.matches()) {
        String lineNumber = matcher.group("lineNumber");
        String fuzz = matcher.group("fuzz");
        String offset = matcher.group("offset");

        String fuzzOffsetMsg = "";
        if (fuzz != null && offset != null) {
          fuzzOffsetMsg = String.format("fuzz %s and offset %s", fuzz, offset);
        } else if (fuzz != null) {
          fuzzOffsetMsg = String.format("fuzz %s", fuzz);
        } else {
          fuzzOffsetMsg = String.format("offset %s", offset);
        }

        String mergeConflictLineWarning =
            hasConflicts ? " (line number excludes merge conflicts)" : "";

        console.warnFmt(
            "%s has patches applied with %s around line %s%s",
            filename, fuzzOffsetMsg, lineNumber, mergeConflictLineWarning);
      }
    }
  }

  private MergeResult applyMergePatch(Path root, Path file, Path patches) throws IOException {
    // some patches failed to apply, apply the rejects using merge mode and return the result
    ImmutableList<String> mergePatchCmdParts =
        ImmutableList.of(
            patchBin, "--ignore-whitespace", "-i", patches.toString(), "--merge", file.toString());
    Command mergePatchCmd =
        new Command(mergePatchCmdParts.toArray(new String[0]), environment, root.toFile());

    try {
      new CommandRunner(mergePatchCmd).execute();

      // merge patch proceeded successfully
      InputStream in = Files.newInputStream(file);
      ByteString patchOutput = ByteString.readFrom(in);
      return MergeResult.create(patchOutput, MergeResultCode.MERGE_CONFLICT);
    } catch (BadExitStatusWithOutputException e2) {
      if (e2.getOutput().getTerminationStatus().getExitCode() == 1) {
        InputStream in = Files.newInputStream(file);
        ByteString patchOutput = ByteString.readFrom(in);
        return MergeResult.create(patchOutput, MergeResultCode.MERGE_CONFLICT);
      }
      throw new IOException(
          "Unexpected exit code from patch --merge", new CommandException(mergePatchCmd, e2));
    } catch (CommandException e2) {
      throw new IOException("Error while executing patch", e2);
    }
  }
}
