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

import com.google.copybara.shell.AbnormalTerminationException;
import com.google.copybara.shell.BadExitStatusException;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandResult;

/**
 * An exception that represents a program that did not exit with 0 exit code.
 *
 * <p>The reason for this class is that {@link Command#execute} doesn't populate {@link
 * CommandResult#stderr} when throwing a {@link BadExitStatusException}
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
