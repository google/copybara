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

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.Change;
import com.google.copybara.ChangeVisitable.VisitResult;
import com.google.copybara.Origin.Reader;
import com.google.copybara.Origin.Reader.ChangesResponse;
import com.google.copybara.Origin.Reader.ChangesResponse.EmptyReason;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.authoring.Authoring.AuthoringMappingMode;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.hg.HgRepository.HgLogEntry;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.Glob;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HgOriginTest {

  private final Authoring authoring = new Authoring(new Author("Copy",
      "copy@bara.com"),
      AuthoringMappingMode.PASS_THRU, ImmutableSet.of());

  private HgOrigin origin;
  private OptionsBuilder options;
  private SkylarkTestExecutor skylark;
  private Glob originFiles;
  private HgRepository repository;
  private Path remotePath;
  private String url;
  private String configRef;

  @Before
  public void setup() throws Exception {
    options = new OptionsBuilder().setOutputRootToTmpDir();
    skylark = new SkylarkTestExecutor(options);
    originFiles = Glob.ALL_FILES;

    remotePath = Files.createTempDirectory("remote");
    url = remotePath.toAbsolutePath().toString();
    configRef = "tip";
    origin = origin();

    repository = new HgRepository(remotePath);
    repository.init();
  }

  private Reader<HgRevision> newReader() {
    return origin.newReader(originFiles, authoring);
  }

  private HgOrigin origin() throws ValidationException {
    return skylark.eval("result",
        String.format("result = hg.origin(\n"
            + "    url = '%s', \n"
            + "    ref = '%s')", url, configRef));
  }

  @Test
  public void testHgOrigin() throws Exception {
    origin = skylark.eval("result",
        "result = hg.origin(\n"
            + "url = 'https://my-server.org/copybara'"
            + ")");

    assertThat(origin.getLabelName())
        .isEqualTo("HgOrigin{"
          + "url = https://my-server.org/copybara"
          + "}");
  }

  @Test
  public void testEmptyUrl() {
    skylark.evalFails("hg.origin(url = '')", "Invalid empty field 'url'");
  }

  @Test
  public void testResolveNonExistentReference() throws Exception {
    String ref = "not_a_ref";
    try {
      origin.resolve(ref);
      fail("Exception should have been thrown");
    } catch (ValidationException expected) {
      assertThat(expected.getMessage()).contains("unknown revision 'not_a_ref'");
    }
  }

  @Test
  public void testResolveNullOrEmptyReference() throws Exception {
    Files.write(remotePath.resolve("bar.txt"), "bara".getBytes(UTF_8));
    repository.hg(remotePath, "add", "bar.txt");
    repository.hg(remotePath, "commit", "-m", "copy");

    ImmutableList<HgLogEntry> commits = repository.log().run();

    assertThat(origin.resolve(null).getGlobalId())
        .isEqualTo(commits.get(0).getGlobalId());

    assertThat(origin.resolve("").getGlobalId())
        .isEqualTo(commits.get(0).getGlobalId());
  }

  @Test
  public void testResolveNullOrEmptyReferenceNoSourceRef() throws Exception {
    origin = skylark.eval("result",
        String.format("result = hg.origin(\n"
            + "    url = '%s', \n"
            + "    ref = '')", url));
    try {
      origin.resolve(null);
      fail("Should have thrown exception");
    } catch (CannotResolveRevisionException expected) {
      assertThat(expected.getMessage()).isEqualTo("No source reference was passed through the"
          + " command line and the default reference is empty");
    }

    try {
      origin.resolve("");
      fail("Should have thrown exception");
    } catch (CannotResolveRevisionException expected) {
      assertThat(expected.getMessage()).isEqualTo("No source reference was passed through the"
          + " command line and the default reference is empty");
    }
  }

  @Test
  public void testCheckout() throws Exception {
    Reader<HgRevision> reader = newReader();

    Path workDir = Files.createTempDirectory("workDir");

    Files.write(remotePath.resolve("foo.txt"), "hello".getBytes(UTF_8));
    Files.write(remotePath.resolve("bar.txt"), "hello".getBytes(UTF_8));

    repository.hg(remotePath, "add", "foo.txt");
    repository.hg(remotePath, "add", "bar.txt");
    repository.hg(remotePath, "commit", "-m", "foo");

    Files.write(remotePath.resolve("foo.txt"), "goodbye".getBytes(UTF_8));
    Files.write(remotePath.resolve("bar.txt"), "other".getBytes(UTF_8));
    repository.hg(remotePath, "add", "foo.txt");
    repository.hg(remotePath, "commit", "-m", "bye");

    repository.hg(remotePath, "rm", "foo.txt");
    repository.hg(remotePath, "commit", "-m", "rm foo");

    ImmutableList<HgLogEntry> commits = repository.log().run();

    reader.checkout(origin.resolve(commits.get(2).getGlobalId()), workDir);

    assertThatPath(workDir)
        .containsFile("foo.txt", "hello")
        .containsFile("bar.txt", "hello")
        .containsFiles(".hg_archival.txt")
        .containsNoMoreFiles();

    reader.checkout(origin.resolve(commits.get(1).getGlobalId()), workDir);

    assertThatPath(workDir)
        .containsFile("foo.txt", "goodbye")
        .containsFile("bar.txt", "other")
        .containsFiles(".hg_archival.txt")
        .containsNoMoreFiles();

    reader.checkout(origin.resolve(commits.get(0).getGlobalId()), workDir);

    assertThatPath(workDir)
        .containsFile("bar.txt", "other")
        .containsFiles(".hg_archival.txt")
        .containsNoMoreFiles();
  }

  @Test
  public void testChanges() throws Exception {
    ZonedDateTime beforeTime = ZonedDateTime.now(ZoneId.systemDefault()).minusSeconds(1);
    String author = "Copy Bara <copy@bara.com>";
    singleFileCommit(author, "one", "foo.txt", "one");
    singleFileCommit(author, "two", "foo.txt", "two");
    Path filePath = singleFileCommit(author, "three", "foo.txt", "three");

    assertThat(Files.readAllBytes(filePath)).isEqualTo("three".getBytes(UTF_8));

    ImmutableList<Change<HgRevision>> changes = newReader().changes(
        origin.resolve("1"), origin.resolve("tip")).getChangesAsListForTest();

    assertThat(changes).hasSize(2);

    assertThat(changes.get(0).getMessage()).isEqualTo("two");
    assertThat(changes.get(1).getMessage()).isEqualTo("three");


    for (Change<HgRevision> change : changes) {
      assertThat(change.getAuthor().getEmail()).isEqualTo("copy@bara.com");
      assertThat(change.getChangeFiles()).hasSize(1);
      assertThat(change.getChangeFiles()).containsExactly("foo.txt");
      assertThat(change.getDateTime()).isAtLeast(beforeTime);
      assertThat(change.getDateTime())
          .isAtMost(ZonedDateTime.now(ZoneId.systemDefault()).plusSeconds(1));
    }
  }

  @Test
  public void testChangesNoFromRef() throws Exception {
    String author = "Copy Bara <copy@bara.com>";
    singleFileCommit(author, "one", "foo.txt", "one");
    singleFileCommit(author, "two", "foo.txt", "two");
    singleFileCommit(author, "three", "foo.txt", "three");

    ImmutableList<Change<HgRevision>> changes = newReader().changes(
        null, origin.resolve("1")).getChangesAsListForTest();

    assertThat(changes).hasSize(2);
    assertThat(changes.get(0).getMessage()).isEqualTo("one");
    assertThat(changes.get(1).getMessage()).isEqualTo("two");
  }

  @Test
  public void testChangesEmptyRepo() throws Exception {
    ChangesResponse<HgRevision> changes = newReader().changes(
        origin.resolve("0"), origin.resolve("tip"));

    assertThat(changes.isEmpty()).isTrue();
    assertThat(changes.getEmptyReason()).isEqualTo(EmptyReason.NO_CHANGES);
  }

  @Test
  public void testChangesUnrelatedRevisions() throws Exception {
    String author = "Copy Bara <copy@bara.com>";
    singleFileCommit(author, "hello", "foo.txt", "hello");

    Path otherDir = Files.createTempDirectory("otherdir");
    HgRepository otherRepo = new HgRepository(otherDir);
    otherRepo.init();
    Path newFile2 = Files.createTempFile(otherDir, "bar", ".txt");
    String fileName2 = newFile2.toString();
    otherRepo.hg(otherDir, "add", fileName2);
    otherRepo.hg(otherDir, "commit", "-m", "foo");

    repository.pullFromRef(otherDir.toString(), "tip");

    ChangesResponse<HgRevision> changes = newReader().changes(
        origin.resolve("0"), origin.resolve("tip"));

    assertThat(changes.isEmpty()).isTrue();
    assertThat(changes.getEmptyReason()).isEqualTo(EmptyReason.UNRELATED_REVISIONS);
  }

  @Test
  public void testChangesToIsAncestor() throws Exception {
    String author = "Copy Bara <copy@bara.com>";
    singleFileCommit(author, "one", "foo.txt", "one");
    singleFileCommit(author, "two", "foo.txt", "two");

    ChangesResponse<HgRevision> changes = newReader().changes(
        origin.resolve("tip"), origin.resolve("0"));

    assertThat(changes.isEmpty()).isTrue();
    assertThat(changes.getEmptyReason()).isEqualTo(EmptyReason.TO_IS_ANCESTOR);
  }

  @Test
  public void testUnknownChanges() throws Exception {
    try {
      ChangesResponse<HgRevision> changes = newReader().changes(
          origin.resolve("4"), origin.resolve("7"));
    }
    catch (ValidationException expected) {
      assertThat(expected.getMessage()).contains("Unknown revision");
    }
  }

  @Test
  public void testChange() throws Exception {
    String author = "Copy Bara <copy@bara.com>";
    singleFileCommit(author, "one", "foo.txt", "one");

    Change<HgRevision> change = newReader().change(origin.resolve("tip"));

    assertThat(change.getAuthor().getEmail()).isEqualTo("copy@bara.com");
    assertThat(change.getAuthor().getName()).isEqualTo("Copy Bara");

    assertThat(change.getChangeFiles()).containsExactly("foo.txt");

    assertThat(change.getMessage()).isEqualTo("one");
  }

  @Test
  public void testUnknownChange() throws Exception {
    try {
      Change<HgRevision> change = newReader().change(origin.resolve("7"));
      fail("Should have thrown exception");
    }
    catch (ValidationException expected) {
      assertThat(expected.getMessage())
          .contains("Unknown revision");
    }
  }

  @Test
  public void testVisit() throws Exception {
    String author = "Copy Bara <copy@bara.com>";
    singleFileCommit(author, "one", "foo.txt", "one");
    singleFileCommit(author, "two", "foo.txt", "two");
    singleFileCommit(author, "three", "foo.txt", "three");
    singleFileCommit(author, "four", "foo.txt", "four");

    List<Change<?>> visited = new ArrayList<>();

    newReader().visitChanges(origin.resolve("tip"),
        input -> {
          visited.add(input);
          return input.firstLineMessage().equals("three")
              ? VisitResult.TERMINATE
              : VisitResult.CONTINUE;
        });

    assertThat(visited).hasSize(2);
    assertThat(visited.get(0).firstLineMessage()).isEqualTo("four");
    assertThat(visited.get(1).firstLineMessage()).isEqualTo("three");
  }

  @Test
  public void testVisitOutsideRoot() throws Exception {
    String author = "Copy Bara <copy@bara.com>";
    singleFileCommit(author, "one", "foo/foo.txt", "one");
    singleFileCommit(author, "two", "bar/foo.txt", "two");
    singleFileCommit(author, "three", "foo/foo.txt", "three");

    originFiles = Glob.createGlob(ImmutableList.of("bar/**"));

    List<Change<?>> visited = new ArrayList<>();
    newReader().visitChanges(origin.resolve("tip"),
        input -> {
          visited.add(input);
          return VisitResult.CONTINUE;
        });
    assertThat(visited).hasSize(1);
    assertThat(visited.get(0).firstLineMessage()).isEqualTo("two");
  }

  private Path singleFileCommit(String author, String commitMessage, String fileName,
      String fileContent) throws Exception {
    Path path = remotePath.resolve(fileName);
    Files.createDirectories(path.getParent());
    Files.write(path, fileContent.getBytes(UTF_8));
    repository.hg(remotePath, "add", fileName);
    repository.hg(remotePath, "--config", "ui.username=" + author, "commit"
        ,"-m", commitMessage);
    return path;
  }
}
