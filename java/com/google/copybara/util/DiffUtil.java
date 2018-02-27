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

import static com.google.copybara.git.GitExecPath.resolveGitBinary;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import com.google.copybara.util.console.AnsiColor;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

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
  public static byte[] diff(Path one, Path other, boolean verbose, Map<String, String> environment)
      throws IOException, InsideGitDirException {
    Preconditions.checkArgument(one.getParent().equals(other.getParent()),
        "Paths 'one' and 'other' must be sibling directories.");
    checkNotInsideGitRepo(one, verbose, environment);
    Path root = one.getParent();
    String[] params = new String[] {
        "git", "diff", "--no-color", "--",
        root.relativize(one).toString(),
        root.relativize(other).toString()
    };
    Command cmd = new Command(params, environment, root.toFile());
    try {
      new CommandRunner(cmd)
          .withVerbose(verbose)
          .execute();
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
      Path rootDir, byte[] diffContents, ImmutableList<String> excludedPaths, int stripSlashes,
      boolean verbose, boolean reverse, Map<String, String> environment)
      throws IOException, InsideGitDirException {
    if (diffContents.length == 0) {
      return;
    }
    Preconditions.checkArgument(stripSlashes >= 0, "stripSlashes must be >= 0.");
    checkNotInsideGitRepo(rootDir, verbose, environment);
    ImmutableList.Builder<String> params = ImmutableList.builder();

    // Show verbose output unconditionally since it is helpful for debugging issues with patches.
    params.add(resolveGitBinary(environment),
        "apply", "-v","--stat","--apply", "-p" + stripSlashes);
    for (String excludedPath : excludedPaths) {
      params.add("--exclude", excludedPath);
    }
    if (reverse) {
      params.add("-R");
    }
    params.add("-");
    Command cmd =
        new Command(params.build().toArray(new String[0]), environment, rootDir.toFile());
    try {
      new CommandRunner(cmd)
          .withVerbose(verbose)
          .withInput(diffContents)
          .execute();
    } catch (BadExitStatusWithOutputException e) {
      throw new IOException(
          "Error executing 'git apply': " + e.getMessage()
              + ". Stderr: \n" + e.getOutput().getStderr(),
          e);
    } catch (CommandException e) {
      throw new IOException("Error executing 'git apply'", e);
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

  /**
   * It is very common for users to have a git repo for their $HOME, so that they can version
   * their configurations. Unfortunately this fails for the default output directory (inside
   * $HOME).
   */
  private static void checkNotInsideGitRepo(Path path, boolean verbose, Map<String, String> env)
      throws IOException, InsideGitDirException {
    try {
      Command cmd = new Command(new String[]{
          resolveGitBinary(env), "rev-parse", "--git-dir"}, env, path.toFile());

      String gitDir = new CommandRunner(cmd)
          .withVerbose(verbose)
          .execute()
          .getStdout()
          .trim();

      // If it doesn't fail it means taht we are inside a git directory
      throw new InsideGitDirException(String.format(
          "Cannot diff/patch because Copybara temporary directory (%s) is inside a git"
              + " directory (%s).", path, gitDir),
          gitDir, path);
    } catch (BadExitStatusWithOutputException e) {
      // Some git versions return "Not", others "not"
      if (!e.getOutput().getStderr().contains(/*N*/"ot a git repository")) {
        throw new IOException("Error executing rev-parse", e);
      }
    } catch (CommandException e) {
      throw new IOException("Error executing rev-parse", e);
    }
  }
}
