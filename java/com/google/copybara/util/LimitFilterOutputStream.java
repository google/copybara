/*
 * Copyright (C) 2024 Google LLC.
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

import static com.google.common.base.Preconditions.checkArgument;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An output stream that limits the output to certain number of bytes. After that skips writing
 * to the delegated output.
 * As an optional last step, it can write an afterLimitSuffix byte (generally a text) to express
 * that it has reached the stream limit (e.g. "(Skipped the rest of the output)".bytes(...)
 */
public class LimitFilterOutputStream extends FilterOutputStream {

  private int left;
  private boolean suffixWritten = false;
  private final byte[] afterLimitSuffix;

  /**
   * Construct a limited output stream.
   *
   * @param out delegate output stream to wrap
   * @param byteLimit number of bytes to write before skipping any futher write. It is required
   * to be > 0.
   * @param afterLimitSuffix an optional suffix byte (generally a string) to write after reaching
   * the limit.
   */
  public LimitFilterOutputStream(OutputStream out, int byteLimit, byte[] afterLimitSuffix) {
    super(out);
    checkArgument(byteLimit > 0, "byteLimit is expected to be greater than zero.");
    this.left = byteLimit;
    this.afterLimitSuffix = afterLimitSuffix;
  }

  @Override
  public void write(int b) throws IOException {
    if (left > 0) {
      out.write(b);
      left--;
    } else {
      maybeWriteKLimitSuffix();
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (len <= 0) {
      return;
    }

    if (left == 0) {
      maybeWriteKLimitSuffix();
      return;
    }

    if (len > left) {
      int oldLeft = left;
      left = 0;
      // Here and other calls. We use out.write because the super.write method writes byte by byte.
      out.write(b, off, oldLeft);
      maybeWriteKLimitSuffix();
    } else {
      left -= len;
      out.write(b, off, len);
    }
  }

  /** Write at most once the suffix to the stream. */
  private void maybeWriteKLimitSuffix() throws IOException {
    if (afterLimitSuffix.length > 0 && !suffixWritten) {
      out.write(afterLimitSuffix);
      suffixWritten = true;
    }
  }
}
