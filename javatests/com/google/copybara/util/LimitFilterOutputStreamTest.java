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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LimitFilterOutputStreamTest {

  private static final byte[] SUFFIX = "NO MORE BYTES FOR YOU!".getBytes(StandardCharsets.UTF_8);

  @Test
  public void simpleTest() throws IOException {
    ByteArrayOutputStream delegate = new ByteArrayOutputStream();
    LimitFilterOutputStream stream = new LimitFilterOutputStream(delegate, 10, SUFFIX);

    byte[] bb = new byte[100];
    Arrays.fill(bb, (byte) '*');
    stream.write(bb);

    assertThat(delegate.toString(StandardCharsets.UTF_8))
        .isEqualTo("**********NO MORE BYTES FOR YOU!");
  }

  @Test
  public void suffixAtMostOnce() throws IOException {
    ByteArrayOutputStream delegate = new ByteArrayOutputStream();
    LimitFilterOutputStream stream = new LimitFilterOutputStream(delegate, 10, SUFFIX);

    byte[] bb = new byte[100];
    Arrays.fill(bb, (byte) '*');
    stream.write(bb);
    stream.write(bb);
    stream.write(bb);

    assertThat(delegate.toString(StandardCharsets.UTF_8))
        .isEqualTo("**********NO MORE BYTES FOR YOU!");
  }

  @Test
  public void noPrefix() throws IOException {
    ByteArrayOutputStream delegate = new ByteArrayOutputStream();
    LimitFilterOutputStream stream = new LimitFilterOutputStream(delegate, 10, new byte[]{});

    byte[] bb = new byte[100];
    Arrays.fill(bb, (byte) '*');
    stream.write(bb);

    assertThat(delegate.toString(StandardCharsets.UTF_8)).isEqualTo("**********");
  }

  @Test
  public void justInLimit() throws IOException {
    ByteArrayOutputStream delegate = new ByteArrayOutputStream();
    LimitFilterOutputStream stream = new LimitFilterOutputStream(delegate, 10, SUFFIX);

    byte[] bb = new byte[10];
    Arrays.fill(bb, (byte) '*');
    stream.write(bb);

    assertThat(delegate.toString(StandardCharsets.UTF_8)).isEqualTo("**********");
  }

  @Test
  public void justInLimitAndThenMore() throws IOException {
    ByteArrayOutputStream delegate = new ByteArrayOutputStream();
    LimitFilterOutputStream stream = new LimitFilterOutputStream(delegate, 10, SUFFIX);

    byte[] bb = new byte[10];
    Arrays.fill(bb, (byte) '*');
    stream.write(bb);
    stream.write(bb);

    assertThat(delegate.toString(StandardCharsets.UTF_8))
        .isEqualTo("**********NO MORE BYTES FOR YOU!");
  }

  @Test
  public void byteByByte() throws IOException {
    ByteArrayOutputStream delegate = new ByteArrayOutputStream();
    LimitFilterOutputStream stream = new LimitFilterOutputStream(delegate, 10, SUFFIX);

    for (int i = 0; i < 20; i++) {
      stream.write('*');
    }

    assertThat(delegate.toString(StandardCharsets.UTF_8))
        .isEqualTo("**********NO MORE BYTES FOR YOU!");
  }

  @Test
  public void badLimit() {
    assertThat(assertThrows(IllegalArgumentException.class,
        () -> new LimitFilterOutputStream(new ByteArrayOutputStream(), 0, SUFFIX)))
        .hasMessageThat().contains("greater than zero");
  }
}
