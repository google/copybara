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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.copybara.git.GitEnvironment;
import com.google.copybara.util.DiffUtil.DiffFile.Operation;
import com.google.copybara.util.console.AnsiColor;
import com.google.copybara.util.console.Console;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Diff utilities that are repository-agnostic. */
public class DiffUtil {

  private static final byte[] EMPTY_DIFF = new byte[]{};

  /**
   * Calculates the diff between two sibling directory trees.
   *
   * <p>Returns the diff as an encoding-independent {@code byte[]}.
   */
  public static byte[] diff(Path one, Path other, boolean verbose, Map<String, String> environment)
      throws IOException, InsideGitDirException {
    return FoldersDiff.builder(verbose, environment).build().run(one.getParent(), one, other);
  }

  /**
   * Calculates the diff between two sibling directory trees while setting --ignore-cr-at-eol.
   *
   * <p>Returns the diff as an encoding-independent {@code byte[]}.
   */
  public static byte[] diffWithIgnoreCrAtEol(
      Path one, Path other, boolean verbose, Map<String, String> environment)
      throws IOException, InsideGitDirException {
    return FoldersDiff.builder(verbose, environment)
        .setIgnoreCrAtEol(true)
        .build()
        .run(one.getParent(), one, other);
  }

  /**
   * Calculates the diff between two files with --ignore-cr-at-eol set
   *
   * <p>Returns the single file diff as an encoding-independent {@code byte[]}
   */
  public static byte[] diffFileWithIgnoreCrAtEol(
      Path root, Path one, Path other, boolean verbose, Map<String, String> environment)
      throws IOException, InsideGitDirException {
    return FoldersDiff.builder(verbose, environment)
        .setIgnoreCrAtEol(true)
        .setSingleFile(true)
        .build()
        .run(root, one, other);
  }

  /**
   * Filter a diff output to only include diffs for original files that match a filter. Identifies
   * file borders via the "diff -git a/left/... b/right/..." line and uses the left/... path.
   */
  public static byte[] filterDiff(byte[] diff, Predicate<String> pathFilter) {
    boolean include = true;
    List<String> filteredLines = Lists.newArrayList();
    for (String line : Splitter.on('\n').split(new String(diff, UTF_8))) {
      if (line.startsWith("diff ")) {
        List<String> diffHeader = Splitter.on(' ').splitToList(line);
        // Given a diff in the format of:
        //     diff --git a/left/copybara/util/Test.java b/right/copybara/util/Test.java
        // Returns "left/copybara/util/Test.java"
        if (diffHeader.size() >= 3) {
          try {
            String path = diffHeader.get(2).substring(2);
            var _ = Path.of(path);
            include = pathFilter.test(path);
          } catch (InvalidPathException | IndexOutOfBoundsException e) {
            // diff line not in expected format, ignore
          }
        }
      }
      if (include) {
        filteredLines.add(line);
      }
    }
    if (filteredLines.isEmpty()) {
      return new byte[0];
    }
    return String.join("\n", filteredLines).getBytes(UTF_8);
  }

  /**
   * Return the changed files without computing renames/copies.
   *
   * <p>Each file name is relative to one/other paths.
   */
  public static ImmutableList<DiffFile> diffFiles(
      Path one, Path other, boolean verbose, @Nullable Map<String, String> environment)
      throws IOException, InsideGitDirException {
    String cmdResult =
        new String(
            FoldersDiff.builder(verbose, environment)
                .setZOption(true)
                .setNameStatus(true)
                .setNoRenames(true)
                .build()
                .run(one.getParent(), one, other),
            UTF_8);

    ImmutableList.Builder<DiffFile> result = ImmutableList.builder();
    for (Iterator<String> iterator = Splitter.on((char) 0).split(cmdResult).iterator();
        iterator.hasNext(); ) {
      String strOp = iterator.next();
      if (Strings.isNullOrEmpty(strOp)) {
        continue;
      }
      Operation op = DiffFile.OP_BY_CHAR.get(strOp);
      if (op == null) {
        throw new IllegalStateException(
            String.format("Unknown type '%s'. Text:\n%s", strOp, cmdResult));
      }
      String file = iterator.next();
      Preconditions.checkState(file.contains("/"));
      result.add(new DiffFile(file.substring(file.indexOf("/") + 1), op));
    }
    return result.build();
  }

  /**
   * Apply the patches in reverse to the directory using git apply. At lease one of either
   * inputStream or a nonempty patchFiles should be supplied.
   *
   * @param patchBytes is an optional diff that will be streamed to the command through stdin.
   * @param patchFiles is a list of paths to patch files that will be supplied to the command.
   */
  public static void reverseApplyPatches(@Nullable byte[] patchBytes, List<Path> patchFiles,
      Path applyDirectory, Map<String, String> environment)
      throws IOException {
    GitEnvironment gitEnv = new GitEnvironment(environment);
    List<String> params = Lists.newArrayList();
    params.add(gitEnv.resolveGitBinary());
    // We want to use `git apply` as a glorified patch command without any
    // git repo involvement. Make sure git doesn't accidentally pick up some
    // git repo from higher up the directory tree.
    params.add("--git-dir=/dev/null");
    params.add("apply");
    params.add("--reverse");
    params.add("-p2");
    params.add("--allow-empty");
    params.addAll(patchFiles.stream().map(Path::toString).collect(toImmutableList()));
    if (patchBytes != null) {
      params.add("-");
    }
    Command cmd =
        new Command(
            params.toArray(new String[] {}), gitEnv.getEnvironment(), applyDirectory.toFile());
    try {
      CommandRunner runner = new CommandRunner(cmd).withVerbose(true);
      if (patchBytes != null) {
        runner = runner.withInput(patchBytes);
      }
      runner.execute();
    } catch (CommandException e) {
      throw new IOException("Error executing 'git apply'", e);
    }
  }

