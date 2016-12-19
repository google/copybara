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

package com.google.copybara.util;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.copybara.util.console.AnsiColor;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandException;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Diff utilities that are repository-agnostic.
 */
public class DiffUtil {

  private static final byte[] EMPTY_DIFF = new byte[]{};

  /**
   * Calculates the diff between two sibling directory trees.
   *
   * <p>Returns the diff as an encoding-independent {@code byte[]} that can be write to a file or
   * fed directly into {@link DiffUtil#patch}.
   */
  public static byte[] diff(Path one, Path other, boolean verbose) throws IOException {
    Preconditions.checkArgument(one.getParent().equals(other.getParent()),
        "Paths 'one' and 'other' must be sibling directories.");
    Path root = one.getParent();
    String[] params = new String[] {
        "git", "diff", "--no-color",
        root.relativize(one).toString(),
        root.relativize(other).toString()
    };
    Command cmd = new Command(params, /*envVars*/ null, root.toFile());
    try {
      CommandUtil.executeCommand(cmd, verbose);
      return EMPTY_DIFF;
    } catch (BadExitStatusWithOutputException e) {
      CommandOutput output = e.getOutput();
      // git diff returns exit status 0 when contents are identical, or 1 when they are different
      if (!Strings.isNullOrEmpty(output.getStderr())) {
        throw new IOException(String.format(
            "Error executing 'git diff': %s. Stderr: \n%s", e.getMessage(), output.getStderr()), e);
      }
      return output.getStdoutBytes();
    } catch (CommandException e) {
      throw new IOException("Error executing 'patch'", e);
    }
  }

  /**
   * Applies the diff into a directory tree.
   *
   * <p>{@code diffContents} is the result of invoking {@link DiffUtil#diff}.
   */
  public static void patch(
      Path rootDir, byte[] diffContents, int stripSlashes, boolean verbose, boolean reverse)
      throws IOException {
    // TODO(copybara-team): Think if it makes sense to throw EmptyChangeException here
    if (diffContents.length == 0) {
      return;
    }
    Preconditions.checkArgument(stripSlashes >= 0, "stripSlashes must be >= 0.");
    ImmutableList.Builder<String> params = ImmutableList.builder();
    params.add("git", "apply", "-p" + stripSlashes);
    if (reverse) {
      params.add("-R");
    }
    params.add("-");
    Command cmd =
        new Command(params.build().toArray(new String[0]), /*envVars*/ null, rootDir.toFile());
    try {
      CommandUtil.executeCommand(cmd, diffContents, verbose);
    } catch (BadExitStatusWithOutputException e) {
      throw new IOException(
          "Error executing 'patch': " + e.getMessage() + ". Stderr: \n" + e.getOutput().getStderr(),
          e);
    } catch (CommandException e) {
      throw new IOException("Error executing 'patch'", e);
    }
  }

  /**
   * Given a git compatible diff, returns the diff colorized if the console allows it.
   */
  public static String colorize(Console console, String diffText) {
    StringBuilder sb = new StringBuilder();
    for (String line : Splitter.on("\n").split(diffText)) {
      sb.append("\n");
      if (line.startsWith("+")) {
        sb.append(console.colorize(AnsiColor.GREEN, line));
      } else if (line.startsWith("-")) {
        sb.append(console.colorize(AnsiColor.RED, line));
      } else {
        sb.append(line);
      }
    }
    return sb.toString();
  }
}
