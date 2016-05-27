package com.google.copybara.util;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.copybara.EnvironmentException;
import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandException;

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
  public static byte[] diff(Path one, Path other, boolean verbose)
      throws EnvironmentException {
    Preconditions.checkArgument(one.getParent().equals(other.getParent()),
        "Paths 'one' and 'other' must be sibling directories.");
    Path root = one.getParent();
    String[] params = new String[] {
        "git", "diff",
        root.relativize(one).toString(),
        root.relativize(other).toString()
    };
    Command cmd = new Command(params, /*envVars*/ null, root.toFile());
    try {
      CommandUtil.executeCommand(cmd, verbose);
      return EMPTY_DIFF;
    } catch (BadExitStatusWithOutputException e) {
      // git diff returns exit status 0 when contents are identical, or 1 when they are different
      if (!Strings.isNullOrEmpty(e.stdErrAsString())) {
        throw new EnvironmentException(
            "Error executing 'git diff': " + e.getMessage() + ". Stderr: \n" + e.stdErrAsString(), e);
      }
      return e.stdOut();
    } catch (CommandException e) {
      throw new EnvironmentException("Error executing 'git diff'", e);
    }
  }

  /**
   * Applies the diff into a directory tree.
   *
   * <p>{@code diffContents} is the result of invoking {@link DiffUtil#diff}.
   */
  public static void patch(Path rootDir, byte[] diffContents, boolean verbose)
      throws EnvironmentException {
    // TODO(danielromero): Think if it makes sense to throw EmptyChangeException here
    if (diffContents.length == 0) {
      return;
    }
    String[] params = new String[]{"git", "apply", "-p2","-"};
    Command cmd = new Command(params, /*envVars*/ null, rootDir.toFile());
    try {
      CommandUtil.executeCommand(cmd, diffContents, verbose);
    } catch (BadExitStatusWithOutputException e) {
      throw new EnvironmentException(
          "Error executing 'patch': " + e.getMessage() + ". Stderr: \n" + e.stdErrAsString(), e);
    } catch (CommandException e) {
      throw new EnvironmentException("Error executing 'patch'", e);
    }
  }
}
