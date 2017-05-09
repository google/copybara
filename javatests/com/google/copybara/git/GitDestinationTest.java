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
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import com.google.copybara.Change;
import com.google.copybara.ChangeMessage;
import com.google.copybara.ChangeVisitable.VisitResult;
import com.google.copybara.Destination;
import com.google.copybara.Destination.Writer;
import com.google.copybara.Destination.WriterResult;
import com.google.copybara.EmptyChangeException;
import com.google.copybara.RepoException;
import com.google.copybara.TransformResult;
import com.google.copybara.ValidationException;
import com.google.copybara.authoring.Author;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.git.testing.GitTesting;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitDestinationTest {

  private String url;
  private String fetch;
  private String push;
  private boolean skipPush;

  private Path repoGitDir;
  private OptionsBuilder options;
  private TestingConsole console;
  private Glob destinationFiles;
  private SkylarkTestExecutor skylark;

  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  private Path workdir;

  @Before
  public void setup() throws Exception {
    repoGitDir = Files.createTempDirectory("GitDestinationTest-repoGitDir");
    workdir = Files.createTempDirectory("workdir");

    git("init", "--bare", repoGitDir.toString());
    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console)
        .setOutputRootToTmpDir();
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";
    destinationFiles = Glob.createGlob(ImmutableList.of("**"));

    url = "file://" + repoGitDir;
    skylark = new SkylarkTestExecutor(options, GitModule.class);
  }

  private GitRepository repo() {
    return repoForPath(repoGitDir);
  }

  private GitRepository repoForPath(Path path) {
    return new GitRepository(path, /*workTree=*/null, /*verbose=*/true, getGitEnv());
  }

  private String git(String... argv) throws RepoException {
    return repo()
        .git(repoGitDir, argv)
        .getStdout();
  }

  @Test
  public void errorIfUrlMissing() throws ValidationException {
    skylark.evalFails(""
            + "git.destination(\n"
            + "    fetch = 'master',\n"
            + "    push = 'master',\n"
            + ")",
        "missing mandatory positional argument 'url'");
  }

  @Test
  public void defaultPushBranch() throws ValidationException {
    GitDestination d = skylark.eval("result", "result = git.destination('file:///foo')");
    assertThat(d.getPush()).isEqualTo("master");
    assertThat(d.getFetch()).isEqualTo("master");
  }

  private GitDestination destinationFirstCommit()
      throws ValidationException {
    options.setForce(true);
    return evalDestination();
  }

  private GitDestination destination() throws ValidationException {
    options.setForce(false);
    return evalDestination();
  }

  private GitDestination evalDestination()
      throws ValidationException {
    return skylark.eval("result",
        String.format("result = git.destination(\n"
            + "    url = '%s',\n"
            + "    fetch = '%s',\n"
            + "    push = '%s',\n"
            + "    skip_push = %s,\n"
            + ")", url, fetch, push, skipPush ? "True" : "False"));
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

  private void process(Destination.Writer writer, DummyRevision originRef)
      throws ValidationException, RepoException, IOException {
    processWithBaseline(writer, originRef, /*baseline=*/ null);
  }

  private void processWithBaseline(Destination.Writer writer, DummyRevision originRef,
      String baseline)
      throws RepoException, ValidationException, IOException {
    processWithBaselineAndConfirmation(writer, originRef, baseline,
        /*askForConfirmation*/false);
  }

  private void processWithBaselineAndConfirmation(Destination.Writer writer,
      DummyRevision originRef,
      String baseline, boolean askForConfirmation)
      throws ValidationException, RepoException, IOException {
    TransformResult result = TransformResults.of(workdir, originRef);
    if (baseline != null) {
      result = result.withBaseline(baseline);
    }

    if (askForConfirmation) {
      result = result.withAskForConfirmation(true);
    }
    WriterResult destinationResult = writer.write(result, console);
    assertThat(destinationResult).isEqualTo(WriterResult.OK);
  }

  @Test
  public void processFirstCommit() throws Exception {
    fetch = "testPullFromRef";
    push = "testPushToRef";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    process(
        firstCommitWriter(),
        new DummyRevision("origin_ref"));

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "testPushToRef");
    assertThat(showResult).contains("some content");

    assertFilesInDir(1, "testPushToRef", ".");
    assertCommitCount(1, "testPushToRef");

    assertCommitHasOrigin("testPushToRef", "origin_ref");
  }

  @Test
  public void processUserAborts() throws Exception {
    console = new TestingConsole()
        .respondNo();
    fetch = "master";
    push = "master";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    thrown.expect(ValidationException.class);
    thrown.expectMessage("User aborted execution: did not confirm diff changes");
    processWithBaselineAndConfirmation(
        firstCommitWriter(),
        new DummyRevision("origin_ref"),
        /*baseline=*/ null, /*askForConfirmation=*/
        true);
  }

  @Test
  public void processEmptyDiff() throws Exception {
    console = new TestingConsole().respondYes();
    fetch = "master";
    push = "master";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    processWithBaselineAndConfirmation(
        firstCommitWriter(),
        new DummyRevision("origin_ref1"),
        /*baseline=*/ null, /*askForConfirmation=*/
        true);

    thrown.expect(EmptyChangeException.class);
    // process empty change. Shouldn't ask anything.
    processWithBaselineAndConfirmation(
        newWriter(),
        new DummyRevision("origin_ref2"),
        /*baseline=*/ null, /*askForConfirmation=*/
        true);
  }

  @Test
  public void processUserConfirms() throws Exception {
    console = new TestingConsole()
        .respondYes();
    fetch = "master";
    push = "master";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    processWithBaselineAndConfirmation(
        firstCommitWriter(),
        new DummyRevision("origin_ref"),
        /*baseline=*/ null, /*askForConfirmation=*/
        true);

    String change = git("--git-dir", repoGitDir.toString(), "show", "HEAD");
    // Validate that we really have pushed the commit.
    assertThat(change).contains("test summary");
    System.out.println(change);
    console.assertThat()
        .matchesNext(MessageType.PROGRESS, "Git Destination: Fetching file:.*")
        .matchesNext(MessageType.PROGRESS, "Git Destination: Checking out master")
        .matchesNext(MessageType.WARNING, "Git Destination: Cannot checkout 'FETCH_HEAD'."
            + " Ignoring baseline.")
        .matchesNext(MessageType.PROGRESS, "Git Destination: Cloning destination")
        .matchesNext(MessageType.PROGRESS, "Git Destination: Adding all files")
        .matchesNext(MessageType.PROGRESS, "Git Destination: Excluding files")
        .matchesNext(MessageType.PROGRESS, "Git Destination: Creating a local commit")
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
    fetch = "master";
    push = "master";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    DummyRevision ref = new DummyRevision("origin_ref");
    process(firstCommitWriter(), ref);
    thrown.expect(EmptyChangeException.class);
    thrown.expectMessage("empty change");
    process(newWriter(), ref);
  }

  @Test
  public void processEmptyCommitWithExcludes() throws Exception {
    fetch = "master";
    push = "master";
    Files.write(workdir.resolve("excluded"), "some content".getBytes());
    repo().withWorkTree(workdir)
        .add().files("excluded").run();
    repo().withWorkTree(workdir).simpleCommand("commit", "-m", "first commit");

    Files.delete(workdir.resolve("excluded"));

    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("excluded"));
    thrown.expect(EmptyChangeException.class);
    thrown.expectMessage("empty change");
    process(
        newWriter(),
        new DummyRevision("origin_ref"));
  }

  @Test
  public void processFetchRefDoesntExist() throws Exception {
    fetch = "testPullFromRef";
    push = "testPushToRef";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    thrown.expect(RepoException.class);
    thrown.expectMessage("'testPullFromRef' doesn't exist");
    process(
        newWriter(),
        new DummyRevision("origin_ref"));
  }

  @Test
  public void processCommitDeletesAndAddsFiles() throws Exception {
    fetch = "pullFromBar";
    push = "pushToFoo";

    Files.write(workdir.resolve("deleted_file"), "deleted content".getBytes());
    process(
        firstCommitWriter(),
        new DummyRevision("origin_ref"));
    git("--git-dir", repoGitDir.toString(), "branch", "pullFromBar", "pushToFoo");

    workdir = Files.createTempDirectory("workdir2");
    Files.write(workdir.resolve("1.txt"), "content 1".getBytes());
    Files.createDirectories(workdir.resolve("subdir"));
    Files.write(workdir.resolve("subdir/2.txt"), "content 2".getBytes());
    process(
        newWriter(),
        new DummyRevision("origin_ref"));

    // Make sure original file was deleted.
    assertFilesInDir(2, "pushToFoo", ".");
    assertFilesInDir(1, "pushToFoo", "subdir");
    // Make sure both commits are present.
    assertCommitCount(2, "pushToFoo");

    assertCommitHasOrigin("pushToFoo", "origin_ref");
  }

  @Test
  public void doNotDeleteIncludedFilesInNonMatchingSubdir() throws Exception {
    fetch = "master";
    push = "master";

    Files.createDirectories(workdir.resolve("foo"));
    Files.write(workdir.resolve("foo/bar"), "content".getBytes(UTF_8));
    repo().withWorkTree(workdir)
        .add().files("foo/bar").run();
    repo().withWorkTree(workdir).simpleCommand("commit", "-m", "message");

    Files.write(workdir.resolve("foo/baz"), "content".getBytes(UTF_8));

    // Note the glob foo/** does not match the directory itself called 'foo',
    // only the contents.
    destinationFiles = Glob.createGlob(ImmutableList.of("foo/**"));
    process(
        newWriter(),
        new DummyRevision("origin_ref"));

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("foo/bar", "content")
        .containsFile("foo/baz", "content")
        .containsNoMoreFiles();
  }

  @Test
  public void lastRevOnlyForAffectedRoots() throws Exception {
    fetch = "master";
    push = "master";

    Files.createDirectories(workdir.resolve("foo"));
    Files.createDirectories(workdir.resolve("bar"));
    Files.createDirectories(workdir.resolve("baz"));

    Files.write(workdir.resolve("foo/one"), "content".getBytes(UTF_8));
    Files.write(workdir.resolve("bar/one"), "content".getBytes(UTF_8));
    Files.write(workdir.resolve("baz/one"), "content".getBytes(UTF_8));

    DummyRevision ref1 = new DummyRevision("first");

    Glob firstGlob = Glob.createGlob(ImmutableList.of("foo/**", "bar/**"));
    Writer writer1 = destinationFirstCommit().newWriter(firstGlob, /*dryRun=*/false);
    process(writer1, ref1);

    Files.write(workdir.resolve("baz/one"), "content2".getBytes(UTF_8));
    DummyRevision ref2 = new DummyRevision("second");

    Writer writer2 = destination().newWriter(Glob.createGlob(ImmutableList.of("baz/**")),
        /*dryRun=*/false);
    process(writer2, ref2);

    // Recreate the writer since a destinationFirstCommit writer never looks
    // for a previous ref.
    assertThat(destination().newWriter(firstGlob, /*dryRun=*/false)
                   .getPreviousRef(ref1.getLabelName())).isEqualTo(
        ref1.asString());
    assertThat(writer2.getPreviousRef(ref2.getLabelName())).isEqualTo(ref2.asString());
  }

  @Test
  public void previousImportReference() throws Exception {
    fetch = "master";
    push = "master";

    Path file = workdir.resolve("test.txt");

    Files.write(file, "some content".getBytes());
    Destination.Writer writer =
        firstCommitWriter();
    assertThat(writer.getPreviousRef(DummyOrigin.LABEL_NAME)).isNull();
    process(writer, new DummyRevision("first_commit"));
    assertCommitHasOrigin("master", "first_commit");

    Files.write(file, "some other content".getBytes());
    writer = newWriter();
    assertThat(writer.getPreviousRef(DummyOrigin.LABEL_NAME)).isEqualTo("first_commit");
    process(writer, new DummyRevision("second_commit"));
    assertCommitHasOrigin("master", "second_commit");

    Files.write(file, "just more text".getBytes());
    writer = newWriter();
    assertThat(writer.getPreviousRef(DummyOrigin.LABEL_NAME)).isEqualTo("second_commit");
    process(writer, new DummyRevision("third_commit"));
    assertCommitHasOrigin("master", "third_commit");
  }

  @Test
  public void previousImportReference_nonCopybaraCommitsSinceLastMigrate() throws Exception {
    fetch = "master";
    push = "master";

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    process(
        firstCommitWriter(),
        new DummyRevision("first_commit"));

    Path scratchTree = Files.createTempDirectory("GitDestinationTest-scratchTree");
    for (int i = 0; i < 20; i++) {
      Files.write(scratchTree.resolve("excluded.dat"), new byte[] {(byte) i});
      repo().withWorkTree(scratchTree)
          .add().files("excluded.dat").run();
      repo().withWorkTree(scratchTree)
          .simpleCommand("commit", "-m", "excluded #" + i);
    }

    assertThat(
        newWriter()
                .getPreviousRef(DummyOrigin.LABEL_NAME))
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

  private String checkPreviousImportReferenceMultipleParents()
      throws IOException, RepoException, ValidationException {
    fetch = "b1";
    push = "b1";

    Path scratchTree = Files.createTempDirectory("GitDestinationTest-scratchTree");
    GitRepository scratchRepo = repo().withWorkTree(scratchTree);

    Files.write(scratchTree.resolve("master" + ".file"), ("master\n\n"
        + DummyOrigin.LABEL_NAME + ": should_not_happen").getBytes(UTF_8));
    scratchRepo.add().files("master" + ".file").run();
    scratchRepo.simpleCommand("commit", "-m", "master\n\n"
        + DummyOrigin.LABEL_NAME + ": should_not_happen");

    scratchRepo.simpleCommand("branch", "b1");
    scratchRepo.simpleCommand("branch", "b2");

    branchChange(scratchTree, scratchRepo, "b2", "b2-1\n\n"
        + DummyOrigin.LABEL_NAME + ": b2-origin");
    branchChange(scratchTree, scratchRepo, "b1", "b1-1\n\n"
        + DummyOrigin.LABEL_NAME + ": b1-origin");
    branchChange(scratchTree, scratchRepo, "b1", "b1-2");
    branchChange(scratchTree, scratchRepo, "b1", "b2-2");

    scratchRepo.simpleCommand("checkout", "b1");
    scratchRepo.simpleCommand("merge", "b2");
    return newWriter()
        .getPreviousRef(DummyOrigin.LABEL_NAME);
  }

  private void branchChange(Path scratchTree, GitRepository scratchRepo, final String branch,
      String msg) throws RepoException, IOException {
    scratchRepo.simpleCommand("checkout", branch);
    Files.write(scratchTree.resolve(branch + ".file"), msg.getBytes(UTF_8));
    scratchRepo.add().files(branch + ".file").run();
    scratchRepo.simpleCommand("commit", "-m", msg);
  }

  @Test
  public void writesOriginTimestampToAuthorField() throws Exception {
    fetch = "master";
    push = "master";

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    process(
        firstCommitWriter(),
        new DummyRevision("first_commit").withTimestamp(timeFromEpoch(1414141414)));
    GitTesting.assertAuthorTimestamp(repo(), "master", timeFromEpoch(1414141414));

    Files.write(workdir.resolve("test2.txt"), "some more content".getBytes());
    process(
        newWriter(),
        new DummyRevision("second_commit").withTimestamp(timeFromEpoch(1515151515)));
    GitTesting.assertAuthorTimestamp(repo(), "master", timeFromEpoch(1515151515));
  }

  static ZonedDateTime timeFromEpoch(long time) {
    return ZonedDateTime.ofInstant(Instant.ofEpochSecond(time), ZoneId.of("-07:00"));
  }

  @Test
  public void canOverrideUrl() throws Exception {
    Path newDestination = Files.createTempDirectory("canOverrideUrl");
    git("init", "--bare", newDestination.toString());
    fetch = "master";
    push = "master";

    options.gitDestination.url = "file://" + newDestination.toAbsolutePath();
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    process(
        firstCommitWriter(),
        new DummyRevision("first_commit"));
    GitTesting.assertCommitterLineMatches(repoForPath(newDestination),
        "master", "Bara Kopi <.*> [-+ 0-9]+");
    // No branches were created in the config file url.
    assertThat(repo().simpleCommand("branch").getStdout()).isEqualTo("");
  }

  @Test
  public void canOverrideCommitterName() throws Exception {
    fetch = "master";
    push = "master";

    options.gitDestination.committerName = "Bara Kopi";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    process(
        firstCommitWriter(),
        new DummyRevision("first_commit").withTimestamp(timeFromEpoch(1414141414)));
    GitTesting.assertCommitterLineMatches(repo(), "master", "Bara Kopi <.*> [-+ 0-9]+");

    options.gitDestination.committerName = "Piko Raba";
    Files.write(workdir.resolve("test.txt"), "some more content".getBytes());
    process(
        newWriter(),
        new DummyRevision("second_commit").withTimestamp(timeFromEpoch(1414141490)));
    GitTesting.assertCommitterLineMatches(repo(), "master", "Piko Raba <.*> [-+ 0-9+]+");
  }

  @Test
  public void canOverrideCommitterEmail() throws Exception {
    fetch = "master";
    push = "master";

    options.gitDestination.committerEmail = "bara.bara@gocha.gocha";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    process(
        firstCommitWriter(),
        new DummyRevision("first_commit").withTimestamp(timeFromEpoch(1414141414)));
    GitTesting.assertCommitterLineMatches(
        repo(), "master", ".* <bara[.]bara@gocha[.]gocha> [-+ 0-9]+");

    options.gitDestination.committerEmail = "kupo.kupo@tan.kou";
    Files.write(workdir.resolve("test.txt"), "some more content".getBytes());
    process(
        newWriter(),
        new DummyRevision("second_commit").withTimestamp(timeFromEpoch(1414141490)));
    GitTesting.assertCommitterLineMatches(
        repo(), "master", ".* <kupo[.]kupo@tan[.]kou> [-+ 0-9]+");
  }

  @Test
  public void gitUserNameMustBeConfigured() throws Exception {
    options.gitDestination.committerName = "";
    options.gitDestination.committerEmail = "foo@bara";
    fetch = "master";
    push = "master";

    thrown.expect(RepoException.class);
    thrown.expectMessage("'user.name' and/or 'user.email' are not configured.");
    process(
        firstCommitWriter(),
        new DummyRevision("first_commit"));
  }

  @Test
  public void gitUserEmailMustBeConfigured() throws Exception {
    options.gitDestination.committerName = "Foo Bara";
    options.gitDestination.committerEmail = "";
    fetch = "master";
    push = "master";

    thrown.expect(RepoException.class);
    thrown.expectMessage("'user.name' and/or 'user.email' are not configured.");
    process(
        firstCommitWriter(),
        new DummyRevision("first_commit"));
  }

  @Test
  public void authorPropagated() throws Exception {
    fetch = "master";
    push = "master";

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    DummyRevision firstCommit = new DummyRevision("first_commit")
        .withAuthor(new Author("Foo Bar", "foo@bar.com"))
        .withTimestamp(timeFromEpoch(1414141414));
    process(
        firstCommitWriter(),
        firstCommit);

    assertCommitHasAuthor("master", new Author("Foo Bar", "foo@bar.com"));
  }

  @Test
  public void canExcludeDestinationPathFromWorkflow() throws Exception {
    fetch = "master";
    push = "master";

    Path scratchTree = Files.createTempDirectory("GitDestinationTest-scratchTree");
    Files.write(scratchTree.resolve("excluded.txt"), "some content".getBytes(UTF_8));
    repo().withWorkTree(scratchTree)
        .add().files("excluded.txt").run();
    repo().withWorkTree(scratchTree)
        .simpleCommand("commit", "-m", "message");

    Files.write(workdir.resolve("normal_file.txt"), "some more content".getBytes(UTF_8));
    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("excluded.txt"));
    process(
        newWriter(),
        new DummyRevision("ref"));
    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("excluded.txt", "some content")
        .containsFile("normal_file.txt", "some more content")
        .containsNoMoreFiles();
  }

  @Test
  public void excludedDestinationPathsIgnoreGitTreeFiles() throws Exception {
    fetch = "master";
    push = "master";

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

    process(
        newWriter(),
        new DummyRevision("ref"));
    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("notgit/HEAD", "some content")
        .containsFile("normal_file.txt", "some more content")
        .containsNoMoreFiles();
  }

  @Test
  public void processWithBaseline() throws Exception {
    fetch = "master";
    push = "master";
    DummyRevision ref = new DummyRevision("origin_ref");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    Files.write(workdir.resolve("excluded"), "some content".getBytes());
    process(firstCommitWriter(), ref);
    String firstCommit = repo().parseRef("HEAD");
    Files.write(workdir.resolve("test.txt"), "new content".getBytes());
    process(newWriter(), ref);

    // Lets exclude now 'excluded' so that we check that the rebase correctly ignores
    // the missing file (IOW, it doesn't delete the file in the commit).
    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("excluded"));

    Files.delete(workdir.resolve("excluded"));
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    Files.write(workdir.resolve("other.txt"), "other file".getBytes());
    processWithBaseline(
        newWriter(), ref, firstCommit);

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "new content")
        .containsFile("other.txt", "other file")
        .containsFile("excluded", "some content")
        .containsNoMoreFiles();
  }

  @Test
  public void processWithBaselineSameFileConflict() throws Exception {
    fetch = "master";
    push = "master";
    DummyRevision ref = new DummyRevision("origin_ref");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    process(firstCommitWriter(), ref);
    String firstCommit = repo().parseRef("HEAD");
    Files.write(workdir.resolve("test.txt"), "new content".getBytes());
    process(newWriter(), ref);

    Files.write(workdir.resolve("test.txt"), "conflict content".getBytes());
    thrown.expect(RebaseConflictException.class);
    thrown.expectMessage("conflict in test.txt");
    processWithBaseline(
        newWriter(), ref, firstCommit);
  }

  @Test
  public void processWithBaselineSameFileNoConflict() throws Exception {
    fetch = "master";
    push = "master";
    String text = "";
    for (int i = 0; i < 1000; i++) {
      text += "Line " + i + "\n";
    }
    DummyRevision ref = new DummyRevision("origin_ref");

    Files.write(workdir.resolve("test.txt"), text.getBytes());
    process(firstCommitWriter(), ref);
    String firstCommit = repo().parseRef("HEAD");
    Files.write(workdir.resolve("test.txt"),
        text.replace("Line 200", "Line 200 Modified").getBytes());
    process(newWriter(), ref);

    Files.write(workdir.resolve("test.txt"),
        text.replace("Line 500", "Line 500 Modified").getBytes());

    processWithBaseline(
        newWriter(), ref, firstCommit);

    GitTesting.assertThatCheckout(repo(), "master").containsFile("test.txt",
        text.replace("Line 200", "Line 200 Modified")
            .replace("Line 500", "Line 500 Modified")).containsNoMoreFiles();
  }

  @Test
  public void processWithBaselineNotFound() throws Exception {
    fetch = "master";
    push = "master";
    DummyRevision ref = new DummyRevision("origin_ref");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    process(firstCommitWriter(), ref);

    Files.write(workdir.resolve("test.txt"), "more content".getBytes());
    thrown.expect(RepoException.class);
    thrown.expectMessage("Cannot find baseline 'I_dont_exist' from fetch reference 'master'");
    processWithBaseline(newWriter(), ref, "I_dont_exist");
  }

  @Test
  public void processWithBaselineNotFoundMasterNotFound() throws Exception {
    fetch = "test_test_test";
    push = "master";

    Files.write(workdir.resolve("test.txt"), "more content".getBytes());
    thrown.expect(RepoException.class);
    thrown.expectMessage(
        "Cannot find baseline 'I_dont_exist' and fetch reference 'test_test_test'");
    processWithBaseline(firstCommitWriter(), new DummyRevision("origin_ref"), "I_dont_exist");
  }

  @Test
  public void pushSequenceOfChangesToReviewBranch() throws Exception {
    fetch = "master";
    push = "refs_for_master";

    Destination.Writer writer =
        firstCommitWriter();

    Files.write(workdir.resolve("test42"), "42".getBytes(UTF_8));
    WriterResult result =
        writer.write(TransformResults.of(workdir, new DummyRevision("ref1")), console);
    assertThat(result).isEqualTo(WriterResult.OK);
    String firstCommitHash = repo().parseRef("refs_for_master");

    Files.write(workdir.resolve("test99"), "99".getBytes(UTF_8));
    result = writer.write(TransformResults.of(workdir, new DummyRevision("ref2")), console);
    assertThat(result).isEqualTo(WriterResult.OK);

    // Make sure parent of second commit is the first commit.
    assertThat(repo().parseRef("refs_for_master~1")).isEqualTo(firstCommitHash);

    // Make sure commits have correct file content.
    GitTesting.assertThatCheckout(repo(), "refs_for_master~1")
        .containsFile("test42", "42")
        .containsNoMoreFiles();
    GitTesting.assertThatCheckout(repo(), "refs_for_master")
        .containsFile("test42", "42")
        .containsFile("test99", "99")
        .containsNoMoreFiles();
  }

  @Test
  public void testGitIgnoreIncluded() throws Exception {
    fetch = "master";
    push = "master";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    Files.write(workdir.resolve(".gitignore"), ".gitignore\n".getBytes());
    DummyRevision ref = new DummyRevision("origin_ref");
    process(firstCommitWriter(), ref);
    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "some content")
        .containsFile(".gitignore", ".gitignore\n")
        .containsNoMoreFiles();
  }

  @Test
  public void testGitIgnoreExcluded() throws Exception {
    fetch = "master";
    push = "master";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    Path scratchTree = Files.createTempDirectory("GitDestinationTest-testGitIgnoreExcluded");
    Files.write(scratchTree.resolve(".gitignore"), ".gitignore\n".getBytes(UTF_8));
    repo().withWorkTree(scratchTree).add().force().files(".gitignore").run();
    repo().withWorkTree(scratchTree).simpleCommand("commit", "-a", "-m", "gitignore file");

    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of(".gitignore"));

    DummyRevision ref = new DummyRevision("origin_ref");
    process(newWriter(), ref);
    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "some content")
        .containsFile(".gitignore", ".gitignore\n")
        .containsNoMoreFiles();
  }

  @Test
  public void testLocalRepo() throws Exception {
    checkLocalRepo(false);

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "another content")
        .containsNoMoreFiles();
  }

  @Test
  public void testLocalRepoSkipPush() throws Exception {
    skipPush = true;
    GitRepository localRepo = checkLocalRepo(false);

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("foo", "foo\n")
        .containsNoMoreFiles();

    // A simple push without origin is able to update the correct destination reference
    localRepo.simpleCommand("push");

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "another content")
        .containsNoMoreFiles();
  }

  @Test
  public void testDryRun() throws Exception {
    fetch = "master";
    push = "master";

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    Path scratchTree = Files.createTempDirectory("GitDestinationTest-testLocalRepo");
    Files.write(scratchTree.resolve("foo"), "foo\n".getBytes(UTF_8));
    repo().withWorkTree(scratchTree).add().force().files("foo").run();
    repo().withWorkTree(scratchTree).simpleCommand("commit", "-a", "-m", "change");

    Writer writer = destination().newWriter(destinationFiles, /*dryRun=*/ true);
    process(writer, new DummyRevision("origin_ref1"));

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("foo", "foo\n")
        .containsNoMoreFiles();

    // Run again without dry run
    writer = destination().newWriter(destinationFiles, /*dryRun=*/ false);
    process(writer, new DummyRevision("origin_ref1"));

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
  }

  @Test
  public void testLocalRepoSkipPushFlag() throws Exception {
    GitRepository localRepo = checkLocalRepo(true);

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("foo", "foo\n")
        .containsNoMoreFiles();

    // A simple push without origin is able to update the correct destination reference
    localRepo.simpleCommand("push");

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "another content")
        .containsNoMoreFiles();
  }

  @Test
  public void testMultipleRefs() throws Exception {
    Path scratchTree = Files.createTempDirectory("GitDestinationTest-testLocalRepo");
    Files.write(scratchTree.resolve("base"), "base\n".getBytes(UTF_8));
    repo().withWorkTree(scratchTree).add().force().files("base").run();
    repo().withWorkTree(scratchTree).simpleCommand("commit", "-a", "-m", "base");

    GitRevision master = repo().resolveReference("master", /*contextRef=*/null);

    repo().simpleCommand("update-ref","refs/other/master", master.asString());

    checkLocalRepo(true);
  }

  private GitRepository checkLocalRepo(boolean skipPushFlag)
      throws Exception {
    fetch = "master";
    push = "master";

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    Path scratchTree = Files.createTempDirectory("GitDestinationTest-testLocalRepo");
    Files.write(scratchTree.resolve("foo"), "foo\n".getBytes(UTF_8));
    repo().withWorkTree(scratchTree).add().force().files("foo").run();
    repo().withWorkTree(scratchTree).simpleCommand("commit", "-a", "-m", "change");

    options.gitDestination.skipPush = skipPushFlag;
    Path localPath = Files.createTempDirectory("local_repo");

    options.gitDestination.localRepoPath = localPath.toString();
    Writer writer = newWriter();
    process(writer, new DummyRevision("origin_ref1"));

    //    Path localPath = Files.createTempDirectory("local_repo");
    GitRepository localRepo = GitRepository.initScratchRepo(/*verbose=*/true, localPath,
        getGitEnv());

    GitTesting.assertThatCheckout(localRepo, "master")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();

    Files.write(workdir.resolve("test.txt"), "another content".getBytes());
    process(writer, new DummyRevision("origin_ref2"));

    GitTesting.assertThatCheckout(localRepo, "master")
        .containsFile("test.txt", "another content")
        .containsNoMoreFiles();

    ImmutableList<GitLogEntry> entries = localRepo.log("HEAD").run();
    assertThat(entries.get(0).getBody()).isEqualTo(""
        + "test summary\n"
        + "\n"
        + "DummyOrigin-RevId: origin_ref2\n");

    assertThat(entries.get(1).getBody()).isEqualTo(""
        + "test summary\n"
        + "\n"
        + "DummyOrigin-RevId: origin_ref1\n");

    assertThat(entries.get(2).getBody()).isEqualTo("change\n");

    return localRepo;
  }

  @Test
  public void testLabelInSameLabelGroupGroup() throws Exception {
    fetch = "master";
    push = "master";
    Writer writer = firstCommitWriter();
    Files.write(workdir.resolve("test.txt"), "".getBytes());
    DummyRevision rev = new DummyRevision("first_commit");
    String msg = "This is a message\n"
        + "\n"
        + "That already has a label\n"
        + "THE_LABEL: value\n";
    writer.write(new TransformResult(workdir, rev, rev.getAuthor(), msg, rev), console);

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
    fetch = "master";
    push = "master";
    DummyRevision ref1 = new DummyRevision("origin_ref1");
    DummyRevision ref2 = new DummyRevision("origin_ref2");
    Files.write(workdir.resolve("test.txt"), "Visit me".getBytes());
    process(
        firstCommitWriter(), ref1);
    Files.write(workdir.resolve("test.txt"), "Visit me soon".getBytes());
    process(newWriter(), ref2);

    final List<Change<?>> visited = new ArrayList<>();
    destination().newReader(destinationFiles).visitChanges(null,
        input -> {
          visited.add(input);
          return input.getLabels().get(DummyOrigin.LABEL_NAME).equals("origin_ref1")
              ? VisitResult.TERMINATE
              : VisitResult.CONTINUE;
        });
    assertThat(visited).hasSize(2);
    assertThat(visited.get(0).getLabels().get(DummyOrigin.LABEL_NAME)).isEqualTo("origin_ref2");
    assertThat(visited.get(1).getLabels().get(DummyOrigin.LABEL_NAME)).isEqualTo("origin_ref1");
  }

  private Writer newWriter() throws ValidationException {
    return destination().newWriter(destinationFiles, /*dryRun=*/ false);
  }

  private Writer firstCommitWriter() throws ValidationException {
    return destinationFirstCommit().newWriter(destinationFiles, /*dryRun=*/ false);
  }
}
