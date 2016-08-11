// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.util;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.devtools.build.lib.shell.TerminationStatus;

/**
 * A class that contains a {@link com.google.devtools.build.lib.shell.Command} result.
 *
 * <p>This class is equivalent to {@link com.google.devtools.build.lib.shell.CommandResult} but
 * doesn't fail if the output was not collected and allows to work in stream mode and accumulate the
 * result.
 */
public final class CommandOutputWithStatus extends CommandOutput {

  private final TerminationStatus terminationStatus;

  CommandOutputWithStatus(TerminationStatus terminationStatus, byte[] stdout, byte[] stderr) {
    super(stdout, stderr);
    this.terminationStatus = Preconditions.checkNotNull(terminationStatus);
  }

  public TerminationStatus getTerminationStatus() {
    return terminationStatus;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .addValue(super.toString())
        .add("terminationStatus", terminationStatus)
        .toString();
  }
}
