/*
 * Copyright (C) 2022 Google Inc.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class Diff3UtilTest {

  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  private Path left;
  private Path right;
  private Path baseline;
  private Path workdir;
  Diff3Util underTest;

  @Before
  public void setUp() throws Exception {
    Path rootPath = tmpFolder.getRoot().toPath();
    left = createDir(rootPath, "left");
    right = createDir(rootPath, "right");
    baseline = createDir(rootPath, "baseline");
    workdir = createDir(rootPath, "workdir");
    underTest = new Diff3Util("/usr/bin/diff3", null);
  }

  @Test
  public void simpleMergeSuccessTest() throws Exception {
    writeFile(baseline, "baseline.txt", "a\nb\nc");
    writeFile(left, "left.txt", "foo\na\nb\nc");
    writeFile(right, "right.txt", "a\nb\nc\nbar");

    CommandOutputWithStatus output =
        underTest.diff(
            left.resolve("left.txt"),
            right.resolve("right.txt"),
            baseline.resolve("baseline.txt"),
            workdir);

    String mergedFile = "foo\na\nb\nc\nbar";
    assertThat(output.getStdout()).isEqualTo(mergedFile);
  }

  @Test
  public void testMergeConflictPropagatedToCommandOutput() throws Exception {
    writeFile(left, "left.txt", "a\nb\nc\n");
    writeFile(right, "right.txt", "d\ne\nf\n");
    writeFile(baseline, "baseline.txt", "g\nh\ni");

    CommandOutputWithStatus output =
        underTest.diff(
            left.resolve("left.txt"),
            right.resolve("right.txt"),
            baseline.resolve("baseline.txt"),
            workdir);

    assertThat(output.getTerminationStatus().getExitCode()).isEqualTo(1);
  }

  private Path createDir(Path parent, String name) throws IOException {
    Path path = parent.resolve(name);
    Files.createDirectories(path);
    return path;
  }

  private void writeFile(Path parent, String fileName, String fileContents) throws IOException {
    Path filePath = parent.resolve(fileName);
    Files.createDirectories(filePath.getParent());
    Files.writeString(parent.resolve(filePath), fileContents);
  }
}
