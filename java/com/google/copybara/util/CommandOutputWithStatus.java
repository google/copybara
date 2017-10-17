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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandResult;
import com.google.copybara.shell.TerminationStatus;

/**
 * A class that contains a {@link Command} result.
 *
 * <p>This class is equivalent to {@link CommandResult} but
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
