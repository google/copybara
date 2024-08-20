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
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import java.io.IOException;
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
    return new FoldersDiff(verbose, environment).run(one.getParent(), one, other);
  }

  /**
   * Calculates the diff between two files with --ignore-cr-at-eol set
   *
   * <p>Returns the single file diff as an encoding-independent {@code byte[]}
   */
  public static byte[] diffFileWithIgnoreCrAtEol(
      Path root, Path one, Path other, boolean verbose, Map<String, String> environment)
      throws IOException, InsideGitDirException {
    return new FoldersDiff(verbose, environment)
        .withIgnoreCrAtEol()
        .withSingleFile()
        .run(root, one, other);
  }

  /**
   * Filter a diff output to only include diffs for original files that match a filter.
   */
  public static String filterDiff(byte[] diff, Predicate<String> pathFilter) {
    boolean include = true;
    StringBuilder filteredDiff = new StringBuilder();
    for (String line : Splitter.on('\n').split(new String(diff, UTF_8))) {
      if (line.startsWith("diff ")) {
        List<String> diffHeader = Splitter.on(' ').splitToList(line);
        // Given a diff in the format of:
        //     diff --git a/left/copybara/util/Test.java b/right/copybara/util/Test.java
        // Returns "left/copybara/util/Test.java"
        String path = diffHeader.get(2).substring(2);
        include = pathFilter.test(path);
      }
      if (include) {
        filteredDiff.append(line).append("\n");
      }
    }
    // Nothing to add
    if (filteredDiff.length() == 0) {
      return "";
    }
    return filteredDiff.toString();
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
            new FoldersDiff(verbose, environment)
                .withZOption()
                .withNameStatus()
                .withNoRenames()
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
    FoldersDiff.reverseApplyPatches(patchBytes, patchFiles, applyDirectory, environment);
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

    public enum Operation {
      ADD("A"),
      DELETE("D"),
      MODIFIED("M");

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

  /**
   * Execute git diff between two folders
   */
  private static class FoldersDiff {

    private final boolean nameStatus;
    private final boolean noRenames;
    private final boolean zOption;
    private final boolean noIndex;
    private final boolean verbose;
    private final boolean ignoreCrAtEol;
    private final boolean singleFile;
    private final Map<String, String> environment;
    private static final Pattern OUTPUT_ERROR_PATTERN =
        Pattern.compile("^error:", Pattern.MULTILINE);

    private FoldersDiff(boolean verbose, Map<String, String> environment) {
      this.verbose = verbose;
      this.environment = environment;
      nameStatus = false;
      noRenames = false;
      zOption = false;
      noIndex = false;
      ignoreCrAtEol = false;
      singleFile = false;
    }

    private FoldersDiff(
        boolean verbose,
        Map<String, String> environment,
        boolean nameStatus,
        boolean noRenames,
        boolean zOption,
        boolean noIndex,
        boolean ignoreCrAtEol,
        boolean singleFile) {
      this.verbose = verbose;
      this.environment = environment;
      this.nameStatus = nameStatus;
      this.noRenames = noRenames;
      this.zOption = zOption;
      this.noIndex = noIndex;
      this.ignoreCrAtEol = ignoreCrAtEol;
      this.singleFile = singleFile;
    }

    @CheckReturnValue
    private FoldersDiff withNameStatus() {
      return new FoldersDiff(
          verbose,
          environment,
          /*nameStatus=*/ true,
          noRenames,
          zOption,
          noIndex,
          ignoreCrAtEol,
          singleFile);
    }

    @CheckReturnValue
    private FoldersDiff withNoRenames() {
      return new FoldersDiff(
          verbose,
          environment,
          nameStatus,
          /*noRenames=*/ true,
          zOption,
          noIndex,
          ignoreCrAtEol,
          singleFile);
    }

    @CheckReturnValue
    private FoldersDiff withZOption() {
      return new FoldersDiff(
          verbose,
          environment,
          nameStatus,
          noRenames,
          /*zOption=*/ true,
          noIndex,
          ignoreCrAtEol,
          singleFile);
    }

    @CheckReturnValue
    private FoldersDiff withIgnoreCrAtEol() {
      return new FoldersDiff(
          verbose,
          environment,
          nameStatus,
          noRenames,
          zOption,
          noIndex,
          /*ignoreCrAtEol=*/ true,
          singleFile);
    }

    @CheckReturnValue
    private FoldersDiff withSingleFile() {
      return new FoldersDiff(
          verbose,
          environment,
          nameStatus,
          noRenames,
          zOption,
          noIndex,
          ignoreCrAtEol,
          /*singleFile=*/ true);
    }

    private byte[] run(Path root, Path one, Path other) throws IOException, InsideGitDirException {
      Preconditions.checkArgument(
          singleFile || one.getParent().equals(other.getParent()),
          "Paths 'one' and 'other' must be sibling directories.");
      GitEnvironment gitEnv = new GitEnvironment(environment);
      if (singleFile) {
        checkNotInsideGitRepo(one.getParent(), verbose, gitEnv);
      } else {
        checkNotInsideGitRepo(one, verbose, gitEnv);
      }
      List<String> params = Lists.newArrayList(gitEnv.resolveGitBinary(), "diff", "--no-color",
          // Be careful, no test coverage for this:
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

      // https://git-scm.com/docs/git-diff#Documentation/git-diff.txt---default-prefix
      // force prefix to a/ and b/, overriding diff.noprefix
      params.add("--default-prefix");

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

    private static void reverseApplyPatches(@Nullable byte[] patchBytes, List<Path> patchFiles,
        Path applyDirectory, Map<String, String> environment)
        throws IOException {
      GitEnvironment gitEnv = new GitEnvironment(environment);
      List<String> params = com.google.api.client.util.Lists.newArrayList();
      params.add(gitEnv.resolveGitBinary());
      params.add("apply");
      params.add("--reverse");
      params.add("-p2");
      params.add("--allow-empty");
      params.addAll(patchFiles.stream().map(Path::toString).collect(toImmutableList()));
      if (patchBytes != null) {
        params.add("-");
      }
      Command cmd =
          new Command(params.toArray(new String[] {}), ImmutableMap.of(), applyDirectory.toFile());
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

  /**
   * It is very common for users to have a git repo for their $HOME, so that they can version their
   * configurations. Unfortunately this fails for the default output directory (inside $HOME).
   */
  public static void checkNotInsideGitRepo(Path path, boolean verbose, GitEnvironment gitEnv)
      throws IOException, InsideGitDirException {
    try {
      Command cmd =
          new Command(
              new String[] {gitEnv.resolveGitBinary(), "rev-parse", "--git-dir"},
              gitEnv.getEnvironment(),
              path.toFile());

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
