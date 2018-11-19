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
import java.util.Comparator;
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
    assertThat(result.get(0).getScore()).isGreaterThan(0);

    result = detector.scoresForLaterFile(new Bytes("x"));
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getKey()).isEqualTo("baz");
    assertThat(result.get(0).getScore()).isGreaterThan(0);

    assertThat(detector.scoresForLaterFile(new Bytes("asdf"))).isEmpty();
  }

  @Test
  public void emptyPriorFile() throws Exception {
    RenameDetector<String> detector = new RenameDetector<>();
    detector.addPriorFile("foo", new Bytes(""));
    detector.addPriorFile("bar", new Bytes("xy\nxy\naa\n"));

    List<Score<String>> result = detector.scoresForLaterFile(new Bytes("xy\nxy\nbb\n"));
    Score<String> top = result.stream().max(Comparator.comparingInt(Score::getScore))
        .get();
    assertThat(top.getKey()).isEqualTo("bar");
  }

  @Test
  public void emptyFiles() throws Exception {
    RenameDetector<String> detector = new RenameDetector<>();
    detector.addPriorFile("foo", new Bytes(""));

    assertThat(detector.scoresForLaterFile(new Bytes(""))).isEmpty();
  }

  @Test
  public void emptyLaterFile() throws Exception {
    RenameDetector<String> detector = new RenameDetector<>();
    detector.addPriorFile("foo", new Bytes("a"));
    detector.addPriorFile("bar", new Bytes("b\nc\n"));

    List<Score<String>> result = detector.scoresForLaterFile(new Bytes(""));
    assertThat(result).isEmpty();
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

  @Test
  public void oneLineMatchIsBetterThanNoLinesMatching() throws Exception {
    RenameDetector<String> detector = new RenameDetector<>();
    detector.addPriorFile("foo", new Bytes("asdf\njkl;"));
    detector.addPriorFile("bar", new Bytes("aaaa\nbbbb"));
    List<Score<String>> result = detector.scoresForLaterFile(new Bytes("aaaa\ncccc"));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getKey()).isEqualTo("bar");
  }

  enum TestKey {FOO, BAR, BAZ, BLAH;}

  @Test
  public void matchingLinesHaveDifferentNumberOfInterleavingLines1() throws Exception {
    RenameDetector<TestKey> detector = new RenameDetector<>();
    detector.addPriorFile(TestKey.BAR, new Bytes("1234\naaaa\nbbbb"));
    detector.addPriorFile(TestKey.FOO, new Bytes("asdf\njkl;"));
    List<Score<TestKey>> result = detector.scoresForLaterFile(new Bytes("aaaa\ncccc"));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getKey()).isEqualTo(TestKey.BAR);
  }

  @Test
  public void matchingLinesHaveDifferentNumberOfInterleavingLines2() throws Exception {
    RenameDetector<TestKey> detector = new RenameDetector<>();
    detector.addPriorFile(TestKey.BAR, new Bytes("aaaa\nbbbb\ncccc"));
    detector.addPriorFile(TestKey.FOO, new Bytes("asdf\njkl;"));
    List<Score<TestKey>> result = detector.scoresForLaterFile(new Bytes("aaaa\ncccc"));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getKey()).isEqualTo(TestKey.BAR);
    assertThat(result.get(0).getScore()).isGreaterThan(0);
  }

  @Test
  public void sortsScoreListDecreasingly() throws Exception {
    RenameDetector<TestKey> detector = new RenameDetector<>();
    detector.addPriorFile(TestKey.FOO, new Bytes("aaaa\ndddd\ncccc"));
    detector.addPriorFile(TestKey.BAR, new Bytes("aaaa\nbbbb\ncccc"));
    detector.addPriorFile(TestKey.BAZ, new Bytes("aaaa\ndddd\nffff"));
    detector.addPriorFile(TestKey.BLAH, new Bytes("not a match at all!"));
    List<Score<TestKey>> result = detector.scoresForLaterFile(new Bytes("aaaa\nbbbb\ncccc"));

    assertThat(result).hasSize(3);
    assertThat(result.get(0).getKey()).isEqualTo(TestKey.BAR);
    assertThat(result.get(1).getKey()).isEqualTo(TestKey.FOO);
    assertThat(result.get(2).getKey()).isEqualTo(TestKey.BAZ);
  }

  @Test
  public void removedContentInLaterFileReducesScore() throws Exception {
    RenameDetector<TestKey> detector = new RenameDetector<>();
    detector.addPriorFile(TestKey.FOO, new Bytes("aaaa\nbbbb\ncccc\ndddd\neeee"));
    detector.addPriorFile(TestKey.BAR, new Bytes("aaaa\nbbbb\ncccc"));
    detector.addPriorFile(TestKey.BAZ, new Bytes("0000\naaaa\nbbbb\ncccc"));

    List<Score<TestKey>> result = detector.scoresForLaterFile(new Bytes("aaaa\nbbbb"));
    assertThat(result).hasSize(3);
    // BAR has only 1 line more than later file - it should have the highest score.
    assertThat(result.get(0).getKey()).isEqualTo(TestKey.BAR);
    assertThat(result.get(0).getScore()).isGreaterThan(result.get(1).getScore());
    // BAZ has 2 lines more than later file - slightly less score
    assertThat(result.get(1).getKey()).isEqualTo(TestKey.BAZ);
    assertThat(result.get(1).getScore()).isGreaterThan(result.get(2).getScore());
    assertThat(result.get(2).getKey()).isEqualTo(TestKey.FOO);
  }

  @Test
  public void addedContentInLaterFileReducesScore() throws Exception {
    RenameDetector<TestKey> detector = new RenameDetector<>();

    detector.addPriorFile(TestKey.FOO, new Bytes("aaaa\nbbbb"));
    detector.addPriorFile(TestKey.BAR, new Bytes("aaaa\nbbbb\ncccc"));
    detector.addPriorFile(TestKey.BAZ, new Bytes(""));

    List<Score<TestKey>> result = detector.scoresForLaterFile(new Bytes("aaaa\nbbbb\ncccc\ndddd"));
    assertThat(result).hasSize(2);
    // BAR has only 1 line less than later file - it should have the highest score.
    assertThat(result.get(0).getKey()).isEqualTo(TestKey.BAR);
    assertThat(result.get(0).getScore()).isGreaterThan(result.get(1).getScore());
    // FOO has 2 lines less than later file - slightly less score
    assertThat(result.get(1).getKey()).isEqualTo(TestKey.FOO);
  }

  @Test
  public void matchSmallSourceFile() throws Exception {
    RenameDetector<TestKey> detector = new RenameDetector<>();

    detector.addPriorFile(TestKey.FOO, new Bytes("aaaa\n"));

    List<Score<TestKey>> result =
        detector.scoresForLaterFile(new Bytes("aaaa\nbbbb\ncccc\ndddd\neeeee"));
    assertThat(result).hasSize(1);
    // BAR has only 1 line less than later file - it should have the highest score.
    assertThat(result.get(0).getKey()).isEqualTo(TestKey.FOO);
    assertThat(result.get(0).getScore()).isEqualTo(200);
  }

}