  public static class DiffFile {

    private final String name;
    private final Operation operation;
    private static final ImmutableMap<String, Operation> OP_BY_CHAR =
        Maps.uniqueIndex(Iterators.forArray(Operation.values()), e -> e.charType);

    @VisibleForTesting
    public DiffFile(String name, Operation operation) {
      this.name = checkNotNull(name);
      this.operation = checkNotNull(operation);
    }

    public String getName() {
      return name;
    }

    public Operation getOperation() {
      return operation;
    }

    /** Git Diff status letters */
    public enum Operation {
      ADD("A"),
      DELETE("D"),
      MODIFIED("M"),
      COPY("C"),
      RENAME("R"),
      TYPE_CHANGE("T"),
      UNMERGED("U");
      // X is omitted because it indicates a bug

      private final String charType;

      Operation(String charType) {
        this.charType = charType;
      }
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("name", name)
          .add("operation", operation)
          .toString();
    }
  }

  /** Execute git diff between two folders */
  record FoldersDiff(
      boolean nameStatus,
      boolean noRenames,
      boolean zOption,
      boolean noIndex,
      boolean verbose,
      boolean ignoreCrAtEol,
      boolean singleFile,
      @Nullable Map<String, String> environment) {

    private static final Pattern OUTPUT_ERROR_PATTERN =
        Pattern.compile("^error:", Pattern.MULTILINE);

    static Builder builder(boolean verbose, @Nullable Map<String, String> environment) {
      return new AutoBuilder_DiffUtil_FoldersDiff_Builder()
          .setVerbose(verbose)
          .setEnvironment(environment)
          .setNameStatus(false)
          .setNoRenames(false)
          .setZOption(false)
          .setNoIndex(false)
          .setIgnoreCrAtEol(false)
          .setSingleFile(false);
    }

    @AutoBuilder(ofClass = FoldersDiff.class)
    abstract static class Builder {
      abstract Builder setNameStatus(boolean nameStatus);

      abstract Builder setNoRenames(boolean noRenames);

      abstract Builder setZOption(boolean zOption);

      abstract Builder setNoIndex(boolean noIndex);

      abstract Builder setVerbose(boolean verbose);

      abstract Builder setIgnoreCrAtEol(boolean ignoreCrAtEol);

      abstract Builder setSingleFile(boolean singleFile);

      abstract Builder setEnvironment(@Nullable Map<String, String> environment);

      abstract FoldersDiff build();
    }

    private byte[] run(Path root, Path one, Path other) throws IOException {
      Preconditions.checkArgument(
          singleFile || one.getParent().equals(other.getParent()),
          "Paths 'one' and 'other' must be sibling directories.");
      GitEnvironment gitEnv = new GitEnvironment(environment);

      List<String> params =
          Lists.newArrayList(
              gitEnv.resolveGitBinary(),
              // We want to use `git apply` as a glorified patch command without any
              // git repo involvement. Make sure git doesn't accidentally pick up some
              // git repo from higher up the directory tree.
              "--git-dir=/dev/null",
              // override diff.noprefix for consistent diff output, must come after "git"
              "-c",
              "diff.noprefix=false",
              "diff",
              "--no-color",
              "--no-index",
              // Be careful, no test coverage for these:
              "--no-ext-diff");
      if (nameStatus) {
        params.add("--name-status");
      }
      if (noRenames) {
        params.add("--no-renames");
      }
      if (zOption) {
        params.add("-z");
      }
      if (ignoreCrAtEol) {
        params.add("--ignore-cr-at-eol");
      }

      params.add("--");
      params.add(root.relativize(one).toString());
      params.add(root.relativize(other).toString());
      Command cmd = new Command(params.toArray(new String[]{}), environment, root.toFile());
      try {
        new CommandRunner(cmd)
            .withVerbose(verbose)
            .execute();
        return EMPTY_DIFF;
      } catch (BadExitStatusWithOutputException e) {
        CommandOutput output = e.getOutput();
        // git diff returns exit status 0 when contents are identical, or 1 when they are different
        // see https://github.com/git/git/blob/master/usage.c#L81 for git error format
        String outputError = output.getStderr();
        if (!Strings.isNullOrEmpty(outputError)
            && OUTPUT_ERROR_PATTERN.matcher(outputError).find()) {
          throw new IOException(String.format(
              "Error executing 'git diff': %s. Stderr: \n%s", e.getMessage(), output.getStderr()),
              e);
        }
        return output.getStdoutBytes();
      } catch (CommandException e) {
        throw new IOException("Error executing 'git diff'", e);
      }
    }
  }

  /**
   * Given a git compatible diff, returns the diff colorized if the console allows it.
   */
  public static String colorize(Console console, String diffText) {
    StringBuilder sb = new StringBuilder();
    for (String line : Splitter.on("\n").split(diffText)) {
      sb.append("\n");
      if (line.startsWith("diff ")) {
        sb.append(console.colorize(AnsiColor.CYAN, line));
      } else if (line.startsWith("rename ")) {
        sb.append(console.colorize(AnsiColor.YELLOW, line));
      } else if (line.startsWith("+")) {
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
