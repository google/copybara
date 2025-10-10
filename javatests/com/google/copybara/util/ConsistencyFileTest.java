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
public class ConsistencyFileTest {

  @Rule
  public final TemporaryFolder tmpFolder = new TemporaryFolder();
  private Path baseline;
  private Path destination;


  // Hash value produced by: 'echo -n 'hello' | sha256sum
  private static final String helloHash =
      "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

  public ImmutableMap<String, String> env = ImmutableMap.copyOf(System.getenv());

  @Before
  public void setUp() throws IOException {
    Path workdir = tmpFolder.getRoot().toPath();
    baseline = Files.createDirectories(workdir.resolve("baseline"));
    destination = Files.createDirectories(workdir.resolve("destination"));
  }

  @Test
  public void testGenerateConsistencyFile() throws Exception {
    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);
    assertThat(consistencyFile).isNotNull();
  }

  @Test
  public void testGenerateConsistencyFile_ignoresSymlinkDirs() throws Exception {
    Path symlinkParentDir = Files.createDirectories(destination.resolve("test/symlinkparent"));
    Path symlinkTargetDir = Files.createDirectories(destination.resolve("test/symlinktarget"));
    Path unused = Files.createSymbolicLink(symlinkParentDir.resolve("symlink"), symlinkTargetDir);

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);
    assertThat(consistencyFile).isNotNull();
  }

  @Test
  public void testGenerateConsistencyFile_ignoresSymlinkFiles() throws Exception {
    Path symlinkParentDir = Files.createDirectories(destination.resolve("test/symlinkparent"));
    Path symlinkTargetFile = Path.of("/invalid/path");
    Path unused = Files.createSymbolicLink(symlinkParentDir.resolve("symlink"), symlinkTargetFile);

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);
    assertThat(consistencyFile).isNotNull();
  }

  @Test
  public void testGenerateConsistencyFile_hashesFile() throws Exception {
    String testPath = "test/foo";
    String testContents = "hello";
    byte[] testBytes = testContents.getBytes(UTF_8);

    Files.createDirectories(destination.resolve(testPath).getParent());
    Files.write(destination.resolve(testPath), testBytes);
    write(baseline, testPath, "asdf");

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);

    assertThat(consistencyFile.getFileHashes()).containsExactly(testPath, helloHash);
  }

  @Test
  public void testGenerateConsistencyFile_diffsDirectories() throws Exception {
    String testPath = "test/foo";
    String testDestinationContents = "hello\n";
    write(destination, testPath, testDestinationContents);

    String testBaselineContents = "baseline\n";
    write(baseline, testPath, testBaselineContents);

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);

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
  public void testGenerateConsistencyFile_diffsDirectories_ignoresCrAtEol() throws Exception {
    String testPath = "test/foo";
    String testDestinationContents = "hello\n";
    write(destination, testPath, testDestinationContents);

    String testBaselineContents = "hello\r\n";
    write(baseline, testPath, testBaselineContents);

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);

    assertThat(new String(consistencyFile.getDiffContent(), UTF_8)).isEqualTo("");
  }

  @Test
  public void testGenerateConsistencyFile_diffsDirectories_multipleFiles() throws Exception {
    String testPath = "test/foo";
    String testPath2 = "test/bar";

    write(destination, testPath, "destination test\n");
    write(baseline, testPath, "baseline test\n");
    write(destination, testPath2, "destination test 2\n");
    write(baseline, testPath2, "baseline test 2\n");

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);

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
  public void testGenerateConsistencyFile_diffsDirectories_emptyDiff() throws Exception {
    String testPath = "test/foo";
    String testDestinationContents = "hello";
    write(destination, testPath, testDestinationContents);

    String testBaselineContents = "hello";
    write(baseline, testPath, testBaselineContents);

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);

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
  public void testPatchContainsHeader() throws Exception {
    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);
    byte[] consistencyBytes = consistencyFile.toBytes();

    assertThat(new String(consistencyBytes, UTF_8)).contains("This file is generated by Copybara");
  }

  @Test
  public void testSerializeEmptyPatch() throws Exception {
    ConsistencyFile emptyPatch =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);
    byte[] emptyPatchBytes = emptyPatch.toBytes();
    ConsistencyFile deserializedPatch = ConsistencyFile.fromBytes(emptyPatchBytes);

    assertThat(deserializedPatch).isEqualTo(emptyPatch);
  }

  @Test
  public void testDeserializedObjectIsEquivalent_singleFile() throws Exception {
    write(destination, "test/path", "123457testcontents");
    write(baseline, "test/path", "123457testcontents");
    ConsistencyFile testConsistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);
    byte[] testPatchBytes = testConsistencyFile.toBytes();

    ConsistencyFile deserializedPatch = ConsistencyFile.fromBytes(testPatchBytes);

    assertThat(deserializedPatch).isEqualTo(testConsistencyFile);
  }

  @Test
  public void testDeserializedObjectNotEquivalent_changedFile() throws Exception {
    write(destination, "test/path", "123457testcontents");
    write(baseline, "test/path", "123457testcontents");
    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);

    // update file in destination
    write(destination, "test/path", "newcontents");

    ConsistencyFile differentPatch =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);
    byte[] differentPatchBytes = differentPatch.toBytes();

    ConsistencyFile deserializedPatch = ConsistencyFile.fromBytes(differentPatchBytes);

    assertThat(deserializedPatch).isNotEqualTo(consistencyFile);
  }

  @Test
  public void testFromBytes_invalidPathValueThrows() throws Exception {
    write(baseline, "foo", "hello");
    write(destination, "foo", "hello");
    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);

    // Manually make an edit to the byte format
    // There should be an entry for "foo" with a corresponding hash.
    // Use a null byte to create an invalid path to force an error when parsing.
    String consistencyContent = new String(consistencyFile.toBytes(), UTF_8);

    String newConsistencyContent = consistencyContent.replace("foo", "fo\0o");

    Throwable throwable =
        assertThrows(
            ValidationException.class,
            () -> ConsistencyFile.fromBytes(newConsistencyContent.getBytes(UTF_8)));
    assertThat(throwable).hasMessageThat().contains("path value is invalid: fo\0o");
  }

  @Test
  public void testFromBytes_invalidHashValueThrows_invalidChar() throws Exception {
    write(baseline, "foo", "hello");
    write(destination, "foo", "hello");
    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);

    String consistencyContent = new String(consistencyFile.toBytes(), UTF_8);

    // Manually make an edit to the byte format
    // There should be an entry for "foo" with a corresponding hash.
    // Use non-hexadecimal chars to make an invalid hash.
    String newConsistencyContent = consistencyContent.replace(helloHash, "gg");

    Throwable throwable =
        assertThrows(
            ValidationException.class,
            () -> ConsistencyFile.fromBytes(newConsistencyContent.getBytes(UTF_8)));
    assertThat(throwable).hasMessageThat().contains("hash value is invalid: gg");
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
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);
    consistencyFile.reversePatches(destination, env);

    // destination after reversing should contain the old content
    assertThatPath(destination).containsFile("foonodiff", "foo");
    assertThatPath(destination).containsFile("bardiff", "bar");
  }

  @Test
  public void testValidateDirectory_success() throws Exception {
    write(baseline, "dir/foo", "aa");
    write(baseline, "dir/bar", "bb");
    write(destination, "dir/foo", "aaa");
    write(destination, "dir/bar", "bbb");

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);

    // should not throw
    consistencyFile.validateDirectory(
        ConsistencyFile.filesInDir(destination),
        ConsistencyFile.simpleHashGetter(destination, Hashing.sha256()));
  }

  @Test
  public void testValidateDirectory_fileAddedToDestination() throws Exception {
    write(destination, "dir/foo", "aaa");

    ConsistencyFile consistencyFile = ConsistencyFile.generateNoDiff(destination, Hashing.sha256());

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

    ConsistencyFile consistencyFile = ConsistencyFile.generateNoDiff(destination, Hashing.sha256());

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
    write(baseline, "dir/foo", "zzz");
    write(destination, "dir/foo", "aaa");

    ConsistencyFile consistencyFile = ConsistencyFile.generateNoDiff(destination, Hashing.sha256());

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
  public void testGenerateConsistencyFile_sortedOutput() throws Exception {
    ImmutableList<String> dirs = ImmutableList.of("a", "b", "c", "d", "e", "f", "g");

    String testPath = "test/foo";
    for (String dir : dirs) {
      write(destination, testPath + "/" + dir, "foo");
      write(baseline, testPath + "/" + dir, "foo");
    }

    ConsistencyFile consistencyFile =
        ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false);

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

  @Test
  public void testGenerateConsistencyFile_fullFilePatchAdditionThrows() throws Exception {
    write(destination, "extra", "extra");

    write(baseline, "existing", "foo");
    write(destination, "existing", "foo");
    Throwable t =
        assertThrows(
            ValidationException.class,
            () -> ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false));
    assertThat(t).hasMessageThat().contains("full-file diffs");
    assertThat(t).hasMessageThat().contains("extra");
  }

  @Test
  public void testGenerateConsistencyFile_fullFilePatchDeletionThrows() throws Exception {
    write(baseline, "existing", "foo");
    write(destination, "existing", "foo");
    write(baseline, "extra", "extra");
    Throwable t =
        assertThrows(
            ValidationException.class,
            () -> ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false));
    assertThat(t).hasMessageThat().contains("full-file diffs");
    assertThat(t).hasMessageThat().contains("extra");
  }

  @Test
  public void testGenerateConsistencyFile_fullFilePatchAdditionThrows_withOrigFileMessage()
      throws Exception {
    write(baseline, "existing", "foo");
    write(destination, "existing", "foo");
    write(destination, "extra.orig", "backup");
    Throwable t =
        assertThrows(
            ValidationException.class,
            () -> ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false));
    assertThat(t).hasMessageThat().contains("'.orig' files may need to be cleaned up");
  }

  @Test
  public void testGenerateConsistencyFile_fullFilePatchAdditionThrows_withDotfileMessage()
      throws Exception {
    write(baseline, "existing", "foo");
    write(destination, "existing", "foo");
    write(destination, ".dotfile", "contents");
    Throwable t =
        assertThrows(
            ValidationException.class,
            () -> ConsistencyFile.generate(baseline, destination, Hashing.sha256(), env, false));
    assertThat(t).hasMessageThat().contains("dot files may not be tracked");
  }
}
