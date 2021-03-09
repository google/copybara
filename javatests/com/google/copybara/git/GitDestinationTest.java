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

package com.google.copybara.git;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.ChangeMessage.parseMessage;
import static com.google.copybara.git.testing.GitTesting.assertThatCheckout;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static com.google.copybara.testing.git.GitTestUtil.writeFile;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.truth.Truth;
import com.google.copybara.Change;
import com.google.copybara.ChangeMessage;
import com.google.copybara.ChangeVisitable.VisitResult;
import com.google.copybara.Changes;
import com.google.copybara.Destination.DestinationStatus;
import com.google.copybara.Destination.Writer;
import com.google.copybara.DestinationEffect;
import com.google.copybara.DestinationEffect.Type;
import com.google.copybara.Origin.Baseline;
import com.google.copybara.TransformResult;
import com.google.copybara.WriterContext;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitCredential.UserPassword;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.git.testing.GitTesting;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.CommandOutput;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitDestinationTest {

  private String url;
  private String fetch;
  private String push;
  private boolean force;

  private Path repoGitDir;
  private OptionsBuilder options;
  private TestingConsole console;
  private Glob destinationFiles;
  private SkylarkTestExecutor skylark;
  private String tagName;
  private String tagMsg;
  private String partialClone;
  private String primaryBranch;
  private String primaryBranchMigration = "False";

  private Path workdir;

  @Before
  public void setup() throws Exception {
    repoGitDir = Files.createTempDirectory("GitDestinationTest-repoGitDir");
    workdir = Files.createTempDirectory("workdir");
    console = new TestingConsole();
    options = getOptionsBuilder(console);
    git("init", "--bare", repoGitDir.toString());
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";
    destinationFiles = Glob.createGlob(ImmutableList.of("**"));
    partialClone = "False";

    url = "file://" + repoGitDir;
    skylark = new SkylarkTestExecutor(options);
    force = false;
    tagName = "test_v1";
    tagMsg = "foo_tag";
    primaryBranch = repo().getPrimaryBranch();
  }

  public OptionsBuilder getOptionsBuilder(
      TestingConsole console) throws IOException {
    return new OptionsBuilder()
        .setConsole(this.console)
        .setOutputRootToTmpDir();
  }

  private GitRepository repo() {
    return repoForPath(repoGitDir);
  }

  private GitRepository repoForPath(Path path) {
    return GitRepository.newBareRepo(path, getEnv(), /*verbose=*/true, DEFAULT_TIMEOUT,
        /*noVerify=*/false);
  }

  public GitEnvironment getEnv() {
    Map<String, String> joinedEnv = Maps.newHashMap(options.general.getEnvironment());
    joinedEnv.putAll(getGitEnv().getEnvironment());
    return new GitEnvironment(joinedEnv);
  }

  private String git(String... argv) throws RepoException {
    return repo()
        .git(repoGitDir, argv)
        .getStdout();
  }

  @Test
  public void errorIfUrlMissing() {
    skylark.evalFails(
        "" + "git.destination(\n"
            + String.format("    fetch = '%s',\n", primaryBranch)
            + String.format("    push = '%s',\n", primaryBranch) + ")",
        "missing 1 required positional argument: url");
  }

  @Test
  public void testHttpUrl() throws Exception {
    GitDestination d = skylark.eval("r", "r = git.destination("
        + "    url = 'http://github.com/foo', \n"
        + ")");
    assertThat(d.describe(Glob.ALL_FILES).get("url")).contains("https://github.com/foo");
  }

  @Test
  public void testTagNameAndTagMsg() throws Exception {
    GitDestination d = skylark.eval("r", "r = git.destination("
        + "    url = 'http://github.com/foo', \n"
        + "    tag_name = 'foo', \n"
        + "    tag_msg = 'bar', \n"
        + ")");
    assertThat(d.describe(Glob.ALL_FILES).get("tagName")).contains("foo");
    assertThat(d.describe(Glob.ALL_FILES).get("tagMsg")).contains("bar");
  }

  @Test
  public void defaultPushBranch() throws ValidationException {
    GitDestination d = skylark.eval("result", "result = git.destination('file:///foo')");
    assertThat(d.getPush()).isEqualTo("master");
    assertThat(d.getFetch()).isEqualTo("master");
  }

  @Test
  public void defaultPushBranch_auto() throws Exception {
    GitRepository repo = repo().withWorkTree(workdir);
    Files.write(workdir.resolve("file"), "".getBytes(UTF_8));
    repo.add().all().run();
    repo.simpleCommand("commit", "-m", "first commit");
    GitDestination d = skylark.eval("result",
        String.format("result = git.destination('file://%s', primary_branch_migration=True)",
            repo.getGitDir()));
    assertThat(d.getPush()).isEqualTo(primaryBranch);
    assertThat(d.getFetch()).isEqualTo(primaryBranch);
  }

  private GitDestination destinationFirstCommit()
      throws ValidationException {
    options.setForce(true);
    return evalDestination();
  }

  private GitDestination destination() throws ValidationException {
    options.setForce(force);
    return evalDestination();
  }

  private GitDestination evalDestination()
      throws ValidationException {
    return skylark.eval("result",
        String.format("result = git.destination(\n"
            + "    url = '%s',\n"
            + "    fetch = '%s',\n"
            + "    push = '%s',\n"
            + "    partial_fetch = %s,\n"
            + "    primary_branch_migration = %s,\n"
            + ")", url, fetch, push, partialClone, primaryBranchMigration));
  }

  private GitDestination evalDestinationWithTag(String tagMsg)
      throws ValidationException {
    return tagMsg == null
        ? skylark.eval("result",
        String.format("result = git.destination(\n"
            + "    url = '%s',\n"
            + "    fetch = '%s',\n"
            + "    push = '%s',\n"
            + "    tag_name = '%s',\n"
            + ")", url, fetch, push, tagName))
        : skylark.eval("result",
            String.format("result = git.destination(\n"
                + "    url = '%s',\n"
                + "    fetch = '%s',\n"
                + "    push = '%s',\n"
                + "    tag_name = '%s',\n"
                + "    tag_msg = '%s',\n"
                + ")", url, fetch, push, tagName, tagMsg));
  }

  private void assertFilesInDir(int expected, String ref, String path) throws Exception {
    String lsResult = git("--git-dir", repoGitDir.toString(), "ls-tree", ref, path);
    assertThat(lsResult.split("\n")).hasLength(expected);
  }

  private void assertCommitCount(int expected, String ref) throws Exception {
    assertThat(repo().log(ref).run()).hasSize(expected);
  }

  private void assertCommitHasOrigin(String branch, String originRef) throws RepoException {
    assertThat(parseMessage(lastCommit(branch).getBody())
        .labelsAsMultimap()).containsEntry(DummyOrigin.LABEL_NAME, originRef);
  }

  private void assertCommitHasAuthor(String branch, Author author) throws RepoException {
    assertThat(lastCommit(branch).getAuthor()).isEqualTo(author);
  }

  private GitLogEntry lastCommit(String ref) throws RepoException {
    return getOnlyElement(repo().log(ref).withLimit(1).run());
  }

  private static ZonedDateTime timeFromEpoch(long time) {
    return ZonedDateTime.ofInstant(Instant.ofEpochSecond(time), ZoneId.of("-07:00"));
  }

  private void process(Writer<GitRevision> writer, DummyRevision ref)
      throws ValidationException, RepoException, IOException {
    process(writer, destinationFiles, ref);
  }

  private void process(Writer<GitRevision> writer, Glob destinationFiles, DummyRevision originRef)
      throws ValidationException, RepoException, IOException {
    processWithBaseline(writer, destinationFiles, originRef, /*baseline=*/ null);
  }

  private void processWithBaseline(Writer<GitRevision> writer, Glob destinationFiles,
      DummyRevision originRef, String baseline)
      throws RepoException, ValidationException, IOException {
    processWithBaselineAndConfirmation(writer, destinationFiles, originRef, baseline,
        /*askForConfirmation*/false);
  }

  private void processWithBaselineAndConfirmation(Writer<GitRevision> writer,
      Glob destinationFiles, DummyRevision originRef, String baseline,
      boolean askForConfirmation)
      throws ValidationException, RepoException, IOException {
    TransformResult result = TransformResults.of(workdir, originRef);
    if (baseline != null) {
      result = result.withBaseline(baseline);
    }

    if (askForConfirmation) {
      result = result.withAskForConfirmation(true);
    }
    ImmutableList<DestinationEffect> destinationResult =
        writer.write(result, destinationFiles, console);
    assertThat(destinationResult).hasSize(1);
    assertThat(destinationResult.get(0).getErrors()).isEmpty();
    assertThat(destinationResult.get(0).getType()).isEqualTo(Type.CREATED);
    assertThat(destinationResult.get(0).getDestinationRef().getType()).isEqualTo("commit");
    assertThat(destinationResult.get(0).getDestinationRef().getId()).matches("[0-9a-f]{40}");
  }

  @Test
  public void processFirstCommit() throws Exception {
    fetch = "testPullFromRef";
    push = "testPushToRef";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    process(firstCommitWriter(), new DummyRevision("origin_ref"));

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "testPushToRef");
    assertThat(showResult).contains("some content");

    assertFilesInDir(1, "testPushToRef", ".");
    assertCommitCount(1, "testPushToRef");

    assertCommitHasOrigin("testPushToRef", "origin_ref");
  }

  @Test
  public void process_autoDetect() throws Exception {
    push = primaryBranch;
    fetch = primaryBranch;

    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    process(firstCommitWriter(), new DummyRevision("origin_ref"));
    Files.write(workdir.resolve("test.txt"), "other content".getBytes(UTF_8));
    primaryBranchMigration = "True";
    push = "master";
    fetch = "master";

    // primaryBranch = repo().getPrimaryBranch(url);
    process(newWriter(), new DummyRevision("process_autoDetect"));
    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", primaryBranch);
    assertThat(showResult).contains("other content");
    assertFilesInDir(1, primaryBranch, ".");
    assertCommitCount(2, primaryBranch);
    assertCommitHasOrigin(primaryBranch, "process_autoDetect");
  }

  @Test
  public void testNoSetRevId() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    TransformResult result = TransformResults.of(workdir, new DummyRevision("origin_ref"))
        .withSetRevId(false);

    ImmutableList<DestinationEffect> destinationResult = firstCommitWriter()
        .write(result, destinationFiles, console);
    assertThat(destinationResult).hasSize(1);

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", primaryBranch);
    assertThat(showResult).contains("some content");

    assertFilesInDir(1, primaryBranch, ".");
    assertCommitCount(1, primaryBranch);

    assertThat(parseMessage(lastCommit(primaryBranch).getBody())
        .labelsAsMultimap()).doesNotContainKey(DummyOrigin.LABEL_NAME);
  }

  @Test
  public void testTag() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    options.setForce(true);
    WriterContext writerContext =
        new WriterContext("piper_to_github", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());
    evalDestination().newWriter(writerContext).write(TransformResults.of(
        workdir, new DummyRevision("ref1")), destinationFiles, console);
    options.setForce(false);
    Changes changes = new Changes(
        ImmutableList.of(
            new Change<>(new DummyRevision("ref2"), new Author("foo", "foo@foo.com"), "message",
                ZonedDateTime.now(ZoneOffset.UTC), ImmutableListMultimap.of("my_label", "12345"))),
        ImmutableList.of());
    Files.write(workdir.resolve("test.txt"), "some content 2".getBytes(UTF_8));
    evalDestinationWithTag(null).newWriter(writerContext).write(TransformResults.of(
         workdir, new DummyRevision("ref2")).withChanges(changes).
        withSummary("message_tag"), destinationFiles, console);
     CommandOutput commandOutput = repo().simpleCommand("tag", "-n9");
     assertThat(commandOutput.getStdout()).matches(".*test_v1.*message_tag\n"
         + ".*\n"
         + ".*DummyOrigin-RevId: ref2\n");
  }

  @Test
  public void testTagWithLabel() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    tagName = "tag_${my_tag}";
    tagMsg = "msg_${my_msg}";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    options.setForce(true);
    WriterContext writerContext =
        new WriterContext("piper_to_github", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());
    evalDestination().newWriter(writerContext).write(TransformResults.of(
        workdir, new DummyRevision("ref1")), destinationFiles, console);
    options.setForce(false);
    Files.write(workdir.resolve("test.txt"), "some content 2".getBytes(UTF_8));
    evalDestinationWithTag(tagMsg).newWriter(writerContext).write(TransformResults.of(
        workdir, new DummyRevision("ref2")).withLabelFinder(Functions.forMap(
        ImmutableMap.of("my_tag", ImmutableList.of("12345"),
            "my_msg", ImmutableList.of("2345")))), destinationFiles, console);
    CommandOutput commandOutput = repo().simpleCommand("tag", "-n9");
    assertThat(commandOutput.getStdout()).matches(".*tag_12345.*msg_2345\n");
  }

  @Test
  public void testTagWithLabelNotFound() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    tagName = "tag_${my_tag}";
    tagMsg = "msg_${my_msg}";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    options.setForce(true);
    WriterContext writerContext =
        new WriterContext("piper_to_github", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());
    evalDestination().newWriter(writerContext).write(TransformResults.of(
        workdir, new DummyRevision("ref1")), destinationFiles, console);
    options.setForce(false);
    Files.write(workdir.resolve("test.txt"), "some content 2".getBytes(UTF_8));
    evalDestinationWithTag(tagMsg).newWriter(writerContext).write(TransformResults.of(
        workdir, new DummyRevision("ref2")).withLabelFinder(Functions.forMap(
        ImmutableMap.of(), null)), destinationFiles, console);
    CommandOutput commandOutput = repo().simpleCommand("tag", "-n9");
    assertThat(commandOutput.getStdout()).isEmpty();
  }

  @Test
  public void testTagWithExisting() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    options.setForce(true);
    WriterContext writerContext =
        new WriterContext("piper_to_github", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());
    evalDestination().newWriter(writerContext).write(TransformResults.of(
        workdir, new DummyRevision("ref1")), destinationFiles, console);
    Files.write(workdir.resolve("test.txt"), "some content 2".getBytes(UTF_8));

    // push tag
    evalDestinationWithTag(null).newWriter(writerContext).write(TransformResults.of(
        workdir, new DummyRevision("ref2")), destinationFiles, console);

    options.setForce(false);
    Files.write(workdir.resolve("test.txt"), "some content 3".getBytes(UTF_8));
    // push existing tag
    evalDestinationWithTag(tagMsg).newWriter(writerContext).write(TransformResults.of(
        workdir, new DummyRevision("ref2")), destinationFiles, console);
    CommandOutput commandOutput = repo().simpleCommand("tag", "-n9");
    assertThat(commandOutput.getStdout()).matches(".*" + tagName + ".*test summary\n"
        + ".*\n"
        + ".*DummyOrigin-RevId: ref2\n");
  }

  @Test
  public void testTagWithDryRun() throws Exception {
    options.general.dryRunMode = true;
    WriterContext writerContext = setUpForTestingTag();
    options.git.gitTagOverwrite = true;
    evalDestinationWithTag(tagMsg).newWriter(writerContext).write(TransformResults.of(
        workdir, new DummyRevision("ref2")), destinationFiles, console);
    CommandOutput commandOutput = repo().simpleCommand("tag", "-n9");
    assertThat(commandOutput.getStdout()).matches(".*" + tagName +".*" + tagMsg + "\n");
  }

  @Test
  public void testTagWithExistingAndForce() throws Exception {
    WriterContext writerContext = setUpForTestingTag();
    // push existing tag with tagMsg
    options.git.gitTagOverwrite = true;
    evalDestinationWithTag(tagMsg).newWriter(writerContext).write(TransformResults.of(
        workdir, new DummyRevision("ref2")), destinationFiles, console);
    CommandOutput commandOutput = repo().simpleCommand("tag", "-n9");
    assertThat(commandOutput.getStdout()).matches(".*" + tagName +".*" + tagMsg + "\n");
  }

  @Test
  public void testTagWithExistingAndWarningMsg() throws Exception {
    WriterContext writerContext = setUpForTestingTag();
    // push existing tag with tagMsg
    evalDestinationWithTag(tagMsg).newWriter(writerContext).write(TransformResults.of(
        workdir, new DummyRevision("ref2")), destinationFiles, console);
    CommandOutput commandOutput = repo().simpleCommand("tag", "-n9");
    console.assertThat().onceInLog(MessageType.WARNING,
        ".*Tag " + tagName + " exists. To overwrite it please use flag '--git-tag-overwrite'.*");
    assertThat(commandOutput.getStdout()).matches(".*" + tagName + ".*test summary\n"
        + ".*\n"
        + ".*DummyOrigin-RevId: ref2\n");
  }

  @Test
  public void testTagWithMsg() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    options.setForce(true);
    WriterContext writerContext =
        new WriterContext("piper_to_github", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());
    evalDestination().newWriter(writerContext).write(TransformResults.of(
        workdir, new DummyRevision("ref1")), destinationFiles, console);
    options.setForce(false);
    Changes changes = new Changes(
        ImmutableList.of(
            new Change<>(new DummyRevision("ref2"), new Author("foo", "foo@foo.com"), "message",
                ZonedDateTime.now(ZoneOffset.UTC), ImmutableListMultimap.of("my_label", "12345"))),
        ImmutableList.of());
    Files.write(workdir.resolve("test.txt"), "some content 2".getBytes(UTF_8));
    evalDestinationWithTag(tagMsg).newWriter(writerContext).write(TransformResults.of(
        workdir, new DummyRevision("ref2")).withChanges(changes), destinationFiles, console);
    CommandOutput commandOutput = repo().simpleCommand("tag", "-n9");
    assertThat(commandOutput.getStdout()).matches(".*test_v1.*" + tagMsg + "\n");
  }

  @Test
  public void processUserAborts() throws Exception {
    console = new TestingConsole()
        .respondNo();
    fetch = primaryBranch;
    push = primaryBranch;
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    ValidationException thrown =
        assertThrows(
            ValidationException.class,
            () ->
                processWithBaselineAndConfirmation(
                    firstCommitWriter(),
                    destinationFiles,
                    new DummyRevision("origin_ref"),
                    /*baseline=*/ null,
                    /*askForConfirmation=*/ true));
    assertThat(thrown)
        .hasMessageThat()
        .contains("User aborted execution: did not confirm diff changes");
  }

  @Test
  public void processEmptyDiff() throws Exception {
    console = new TestingConsole().respondYes();
    fetch = primaryBranch;
    push = primaryBranch;
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    processWithBaselineAndConfirmation(
        firstCommitWriter(),
        destinationFiles, new DummyRevision("origin_ref1"),
        /*baseline=*/ null, /*askForConfirmation=*/
        true);

    // process empty change. Shouldn't ask anything.
    assertThrows(
        EmptyChangeException.class,
        () ->
            processWithBaselineAndConfirmation(
                newWriter(),
                destinationFiles,
                new DummyRevision("origin_ref2"),
                /*baseline=*/ null,
                /*askForConfirmation=*/ true));
  }

  @Test
  public void processUserConfirms() throws Exception {
    console = new TestingConsole()
        .respondYes();
    fetch = primaryBranch;
    push = primaryBranch;
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    processWithBaselineAndConfirmation(
        firstCommitWriter(),
        destinationFiles, new DummyRevision("origin_ref"),
        /*baseline=*/ null, /*askForConfirmation=*/
        true);

    String change = git("--git-dir", repoGitDir.toString(), "show", "HEAD");
    // Validate that we really have pushed the commit.
    assertThat(change).contains("test summary");
    console.assertThat()
        .matchesNext(MessageType.PROGRESS, "Git Destination: Fetching: file:.* refs/heads/.*")
        .matchesNext(MessageType.WARNING,
            "Git Destination: 'refs/heads/.*' doesn't exist in 'file://.*")
        .matchesNext(MessageType.PROGRESS, "Git Destination: Checking out .*")
        .matchesNext(MessageType.PROGRESS, "Git Destination: Adding all files")
        .matchesNext(MessageType.PROGRESS, "Git Destination: Excluding files")
        .matchesNext(MessageType.PROGRESS, "Git Destination: Creating a local commit")
        .matchesNext(MessageType.VERBOSE, "Integrates for.*")
        // Validate that we showed the confirmation
        .matchesNext(MessageType.INFO, "(?m)(\n|.)*test summary(\n|.)+"
            + "diff --git a/test.txt b/test.txt\n"
            + "new file mode 100644\n"
            + "index 0000000\\.\\.f0eec86\n"
            + "--- /dev/null\n"
            + "\\+\\+\\+ b/test.txt\n"
            + "@@ -0,0 \\+1 @@\n"
            + "\\+some content\n"
            + "\\\\ No newline at end of file\n")
        .matchesNext(MessageType.WARNING, "Proceed with push to.*[?]")
        .matchesNext(MessageType.PROGRESS, "Git Destination: Pushing to .*")
        .containsNoMoreMessages();
  }

  @Test
  public void processEmptyCommit() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    DummyRevision ref = new DummyRevision("origin_ref");
    process(firstCommitWriter(), ref);
    EmptyChangeException thrown =
        assertThrows(EmptyChangeException.class, () -> process(newWriter(), ref));
    assertThat(thrown).hasMessageThat().contains("empty change");
  }

  /**
   * regression to ensure we don't do:
   *
   *     git log -- some_path
   *
   *  This doesn't work for fake merges as the merge is not shown when a path is passed even
   *  with -m.
   */
  @Test
  public void getDestinationStatusForFakeMergeAndNonEmptyRoots() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;

    Files.createDirectories(workdir.resolve("dir"));
    Files.write(workdir.resolve("dir/file"), "".getBytes(UTF_8));
    GitRepository repo = repo().withWorkTree(workdir);
    repo.add().files("dir/file").run();
    repo.simpleCommand("commit", "-m", "first commit");

    repo.simpleCommand("branch", "foo");

    Files.write(workdir.resolve("dir/file"), "other".getBytes(UTF_8));
    repo.add().files("dir/file").run();
    repo.simpleCommand("commit", "-m", "first commit");

    repo.forceCheckout("foo");

    Files.write(workdir.resolve("dir/file"), "feature".getBytes(UTF_8));
    repo.add().files("dir/file").run();
    repo.simpleCommand("commit", "-m", "first commit");

    repo.forceCheckout(primaryBranch);

    // Fake merge
    repo.simpleCommand("merge", "-Xours", "foo", "-m",
        "A fake merge\n\n" + DummyOrigin.LABEL_NAME + ": foo");

    destinationFiles = Glob.createGlob(ImmutableList.of("dir/**"));
    WriterContext writerContext =
        new WriterContext("piper_to_github", "TEST", false, new DummyRevision("feature"),
            Glob.ALL_FILES.roots());
    DestinationStatus status = destination().newWriter(writerContext)
        .getDestinationStatus(destinationFiles, DummyOrigin.LABEL_NAME);

    assertThat(status).isNotNull();
    assertThat(status.getBaseline()).isEqualTo("foo");
  }

  @Test
  public void processEmptyCommitWithExcludes() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    Files.write(workdir.resolve("excluded"), "some content".getBytes(UTF_8));
    repo().withWorkTree(workdir)
        .add().files("excluded").run();
    repo().withWorkTree(workdir).simpleCommand("commit", "-m", "first commit");

    Files.delete(workdir.resolve("excluded"));

    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("excluded"));
    EmptyChangeException thrown =
        assertThrows(
            EmptyChangeException.class,
            () -> process(newWriter(), new DummyRevision("origin_ref")));
    assertThat(thrown).hasMessageThat().contains("empty change");
  }

  @Test
  public void processFetchRefDoesntExist() throws Exception {
    fetch = "testPullFromRef";
    push = "testPushToRef";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));

    ValidationException thrown =
        assertThrows(
            ValidationException.class, () -> process(newWriter(), new DummyRevision("origin_ref")));
    assertThat(thrown).hasMessageThat().contains("'refs/heads/testPullFromRef' doesn't exist");
  }

  @Test
  public void processCommitDeletesAndAddsFiles() throws Exception {
    fetch = "pullFromBar";
    push = "pushToFoo";

    Files.write(workdir.resolve("deleted_file"), "deleted content".getBytes(UTF_8));
    process(firstCommitWriter(), new DummyRevision("origin_ref"));
    git("--git-dir", repoGitDir.toString(), "branch", "pullFromBar", "pushToFoo");

    workdir = Files.createTempDirectory("workdir2");
    Files.write(workdir.resolve("1.txt"), "content 1".getBytes(UTF_8));
    Files.createDirectories(workdir.resolve("subdir"));
    Files.write(workdir.resolve("subdir/2.txt"), "content 2".getBytes(UTF_8));
    process(newWriter(), new DummyRevision("origin_ref"));

    // Make sure original file was deleted.
    assertFilesInDir(2, "pushToFoo", ".");
    assertFilesInDir(1, "pushToFoo", "subdir");
    // Make sure both commits are present.
    assertCommitCount(2, "pushToFoo");

    assertCommitHasOrigin("pushToFoo", "origin_ref");
  }

  @Test
  public void doNotDeleteIncludedFilesInNonMatchingSubdir() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;

    Files.createDirectories(workdir.resolve("foo"));
    Files.write(workdir.resolve("foo/bar"), "content".getBytes(UTF_8));
    repo().withWorkTree(workdir)
        .add().files("foo/bar").run();
    repo().withWorkTree(workdir).simpleCommand("commit", "-m", "message");

    Files.write(workdir.resolve("foo/baz"), "content".getBytes(UTF_8));

    // Note the glob foo/** does not match the directory itself called 'foo',
    // only the contents.
    destinationFiles = Glob.createGlob(ImmutableList.of("foo/**"));
    process(newWriter(), new DummyRevision("origin_ref"));

    assertThatCheckout(repo(), primaryBranch)
        .containsFile("foo/bar", "content")
        .containsFile("foo/baz", "content")
        .containsNoMoreFiles();
  }

  @Test
  public void emptyRebaseTest() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    GitRepository destRepo = repo().withWorkTree(workdir);

    writeFile(workdir, "foo", "");
    destRepo.add().files("foo").run();
    destRepo.simpleCommand("commit", "-m", "baseline");

    GitRevision baseline = destRepo.resolveReference("HEAD");

    writeFile(workdir, "foo", "updated");
    destRepo.add().files("foo").run();
    destRepo.simpleCommand("commit", "-m", "main head");

    writeFile(workdir, "foo", "updated");

    destinationFiles = Glob.createGlob(ImmutableList.of("foo"));
    EmptyChangeException e =
        assertThrows(
            EmptyChangeException.class,
            () ->
                processWithBaseline(
                    newWriter(),
                    destinationFiles,
                    new DummyRevision("origin_ref"),
                    baseline.getSha1()));
    assertThat(e).hasMessageThat().contains("Empty change after rebase");
  }

  @Test
  public void previousRebaseFailureDoesNotAffectNextOne() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    GitRepository destRepo = repo().withWorkTree(workdir);

    writeFile(workdir, "foo", "");
    destRepo.add().files("foo").run();
    destRepo.simpleCommand("commit", "-m", "baseline");

    GitRevision baseline = destRepo.resolveReference("HEAD");

    writeFile(workdir, "foo", "conflict");
    destRepo.add().files("foo").run();
    destRepo.simpleCommand("commit", "-m", "primary head");

    writeFile(workdir, "foo", "updated");

    destinationFiles = Glob.createGlob(ImmutableList.of("foo"));
    RebaseConflictException rebaseConflictException =
        assertThrows(
          RebaseConflictException.class,
          () ->
              processWithBaseline(
                  newWriter(),
                  destinationFiles,
                  new DummyRevision("origin_ref"),
                  baseline.getSha1()));

    writeFile(workdir, "foo", "conflict");
    writeFile(workdir, "bar", "other file");

    destinationFiles = Glob.createGlob(ImmutableList.of("foo", "bar"));
    processWithBaseline(newWriter(), destinationFiles, new DummyRevision("origin_ref"),
        baseline.getSha1());

    assertThat(rebaseConflictException).hasMessageThat()
        .containsMatch(
            ".*Please consider to use flag nogit-destination-rebase to workaround.*");
    assertThatCheckout(destRepo, "HEAD")
        .containsFile("foo", "conflict")
        .containsFile("bar", "other file")
        .containsNoMoreFiles();
  }

  @Test
  public void emptyRebaseEmptyDescription() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    GitRepository destRepo = repo().withWorkTree(workdir);

    writeFile(workdir, "foo", "");
    destRepo.add().files("foo").run();
    destRepo.simpleCommand("commit", "-m", "baseline");

    GitRevision baseline = destRepo.resolveReference("HEAD");

    writeFile(workdir, "foo", "updated");
    destRepo.add().files("foo").run();
    destRepo.simpleCommand("commit", "-m", "primary head");

    writeFile(workdir, "foo", "updated");

    destinationFiles = Glob.createGlob(ImmutableList.of("foo"));
    TransformResult result = TransformResults.of(workdir, new DummyRevision("origin_ref"))
        .withBaseline(baseline.getSha1()).withSummary("");
    EmptyChangeException e =
        assertThrows(
            EmptyChangeException.class,
            () -> {
              ImmutableList<DestinationEffect> destinationResult =
                  newWriter().write(result, destinationFiles, console);
            });
    assertThat(e).hasMessageThat().contains("Empty change after rebase");
  }

  @Test
  public void lastRevOnlyForAffectedRoots() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;

    Files.createDirectories(workdir.resolve("foo"));
    Files.createDirectories(workdir.resolve("bar"));
    Files.createDirectories(workdir.resolve("baz"));

    Files.write(workdir.resolve("foo/one"), "content".getBytes(UTF_8));
    Files.write(workdir.resolve("bar/one"), "content".getBytes(UTF_8));
    Files.write(workdir.resolve("baz/one"), "content".getBytes(UTF_8));

    DummyRevision ref1 = new DummyRevision("first");

    Glob firstGlob = Glob.createGlob(ImmutableList.of("foo/**", "bar/**"));
    WriterContext writerContext =
        new WriterContext("piper_to_github", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());

    Writer<GitRevision> writer = destinationFirstCommit().newWriter(writerContext);
    process(writer, ref1);

    Files.write(workdir.resolve("baz/one"), "content2".getBytes(UTF_8));
    DummyRevision ref2 = new DummyRevision("second");
    process(writer, Glob.createGlob(ImmutableList.of("baz/**")), ref2);

    assertThat(
            destination()
                .newWriter(writerContext)
                .getDestinationStatus(firstGlob, DummyOrigin.LABEL_NAME)
                .getBaseline())
        .isEqualTo(ref1.asString());
    assertThat(writer.getDestinationStatus(Glob.createGlob(ImmutableList.of("baz/**")),
        DummyOrigin.LABEL_NAME).getBaseline())
        .isEqualTo(ref2.asString());
  }

  /**
   * Verify that multiple (exclusive) migrations can write to the same Git repository. This is,
   * migrations from different origins writing to a different subpaths in the Git repo and excluding
   * the other ones.
   */
  @Test
  public void multipleMigrationsToOneDestination_separateRoots() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;

    Files.createDirectories(workdir.resolve("foo"));
    Files.createDirectories(workdir.resolve("bar"));
    Files.createDirectories(workdir.resolve("baz"));

    Files.write(workdir.resolve("foo/one"), "First version".getBytes(UTF_8));
    Files.write(workdir.resolve("bar/one"), "First version".getBytes(UTF_8));
    Files.write(workdir.resolve("baz/one"), "First version".getBytes(UTF_8));

    repo().withWorkTree(workdir).add().files("foo/one").files("bar/one").files("baz/one").run();
    repo().withWorkTree(workdir).simpleCommand("commit", "-m", "Initial commit");

    Glob repoAglob = Glob.createGlob(ImmutableList.of("foo/**"), ImmutableList.of("bar/**"));
    Glob repoBglob = Glob.createGlob(ImmutableList.of("bar/**"), ImmutableList.of("foo/**"));

    // Change on repo A
    Files.write(workdir.resolve("foo/one"), "Second version".getBytes(UTF_8));
    Writer<GitRevision> writer1 = newWriter();
    DummyRevision repoAfirstRev = new DummyRevision("Foo first");
    process(writer1, repoAglob, repoAfirstRev);

    assertThatCheckout(repo(), primaryBranch)
        .containsFile("foo/one", "Second version")
        .containsFile("bar/one", "First version")
        .containsFile("baz/one", "First version")
        .containsNoMoreFiles();
    verifyDestinationStatus(repoAglob, repoAfirstRev);

    // Change on repo B, does not affect repo A paths
    Files.write(workdir.resolve("bar/one"), "Second version".getBytes(UTF_8));
    Writer<GitRevision> writer2 = newWriter();
    DummyRevision repoBfirstRev = new DummyRevision("Bar first");
    process(writer2, repoBglob, repoBfirstRev);

    assertThatCheckout(repo(), primaryBranch)
        .containsFile("foo/one", "Second version")
        .containsFile("bar/one", "Second version")
        .containsFile("baz/one", "First version")
        .containsNoMoreFiles();
    verifyDestinationStatus(repoAglob, repoAfirstRev);
    verifyDestinationStatus(repoBglob, repoBfirstRev);

    // Change on repo A does not affect repo B paths
    Files.write(workdir.resolve("foo/one"), "Third version".getBytes(UTF_8));
    Writer<GitRevision> writer3 = newWriter();
    DummyRevision repoASecondRev = new DummyRevision("Foo second");
    process(writer3, repoAglob, repoASecondRev);

    assertThatCheckout(repo(), primaryBranch)
        .containsFile("foo/one", "Third version")
        .containsFile("bar/one", "Second version")
        .containsFile("baz/one", "First version")
        .containsNoMoreFiles();
    verifyDestinationStatus(repoAglob, repoASecondRev);
    verifyDestinationStatus(repoBglob, repoBfirstRev);
  }

  @Test
  public void previousImportReference() throws Exception {
    checkPreviousImportReference();
  }

  /**
   * Verify that multiple (exclusive) migrations can write to the same Git repository. This is,
   * migrations from different origins writing to a different subpaths in the Git repo and excluding
   * the other ones.
   */
  @Test
  public void multipleMigrationsToOneDestination_withComplexGlobs() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;

    Files.createDirectories(workdir.resolve("foo"));
    Files.createDirectories(workdir.resolve("foo/bar"));
    Files.createDirectories(workdir.resolve("baz"));

    Files.write(workdir.resolve("foo/one"), "First version".getBytes(UTF_8));
    Files.write(workdir.resolve("foo/bar/one"), "First version".getBytes(UTF_8));
    Files.write(workdir.resolve("baz/one"), "First version".getBytes(UTF_8));

    repo().withWorkTree(workdir).add().files("foo/one").files("foo/bar/one").files("baz/one").run();
    repo().withWorkTree(workdir).simpleCommand("commit", "-m", "Initial commit");

    Glob repoAglob = Glob.createGlob(ImmutableList.of("foo/**"), ImmutableList.of("foo/bar/**"));
    Glob repoBglob = Glob.createGlob(ImmutableList.of("foo/bar/**"));

    // Change on repo A
    Files.write(workdir.resolve("foo/one"), "Second version".getBytes(UTF_8));
    Writer<GitRevision> writer1 = newWriter();
    DummyRevision repoAfirstRev = new DummyRevision("Foo first");
    process(writer1, repoAglob, repoAfirstRev);

    assertThatCheckout(repo(), primaryBranch)
        .containsFile("foo/one", "Second version")
        .containsFile("foo/bar/one", "First version")
        .containsFile("baz/one", "First version")
        .containsNoMoreFiles();
    verifyDestinationStatus(repoAglob, repoAfirstRev);

    // Change on repo B, does not affect repo A paths
    Files.write(workdir.resolve("foo/bar/one"), "Second version".getBytes(UTF_8));
    Writer<GitRevision> writer2 = newWriter();
    DummyRevision repoBfirstRev = new DummyRevision("Bar first");
    process(writer2, repoBglob, repoBfirstRev);

    assertThatCheckout(repo(), primaryBranch)
        .containsFile("foo/one", "Second version")
        .containsFile("foo/bar/one", "Second version")
        .containsFile("baz/one", "First version")
        .containsNoMoreFiles();
    verifyDestinationStatus(repoAglob, repoAfirstRev);
    verifyDestinationStatus(repoBglob, repoBfirstRev);

    // Change on repo A does not affect repo B paths
    Files.write(workdir.resolve("foo/one"), "Third version".getBytes(UTF_8));
    Writer<GitRevision> writer3 = newWriter();
    DummyRevision repoASecondRev = new DummyRevision("Foo second");
    process(writer3, repoAglob, repoASecondRev);

    assertThatCheckout(repo(), primaryBranch)
        .containsFile("foo/one", "Third version")
        .containsFile("foo/bar/one", "Second version")
        .containsFile("baz/one", "First version")
        .containsNoMoreFiles();
    verifyDestinationStatus(repoAglob, repoASecondRev);
    verifyDestinationStatus(repoBglob, repoBfirstRev);
  }

  @Test
  public void previousImportReference_with_force() throws Exception {
    force = true;
    checkPreviousImportReference();
  }

  @Test
  public void test_force_rewrite_history() throws Exception {
    fetch = primaryBranch;
    push = "feature";

    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("excluded.txt"));

    Path scratchTree = Files.createTempDirectory("GitDestinationTest-scratchTree");
    Files.write(scratchTree.resolve("excluded.txt"), "some content".getBytes(UTF_8));
    repo().withWorkTree(scratchTree).add().files("excluded.txt").run();
    repo().withWorkTree(scratchTree).simpleCommand("commit", "-m", "primary change");

    Path file = workdir.resolve("test.txt");

    Files.write(file, "some content".getBytes(UTF_8));
    Writer<GitRevision> writer = newWriter();

    assertThat(writer.getDestinationStatus(destinationFiles, DummyOrigin.LABEL_NAME)).isNull();
    process(writer, new DummyRevision("first_commit"));
    assertCommitHasOrigin("feature", "first_commit");

    Files.write(file, "changed".getBytes(UTF_8));

    process(writer, new DummyRevision("second_commit"));
    assertCommitHasOrigin("feature", "second_commit");

    options.gitDestination.nonFastForwardPush = true;

    Files.write(file, "some content".getBytes(UTF_8));
    writer = newWriter();

    assertThat(writer.getDestinationStatus(destinationFiles, DummyOrigin.LABEL_NAME)).isNull();
    process(writer, new DummyRevision("first_commit_2"));
    assertCommitHasOrigin("feature", "first_commit_2");

    Files.write(file, "changed".getBytes(UTF_8));

    process(writer, new DummyRevision("second_commit_2"));
    assertCommitHasOrigin("feature", "second_commit_2");

    assertThat(repo().log(primaryBranch + "..feature").run()).hasSize(2);
  }

  private void checkPreviousImportReference()
      throws IOException, ValidationException, RepoException {
    fetch = primaryBranch;
    push = primaryBranch;

    Path file = workdir.resolve("test.txt");

    Files.write(file, "some content".getBytes(UTF_8));
    Writer<GitRevision>writer =
        firstCommitWriter();
    assertThat(writer.getDestinationStatus(destinationFiles, DummyOrigin.LABEL_NAME)).isNull();
    process(writer, new DummyRevision("first_commit"));
    assertCommitHasOrigin(primaryBranch, "first_commit");

    Files.write(file, "some other content".getBytes(UTF_8));
    writer = newWriter();
    assertThat(writer.getDestinationStatus(destinationFiles, DummyOrigin.LABEL_NAME).getBaseline())
        .isEqualTo("first_commit");
    process(writer, new DummyRevision("second_commit"));
    assertCommitHasOrigin(primaryBranch, "second_commit");

    Files.write(file, "just more text".getBytes(UTF_8));
    writer = newWriter();
    assertThat(writer.getDestinationStatus(destinationFiles, DummyOrigin.LABEL_NAME).getBaseline())
        .isEqualTo("second_commit");
    process(writer, new DummyRevision("third_commit"));
    assertCommitHasOrigin(primaryBranch, "third_commit");
  }

  @Test
  public void previousImportReference_nonCopybaraCommitsSinceLastMigrate() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;

    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    process(firstCommitWriter(), new DummyRevision("first_commit"));

    Path scratchTree = Files.createTempDirectory("GitDestinationTest-scratchTree");
    for (int i = 0; i < 20; i++) {
      Files.write(scratchTree.resolve("excluded.dat"), new byte[] {(byte) i});
      repo().withWorkTree(scratchTree)
          .add().files("excluded.dat").run();
      repo().withWorkTree(scratchTree)
          .simpleCommand("commit", "-m", "excluded #" + i);
    }

    assertThat(newWriter()
        .getDestinationStatus(destinationFiles, DummyOrigin.LABEL_NAME).getBaseline())
        .isEqualTo("first_commit");
  }

  @Test
  public void previousImportReferenceIsBeforeACommitWithMultipleParents() throws Exception {
    Truth.assertThat(checkPreviousImportReferenceMultipleParents()).isEqualTo("b2-origin");
  }

  @Test
  public void previousImportReferenceIsBeforeACommitWithMultipleParents_first_parent()
      throws Exception {
    options.gitDestination.lastRevFirstParent = true;
    Truth.assertThat(checkPreviousImportReferenceMultipleParents()).isEqualTo("b1-origin");
  }

  private void branchChange(Path scratchTree, GitRepository scratchRepo, String branch,
      String msg) throws RepoException, IOException {
    scratchRepo.simpleCommand("checkout", branch);
    Files.write(scratchTree.resolve(branch + ".file"), msg.getBytes(UTF_8));
    scratchRepo.add().files(branch + ".file").run();
    scratchRepo.simpleCommand("commit", "-m", msg);
  }

  private String checkPreviousImportReferenceMultipleParents()
      throws IOException, RepoException, ValidationException {
    fetch = "b1";
    push = "b1";

    Path scratchTree = Files.createTempDirectory("GitDestinationTest-scratchTree");
    GitRepository scratchRepo = repo().withWorkTree(scratchTree);

    Files.write(scratchTree.resolve(primaryBranch + ".file"), (primaryBranch + "\n\n"
        + DummyOrigin.LABEL_NAME + ": should_not_happen").getBytes(UTF_8));
    scratchRepo.add().files(primaryBranch + ".file").run();
    scratchRepo.simpleCommand("commit", "-m", primaryBranch + "\n\n"
        + DummyOrigin.LABEL_NAME + ": should_not_happen");

    scratchRepo.simpleCommand("branch", "b1");
    scratchRepo.simpleCommand("branch", "b2");

    branchChange(scratchTree, scratchRepo, "b1", "b1-1\n\n"
        + DummyOrigin.LABEL_NAME + ": b1-origin");

    // Wait a second so that the git log history is ordered.
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      fail("Interrupted while waiting: " + e.getMessage());
    }
    branchChange(scratchTree, scratchRepo, "b2", "b2-1\n\n"
        + DummyOrigin.LABEL_NAME + ": b2-origin");
    branchChange(scratchTree, scratchRepo, "b1", "b1-2");
    branchChange(scratchTree, scratchRepo, "b2", "b2-2");

    scratchRepo.simpleCommand("checkout", "b1");
    scratchRepo.simpleCommand("merge", "b2");
    return newWriter().getDestinationStatus(destinationFiles, DummyOrigin.LABEL_NAME).getBaseline();
  }

  @Test
  public void writesOriginTimestampToAuthorField() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;

    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    process(firstCommitWriter(),
        new DummyRevision("first_commit").withTimestamp(timeFromEpoch(1414141414))
    );
    GitTesting.assertAuthorTimestamp(repo(), primaryBranch, timeFromEpoch(1414141414));

    Files.write(workdir.resolve("test2.txt"), "some more content".getBytes(UTF_8));
    process(newWriter(), new DummyRevision("second_commit").withTimestamp(timeFromEpoch(1515151515))
    );
    GitTesting.assertAuthorTimestamp(repo(), primaryBranch, timeFromEpoch(1515151515));
  }

  @Test
  public void canOverrideUrl() throws Exception {
    Path newDestination = Files.createTempDirectory("canOverrideUrl");
    git("init", "--bare", newDestination.toString());
    fetch = primaryBranch;
    push = primaryBranch;

    options.gitDestination.url = "file://" + newDestination.toAbsolutePath();
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    process(firstCommitWriter(), new DummyRevision("first_commit"));
    GitTesting.assertCommitterLineMatches(repoForPath(newDestination),
        primaryBranch, "Bara Kopi <.*> [-+ 0-9]+");
    // No branches were created in the config file url.
    assertThat(repo().simpleCommand("branch").getStdout()).isEqualTo("");
  }

  @Test
  public void canOverrideCommitterName() throws Exception {
    fetch = "master";
    push = "master";

    options.gitDestination.committerName = "Bara Kopi";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    process(firstCommitWriter(),
        new DummyRevision("first_commit").withTimestamp(timeFromEpoch(1414141414))
    );
    GitTesting.assertCommitterLineMatches(repo(), "master", "Bara Kopi <.*> [-+ 0-9]+");

    options.gitDestination.committerName = "Piko Raba";
    Files.write(workdir.resolve("test.txt"), "some more content".getBytes(UTF_8));
    process(newWriter(), new DummyRevision("second_commit").withTimestamp(timeFromEpoch(1414141490))
    );
    GitTesting.assertCommitterLineMatches(repo(), "master", "Piko Raba <.*> [-+ 0-9+]+");
  }

  @Test
  public void canOverrideCommitterEmail() throws Exception {
    fetch = "master";
    push = "master";

    options.gitDestination.committerEmail = "bara.bara@gocha.gocha";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));

    process(firstCommitWriter(),
        new DummyRevision("first_commit").withTimestamp(timeFromEpoch(1414141414))
    );
    GitTesting.assertCommitterLineMatches(
        repo(), "master", ".* <bara[.]bara@gocha[.]gocha> [-+ 0-9]+");

    options.gitDestination.committerEmail = "kupo.kupo@tan.kou";
    Files.write(workdir.resolve("test.txt"), "some more content".getBytes(UTF_8));
    process(newWriter(), new DummyRevision("second_commit").withTimestamp(timeFromEpoch(1414141490))
    );
    GitTesting.assertCommitterLineMatches(
        repo(), "master", ".* <kupo[.]kupo@tan[.]kou> [-+ 0-9]+");
  }

  @Test
  public void gitUserNameMustBeConfigured() throws Exception {
    options.gitDestination.committerName = "";
    options.gitDestination.committerEmail = "foo@bara";
    fetch = primaryBranch;
    push = primaryBranch;

    ValidationException thrown =
        assertThrows(
            ValidationException.class,
            () -> process(firstCommitWriter(), new DummyRevision("first_commit")));
    assertThat(thrown)
        .hasMessageThat()
        .contains("'user.name' and/or 'user.email' are not configured.");
  }

  @Test
  public void gitUserEmailMustBeConfigured() throws Exception {
    options.gitDestination.committerName = "Foo Bara";
    options.gitDestination.committerEmail = "";
    fetch = primaryBranch;
    push = primaryBranch;

    ValidationException thrown =
        assertThrows(
            ValidationException.class,
            () -> process(firstCommitWriter(), new DummyRevision("first_commit")));
    assertThat(thrown)
        .hasMessageThat()
        .contains("'user.name' and/or 'user.email' are not configured.");
  }

  @Test
  public void authorPropagated() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;

    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));

    DummyRevision firstCommit = new DummyRevision("first_commit")
        .withAuthor(new Author("Foo Bar", "foo@bar.com"))
        .withTimestamp(timeFromEpoch(1414141414));
    process(firstCommitWriter(), firstCommit);

    assertCommitHasAuthor(primaryBranch, new Author("Foo Bar", "foo@bar.com"));
  }

  /**
   * This test reproduces an issue where the author timestamp has subseconds and, as a result,
   * before the fix the change was committed with the (incorrect) date '2017-04-12T12:19:00-07:00',
   * instead of '2017-06-01T12:19:00-04:00'.
   */
  @Test
  public void authorDateWithSubsecondsCorrectlyPopulated() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;

    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));

    ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(
        Instant.ofEpochMilli(1496333940012L), ZoneId.of("-04:00"));
    DummyRevision firstCommit = new DummyRevision("first_commit")
        .withAuthor(new Author("Foo Bar", "foo@bar.com"))
        .withTimestamp(zonedDateTime);
    process(firstCommitWriter(), firstCommit);

    String authorDate = git("log", "-1", "--pretty=%aI");

    assertThat(authorDate).isEqualTo("2017-06-01T12:19:00-04:00\n");
  }

  @Test
  public void canExcludeDestinationPathFromWorkflow() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;

    Path scratchTree = Files.createTempDirectory("GitDestinationTest-scratchTree");
    Files.write(scratchTree.resolve("excluded.txt"), "some content".getBytes(UTF_8));
    repo().withWorkTree(scratchTree)
        .add().files("excluded.txt").run();
    repo().withWorkTree(scratchTree)
        .simpleCommand("commit", "-m", "message");

    Files.write(workdir.resolve("normal_file.txt"), "some more content".getBytes(UTF_8));
    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("excluded.txt"));
    process(newWriter(), new DummyRevision("ref"));
    assertThatCheckout(repo(), primaryBranch)
        .containsFile("excluded.txt", "some content")
        .containsFile("normal_file.txt", "some more content")
        .containsNoMoreFiles();
  }

  @Test
  public void excludedDestinationPathsIgnoreGitTreeFiles() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;

    Path scratchTree = Files.createTempDirectory("GitDestinationTest-scratchTree");
    Files.createDirectories(scratchTree.resolve("notgit"));
    Files.write(scratchTree.resolve("notgit/HEAD"), "some content".getBytes(UTF_8));
    repo().withWorkTree(scratchTree)
        .add().files("notgit/HEAD").run();
    repo().withWorkTree(scratchTree)
        .simpleCommand("commit", "-m", "message");

    Files.write(workdir.resolve("normal_file.txt"), "some more content".getBytes(UTF_8));

    // Make sure this glob does not cause .git/HEAD to be added.
    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("**/HEAD"));

    process(newWriter(), new DummyRevision("ref"));
    assertThatCheckout(repo(), primaryBranch)
        .containsFile("notgit/HEAD", "some content")
        .containsFile("normal_file.txt", "some more content")
        .containsNoMoreFiles();
  }

  @Test
  public void processWithBaseline() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    DummyRevision ref = new DummyRevision("origin_ref");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    Files.write(workdir.resolve("excluded"), "some content".getBytes(UTF_8));
    process(firstCommitWriter(), ref);
    String firstCommit = repo().parseRef("HEAD");
    Files.write(workdir.resolve("test.txt"), "new content".getBytes(UTF_8));
    process(newWriter(), ref);

    // Lets exclude now 'excluded' so that we check that the rebase correctly ignores
    // the missing file (IOW, it doesn't delete the file in the commit).
    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("excluded"));

    Files.delete(workdir.resolve("excluded"));
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    Files.write(workdir.resolve("other.txt"), "other file".getBytes(UTF_8));
    processWithBaseline(newWriter(), destinationFiles, ref, firstCommit);

    assertThatCheckout(repo(), primaryBranch)
        .containsFile("test.txt", "new content")
        .containsFile("other.txt", "other file")
        .containsFile("excluded", "some content")
        .containsNoMoreFiles();
  }

  @Test
  public void processWithBaseline_noRebase() throws Exception {
    options.gitDestination.noRebase = true;
    options.setForce(true);
    fetch = primaryBranch;
    push = primaryBranch;
    DummyRevision ref = new DummyRevision("origin_ref");
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    Files.write(workdir.resolve("excluded"), "some content".getBytes(UTF_8));
    process(firstCommitWriter(), ref);
    String firstCommit = repo().parseRef(primaryBranch);
    Files.write(workdir.resolve("test.txt"), "new content".getBytes(UTF_8));
    process(newWriter(), ref);

    // Lets exclude now 'excluded' so that we check that the rebase correctly ignores
    // the missing file (IOW, it doesn't delete the file in the commit).
    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("excluded"));

    Files.delete(workdir.resolve("excluded"));
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    Files.write(workdir.resolve("other.txt"), "other file".getBytes(UTF_8));
    push = "refs/heads/my_branch";
    processWithBaseline(newWriter(), destinationFiles, ref, firstCommit);

    assertThatCheckout(repo(), "refs/heads/my_branch")
        .containsFile("test.txt", "some content")
        .containsFile("other.txt", "other file")
        .containsFile("excluded", "some content")
        .containsNoMoreFiles();
  }

  @Test
  public void processWithBaselineSameFileConflict() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    DummyRevision ref = new DummyRevision("origin_ref");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    process(firstCommitWriter(), ref);
    String firstCommit = repo().parseRef("HEAD");
    Files.write(workdir.resolve("test.txt"), "new content".getBytes(UTF_8));
    process(newWriter(), ref);

    Files.write(workdir.resolve("test.txt"), "conflict content".getBytes(UTF_8));
    RebaseConflictException thrown =
        assertThrows(
            RebaseConflictException.class,
            () -> processWithBaseline(newWriter(), destinationFiles, ref, firstCommit));
    assertThat(thrown).hasMessageThat().contains("conflict in test.txt");
  }

  @Test
  public void processWithBaselineSameFileNoConflict() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    String text = "";
    for (int i = 0; i < 1000; i++) {
      text += "Line " + i + "\n";
    }
    DummyRevision ref = new DummyRevision("origin_ref");

    Files.write(workdir.resolve("test.txt"), text.getBytes(UTF_8));
    process(firstCommitWriter(), ref);
    String firstCommit = repo().parseRef("HEAD");
    Files.write(
        workdir.resolve("test.txt"), text.replace("Line 200", "Line 200 Modified").getBytes(UTF_8));
    process(newWriter(), ref);

    Files.write(
        workdir.resolve("test.txt"), text.replace("Line 500", "Line 500 Modified").getBytes(UTF_8));

    processWithBaseline(newWriter(), destinationFiles, ref, firstCommit);

    assertThatCheckout(repo(), primaryBranch).containsFile("test.txt",
        text.replace("Line 200", "Line 200 Modified")
            .replace("Line 500", "Line 500 Modified")).containsNoMoreFiles();
  }

  @Test
  public void processWithBaselineNotFound() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    DummyRevision ref = new DummyRevision("origin_ref");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    process(firstCommitWriter(), ref);

    Files.write(workdir.resolve("test.txt"), "more content".getBytes(UTF_8));
    RepoException thrown =
        assertThrows(
            RepoException.class,
            () -> processWithBaseline(newWriter(), destinationFiles, ref, "I_dont_exist"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Cannot find baseline 'I_dont_exist' from fetch reference '");
  }

  @Test
  public void processWithBaselineNotFoundPrimaryNotFound() throws Exception {
    fetch = "test_test_test";
    push = primaryBranch;

    Files.write(workdir.resolve("test.txt"), "more content".getBytes(UTF_8));
    RepoException thrown =
        assertThrows(
            RepoException.class,
            () ->
                processWithBaseline(
                    firstCommitWriter(),
                    destinationFiles,
                    new DummyRevision("origin_ref"),
                    "I_dont_exist"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Cannot find baseline 'I_dont_exist' and fetch reference 'test_test_test'");
  }

  @Test
  public void pushSequenceOfChangesToReviewBranch() throws Exception {
    fetch = primaryBranch;
    push = "refs_for_primary";

    Writer<GitRevision> writer = firstCommitWriter();

    Files.write(workdir.resolve("test42"), "42".getBytes(UTF_8));
    ImmutableList<DestinationEffect> result =
        writer.write(TransformResults.of(
            workdir, new DummyRevision("ref1")), destinationFiles, console);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getErrors()).isEmpty();
    assertThat(result.get(0).getType()).isEqualTo(Type.CREATED);
    assertThat(result.get(0).getDestinationRef().getType()).isEqualTo("commit");
    assertThat(result.get(0).getDestinationRef().getId()).matches("[0-9a-f]{40}");

    String firstCommitHash = repo().parseRef("refs_for_primary");

    Files.write(workdir.resolve("test99"), "99".getBytes(UTF_8));
    result = writer.write(TransformResults.of(
        workdir, new DummyRevision("ref2")), destinationFiles, console);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getErrors()).isEmpty();
    assertThat(result.get(0).getType()).isEqualTo(Type.CREATED);
    assertThat(result.get(0).getDestinationRef().getType()).isEqualTo("commit");
    assertThat(result.get(0).getDestinationRef().getId()).matches("[0-9a-f]{40}");

    // Make sure parent of second commit is the first commit.
    assertThat(repo().parseRef("refs_for_primary~1")).isEqualTo(firstCommitHash);

    // Make sure commits have correct file content.
    assertThatCheckout(repo(), "refs_for_primary~1")
        .containsFile("test42", "42")
        .containsNoMoreFiles();
    assertThatCheckout(repo(), "refs_for_primary")
        .containsFile("test42", "42")
        .containsFile("test99", "99")
        .containsNoMoreFiles();
  }

  @Test
  public void testGitIgnoreIncluded() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    Files.write(workdir.resolve(".gitignore"), ".gitignore\n".getBytes(UTF_8));
    DummyRevision ref = new DummyRevision("origin_ref");
    process(firstCommitWriter(), ref);
    assertThatCheckout(repo(), primaryBranch)
        .containsFile("test.txt", "some content")
        .containsFile(".gitignore", ".gitignore\n")
        .containsNoMoreFiles();
  }

  @Test
  public void testGitIgnoreExcluded() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    Path scratchTree = Files.createTempDirectory("GitDestinationTest-testGitIgnoreExcluded");
    Files.write(scratchTree.resolve(".gitignore"), ".gitignore\n".getBytes(UTF_8));
    repo().withWorkTree(scratchTree).add().force().files(".gitignore").run();
    repo().withWorkTree(scratchTree).simpleCommand("commit", "-a", "-m", "gitignore file");

    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of(".gitignore"));

    DummyRevision ref = new DummyRevision("origin_ref");
    process(newWriter(), ref);
    assertThatCheckout(repo(), primaryBranch)
        .containsFile("test.txt", "some content")
        .containsFile(".gitignore", ".gitignore\n")
        .containsNoMoreFiles();
  }

  @Test
  public void testLocalRepo() throws Exception {
    checkLocalRepo(false);

    assertThatCheckout(repo(), primaryBranch)
        .containsFile("test.txt", "another content")
        .containsNoMoreFiles();
  }

  @Test
  public void testDryRun() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;

    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));

    Path scratchTree = Files.createTempDirectory("GitDestinationTest-testLocalRepo");
    Files.write(scratchTree.resolve("foo"), "foo\n".getBytes(UTF_8));
    repo().withWorkTree(scratchTree).add().force().files("foo").run();
    repo().withWorkTree(scratchTree).simpleCommand("commit", "-a", "-m", "change");
    WriterContext writerContext =
        new WriterContext("piper_to_github", "test", true, new DummyRevision("origin_ref1"),
            Glob.ALL_FILES.roots());
    Writer<GitRevision> writer = destination().newWriter(writerContext);
    process(writer, new DummyRevision("origin_ref1"));

    assertThatCheckout(repo(), primaryBranch)
        .containsFile("foo", "foo\n")
        .containsNoMoreFiles();

    // Run again without dry run
    writer = newWriter();
    process(writer, new DummyRevision("origin_ref1"));

    assertThatCheckout(repo(), primaryBranch)
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
  }

  @Test
  public void testChangeDescriptionEmpty() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    Path scratchTree = Files.createTempDirectory("GitDestinationTest-testLocalRepo");
    Files.write(scratchTree.resolve("foo"), "foo\n".getBytes(UTF_8));
    repo().withWorkTree(scratchTree).add().force().files("foo").run();
    repo().withWorkTree(scratchTree).simpleCommand("commit", "-a", "-m", "change");
    DummyRevision originRef = new DummyRevision("origin_ref");
    WriterContext writerContext =
        new WriterContext("GitDestinationTest", "test", true, new DummyRevision("origin_ref1"),
            Glob.ALL_FILES.roots());
    Writer<GitRevision> writer = destination().newWriter(writerContext);
    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                writer.write(
                    TransformResults.of(workdir, originRef).withSummary(" "),
                    Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("test.txt")),
                    console));
    assertThat(e.getMessage()).isEqualTo("Change description is empty.");
  }

  @Test
  public void testLocalRepoSkipPushFlag() throws Exception {
    GitRepository localRepo = checkLocalRepo(true);

    assertThatCheckout(repo(), primaryBranch)
        .containsFile("foo", "foo\n")
        .containsNoMoreFiles();

    // A simple push without origin is able to update the correct destination reference
    localRepo.push().run();

    assertThatCheckout(repo(), primaryBranch)
        .containsFile("test.txt", "another content")
        .containsNoMoreFiles();
  }

  @Test
  public void testMultipleRefs() throws Exception {
    Path scratchTree = Files.createTempDirectory("GitDestinationTest-testLocalRepo");
    Files.write(scratchTree.resolve("base"), "base\n".getBytes(UTF_8));
    repo().withWorkTree(scratchTree).add().force().files("base").run();
    repo().withWorkTree(scratchTree).simpleCommand("commit", "-a", "-m", "base");

    GitRevision primary = repo().resolveReference(primaryBranch);

    repo().simpleCommand("update-ref", "refs/other/" + primaryBranch, primary.getSha1());

    checkLocalRepo(true);
  }

  private GitRepository checkLocalRepo(boolean dryRun)
      throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;

    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));

    Path scratchTree = Files.createTempDirectory("GitDestinationTest-testLocalRepo");
    Files.write(scratchTree.resolve("foo"), "foo\n".getBytes(UTF_8));
    repo().withWorkTree(scratchTree).add().force().files("foo").run();
    repo().withWorkTree(scratchTree).simpleCommand("commit", "-a", "-m", "change");
    String baseline =
        repo().withWorkTree(scratchTree).simpleCommand("rev-parse", "HEAD").getStdout().trim();

    Path localPath = Files.createTempDirectory("local_repo");

    options.gitDestination.localRepoPath = localPath.toString();
    Writer<GitRevision> writer = newWriter(dryRun);
    process(writer, new DummyRevision("origin_ref1"));

    //    Path localPath = Files.createTempDirectory("local_repo");
    GitRepository localRepo = GitRepository.newRepo(/*verbose*/ true, localPath, getEnv()).init();

    assertThatCheckout(localRepo, primaryBranch)
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();

    Files.write(workdir.resolve("test.txt"), "another content".getBytes(UTF_8));
    processWithBaseline(writer, destinationFiles, new DummyRevision("origin_ref2"), baseline);

    assertThatCheckout(localRepo, primaryBranch)
        .containsFile("test.txt", "another content")
        .containsNoMoreFiles();

    for (String ref : ImmutableList.of("HEAD", "copybara/local")) {
      ImmutableList<GitLogEntry> entries = localRepo.log(ref).run();
      assertThat(entries.get(0).getBody()).isEqualTo(""
          + "test summary\n"
          + "\n"
          + "DummyOrigin-RevId: origin_ref2\n");

      assertThat(entries.get(1).getBody()).isEqualTo(""
          + "test summary\n"
          + "\n"
          + "DummyOrigin-RevId: origin_ref1\n");

      assertThat(entries.get(2).getBody()).isEqualTo("change\n");
    }
    return localRepo;
  }

  @Test
  public void testLabelInSameLabelGroupGroup() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    Writer<GitRevision> writer = firstCommitWriter();
    Files.write(workdir.resolve("test.txt"), "".getBytes(UTF_8));
    DummyRevision rev = new DummyRevision("first_commit");
    String msg = "This is a message\n"
        + "\n"
        + "That already has a label\n"
        + "THE_LABEL: value\n";
    writer.write(
        new TransformResult(workdir, rev, rev.getAuthor(), msg, rev, /*workflowName*/ "default",
                            TransformWorks.EMPTY_CHANGES, "first_commit", /*setRevId=*/ true,
                            ImmutableList::of, DummyOrigin.LABEL_NAME),
        destinationFiles,
        console);

    String body = lastCommit("HEAD").getBody();
    assertThat(body).isEqualTo("This is a message\n"
        + "\n"
        + "That already has a label\n"
        + "THE_LABEL: value\n"
        + "DummyOrigin-RevId: first_commit\n");
    // Double check that we can access it as a label.
    assertThat(ChangeMessage.parseMessage(body).labelsAsMultimap())
        .containsEntry("DummyOrigin-RevId", "first_commit");
  }

  @Test
  public void testFetchPushParamsSimple() throws Exception {
    GitDestination gitDestination = skylark.eval("result",
        "result = git.destination(\n"
            + "    url = 'file:///foo/bar/baz',\n"
            + "    push = 'test',\n"
            + ")");
    assertThat(gitDestination.getFetch()).isEqualTo("test");
    assertThat(gitDestination.getPush()).isEqualTo("test");
  }

  @Test
  public void testFetchPushParamsExplicit() throws Exception {
    GitDestination gitDestination = skylark.eval("result",
        "result = git.destination(\n"
            + "    url = 'file:///foo/bar/baz',\n"
            + "    fetch = 'test1',\n"
            + "    push = 'test2',\n"
            + ")");
    assertThat(gitDestination.getFetch()).isEqualTo("test1");
    assertThat(gitDestination.getPush()).isEqualTo("test2");
  }

  @Test
  public void testFetchPushParamsCliFlags() throws Exception {
    options.gitDestination.fetch = "aaa";
    options.gitDestination.push = "bbb";
    GitDestination gitDestination = skylark.eval("result",
        "result = git.destination(\n"
            + "    url = 'file:///foo/bar/baz',\n"
            + "    push = 'test',\n"
            + ")");
    assertThat(gitDestination.getFetch()).isEqualTo("aaa");
    assertThat(gitDestination.getPush()).isEqualTo("bbb");
  }

  @Test
  public void testVisit() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    DummyRevision ref1 = new DummyRevision("origin_ref1");
    DummyRevision ref2 = new DummyRevision("origin_ref2");
    Files.write(workdir.resolve("test.txt"), "Visit me".getBytes(UTF_8));
    process(firstCommitWriter(), ref1);
    Files.write(workdir.resolve("test.txt"), "Visit me soon".getBytes(UTF_8));
    process(newWriter(), ref2);

    List<Change<?>> visited = new ArrayList<>();
    newWriter().visitChanges(null,
        input -> {
          visited.add(input);
          return input.getLabels().get(DummyOrigin.LABEL_NAME).contains("origin_ref1")
              ? VisitResult.TERMINATE
              : VisitResult.CONTINUE;
        });
    assertThat(visited).hasSize(2);
    assertThat(visited.get(0).getLabels().get(DummyOrigin.LABEL_NAME)).containsExactly(
        "origin_ref2");
    assertThat(visited.get(1).getLabels().get(DummyOrigin.LABEL_NAME)).containsExactly(
        "origin_ref1");
  }

  @Test
  public void testCredentials() throws Exception {
    checkCredentials();
  }

  @Test
  public void testCredentials_localRepo() throws Exception {
    Path path = Files.createTempDirectory("local");
    options.gitDestination.localRepoPath = path.toString();
    GitRepository repository = checkCredentials();
    assertThat(repository.getGitDir().toString()).isEqualTo(path.resolve(".git").toString());
  }

  private GitRepository checkCredentials() throws IOException, RepoException, ValidationException {
    Path credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, "https://user:SECRET@somehost.com".getBytes(UTF_8));
    options.git.credentialHelperStorePath = credentialsFile.toString();

    GitRepository repository = destinationFirstCommit().getLocalRepo().load(console);
    UserPassword result = repository
        .credentialFill("https://somehost.com/foo/bar");

    assertThat(result.getUsername()).isEqualTo("user");
    assertThat(result.getPassword_BeCareful()).isEqualTo("SECRET");
    return repository;
  }

  private Writer<GitRevision> newWriter() throws ValidationException {
    return newWriter(/*dryRun=*/false);
  }

  private Writer<GitRevision> newWriter(boolean dryRun) throws ValidationException {
    return destination().newWriter(
        new WriterContext("piper_to_github", "TEST", dryRun, new DummyRevision("test"),
            Glob.ALL_FILES.roots()));
  }

  private Writer<GitRevision> firstCommitWriter() throws ValidationException {
    WriterContext writerContext =
        new WriterContext("piper_to_github", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());

    return destinationFirstCommit().newWriter(writerContext);
  }

  private void verifyDestinationStatus(Glob destinationFiles, DummyRevision revision)
      throws RepoException, ValidationException {
    assertThat(
        newWriter()
            .getDestinationStatus(destinationFiles, DummyOrigin.LABEL_NAME).getBaseline())
        .isEqualTo(revision.asString());
  }

  private WriterContext setUpForTestingTag() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    options.setForce(true);

    WriterContext writerContext =
        new WriterContext("piper_to_github", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());
    evalDestination().newWriter(writerContext).write(TransformResults.of(
        workdir, new DummyRevision("ref1")), destinationFiles, console);
    Files.write(workdir.resolve("test.txt"), "some content 2".getBytes(UTF_8));

    // push tag without tagMsg
    evalDestinationWithTag(null).newWriter(writerContext).write(TransformResults.of(
        workdir, new DummyRevision("ref2")), destinationFiles, console);

    Files.write(workdir.resolve("test.txt"), "some content 3".getBytes(UTF_8));
    return writerContext;
  }

  @Test
  public void testMapReferences() throws Exception {
    Files.write(workdir.resolve("test.txt"), "one".getBytes(UTF_8));
    Writer<GitRevision> writer = firstCommitWriter();
    process(writer, new DummyRevision("1"));

    Files.write(workdir.resolve("test.txt"), "two".getBytes(UTF_8));
    writer = newWriter();
    process(writer, new DummyRevision("2"));

    Files.write(workdir.resolve("test.txt"), "three".getBytes(UTF_8));
    process(writer, new DummyRevision("3"));

    writer.visitChanges(/*start=*/ null, ignore -> VisitResult.CONTINUE);

    Files.write(workdir.resolve("test.txt"), "four".getBytes(UTF_8));
    process(writer, new DummyRevision("4"));
  }

  @Test
  public void testDestinationReader() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    Path file = workdir.resolve("test.txt");
    Files.write(file, "some content".getBytes(UTF_8));
    Writer<GitRevision> writer = firstCommitWriter();
    process(writer, new DummyRevision("first_commit"));
    assertThat(
        writer.getDestinationReader(
            console,
            new Baseline<>(repo().resolveReference("HEAD").getSha1(), null),
            workdir)
            .readFile("test.txt")).contains("some content");
  }

}
