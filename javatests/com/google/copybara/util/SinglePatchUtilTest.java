/*
 * Copyright (C) 2023 Google Inc.
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

import com.google.common.hash.Hashing;
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
public final class SinglePatchUtilTest {

  @Rule
  public final TemporaryFolder tmpFolder = new TemporaryFolder();
  private Path workdir;
  private Path baseline;
  private Path destination;

  @Before
  public void setUp() throws IOException {
    workdir = tmpFolder.getRoot().toPath();
    baseline = Files.createDirectories(workdir.resolve("baseline"));
    destination = Files.createDirectories(workdir.resolve("destination"));
  }

  @Test
  public void testGenerateSinglePatch() throws IOException, InsideGitDirException {
    SinglePatch singlePatch = SinglePatchUtil.generateSinglePatch(destination, baseline,
        Hashing.sha256(), System.getenv());
    assertThat(singlePatch).isNotNull();
  }

  @Test
  public void testGenerateSinglePatch_hashesFile() throws IOException, InsideGitDirException {
    String testPath = "test/foo";
    String testContents = "hello";
    byte[] testBytes = testContents.getBytes(UTF_8);

    Files.createDirectories(destination.resolve(testPath).getParent());
    Files.write(destination.resolve(testPath), testBytes);

    SinglePatch singlePatch = SinglePatchUtil.generateSinglePatch(destination, baseline,
        Hashing.sha256(), System.getenv());

    assertThat(singlePatch.getFileHashes()).containsExactly(
        testPath, new String(Hashing.sha256().hashBytes(testBytes).asBytes(), UTF_8)
    );
  }

  @Test
  public void testGenerateSinglePatch_diffsDirectories() throws IOException, InsideGitDirException {
    String testPath = "test/foo";
    String testDestinationContents = "hello\n";
    write(destination, testPath, testDestinationContents);

    String testBaselineContents = "baseline\n";
    write(baseline, testPath, testBaselineContents);

    SinglePatch singlePatch = SinglePatchUtil.generateSinglePatch(destination, baseline,
        Hashing.sha256(), System.getenv());

    assertThat(new String(singlePatch.getDiffContent(), UTF_8)).isEqualTo(""
        + "diff --git a/destination/test/foo b/baseline/test/foo\n"
        + "index ce01362..180b47c 100644\n"
        + "--- a/destination/test/foo\n"
        + "+++ b/baseline/test/foo\n"
        + "@@ -1 +1 @@\n"
        + "-hello\n"
        + "+baseline\n");
  }

  @Test
  public void testGenerateSinglePatch_diffsDirectories_multipleFiles()
      throws IOException, InsideGitDirException {
    String testPath = "test/foo";
    String testPath2 = "test/bar";

    write(destination, testPath, "destination test\n");
    write(baseline, testPath, "baseline test\n");
    write(destination, testPath2, "destination test 2\n");
    write(baseline, testPath2, "baseline test 2\n");

    SinglePatch singlePatch = SinglePatchUtil.generateSinglePatch(destination, baseline,
        Hashing.sha256(), System.getenv());

    assertThat(new String(singlePatch.getDiffContent(), UTF_8)).isEqualTo(""
        + "diff --git a/destination/test/bar b/baseline/test/bar\n"
        + "index 8630a48..55208f4 100644\n"
        + "--- a/destination/test/bar\n"
        + "+++ b/baseline/test/bar\n"
        + "@@ -1 +1 @@\n"
        + "-destination test 2\n"
        + "+baseline test 2\n"
        + "diff --git a/destination/test/foo b/baseline/test/foo\n"
        + "index 06c9033..fd50d5b 100644\n"
        + "--- a/destination/test/foo\n"
        + "+++ b/baseline/test/foo\n"
        + "@@ -1 +1 @@\n"
        + "-destination test\n"
        + "+baseline test\n");
  }

  @Test
  public void testGenerateSinglePatch_diffsDirectories_emptyDiff()
      throws IOException, InsideGitDirException {
    String testPath = "test/foo";
    String testDestinationContents = "hello";
    write(destination, testPath, testDestinationContents);

    String testBaselineContents = "hello";
    write(baseline, testPath, testBaselineContents);

    SinglePatch singlePatch = SinglePatchUtil.generateSinglePatch(destination, baseline,
        Hashing.sha256(), System.getenv());

    assertThat(new String(singlePatch.getDiffContent(), UTF_8)).isEqualTo("");
  }

  public void write(Path folder, String relativePath, String contents) throws IOException {
    byte[] bytes = contents.getBytes(UTF_8);
    Files.createDirectories(folder.resolve(relativePath).getParent());
    Files.write(folder.resolve(relativePath), bytes);

  }

}
