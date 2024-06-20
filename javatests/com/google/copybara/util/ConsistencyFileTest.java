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
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.google.copybara.exception.ValidationException;
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
public final class ConsistencyFileTest {

  @Rule
  public final TemporaryFolder tmpFolder = new TemporaryFolder();
  private Path baseline;
  private Path destination;

  private static final byte[] emptyDiff = {};
  private static final ImmutableMap<String, String> emptyHashes = ImmutableMap.of();

  // Hash value produced by: 'echo -n 'hello' | sha256sum
  private static final String helloHash =
      "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

  @Before
  public void setUp() throws IOException {
    Path workdir = tmpFolder.getRoot().toPath();
    baseline = Files.createDirectories(workdir.resolve("baseline"));
    destination = Files.createDirectories(workdir.resolve("destination"));
  }

  @Test
  public void testGenerateConsistencyFile() throws IOException, InsideGitDirException {
    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);
    assertThat(consistencyFile).isNotNull();
  }

  @Test
  public void testGenerateConsistencyFile_hashesFile() throws IOException, InsideGitDirException {
    String testPath = "test/foo";
    String testContents = "hello";
    byte[] testBytes = testContents.getBytes(UTF_8);

    Files.createDirectories(destination.resolve(testPath).getParent());
    Files.write(destination.resolve(testPath), testBytes);

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);

    assertThat(consistencyFile.getFileHashes()).containsExactly(testPath, helloHash);
  }

  @Test
  public void testGenerateConsistencyFile_diffsDirectories()
      throws IOException, InsideGitDirException {
    String testPath = "test/foo";
    String testDestinationContents = "hello\n";
    write(destination, testPath, testDestinationContents);

    String testBaselineContents = "baseline\n";
    write(baseline, testPath, testBaselineContents);

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);

    assertThat(new String(consistencyFile.getDiffContent(), UTF_8))
        .isEqualTo(
            ""
                + "diff --git a/baseline/test/foo b/destination/test/foo\n"
                + "index 180b47c..ce01362 100644\n"
                + "--- a/baseline/test/foo\n"
                + "+++ b/destination/test/foo\n"
                + "@@ -1 +1 @@\n"
                + "-baseline\n"
                + "+hello\n");
  }

  @Test
  public void testGenerateConsistencyFile_diffsDirectories_multipleFiles()
      throws IOException, InsideGitDirException {
    String testPath = "test/foo";
    String testPath2 = "test/bar";

    write(destination, testPath, "destination test\n");
    write(baseline, testPath, "baseline test\n");
    write(destination, testPath2, "destination test 2\n");
    write(baseline, testPath2, "baseline test 2\n");

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);

    assertThat(new String(consistencyFile.getDiffContent(), UTF_8))
        .isEqualTo(
            ""
                + "diff --git a/baseline/test/bar b/destination/test/bar\n"
                + "index 55208f4..8630a48 100644\n"
                + "--- a/baseline/test/bar\n"
                + "+++ b/destination/test/bar\n"
                + "@@ -1 +1 @@\n"
                + "-baseline test 2\n"
                + "+destination test 2\n"
                + "diff --git a/baseline/test/foo b/destination/test/foo\n"
                + "index fd50d5b..06c9033 100644\n"
                + "--- a/baseline/test/foo\n"
                + "+++ b/destination/test/foo\n"
                + "@@ -1 +1 @@\n"
                + "-baseline test\n"
                + "+destination test\n");
  }

  @Test
  public void testGenerateConsistencyFile_diffsDirectories_emptyDiff()
      throws IOException, InsideGitDirException {
    String testPath = "test/foo";
    String testDestinationContents = "hello";
    write(destination, testPath, testDestinationContents);

    String testBaselineContents = "hello";
    write(baseline, testPath, testBaselineContents);

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);

    assertThat(new String(consistencyFile.getDiffContent(), UTF_8)).isEqualTo("");
  }

  public void write(Path folder, String relativePath, String contents) throws IOException {
    byte[] bytes = contents.getBytes(UTF_8);
    Files.createDirectories(folder.resolve(relativePath).getParent());
    Files.write(folder.resolve(relativePath), bytes);
  }

  public void delete(Path folder, String relativePath) throws IOException {
    Files.delete(folder.resolve(relativePath));
  }

  @Test
  public void testPatchContainsHeader() throws IOException, InsideGitDirException {
    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);
    byte[] consistencyBytes = consistencyFile.toBytes();

    assertThat(new String(consistencyBytes, UTF_8)).contains("This file is generated by Copybara");
  }

  @Test
  public void testSerializeEmptyPatch() throws Exception {
    ConsistencyFile emptyPatch =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);
    byte[] emptyPatchBytes = emptyPatch.toBytes();
    ConsistencyFile deserializedPatch = ConsistencyFile.fromBytes(emptyPatchBytes);

    assertThat(deserializedPatch).isEqualTo(emptyPatch);
  }

  @Test
  public void testDeserializedObjectIsEquivalent_singleFile() throws Exception {
    write(destination, "test/path", "123457testcontents");
    write(baseline, "test/path", "123457testcontents");
    ConsistencyFile testConsistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);
    byte[] testPatchBytes = testConsistencyFile.toBytes();

    ConsistencyFile deserializedPatch = ConsistencyFile.fromBytes(testPatchBytes);

    assertThat(deserializedPatch).isEqualTo(testConsistencyFile);
  }

  @Test
  public void testDeserializedObjectNotEquivalent_addedFile() throws Exception {
    write(destination, "test/path", "123457testcontents");
    write(baseline, "test/path", "123457testcontents");
    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);

    // add new file to destination
    write(destination, "test/new", "asdf");

    ConsistencyFile differentPatch =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);
    byte[] differentPatchBytes = differentPatch.toBytes();

    ConsistencyFile deserializedPatch = ConsistencyFile.fromBytes(differentPatchBytes);

    assertThat(deserializedPatch).isNotEqualTo(consistencyFile);
  }

  @Test
  public void testDeserializedObjectNotEquivalent_changedFile() throws Exception {
    write(destination, "test/path", "123457testcontents");
    write(baseline, "test/path", "123457testcontents");
    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);

    // update file in destination
    write(destination, "test/path", "newcontents");

    ConsistencyFile differentPatch =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);
    byte[] differentPatchBytes = differentPatch.toBytes();

    ConsistencyFile deserializedPatch = ConsistencyFile.fromBytes(differentPatchBytes);

    assertThat(deserializedPatch).isNotEqualTo(consistencyFile);
  }

  @Test
  public void testFromBytes_invalidPathValueThrows() throws Exception {
    write(baseline, "foo", "hello");
    write(destination, "foo", "hello");
    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);

    // Manually make an edit to the byte format
    // There should be an entry for "foo" with a corresponding hash.
    // Use a null byte to create an invalid path to force an error when parsing.
    String consistencyContent = new String(consistencyFile.toBytes(), UTF_8);

    String newConsistencyContent = consistencyContent.replace("foo", "fo\0o");

    Throwable throwable =
        assertThrows(
            ValidationException.class,
            () -> ConsistencyFile.fromBytes(newConsistencyContent.getBytes(UTF_8)));
    assertThat(throwable).hasMessageThat().contains("path value is invalid");
  }

  @Test
  public void testFromBytes_invalidHashValueThrows_invalidChar() throws Exception {
    write(baseline, "foo", "hello");
    write(destination, "foo", "hello");
    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);

    String consistencyContent = new String(consistencyFile.toBytes(), UTF_8);

    // Manually make an edit to the byte format
    // There should be an entry for "foo" with a corresponding hash.
    // Use non-hexadecimal chars to make an invalid hash.
    String newConsistencyContent = consistencyContent.replace(helloHash, "gg");

    Throwable throwable =
        assertThrows(
            ValidationException.class,
            () -> ConsistencyFile.fromBytes(newConsistencyContent.getBytes(UTF_8)));
    assertThat(throwable).hasMessageThat().contains("hash value is invalid");
  }

  @Test
  public void testReversePatchConsistencyFile_appliesDiff() throws Exception {
    write(baseline, "foonodiff", "foo");
    write(destination, "foonodiff", "foo");

    write(baseline, "bardiff", "bar");
    write(destination, "bardiff", "newbar");

    // verify destination before reversing contains the new content
    assertThatPath(destination).containsFile("foonodiff", "foo");
    assertThatPath(destination).containsFile("bardiff", "newbar");

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);
    consistencyFile.reversePatches(destination, System.getenv());

    // destination after reversing should contain the old content
    assertThatPath(destination).containsFile("foonodiff", "foo");
    assertThatPath(destination).containsFile("bardiff", "bar");
  }

  @Test
  public void testReversePatchConsistencyFile_deletesFile() throws Exception {
    // Destination has a file that the baseline doesn't.
    // Reversing the patch should delete the file.
    write(baseline, "foonodiff", "foo");
    write(destination, "foonodiff", "foo");

    write(destination, "baradded", "bar");

    // verify destination before reversing contains the new content
    assertThatPath(destination).containsFile("foonodiff", "foo");
    assertThatPath(destination).containsFile("baradded", "bar");

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);
    consistencyFile.reversePatches(destination, System.getenv());

    // destination after reversing should contain the old content
    assertThatPath(destination).containsFile("foonodiff", "foo");
    assertThatPath(destination).containsNoFiles("baradded");
  }

  @Test
  public void testReversePatchConsistencyFile_addsFile() throws Exception {
    // Baseline has a file that the destination doesn't.
    // Reversing the patch should re-add the file.
    write(baseline, "foonodiff", "foo");
    write(destination, "foonodiff", "foo");

    write(baseline, "barremoved", "bar");

    // verify destination before reversing contains the new content
    assertThatPath(destination).containsFile("foonodiff", "foo");
    assertThatPath(destination).containsNoFiles("barremoved");

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);
    consistencyFile.reversePatches(destination, System.getenv());

    // destination after reversing should contain the old content
    assertThatPath(destination).containsFile("foonodiff", "foo");
    assertThatPath(destination).containsFile("barremoved", "bar");
  }

  @Test
  public void testValidateDirectory_success() throws Exception {
    write(destination, "dir/foo", "aaa");
    write(destination, "dir/bar", "bbb");

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);

    // should not throw
    consistencyFile.validateDirectory(
        ConsistencyFile.filesInDir(destination),
        ConsistencyFile.simpleHashGetter(destination, Hashing.sha256()));
  }

  @Test
  public void testValidateDirectory_fileAddedToDestination() throws Exception {
    write(destination, "dir/foo", "aaa");

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);

    write(destination, "dir/bar", "bbb");

    Throwable t =
        assertThrows(
            ValidationException.class,
            () ->
                consistencyFile.validateDirectory(
                    ConsistencyFile.filesInDir(destination),
                    ConsistencyFile.simpleHashGetter(destination, Hashing.sha256())));

    assertThat(t).hasMessageThat().contains("files in directory not present in ConsistencyFile");
  }

  @Test
  public void testValidateDirectory_fileRemovedFromDestination() throws Exception {
    write(destination, "dir/foo", "aaa");
    write(destination, "dir/bar", "bbb");

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);

    delete(destination, "dir/bar");

    Throwable t =
        assertThrows(
            ValidationException.class,
            () ->
                consistencyFile.validateDirectory(
                    ConsistencyFile.filesInDir(destination),
                    ConsistencyFile.simpleHashGetter(destination, Hashing.sha256())));

    assertThat(t)
        .hasMessageThat()
        .contains("files not found in directory but present in ConsistencyFile");
  }

  @Test
  public void testValidateDirectory_fileChanged() throws Exception {
    write(destination, "dir/foo", "aaa");

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);

    write(destination, "dir/foo", "bbb");

    Throwable t =
        assertThrows(
            ValidationException.class,
            () ->
                consistencyFile.validateDirectory(
                    ConsistencyFile.filesInDir(destination),
                    ConsistencyFile.simpleHashGetter(destination, Hashing.sha256())));

    assertThat(t)
        .hasMessageThat()
        .containsMatch(".* has hash value [\\d\\w]+ in ConsistencyFile but [\\d\\w]+ in directory");
  }

  @Test
  public void testGenerateConsistencyFile_sortedOutput() throws IOException, InsideGitDirException {
    ImmutableList<String> dirs = ImmutableList.of("a", "b", "c", "d", "e", "f", "g");

    String testPath = "test/foo";
    for (String dir : dirs) {
      write(destination, testPath + "/" + dir, "foo");
    }
    write(baseline, testPath, "foo");

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), System.getenv(), false);

    // maybe worth noting that even if sorting behavior broke, it is technically
    // possible that these end up sorted by coincidence
    assertThat(new String(consistencyFile.toBytes()))
        .matches(
            "(?s).*test/foo/a.*\n"
                + "test/foo/b.*\n"
                + "test/foo/c.*\n"
                + "test/foo/d.*\n"
                + "test/foo/e.*\n"
                + "test/foo/f.*\n"
                + "test/foo/g.*");
  }
}
