package com.google.copybara.util;

import com.google.devtools.build.lib.shell.TerminationStatus;

import java.nio.charset.StandardCharsets;

/**
 * A class that contains a {@link com.google.devtools.build.lib.shell.Command} result.
 *
 * <p>This class is equivalent to {@link com.google.devtools.build.lib.shell.CommandResult} but
 * doesn't fail if the output was not collected and allows to work in stream mode and accumulate the
 * result.
 */
public final class CommandOutput {

  private final TerminationStatus terminationStatus;
  private final byte[] stdout;
  private final byte[] stderr;

  CommandOutput(TerminationStatus terminationStatus, byte[] stdout, byte[] stderr) {
    this.terminationStatus = terminationStatus;
    this.stdout = stdout;
    this.stderr = stderr;
  }

  public TerminationStatus getTerminationStatus() {
    return terminationStatus;
  }

  /**
   * Returns stdout as a UTF-8 String.
   */
  public String getStdout() {
    return new String(stdout, StandardCharsets.UTF_8);
  }

  /**
   * Returns stderr as a UTF-8 String.
   */
  public String getStderr() {
    return new String(stderr, StandardCharsets.UTF_8);
  }

  @Override
  public String toString() {
    return "CommandOutput{" +
        "terminationStatus=" + terminationStatus +
        ", stdout=" + getStdout() +
        ", stderr=" + getStderr() +
        '}';
  }
}
