package com.google.copybara.util;

import com.google.common.base.Preconditions;
import com.google.devtools.build.lib.shell.AbnormalTerminationException;
import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandResult;

/**
 * An exception that represents a program that did not exit with 0 exit code.
 *
 * <p>The reason for this class is that {@link Command#execute} doesn't populate {@link
 * CommandResult#stderr} when throwing a {@link com.google.devtools.build.lib.shell.BadExitStatusException}
 * exception. This class allows us to collect the error and store in this alternative exception.
 */
public class BadExitStatusWithOutputException extends AbnormalTerminationException {

  private final String stdOut;
  private final String stdErr;

  BadExitStatusWithOutputException(Command command, CommandResult result, String message,
      String stdOut, String stdErr) {
    super(command, result, message);
    this.stdOut = Preconditions.checkNotNull(stdOut);
    this.stdErr = Preconditions.checkNotNull(stdErr);
  }

  public String getStdOut() {
    return stdOut;
  }

  public String getStdErr() {
    return stdErr;
  }
}
