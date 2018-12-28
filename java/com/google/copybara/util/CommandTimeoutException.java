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

import com.google.copybara.shell.AbnormalTerminationException;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandResult;
import java.time.Duration;

/** An exception that represents a program that timeout and was killed. */
public class CommandTimeoutException extends AbnormalTerminationException {

  private final CommandOutputWithStatus output;
  private final Duration timeout;

  CommandTimeoutException(Command command, CommandResult result, String message,
      byte[] stdout, byte[] stderr, Duration timeout) {
    super(checkNotNull(command), result, checkNotNull(message));
    this.timeout = checkNotNull(timeout);
    this.output = new CommandOutputWithStatus(result.getTerminationStatus(),
        checkNotNull(stdout), checkNotNull(stderr));
  }

  public CommandOutputWithStatus getOutput() {
    return output;
  }

  public Duration getTimeout() {
    return timeout;
  }
}
