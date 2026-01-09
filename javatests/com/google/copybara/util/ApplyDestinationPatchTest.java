/*
 * Copyright (C) 2024 Google LLC
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

import com.google.common.collect.ImmutableMap;
import com.google.copybara.util.MergeImportTool.MergeResult;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ApplyDestinationPatchTest {
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  private Path rootPath;
  private Path left;
  private Path right;
  private Path baseline;
  private Path workdir;

  private TestingConsole console;

  public String patchBin = "patch";

  public String getPatchBin() {
    return patchBin;
  }

  public ImmutableMap<String, String> env = ImmutableMap.of();

  @Rule public TestName testName = new TestName();

  @Before
  public void setUp() throws Exception {
    console = new TestingConsole(false);
    rootPath = tmpFolder.getRoot().toPath();
    left = createDir(rootPath, "left");
    right = createDir(rootPath, "right");
    baseline = createDir(rootPath, "baseline");
    workdir = createDir(rootPath, "workdir");
  }

  private ApplyDestinationPatch getUnderTest() {
    return new ApplyDestinationPatch(console, getPatchBin(), env);
  }

  @Test
  public void simpleMergeSuccessTest() throws Exception {
    String filename = testName.getMethodName() + ".txt";
    writeFile(baseline, filename, "" + "baseline\n" + "baseline\n");
    writeFile(left, filename, "" + "baseline\n" + "baseline\n");
    writeFile(right, filename, "" + "internal\n" + "internal\n");

    String expected = "" + "internal\n" + "internal\n";

    MergeResult output =
        getUnderTest()
            .merge(
                left.resolve(filename),
                right.resolve(filename),
                baseline.resolve(filename),
                workdir);

    assertThat(output.fileContents().toString(UTF_8)).isEqualTo(expected);
  }

  @Test
  public void externalChangePropagated() throws Exception {
    String filename = testName.getMethodName() + ".txt";
    writeFile(baseline, filename, "" + "baseline\n" + "baseline\n" + "baseline\n");
    writeFile(left, filename, "" + "extra line\n" + "baseline\n" + "baseline\n" + "baseline\n");
    writeFile(right, filename, "" + "baseline\n" + "baseline\n" + "baseline\n");

    String expected = "" + "extra line\n" + "baseline\n" + "baseline\n" + "baseline\n";

    MergeResult output =
        getUnderTest()
            .merge(
                left.resolve(filename),
                right.resolve(filename),
                baseline.resolve(filename),
                workdir);

    assertThat(output.fileContents().toString(UTF_8)).isEqualTo(expected);
  }

  @Test
  public void internalPatchPreserved() throws Exception {
    String filename = testName.getMethodName() + ".txt";
    writeFile(baseline, filename, "" + "baseline\n" + "baseline\n" + "baseline\n");
    writeFile(left, filename, "" + "baseline\n" + "baseline\n" + "baseline\n");
    writeFile(
        right, filename, "" + "internal patch\n" + "baseline\n" + "baseline\n" + "baseline\n");

    String expected = "" + "internal patch\n" + "baseline\n" + "baseline\n" + "baseline\n";

    MergeResult output =
        getUnderTest()
            .merge(
                left.resolve(filename),
                right.resolve(filename),
                baseline.resolve(filename),
                workdir);

    assertThat(output.fileContents().toString(UTF_8)).isEqualTo(expected);
  }


  @Test
  public void simpleMergeConflictTest() throws Exception {
    String filename = testName.getMethodName() + ".txt";
    writeFile(
        baseline,
        filename,
        """
        a
        b
        """);
    writeFile(
        left,
        filename,
        """
        a
        left
        """);
    writeFile(
        right,
        filename,
        """
        a
        right
        """);

    String expected = "" + "a\n" + "<<<<<<<\n" + "left\n" + "=======\n" + "right\n" + ">>>>>>>\n";

    MergeResult output =
        getUnderTest()
            .merge(
                left.resolve(filename),
                right.resolve(filename),
                baseline.resolve(filename),
                workdir);

    assertThat(output.fileContents().toString(UTF_8)).isEqualTo(expected);
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
