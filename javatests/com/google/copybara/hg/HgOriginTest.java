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
import static com.google.copybara.Origin.Reader.ChangesResponse.EmptyReason.NO_CHANGES;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
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
import com.google.copybara.exception.ValidationException;
import com.google.copybara.hg.HgRepository.HgLogEntry;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.CommandRunner;
import com.google.copybara.util.Glob;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
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

  private final Authoring authoring =
      new Authoring(
          new Author("Copy", "copy@bara.com"), AuthoringMappingMode.PASS_THRU, ImmutableSet.of());

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

    repository = new HgRepository(remotePath, /*verbose*/ false, CommandRunner.DEFAULT_TIMEOUT);
    repository.init();
  }

  private Reader<HgRevision> newReader() {
    return origin.newReader(originFiles, authoring);
  }

  private HgOrigin origin() throws ValidationException {
    return skylark.eval(
        "result",
        String.format(
            "result = hg.origin(\n" + "    url = '%s', \n" + "    ref = '%s')", url, configRef));
  }

  @Test
  public void testHgOrigin() throws Exception {
    origin =
        skylark.eval(
            "result", "result = hg.origin(\n" + "url = 'https://my-server.org/copybara'" + ")");

    assertThat(origin.toString())
        .isEqualTo("HgOrigin{" + "url = https://my-server.org/copybara, ref = default}");
  }

  @Test
  public void testEmptyUrl() {
    skylark.evalFails("hg.origin(url = '')", "Invalid empty field 'url'");
  }

  @Test
  public void testHgOriginWithHook() throws Exception {
    Files.write(remotePath.resolve("foo.txt"), "hello".getBytes(UTF_8));
    Files.write(remotePath.resolve("bar.txt"), "hello".getBytes(UTF_8));

    repository.hg(remotePath, "add", "foo.txt");
    repository.hg(remotePath, "add", "bar.txt");
    repository.hg(remotePath, "commit", "-m", "foo");

    Path hook = Files.createTempFile("script", "script");
    Files.write(hook, "touch hook.txt".getBytes(UTF_8));

    Files.setPosixFilePermissions(
        hook,
        ImmutableSet.<PosixFilePermission>builder()
            .addAll(Files.getPosixFilePermissions(hook))
            .add(PosixFilePermission.OWNER_EXECUTE)
            .build());

    options.hgOrigin.originCheckoutHook = hook.toString();
    origin = origin();

    Path checkoutDir = Files.createTempDirectory("checkout");
    newReader().checkout(origin.resolve("tip"), checkoutDir);
    assertThatPath(checkoutDir).containsFile("hook.txt", "");
  }

  @Test
  public void testResolveNonExistentReference() throws Exception {
    String ref = "not_a_ref";
    ValidationException expected =
        assertThrows(ValidationException.class, () -> origin.resolve(ref));
    assertThat(expected.getMessage()).contains("unknown revision 'not_a_ref'");
  }

  @Test
  public void testResolveNullOrEmptyReference() throws Exception {
    Files.write(remotePath.resolve("bar.txt"), "bara".getBytes(UTF_8));
    repository.hg(remotePath, "add", "bar.txt");
    repository.hg(remotePath, "commit", "-m", "copy");

    ImmutableList<HgLogEntry> commits = repository.log().run();

    assertThat(origin.resolve(null).getGlobalId()).isEqualTo(commits.get(0).getGlobalId());

    assertThat(origin.resolve("").getGlobalId()).isEqualTo(commits.get(0).getGlobalId());
  }

  @Test
  public void testResolveNullOrEmptyReferenceNoSourceRef() throws Exception {
    origin =
        skylark.eval(
            "result",
            String.format("result = hg.origin(\n" + "    url = '%s', \n" + "    ref = '')", url));
    CannotResolveRevisionException expected1 =
        assertThrows(CannotResolveRevisionException.class, () -> origin.resolve(null));
    assertThat(expected1.getMessage())
        .isEqualTo(
            "No source reference was passed through the"
                + " command line and the default reference is empty");
    CannotResolveRevisionException expected2 =
        assertThrows(CannotResolveRevisionException.class, () -> origin.resolve(""));
    assertThat(expected2.getMessage())
        .isEqualTo(
            "No source reference was passed through the"
                + " command line and the default reference is empty");
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
  public void testTagCheckout() throws Exception {
    Reader<HgRevision> reader = newReader();

    Path workDir = Files.createTempDirectory("workDir");

    Files.write(remotePath.resolve("foo.txt"), "hello".getBytes(UTF_8));

    repository.hg(remotePath, "add", "foo.txt");
    repository.hg(remotePath, "commit", "-m", "foo");

    HgRevision tip = repository.identify("tip");

    Files.write(remotePath.resolve("bar.txt"), "hello".getBytes(UTF_8));
    repository.hg(remotePath, "add", "bar.txt");
    repository.hg(remotePath, "commit", "-m", "bar");

    repository.hg(remotePath, "tag", "-r", tip.asString(), "mytag");
    configRef = "mytag";
    origin = origin();

    reader.checkout(origin.resolve("mytag"), workDir);

    assertThatPath(workDir)
        .containsFile("foo.txt", "hello")
        .containsFiles(".hg_archival.txt")
        .containsNoMoreFiles();
  }

  @Test
  public void testChanges() throws Exception {
    ZonedDateTime beforeTime = ZonedDateTime.now(ZoneId.systemDefault()).minusSeconds(1);
    String author = "Copy Bara <copy@bara.com>";
    singleFileCommit(author, "zero", "foo.txt", "zero");
    singleFileCommit(author, "one", "foo.txt", "one");
    Path filePath = singleFileCommit(author, "two", "foo.txt", "two");

    assertThat(Files.readAllBytes(filePath)).isEqualTo("two".getBytes(UTF_8));

    ImmutableList<Change<HgRevision>> changes =
        newReader().changes(origin.resolve("0"), origin.resolve("tip")).getChanges();

    assertThat(changes).hasSize(2);

    assertThat(changes.get(0).getMessage()).isEqualTo("one");
    assertThat(changes.get(1).getMessage()).isEqualTo("two");

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
  public void testFirstImportFromEmptyRepo() throws Exception {
    ChangesResponse<HgRevision> changes =
        newReader().changes(/*fromRef=*/ null, origin.resolve("tip"));
    assertThat(changes.isEmpty()).isTrue();
    assertThat(changes.getEmptyReason()).isEqualTo(NO_CHANGES);
  }

  @Test
  public void testChangesNoFromRef() throws Exception {
    String author = "Copy Bara <copy@bara.com>";
    singleFileCommit(author, "zero", "foo.txt", "zero");
    singleFileCommit(author, "one", "foo.txt", "one");
    singleFileCommit(author, "two", "foo.txt", "two");

    ImmutableList<Change<HgRevision>> changes =
        newReader().changes(null, origin.resolve("1")).getChanges();

    assertThat(changes).hasSize(2);
    assertThat(changes.get(0).getMessage()).isEqualTo("zero");
    assertThat(changes.get(1).getMessage()).isEqualTo("one");
  }

  @Test
  public void testChangesEmptyRepo() throws Exception {
    ChangesResponse<HgRevision> changes =
        newReader().changes(origin.resolve("0"), origin.resolve("tip"));

    assertThat(changes.isEmpty()).isTrue();
    assertThat(changes.getEmptyReason()).isEqualTo(NO_CHANGES);
  }

  @Test
  public void testChangesUnrelatedRevisions() throws Exception {
    String author = "Copy Bara <copy@bara.com>";
    singleFileCommit(author, "hello", "foo.txt", "hello");

    Path otherDir = Files.createTempDirectory("otherdir");
    HgRepository otherRepo = new HgRepository(otherDir, /*verbose*/ false,
        CommandRunner.DEFAULT_TIMEOUT);
    otherRepo.init();
    Path newFile2 = Files.createTempFile(otherDir, "bar", ".txt");
    String fileName2 = newFile2.toString();
    otherRepo.hg(otherDir, "add", fileName2);
    otherRepo.hg(otherDir, "commit", "-m", "foo");

    repository.pullFromRef(otherDir.toString(), "tip");

    ChangesResponse<HgRevision> changes =
        newReader().changes(origin.resolve("0"), origin.resolve("tip"));

    assertThat(changes.isEmpty()).isTrue();
    assertThat(changes.getEmptyReason()).isEqualTo(EmptyReason.UNRELATED_REVISIONS);
  }

  @Test
  public void testChangesToIsAncestor() throws Exception {
    String author = "Copy Bara <copy@bara.com>";
    singleFileCommit(author, "zero", "foo.txt", "zero");
    singleFileCommit(author, "one", "foo.txt", "one");

    ChangesResponse<HgRevision> changes =
        newReader().changes(origin.resolve("tip"), origin.resolve("0"));

    assertThat(changes.isEmpty()).isTrue();
    assertThat(changes.getEmptyReason()).isEqualTo(EmptyReason.TO_IS_ANCESTOR);
  }

  @Test
  public void testUnknownChanges() throws Exception {
    try {
      newReader().changes(origin.resolve("4"), origin.resolve("7"));
    } catch (ValidationException expected) {
      assertThat(expected.getMessage()).contains("Unknown revision");
    }
  }

  @Test
  public void testChange() throws Exception {
    String author = "Copy Bara <copy@bara.com>";
    singleFileCommit(author, "zero", "foo.txt", "zero");

    Change<HgRevision> change = newReader().change(origin.resolve("tip"));

    assertThat(change.getAuthor().getEmail()).isEqualTo("copy@bara.com");
    assertThat(change.getAuthor().getName()).isEqualTo("Copy Bara");

    assertThat(change.getChangeFiles()).containsExactly("foo.txt");

    assertThat(change.getMessage()).isEqualTo("zero");
  }

  @Test
  public void testUnknownChange() throws Exception {
    ValidationException expected =
        assertThrows(ValidationException.class, () -> newReader().change(origin.resolve("7")));
    assertThat(expected.getMessage()).contains("Unknown revision");
  }

  @Test
  public void testVisit() throws Exception {
    String author = "Copy Bara <copy@bara.com>";
    singleFileCommit(author, "zero", "foo.txt", "zero");
    singleFileCommit(author, "one", "foo.txt", "one");
    singleFileCommit(author, "two", "foo.txt", "two");
    singleFileCommit(author, "three", "foo.txt", "three");

    List<Change<?>> visited = new ArrayList<>();

    newReader()
        .visitChanges(
            origin.resolve("tip"),
            input -> {
              visited.add(input);
              return input.firstLineMessage().equals("two")
                  ? VisitResult.TERMINATE
                  : VisitResult.CONTINUE;
            });

    assertThat(visited).hasSize(2);
    assertThat(visited.get(0).firstLineMessage()).isEqualTo("three");
    assertThat(visited.get(1).firstLineMessage()).isEqualTo("two");
  }

  @Test
  public void testVisitOutsideRoot() throws Exception {
    String author = "Copy Bara <copy@bara.com>";
    singleFileCommit(author, "zero", "foo/foo.txt", "zero");
    singleFileCommit(author, "one", "bar/foo.txt", "one");
    singleFileCommit(author, "two", "foo/foo.txt", "two");

    originFiles = Glob.createGlob(ImmutableList.of("bar/**"));

    List<Change<?>> visited = new ArrayList<>();
    newReader()
        .visitChanges(
            origin.resolve("tip"),
            input -> {
              visited.add(input);
              return VisitResult.CONTINUE;
            });
    assertThat(visited).hasSize(1);
    assertThat(visited.get(0).firstLineMessage()).isEqualTo("one");
  }

  @Test
  public void testDescribe() {
    ImmutableMultimap<String, String> actual = origin.describe(Glob.ALL_FILES);
    assertThat(actual).hasSize(3);
    assertThat(actual.get("type")).containsExactly("hg.origin");
    assertThat(actual.get("url")).hasSize(1);
    assertThat(actual.get("ref")).containsExactly("tip");
  }

  private Path singleFileCommit(
      String author, String commitMessage, String fileName, String fileContent) throws Exception {
    Path path = remotePath.resolve(fileName);
    Files.createDirectories(path.getParent());
    Files.write(path, fileContent.getBytes(UTF_8));
    repository.hg(remotePath, "add", fileName);
    repository.hg(remotePath, "--config", "ui.username=" + author, "commit", "-m", commitMessage);
    return path;
  }
}
