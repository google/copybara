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
import java.nio.file.PathMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SinglePatchTest {

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
  public void testGenerateSinglePatch() throws IOException, InsideGitDirException {
    SinglePatch singlePatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);
    assertThat(singlePatch).isNotNull();
  }

  @Test
  public void testGenerateSinglePatch_hashesFile() throws IOException, InsideGitDirException {
    String testPath = "test/foo";
    String testContents = "hello";
    byte[] testBytes = testContents.getBytes(UTF_8);

    Files.createDirectories(destination.resolve(testPath).getParent());
    Files.write(destination.resolve(testPath), testBytes);

    SinglePatch singlePatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);

    assertThat(singlePatch.getFileHashes()).containsExactly(
        testPath, helloHash
    );
  }

  @Test
  public void testGenerateSinglePatch_diffsDirectories() throws IOException, InsideGitDirException {
    String testPath = "test/foo";
    String testDestinationContents = "hello\n";
    write(destination, testPath, testDestinationContents);

    String testBaselineContents = "baseline\n";
    write(baseline, testPath, testBaselineContents);

    SinglePatch singlePatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);

    assertThat(new String(singlePatch.getDiffContent(), UTF_8)).isEqualTo(""
        + "diff --git a/baseline/test/foo b/destination/test/foo\n"
        + "index 180b47c..ce01362 100644\n"
        + "--- a/baseline/test/foo\n"
        + "+++ b/destination/test/foo\n"
        + "@@ -1 +1 @@\n"
        + "-baseline\n"
        + "+hello\n");
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

    SinglePatch singlePatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);

    assertThat(new String(singlePatch.getDiffContent(), UTF_8)).isEqualTo(""
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
  public void testGenerateSinglePatch_diffsDirectories_emptyDiff()
      throws IOException, InsideGitDirException {
    String testPath = "test/foo";
    String testDestinationContents = "hello";
    write(destination, testPath, testDestinationContents);

    String testBaselineContents = "hello";
    write(baseline, testPath, testBaselineContents);

    SinglePatch singlePatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);

    assertThat(new String(singlePatch.getDiffContent(), UTF_8)).isEqualTo("");
  }

  public void write(Path folder, String relativePath, String contents) throws IOException {
    byte[] bytes = contents.getBytes(UTF_8);
    Files.createDirectories(folder.resolve(relativePath).getParent());
    Files.write(folder.resolve(relativePath), bytes);
  }

  @Test
  public void testPatchContainsHeader() throws IOException, InsideGitDirException {
    SinglePatch singlePatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);
    byte[] singlePatchBytes = singlePatch.toBytes();

    assertThat(new String(singlePatchBytes, UTF_8)).contains("This file is generated by Copybara");
  }

  @Test
  public void testSerializeEmptyPatch() throws Exception {
    SinglePatch emptyPatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);
    byte[] emptyPatchBytes = emptyPatch.toBytes();
    SinglePatch deserializedPatch = SinglePatch.fromBytes(emptyPatchBytes, Hashing.sha256());

    assertThat(deserializedPatch).isEqualTo(emptyPatch);
  }

  @Test
  public void testDeserializedObjectIsEquivalent_singleFile() throws Exception {
    write(destination, "test/path", "123457testcontents");
    write(baseline, "test/path", "123457testcontents");
    SinglePatch testSinglePatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);
    byte[] testPatchBytes = testSinglePatch.toBytes();

    SinglePatch deserializedPatch = SinglePatch.fromBytes(testPatchBytes, Hashing.sha256());

    assertThat(deserializedPatch).isEqualTo(testSinglePatch);
  }

  @Test
  public void testDeserializedObjectIsEquivalent_glob() throws Exception {
    write(destination, "test/path", "123457testcontents");
    write(baseline, "test/path", "123457testcontents");

    Glob glob = Glob.createGlob(ImmutableList.of("test/**"), ImmutableList.of("exclude/**"));
    Glob glob2 = Glob.createGlob(ImmutableList.of("test2/**"));
    Glob glob3 = Glob.createGlob(ImmutableList.of("test3/**"), ImmutableList.of("internal/**"));
    Glob glob4 = Glob.createGlob(ImmutableList.of("test4/**"));

    Glob destinationFiles = Glob.union(glob, glob2);
    destinationFiles = Glob.union(destinationFiles, glob3);
    destinationFiles = Glob.difference(destinationFiles, glob4);

    SinglePatch testSinglePatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), destinationFiles);
    byte[] testPatchBytes = testSinglePatch.toBytes();

    SinglePatch deserializedPatch = SinglePatch.fromBytes(testPatchBytes, Hashing.sha256());

    assertThat(deserializedPatch).isEqualTo(testSinglePatch);
    Glob parsedDestinationFiles = deserializedPatch.getDestinationFiles();
    PathMatcher matcher = parsedDestinationFiles.relativeTo(Path.of("/"));

    assertThat(matcher.matches(Path.of("/test/foo"))).isTrue();
    assertThat(matcher.matches(Path.of("/exclude/foo"))).isFalse();
    assertThat(matcher.matches(Path.of("/test2/foo"))).isTrue();
    assertThat(matcher.matches(Path.of("/test3/foo"))).isTrue();
    assertThat(matcher.matches(Path.of("/internal/foo"))).isFalse();
    assertThat(matcher.matches(Path.of("/test4/foo"))).isFalse();
  }

  @Test
  public void testDeserializedObjectNotEquivalent_addedFile() throws Exception {
    write(destination, "test/path", "123457testcontents");
    write(baseline, "test/path", "123457testcontents");
    SinglePatch singlePatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);

    // add new file to destination
    write(destination, "test/new", "asdf");

    SinglePatch differentPatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);
    byte[] differentPatchBytes = differentPatch.toBytes();

    SinglePatch deserializedPatch = SinglePatch.fromBytes(differentPatchBytes, Hashing.sha256());

    assertThat(deserializedPatch).isNotEqualTo(singlePatch);
  }

  @Test
  public void testDeserializedObjectNotEquivalent_changedFile() throws Exception {
    write(destination, "test/path", "123457testcontents");
    write(baseline, "test/path", "123457testcontents");
    SinglePatch singlePatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);

    // update file in destination
    write(destination, "test/path", "newcontents");

    SinglePatch differentPatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);
    byte[] differentPatchBytes = differentPatch.toBytes();

    SinglePatch deserializedPatch = SinglePatch.fromBytes(differentPatchBytes, Hashing.sha256());

    assertThat(deserializedPatch).isNotEqualTo(singlePatch);
  }

  @Test
  public void testDeserializedObjectNotEquivalent_glob() throws Exception {
    write(destination, "test/path", "123457testcontents");
    write(baseline, "test/path", "123457testcontents");
    SinglePatch singlePatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);

    SinglePatch singlePatchWithGlob = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.createGlob(ImmutableList.of("foo")));

    SinglePatch deserializedPatch = SinglePatch.fromBytes(singlePatch.toBytes(), Hashing.sha256());
    assertThat(deserializedPatch).isNotEqualTo(singlePatchWithGlob);
  }

  @Test
  public void testFromBytes_invalidPathValueThrows() throws Exception {
    write(baseline, "foo", "hello");
    write(destination, "foo", "hello");
    SinglePatch singlePatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);

    // Manually make an edit to the byte format
    // There should be an entry for "foo" with a corresponding hash.
    // Use a null byte to create an invalid path to force an error when parsing.
    String singlePatchContent = new String(singlePatch.toBytes(), UTF_8);

    String newSinglePatchContent = singlePatchContent.replace("foo", "fo\0o");

    Throwable throwable = assertThrows(ValidationException.class,
        () -> SinglePatch.fromBytes(newSinglePatchContent.getBytes(UTF_8), Hashing.sha256()));
    assertThat(throwable).hasMessageThat().contains("path value is invalid");
  }

  @Test
  public void testFromBytes_invalidHashValueThrows_invalidChar() throws Exception {
    write(baseline, "foo", "hello");
    write(destination, "foo", "hello");
    SinglePatch singlePatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);

    String singlePatchContent = new String(singlePatch.toBytes(), UTF_8);

    // Manually make an edit to the byte format
    // There should be an entry for "foo" with a corresponding hash.
    // Use non-hexadecimal chars to make an invalid hash.
    String newSinglePatchContent = singlePatchContent.replace(helloHash, "gg");

    Throwable throwable = assertThrows(ValidationException.class,
        () -> SinglePatch.fromBytes(newSinglePatchContent.getBytes(UTF_8), Hashing.sha256()));
    assertThat(throwable).hasMessageThat().contains("hash value is invalid");
  }

  @Test
  public void testFromBytes_invalidHashValueThrows_wrongHashLength() throws Exception {
    write(baseline, "foo", "hello");
    write(destination, "foo", "hello");
    SinglePatch singlePatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);

    String singlePatchContent = new String(singlePatch.toBytes(), UTF_8);

    // Manually make an edit to the byte format
    // There should be an entry for "foo" with a corresponding hash.
    // Add extra hex chars to make an invalid hash for the given hash function.
    String newSinglePatchContent = singlePatchContent.replace(helloHash, helloHash + "ff");

    Throwable throwable = assertThrows(ValidationException.class,
        () -> SinglePatch.fromBytes(newSinglePatchContent.getBytes(UTF_8), Hashing.sha256()));
    assertThat(throwable).hasMessageThat().contains("hash value has incorrect number of hex chars");
  }
  
  @Test
  public void testReverseSinglePatch_appliesDiff() throws Exception {
    write(baseline, "foonodiff", "foo");
    write(destination, "foonodiff", "foo");

    write(baseline, "bardiff", "bar");
    write(destination, "bardiff", "newbar");

    // verify destination before reversing contains the new content
    assertThatPath(destination).containsFile("foonodiff", "foo");
    assertThatPath(destination).containsFile("bardiff", "newbar");

    SinglePatch singlePatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);
    singlePatch.reverseSinglePatch(destination, System.getenv());

    // destination after reversing should contain the old content
    assertThatPath(destination).containsFile("foonodiff", "foo");
    assertThatPath(destination).containsFile("bardiff", "bar");
  }

  @Test
  public void testReverseSinglePatch_deletesFile() throws Exception {
    // Destination has a file that the baseline doesn't.
    // Reversing the patch should delete the file.
    write(baseline, "foonodiff", "foo");
    write(destination, "foonodiff", "foo");

    write(destination, "baradded", "bar");

    // verify destination before reversing contains the new content
    assertThatPath(destination).containsFile("foonodiff", "foo");
    assertThatPath(destination).containsFile("baradded", "bar");

    SinglePatch singlePatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);
    singlePatch.reverseSinglePatch(destination, System.getenv());

    // destination after reversing should contain the old content
    assertThatPath(destination).containsFile("foonodiff", "foo");
    assertThatPath(destination).containsNoFiles("baradded");
  }

  @Test
  public void testReverseSinglePatch_addsFile() throws Exception {
    // Baseline has a file that the destination doesn't.
    // Reversing the patch should re-add the file.
    write(baseline, "foonodiff", "foo");
    write(destination, "foonodiff", "foo");

    write(baseline, "barremoved", "bar");

    // verify destination before reversing contains the new content
    assertThatPath(destination).containsFile("foonodiff", "foo");
    assertThatPath(destination).containsNoFiles("barremoved");

    SinglePatch singlePatch = SinglePatch.generateSinglePatch(baseline, destination,
        Hashing.sha256(), System.getenv(), Glob.ALL_FILES);
    singlePatch.reverseSinglePatch(destination, System.getenv());

    // destination after reversing should contain the old content
    assertThatPath(destination).containsFile("foonodiff", "foo");
    assertThatPath(destination).containsFile("barremoved", "bar");
  }
}
