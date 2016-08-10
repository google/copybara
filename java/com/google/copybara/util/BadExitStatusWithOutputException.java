// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.util;

import com.google.common.base.Preconditions;
import com.google.devtools.build.lib.shell.AbnormalTerminationException;
import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandResult;

import java.nio.charset.StandardCharsets;

/**
 * An exception that represents a program that did not exit with 0 exit code.
 *
 * <p>The reason for this class is that {@link Command#execute} doesn't populate {@link
 * CommandResult#stderr} when throwing a {@link com.google.devtools.build.lib.shell.BadExitStatusException}
 * exception. This class allows us to collect the error and store in this alternative exception.
 */
public class BadExitStatusWithOutputException extends AbnormalTerminationException {

  private final CommandOutputWithStatus output;

  BadExitStatusWithOutputException(Command command, CommandResult result, String message,
      byte[] stdout, byte[] stderr) {
    super(command, result, message);
    this.output = new CommandOutputWithStatus(result.getTerminationStatus(), stdout, stderr);
  }

  public CommandOutputWithStatus getOutput() {
    return output;
  }
}
