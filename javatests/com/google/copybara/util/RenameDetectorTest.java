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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.copybara.util.RenameDetector.Score;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RenameDetectorTest {

  static final class Bytes extends FilterInputStream {

    Bytes(String source) {
      super(new ByteArrayInputStream(source.getBytes(UTF_8)));
    }

    boolean isClosed = false;

    @Override
    public void close() throws IOException {
      super.close();
      isClosed = true;
    }
  }

  @Test
  public void identifiesIdenticalContentWithHigherScore() throws Exception {
    RenameDetector<String> detector = new RenameDetector<>();
    detector.addPriorFile("foo", new Bytes("xyz"));
    detector.addPriorFile("bar", new Bytes("xy"));
    detector.addPriorFile("baz", new Bytes("x"));

    List<Score<String>> result = detector.scoresForLaterFile(new Bytes("xyz"));
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getKey()).isEqualTo("foo");
    assertThat(result.get(0).getScore()).isEqualTo(Integer.MAX_VALUE);

    result = detector.scoresForLaterFile(new Bytes("x"));
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getKey()).isEqualTo("baz");
    assertThat(result.get(0).getScore()).isEqualTo(Integer.MAX_VALUE);

    assertThat(detector.scoresForLaterFile(new Bytes("asdf"))).isEmpty();
  }

  @Test
  public void orderOfBytesMatters() throws Exception {
    RenameDetector<String> detector = new RenameDetector<>();
    detector.addPriorFile("foo", new Bytes("ab"));
    detector.addPriorFile("bar", new Bytes("ba"));

    List<Score<String>> result = detector.scoresForLaterFile(new Bytes("ab"));
    assertThat(result).hasSize(1);
  }

  @Test
  public void canReturnMultipleFiles() throws Exception {
    RenameDetector<String> detector = new RenameDetector<>();
    detector.addPriorFile("foo", new Bytes("asdf"));
    detector.addPriorFile("bar", new Bytes("asdf"));

    List<Score<String>> result = detector.scoresForLaterFile(new Bytes("asdf"));
    assertThat(result).hasSize(2);
  }

  @Test
  public void usesOddFactorInHash() throws Exception {
    RenameDetector<String> detector = new RenameDetector<>();
    detector.addPriorFile("foo", new Bytes("baaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    detector.addPriorFile("bar", new Bytes("caaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));

    List<Score<String>> result =
        detector.scoresForLaterFile(new Bytes("caaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    assertThat(result).hasSize(1);
  }

  @Test
  public void closesPriorFile() throws Exception {
    RenameDetector<String> detector = new RenameDetector<>();
    Bytes bytes = new Bytes("asdf");

    assertThat(bytes.isClosed).isFalse();
    detector.addPriorFile("", bytes);
    assertThat(bytes.isClosed).isTrue();
  }

  @Test
  public void closesLaterFile() throws Exception {
    RenameDetector<String> detector = new RenameDetector<>();
    Bytes bytes = new Bytes("asdf");

    assertThat(bytes.isClosed).isFalse();
    detector.scoresForLaterFile(bytes);
    assertThat(bytes.isClosed).isTrue();
  }
}
