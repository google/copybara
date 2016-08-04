// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.git.GitRepository.CURRENT_PROCESS_ENVIRONMENT;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Author;
import com.google.copybara.Destination;
import com.google.copybara.Destination.WriterResult;
import com.google.copybara.EmptyChangeException;
import com.google.copybara.RepoException;
import com.google.copybara.TransformResult;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.git.testing.GitTesting;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.DummyOriginalAuthor;
import com.google.copybara.testing.DummyReference;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.util.PathMatcherBuilder;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.util.console.testing.TestingConsole.MessageType;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
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

  private Path repoGitDir;
  private OptionsBuilder options;
  private TestingConsole console;
  private ImmutableList<String> excludedDestinationPaths;
  private SkylarkTestExecutor skylark;

  @Rule
  public ExpectedException thrown = ExpectedException.none();
  private Path workdir;

  @Before
  public void setup() throws Exception {
    repoGitDir = Files.createTempDirectory("GitDestinationTest-repoGitDir");
    workdir = Files.createTempDirectory("workdir");

    git("init", "--bare", repoGitDir.toString());
    console = new TestingConsole();
    options = new OptionsBuilder().setConsole(console);
    options.git.gitCommitterEmail = "commiter@email";
    options.git.gitCommitterName = "Bara Kopi";
    excludedDestinationPaths = ImmutableList.of();

    url = "file://" + repoGitDir;
    skylark = new SkylarkTestExecutor(options, Git.class);
  }

  private GitRepository repo() {
    return new GitRepository(repoGitDir, /*workTree=*/null, /*verbose=*/true,
        CURRENT_PROCESS_ENVIRONMENT);
  }

  private String git(String... argv) throws RepoException {
    return repo()
        .git(repoGitDir, argv)
        .getStdout();
  }

  @Test
  public void errorIfUrlMissing() throws ConfigValidationException {
    skylark.evalFails(
        "git.destination(\n"
        + "    fetch = 'master',\n"
        + "    push = 'master',\n"
        + ")",
        "missing mandatory positional argument 'url'");
  }

  @Test
  public void errorIfFetchMissing() throws ConfigValidationException {
    skylark.evalFails(
        "git.destination(\n"
            + "    url = 'file:///foo',\n"
            + "    push = 'master',\n"
            + ")",
        "missing mandatory positional argument 'fetch'");
  }

  @Test
  public void errorIfPushMissing() throws ConfigValidationException {
    skylark.evalFails(
        "git.destination(\n"
            + "    url = 'file:///foo',\n"
            + "    fetch = 'master',\n"
            + ")",
        "missing mandatory positional argument 'push'");
  }

  private GitDestination destinationFirstCommit()
      throws ConfigValidationException {
    options.git.gitFirstCommit = true;
    return evalDestination();
  }

  private GitDestination destination() throws ConfigValidationException {
    options.git.gitFirstCommit = false;
    return evalDestination();
  }

  private GitDestination evalDestination()
      throws ConfigValidationException {
    return skylark.eval("result",
        String.format("result = git.destination(\n"
            + "    url = '%s',\n"
            + "    fetch = '%s',\n"
            + "    push = '%s',\n"
            + ")", url, fetch, push));
  }

  private void assertFilesInDir(int expected, String ref, String path) throws Exception {
    String lsResult = git("--git-dir", repoGitDir.toString(), "ls-tree", ref, path);
    assertThat(lsResult.split("\n")).hasLength(expected);
  }

  private void assertCommitCount(int expected, String ref) throws Exception {
    String logResult = git("--git-dir", repoGitDir.toString(), "log", "--oneline", ref);
    assertThat(logResult.split("\n")).hasLength(expected);
  }

  private void assertCommitHasOrigin(String branch, String originRef) throws RepoException {
    assertThat(git("--git-dir", repoGitDir.toString(), "log", "-n1", branch))
        .contains("\n    " + DummyOrigin.LABEL_NAME + ": " + originRef + "\n");
  }

  private void assertCommitHasAuthor(String branch, Author author) throws RepoException {
    assertThat(git("--git-dir", repoGitDir.toString(), "log", "-n1",
        "--pretty=format:\"%an <%ae>\"", branch))
        .isEqualTo("\"" + author + "\"");
  }

  private void process(GitDestination destination, DummyReference originRef)
      throws ConfigValidationException, RepoException, IOException {
    processWithBaseline(destination, originRef, /*baseline=*/ null);
  }

  private void processWithBaseline(GitDestination destination, DummyReference originRef,
      String baseline)
      throws RepoException, ConfigValidationException, IOException {
    processWithBaselineAndConfirmation(destination, originRef, baseline,
        /*askForConfirmation*/false);
  }

  private void processWithBaselineAndConfirmation(GitDestination destination,
      DummyReference originRef,
      String baseline, boolean askForConfirmation)
      throws ConfigValidationException, RepoException, IOException {
    TransformResult result = TransformResults.of(workdir,
        originRef,
        PathMatcherBuilder.create(FileSystems.getDefault(), excludedDestinationPaths,
            ImmutableList.<String>of()));
    if (baseline != null) {
      result = result.withBaseline(baseline);
    }

    if (askForConfirmation) {
      result = result.withAskForConfirmation(true);
    }
    WriterResult destinationResult = destination.newWriter().write(result, console);
    assertThat(destinationResult).isEqualTo(WriterResult.OK);
  }

  @Test
  public void processFirstCommit() throws Exception {
    fetch = "testPullFromRef";
    push = "testPushToRef";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    process(destinationFirstCommit(), new DummyReference("origin_ref"));

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
    thrown.expect(RepoException.class);
    thrown.expectMessage("User aborted execution: did not confirm diff changes");
    processWithBaselineAndConfirmation(destinationFirstCommit(), new DummyReference("origin_ref"),
        /*baseline=*/null, /*askForConfirmation=*/true);
  }

  @Test
  public void processUserConfirms() throws Exception {
    console = new TestingConsole()
        .respondYes();
    fetch = "master";
    push = "master";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    processWithBaselineAndConfirmation(destinationFirstCommit(), new DummyReference("origin_ref"),
        /*baseline=*/null, /*askForConfirmation=*/true);

    console.assertThat()
        .matchesNext(MessageType.PROGRESS, "Git Destination: Fetching file:.*")
        .matchesNext(MessageType.PROGRESS, "Git Destination: Adding files for push")
        .equalsNext(MessageType.INFO, "\n"
            + "diff --git a/test.txt b/test.txt\n"
            + "new file mode 100644\n"
            + "index 0000000..f0eec86\n"
            + "--- /dev/null\n"
            + "+++ b/test.txt\n"
            + "@@ -0,0 +1 @@\n"
            + "+some content\n"
            + "\\ No newline at end of file\n")
        .matchesNext(MessageType.WARNING, "Proceed with push to.*[?]")
        .matchesNext(MessageType.PROGRESS, "Git Destination: Pushing to .*")
        .containsNoMoreMessages();
  }

  @Test
  public void processEmptyCommit() throws Exception {
    fetch = "master";
    push = "master";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    DummyReference ref = new DummyReference("origin_ref");
    process(destinationFirstCommit(), ref);
    thrown.expect(EmptyChangeException.class);
    thrown.expectMessage("empty change");
    process(destination(), ref);
  }

  @Test
  public void processFetchRefDoesntExist() throws Exception {
    fetch = "testPullFromRef";
    push = "testPushToRef";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    thrown.expect(RepoException.class);
    thrown.expectMessage("'testPullFromRef' doesn't exist");
    process(destination(), new DummyReference("origin_ref"));
  }

  @Test
  public void processCommitDeletesAndAddsFiles() throws Exception {
    fetch = "pullFromBar";
    push = "pushToFoo";

    Files.write(workdir.resolve("deleted_file"), "deleted content".getBytes());
    process(destinationFirstCommit(), new DummyReference("origin_ref"));
    git("--git-dir", repoGitDir.toString(), "branch", "pullFromBar", "pushToFoo");

    workdir = Files.createTempDirectory("workdir2");
    Files.write(workdir.resolve("1.txt"), "content 1".getBytes());
    Files.createDirectories(workdir.resolve("subdir"));
    Files.write(workdir.resolve("subdir/2.txt"), "content 2".getBytes());
    process(destination(), new DummyReference("origin_ref"));

    // Make sure original file was deleted.
    assertFilesInDir(2, "pushToFoo", ".");
    assertFilesInDir(1, "pushToFoo", "subdir");
    // Make sure both commits are present.
    assertCommitCount(2, "pushToFoo");

    assertCommitHasOrigin("pushToFoo", "origin_ref");
  }

  @Test
  public void previousImportReference() throws Exception {
    fetch = "master";
    push = "master";

    Path file = workdir.resolve("test.txt");

    Files.write(file, "some content".getBytes());
    GitDestination destination1 = destinationFirstCommit();
    assertThat(destination1.getPreviousRef(DummyOrigin.LABEL_NAME)).isNull();
    process(destination1, new DummyReference("first_commit"));
    assertCommitHasOrigin("master", "first_commit");

    Files.write(file, "some other content".getBytes());
    GitDestination destination2 = destination();
    assertThat(destination2.getPreviousRef(DummyOrigin.LABEL_NAME)).isEqualTo("first_commit");
    process(destination2, new DummyReference("second_commit"));
    assertCommitHasOrigin("master", "second_commit");

    Files.write(file, "just more text".getBytes());
    GitDestination destination3 = destination();
    assertThat(destination3.getPreviousRef(DummyOrigin.LABEL_NAME)).isEqualTo("second_commit");
    process(destination3, new DummyReference("third_commit"));
    assertCommitHasOrigin("master", "third_commit");
  }

  @Test
  public void previousImportReference_nonCopybaraCommitsSinceLastMigrate() throws Exception {
    fetch = "master";
    push = "master";

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    process(destinationFirstCommit(), new DummyReference("first_commit"));

    Path scratchTree = Files.createTempDirectory("GitDestinationTest-scratchTree");
    for (int i = 0; i < 20; i++) {
      Files.write(scratchTree.resolve("excluded.dat"), new byte[] {(byte) i});
      repo().withWorkTree(scratchTree)
          .simpleCommand("add", "excluded.dat");
      repo().withWorkTree(scratchTree)
          .simpleCommand("commit", "-m", "excluded #" + i);
    }

    assertThat(destination().getPreviousRef(DummyOrigin.LABEL_NAME))
        .isEqualTo("first_commit");
  }

  @Test
  public void previousImportReferenceIsBeforeACommitWithMultipleParents() throws Exception {
    fetch = "master";
    push = "master";

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    process(destinationFirstCommit(), new DummyReference("first_commit"));

    Path scratchTree = Files.createTempDirectory("GitDestinationTest-scratchTree");
    GitRepository scratchRepo = repo().withWorkTree(scratchTree);

    scratchRepo.simpleCommand("checkout", "-b", "b1");
    Files.write(scratchTree.resolve("b1.file"), new byte[] {1});
    scratchRepo.simpleCommand("add", "b1.file");
    scratchRepo.simpleCommand("commit", "-m", "b1");

    scratchRepo.simpleCommand("checkout", "-b", "b2", "master");
    Files.write(scratchTree.resolve("b2.file"), new byte[] {2});
    scratchRepo.simpleCommand("add", "b2.file");
    scratchRepo.simpleCommand("commit", "-m", "b2");

    scratchRepo.simpleCommand("checkout", "master");
    scratchRepo.simpleCommand("merge", "b1");
    scratchRepo.simpleCommand("merge", "b2");

    thrown.expect(RepoException.class);
    thrown.expectMessage(
        "Found commit with multiple parents (merge commit) when looking for "
        + DummyOrigin.LABEL_NAME + ".");
    destination().getPreviousRef(DummyOrigin.LABEL_NAME);
  }

  @Test
  public void writesOriginTimestampToAuthorField() throws Exception {
    fetch = "master";
    push = "master";

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    process(destinationFirstCommit(), new DummyReference("first_commit").withTimestamp(1414141414));
    GitTesting.assertAuthorTimestamp(repo(), "master", 1414141414);

    Files.write(workdir.resolve("test2.txt"), "some more content".getBytes());
    process(destination(), new DummyReference("second_commit").withTimestamp(1515151515));
    GitTesting.assertAuthorTimestamp(repo(), "master", 1515151515);
  }

  @Test
  public void canOverrideCommitterName() throws Exception {
    fetch = "master";
    push = "master";

    options.git.gitCommitterName = "Bara Kopi";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    process(destinationFirstCommit(), new DummyReference("first_commit").withTimestamp(1414141414));
    GitTesting.assertCommitterLineMatches(repo(), "master", "Bara Kopi <.*> [-+ 0-9]+");

    options.git.gitCommitterName = "Piko Raba";
    Files.write(workdir.resolve("test.txt"), "some more content".getBytes());
    process(destination(), new DummyReference("second_commit").withTimestamp(1414141490));
    GitTesting.assertCommitterLineMatches(repo(), "master", "Piko Raba <.*> [-+ 0-9+]+");
  }

  @Test
  public void canOverrideCommitterEmail() throws Exception {
    fetch = "master";
    push = "master";

    options.git.gitCommitterEmail = "bara.bara@gocha.gocha";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    process(destinationFirstCommit(), new DummyReference("first_commit").withTimestamp(1414141414));
    GitTesting.assertCommitterLineMatches(
        repo(), "master", ".* <bara[.]bara@gocha[.]gocha> [-+ 0-9]+");

    options.git.gitCommitterEmail = "kupo.kupo@tan.kou";
    Files.write(workdir.resolve("test.txt"), "some more content".getBytes());
    process(destination(), new DummyReference("second_commit").withTimestamp(1414141490));
    GitTesting.assertCommitterLineMatches(
        repo(), "master", ".* <kupo[.]kupo@tan[.]kou> [-+ 0-9]+");
  }

  @Test
  public void gitUserNameMustBeConfigured() throws Exception {
    options.git.gitCommitterName = "";
    options.git.gitCommitterEmail = "foo@bara";
    fetch = "master";
    push = "master";

    thrown.expect(RepoException.class);
    thrown.expectMessage("'user.name' and/or 'user.email' are not configured.");
    process(destinationFirstCommit(), new DummyReference("first_commit"));
  }

  @Test
  public void gitUserEmailMustBeConfigured() throws Exception {
    options.git.gitCommitterName = "Foo Bara";
    options.git.gitCommitterEmail = "";
    fetch = "master";
    push = "master";

    thrown.expect(RepoException.class);
    thrown.expectMessage("'user.name' and/or 'user.email' are not configured.");
    process(destinationFirstCommit(), new DummyReference("first_commit"));
  }

  @Test
  public void authorPropagated() throws Exception {
    fetch = "master";
    push = "master";

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    DummyReference firstCommit = new DummyReference("first_commit")
        .withOriginalAuthor(new DummyOriginalAuthor("Foo Bar", "foo@bar.com"))
        .withTimestamp(1414141414);
    process(destinationFirstCommit(), firstCommit);

    assertCommitHasAuthor("master", new Author("Foo Bar", "foo@bar.com"));
  }

  @Test
  public void canExcludeDestinationPathFromWorkflow() throws Exception {
    fetch = "master";
    push = "master";

    Path scratchTree = Files.createTempDirectory("GitDestinationTest-scratchTree");
    Files.write(scratchTree.resolve("excluded.txt"), "some content".getBytes(UTF_8));
    repo().withWorkTree(scratchTree)
        .simpleCommand("add", "excluded.txt");
    repo().withWorkTree(scratchTree)
        .simpleCommand("commit", "-m", "message");

    Files.write(workdir.resolve("normal_file.txt"), "some more content".getBytes(UTF_8));
    excludedDestinationPaths = ImmutableList.of("excluded.txt");
    process(destination(), new DummyReference("ref"));
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
        .simpleCommand("add", "notgit/HEAD");
    repo().withWorkTree(scratchTree)
        .simpleCommand("commit", "-m", "message");

    Files.write(workdir.resolve("normal_file.txt"), "some more content".getBytes(UTF_8));

    // Make sure this glob does not cause .git/HEAD to be added.
    excludedDestinationPaths = ImmutableList.of("**/HEAD");

    process(destination(), new DummyReference("ref"));
    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("notgit/HEAD", "some content")
        .containsFile("normal_file.txt", "some more content")
        .containsNoMoreFiles();
  }

  @Test
  public void processWithBaseline() throws Exception {
    fetch = "master";
    push = "master";
    DummyReference ref = new DummyReference("origin_ref");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    process(destinationFirstCommit(), ref);
    String firstCommit = repo().revParse("HEAD");
    Files.write(workdir.resolve("test.txt"), "new content".getBytes());
    process(destination(), ref);

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    Files.write(workdir.resolve("other.txt"), "other file".getBytes());
    processWithBaseline(destination(), ref, firstCommit);

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "new content")
        .containsFile("other.txt", "other file")
        .containsNoMoreFiles();
  }

  @Test
  public void processWithBaselineSameFileConflict() throws Exception {
    fetch = "master";
    push = "master";
    DummyReference ref = new DummyReference("origin_ref");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    process(destinationFirstCommit(), ref);
    String firstCommit = repo().revParse("HEAD");
    Files.write(workdir.resolve("test.txt"), "new content".getBytes());
    process(destination(), ref);

    Files.write(workdir.resolve("test.txt"), "conflict content".getBytes());
    thrown.expect(RebaseConflictException.class);
    thrown.expectMessage("conflict in test.txt");
    processWithBaseline(destination(), ref, firstCommit);
  }

  @Test
  public void processWithBaselineSameFileNoConflict() throws Exception {
    fetch = "master";
    push = "master";
    String text = "";
    for (int i = 0; i < 1000; i++) {
      text += "Line " + i + "\n";
    }
    DummyReference ref = new DummyReference("origin_ref");

    Files.write(workdir.resolve("test.txt"), text.getBytes());
    process(destinationFirstCommit(), ref);
    String firstCommit = repo().revParse("HEAD");
    Files.write(workdir.resolve("test.txt"),
        text.replace("Line 200", "Line 200 Modified").getBytes());
    process(destination(), ref);

    Files.write(workdir.resolve("test.txt"),
        text.replace("Line 500", "Line 500 Modified").getBytes());

    processWithBaseline(destination(), ref, firstCommit);

    GitTesting.assertThatCheckout(repo(), "master").containsFile("test.txt",
        text.replace("Line 200", "Line 200 Modified")
            .replace("Line 500", "Line 500 Modified")).containsNoMoreFiles();
  }

  @Test
  public void pushSequenceOfChangesToReviewBranch() throws Exception {
    fetch = "master";
    push = "refs_for_master";

    Destination.Writer writer = destinationFirstCommit().newWriter();

    Files.write(workdir.resolve("test42"), "42".getBytes(UTF_8));
    WriterResult result = writer.write(TransformResults.of(workdir, new DummyReference("ref1")), console);
    assertThat(result).isEqualTo(WriterResult.OK);
    String firstCommitHash = repo().simpleCommand("rev-parse", "refs_for_master").getStdout();

    Files.write(workdir.resolve("test99"), "99".getBytes(UTF_8));
    result = writer.write(TransformResults.of(workdir, new DummyReference("ref2")), console);
    assertThat(result).isEqualTo(WriterResult.OK);

    // Make sure parent of second commit is the first commit.
    assertThat(repo().simpleCommand("rev-parse", "refs_for_master~1").getStdout())
        .isEqualTo(firstCommitHash);

    // Make sure commits have correct file content.
    GitTesting.assertThatCheckout(repo(), "refs_for_master~1")
        .containsFile("test42", "42")
        .containsNoMoreFiles();
    GitTesting.assertThatCheckout(repo(), "refs_for_master")
        .containsFile("test42", "42")
        .containsFile("test99", "99")
        .containsNoMoreFiles();
  }
}
