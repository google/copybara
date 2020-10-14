/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.hg;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.ChangeMessage.parseMessage;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Destination.Writer;
import com.google.copybara.DestinationEffect;
import com.google.copybara.DestinationEffect.Type;
import com.google.copybara.TransformResult;
import com.google.copybara.WriterContext;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.hg.HgRepository.HgLogEntry;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.util.CommandRunner;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HgDestinationTest {

  private HgRepository remoteRepo;
  private HgDestination destination;
  private Glob destinationFiles;
  private Path hgDestPath;
  private Path workdir;
  private TestingConsole console;
  private OptionsBuilder options;
  private String url;
  private String fetch;
  private String push;
  private Writer<HgRevision> writer;

  @Before
  public void setup() throws Exception {
    options = new OptionsBuilder()
        .setHomeDir(Files.createTempDirectory("home").toString());
    destinationFiles = Glob.ALL_FILES;

    options.general.setFileSystemForTest(FileSystems.getDefault());
    workdir = options.general.getDirFactory().newTempDir("workdir");

    console = new TestingConsole();

    hgDestPath = Files.createTempDirectory("HgDestinationTest-hgDestRepo");
    url = "file://" + hgDestPath;
    remoteRepo = new HgRepository(hgDestPath, /*verbose*/ false, CommandRunner.DEFAULT_TIMEOUT);
    remoteRepo.init();

    Files.write(hgDestPath.resolve("file.txt"), "first write".getBytes(UTF_8));
    remoteRepo.hg(hgDestPath, "add");
    remoteRepo.hg(hgDestPath, "commit", "-m", "first commit");

    fetch = "tip";
    push = "default";
    destination = HgDestination.newHgDestination(url, fetch, push, options.general, options.hg);
    writer = newWriter();
  }

  @Test
  public void testWrite() throws Exception {
    Files.write(workdir.resolve("file.txt"), "first write".getBytes(UTF_8));
    Files.write(workdir.resolve("test.txt"), "test".getBytes(UTF_8));

    ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(
        Instant.ofEpochMilli(1496333940000L), ZoneId.of("-04:00"));

    DummyRevision originRef = new DummyRevision("origin_ref")
        .withAuthor(new Author("Copy Bara", "copy@bara.com"))
        .withTimestamp(zonedDateTime);
    TransformResult result = TransformResults.of(workdir, originRef);

    ImmutableList<DestinationEffect> destinationResult =
        writer.write(result, destinationFiles, console);
    assertThat(destinationResult).hasSize(1);
    assertThat(destinationResult.get(0).getErrors()).isEmpty();
    assertThat(destinationResult.get(0).getType()).isEqualTo(Type.CREATED);
    assertThat(destinationResult.get(0).getDestinationRef().getType()).isEqualTo("commit");
    assertThat(destinationResult.get(0).getDestinationRef().getId()).matches("[0-9a-f]{40}");

    ImmutableList<HgLogEntry> commits = remoteRepo.log().run();
    assertThat(commits).hasSize(2);
    assertThat(commits.get(0).getDescription()).isEqualTo(""
        + "test summary\n"
        + "\n"
        + "DummyOrigin-RevId: origin_ref");
    assertThat(commits).hasSize(2);
    assertThat(commits.get(0).getZonedDate()).isEqualTo(zonedDateTime);
    assertThat(commits.get(0).getFiles()).hasSize(1);
    assertThat(commits.get(0).getFiles().get(0)).isEqualTo("test.txt");

    assertThat(commits.get(1).getFiles()).hasSize(1);
    assertThat(commits.get(1).getFiles().get(0)).isEqualTo("file.txt");
  }

  @Test
  public void testEmptyChange() throws Exception {
    remoteRepo.archive(workdir.toString());

    DummyRevision originRef = new DummyRevision("origin_ref");
    TransformResult result = TransformResults.of(workdir, originRef);

    RepoException expected =
        assertThrows(RepoException.class, () -> writer.write(result, destinationFiles, console));
    assertThat(expected.getMessage()).contains("Error executing hg");
  }

  @Test
  public void testWriteDeletesAndAddsFiles() throws Exception {
    Files.write(workdir.resolve("delete_me.txt"), "deleted content".getBytes(UTF_8));
    DummyRevision deletedRef = new DummyRevision("delete_ref");
    TransformResult result = TransformResults.of(workdir, deletedRef);
    writer.write(result, destinationFiles, console);

    workdir = options.general.getDirFactory().newTempDir("testWriteDeletesAndAddsFiles-workdir");
    Files.write(workdir.resolve("add_me.txt"), "added content".getBytes(UTF_8));
    createRevisionAndWrite("add_ref");

    remoteRepo.cleanUpdate("tip");

    ImmutableList<HgLogEntry> commits = remoteRepo.log().run();
    assertThat(commits).hasSize(3);

    assertThat(commits.get(0).getFiles()).hasSize(2);
    assertThat(commits.get(0).getFiles().get(0)).isEqualTo("add_me.txt");
    assertThat(commits.get(0).getFiles().get(1)).isEqualTo("delete_me.txt");

    assertThat(commits.get(1).getFiles()).hasSize(2);
    assertThat(commits.get(1).getFiles().get(0)).isEqualTo("delete_me.txt");
    assertThat(commits.get(1).getFiles().get(1)).isEqualTo("file.txt");

    assertThatPath(hgDestPath).containsFile("add_me.txt", "added content");
    assertThatPath(hgDestPath).containsNoFiles("delete_me.txt");
    assertThatPath(hgDestPath).containsNoFiles("file.txt");
  }

  @Test
  public void testWriteModifyFiles() throws Exception {
    Files.write(workdir.resolve("file.txt"), "modified content".getBytes(UTF_8));
    createRevisionAndWrite("modified_ref");

    remoteRepo.cleanUpdate("tip");

    ImmutableList<HgLogEntry> commits = remoteRepo.log().run();
    assertThat(commits).hasSize(2);
    assertThatPath(hgDestPath).containsFile("file.txt", "modified content");
  }

  @Test
  public void testWriteModifyExcluded() throws Exception {
    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("file.txt"));
    writer = newWriter();

    Files.write(workdir.resolve("file.txt"), "modified content".getBytes(UTF_8));
    Files.write(workdir.resolve("other.txt"), "other content".getBytes(UTF_8));
    createRevisionAndWrite("origin_ref");

    remoteRepo.cleanUpdate("tip");

    ImmutableList<HgLogEntry> commits = remoteRepo.log().run();
    assertThat(commits).hasSize(2);
    assertThatPath(hgDestPath).containsFile("file.txt", "first write");
    assertThatPath(hgDestPath).containsFile("other.txt", "other content");
  }

  @Test
  public void testWriteAddExcluded() throws Exception {
    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("excluded.txt"));
    writer = newWriter();

    Files.write(workdir.resolve("file.txt"), "modified content".getBytes(UTF_8));
    Files.write(workdir.resolve("excluded.txt"), "excluded content".getBytes(UTF_8));
    createRevisionAndWrite("origin_ref");

    remoteRepo.cleanUpdate("tip");

    ImmutableList<HgLogEntry> commits = remoteRepo.log().run();
    assertThat(commits).hasSize(2);
    assertThatPath(hgDestPath).containsFile("file.txt", "modified content");

    // File that was excluded is still written because it didn't originally exist in the destination
    assertThatPath(hgDestPath).containsFile("excluded.txt", "excluded content");
  }

  @Test
  public void testWriteDeleteExcluded() throws Exception {
    Files.write(hgDestPath.resolve("excluded.txt"), "content".getBytes(StandardCharsets.UTF_8));
    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("excluded.txt"));
    writer = newWriter();

    Files.write(workdir.resolve("file.txt"), "file".getBytes(UTF_8));
    createRevisionAndWrite("origin_ref");

    remoteRepo.cleanUpdate("tip");

    ImmutableList<HgLogEntry> commits = remoteRepo.log().run();
    assertThat(commits).hasSize(2);
    assertThatPath(hgDestPath).containsFile("file.txt", "file");
    assertThatPath(hgDestPath).containsFile("excluded.txt", "content");
  }

  @Test
  public void testPreviousImportReference() throws Exception {
    Path file = workdir.resolve("test.txt");
    Files.write(file, "first write".getBytes(StandardCharsets.UTF_8));
    writer = newWriter();
    assertThat(writer.getDestinationStatus(destinationFiles, DummyOrigin.LABEL_NAME)).isNull();
    createRevisionAndWrite("first_commit");
    assertCommitHasOrigin(getLastCommit(fetch), "first_commit");

    Files.write(file, "second write".getBytes(StandardCharsets.UTF_8));
    assertThat(writer.getDestinationStatus(destinationFiles, DummyOrigin.LABEL_NAME).getBaseline())
        .isEqualTo("first_commit");
    createRevisionAndWrite("second_commit");
    assertCommitHasOrigin(getLastCommit(fetch), "second_commit");

    Path otherFile = workdir.resolve("other.txt");
    Files.write(otherFile, "third write".getBytes(StandardCharsets.UTF_8));
    assertThat(writer.getDestinationStatus(destinationFiles, DummyOrigin.LABEL_NAME).getBaseline())
        .isEqualTo("second_commit");
    createRevisionAndWrite("third_commit");
    assertCommitHasOrigin(getLastCommit(fetch), "third_commit");
  }

  @Test
  public void testPreviousImportReference_nonCopybaraCommitsSinceLastMigrate() throws Exception {
    Files.write(workdir.resolve("file.txt"), "test write".getBytes(StandardCharsets.UTF_8));
    writer = newWriter();
    createRevisionAndWrite("first_commit");

    // update the remote repo workdir to have new changes
    remoteRepo.cleanUpdate(push);

    Path scratchTree = Files.createTempDirectory(hgDestPath,"HgDestinationTest-scratchTree");
    for (int i = 0; i < 20; i++) {
      Path excludedFile = Files.write(scratchTree.resolve("excluded.dat"), new byte[] {(byte) i});
      remoteRepo.hg(hgDestPath, "add", excludedFile.toString());
      remoteRepo.hg(hgDestPath, "commit", "-m", "excluded #" + i);
    }

    assertThat(writer.getDestinationStatus(destinationFiles, DummyOrigin.LABEL_NAME).getBaseline())
        .isEqualTo("first_commit");
  }

  private void createRevisionAndWrite(String referenceName)
      throws RepoException, ValidationException, IOException {
    DummyRevision originRef = new DummyRevision(referenceName);
    TransformResult result = TransformResults.of(workdir, originRef);
    writer.write(result, destinationFiles, console);
  }

  private void assertCommitHasOrigin(HgLogEntry commit, String originRef) {
    assertThat(parseMessage(commit.getDescription())
        .labelsAsMultimap()).containsEntry(DummyOrigin.LABEL_NAME, originRef);
  }

  private HgLogEntry getLastCommit(String ref) throws RepoException, ValidationException {
    return getOnlyElement(remoteRepo.log().withReferenceExpression(ref).withLimit(1).run());
  }

  private Writer<HgRevision> newWriter() {
    WriterContext writerContext = new WriterContext("", "Test", false, new HgRevision("test"),
        Glob.ALL_FILES.roots());
    return destination.newWriter(writerContext);
  }
}
