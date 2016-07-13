package com.google.copybara.util;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

import java.nio.charset.StandardCharsets;

/**
 * Holds the {@code stdout} and {@code stderr} contents of a command execution.
 */
public class CommandOutput {

  private final byte[] stdout;
  private final byte[] stderr;

  CommandOutput(byte[] stdout, byte[] stderr) {
    this.stdout = Preconditions.checkNotNull(stdout);
    this.stderr = Preconditions.checkNotNull(stderr);
  }

  public String getStdout() {
    return new String(stdout, StandardCharsets.UTF_8);
  }

  public String getStderr() {
    return new String(stderr, StandardCharsets.UTF_8);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("stdout", getStdout())
        .add("stderr", getStderr())
        .toString();
  }
}
