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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import java.nio.charset.StandardCharsets;

/**
 * Holds the {@code stdout} and {@code stderr} contents of a command execution.
 */
public class CommandOutput {

  private final byte[] stdout;
  private final byte[] stderr;


  @VisibleForTesting
  public CommandOutput(byte[] stdout, byte[] stderr) {
    this.stdout = Preconditions.checkNotNull(stdout);
    this.stderr = Preconditions.checkNotNull(stderr);
  }

  public String getStdout() {
    return new String(stdout, StandardCharsets.UTF_8);
  }

  public byte[] getStdoutBytes() {
    return stdout;
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
