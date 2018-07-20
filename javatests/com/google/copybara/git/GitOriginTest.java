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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static com.google.copybara.util.Glob.createGlob;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.copybara.Change;
import com.google.copybara.ChangeVisitable.VisitResult;
import com.google.copybara.Origin.Reader;
import com.google.copybara.Origin.Reader.ChangesResponse;
import com.google.copybara.Origin.Reader.ChangesResponse.EmptyReason;
import com.google.copybara.Revision;
import com.google.copybara.Workflow;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.authoring.Authoring.AuthoringMappingMode;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitCredential.UserPassword;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.RecordsProcessCallDestination.ProcessedChange;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitOriginTest {

  private static final String commitTime = "2037-02-16T13:00:00Z";
  private String url;
  private String ref;
  private GitOrigin origin;
  private Path remote;
  private Path checkoutDir;
  private String firstCommitRef;
  private OptionsBuilder options;
  private GitRepository repo;
  private final Authoring authoring = new Authoring(new Author("foo", "default@example.com"),
      AuthoringMappingMode.PASS_THRU, ImmutableSet.of());
  private Glob originFiles;
  private String moreOriginArgs;

  @Rule public final ExpectedException thrown = ExpectedException.none();

  private TestingConsole console;
  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws Exception {
    options = new OptionsBuilder();
    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console)
        .setOutputRootToTmpDir();

    skylark = new SkylarkTestExecutor(options);
    // Pass custom HOME directory so that we run an hermetic test and we
    // can add custom configuration to $HOME/.gitconfig.
    Path userHomeForTest = Files.createTempDirectory("home");
    options.setEnvironment(GitTestUtil.getGitEnv());
    options.setHomeDir(userHomeForTest.toString());

    createTestRepo(Files.createTempDirectory("remote"));
    checkoutDir = Files.createTempDirectory("checkout");

    url = "file://" + remote.toFile().getAbsolutePath();
    ref = "other";
    moreOriginArgs = "";

    origin = origin();

    Files.write(remote.resolve("test.txt"), "some content".getBytes(UTF_8));
    repo.add().files("test.txt").run();
    git("commit", "-m", "first file", "--date", commitTime);
    firstCommitRef = repo.parseRef("HEAD");

    originFiles = Glob.ALL_FILES;
  }

  private void createTestRepo(Path folder) throws Exception {
    remote = folder;
    repo = GitRepository.newRepo(true, remote, options.general.getEnvironment()).init(
    );
  }

  private Reader<GitRevision> newReader() {
    return origin.newReader(originFiles, authoring);
  }

  private GitOrigin origin() throws ValidationException {
    return skylark.eval("result",
        String.format("result = git.origin(\n"
            + "    url = '%s',\n"
            + "    ref = '%s',\n"
            + "    %s"
            + ")", url, ref, moreOriginArgs));
  }

  private String git(String... params) throws RepoException {
    return repo.git(remote, params).getStdout();
  }

  @Test
  public void testGitOrigin() throws Exception {
    origin = skylark.eval("result",
        "result = git.origin(\n"
            + "    url = 'https://my-server.org/copybara',\n"
            + "    ref = 'master',\n"
            + ")");
    assertThat(origin.toString())
        .isEqualTo(
            "GitOrigin{"
                + "repoUrl=https://my-server.org/copybara, "
                + "ref=master, "
                + "repoType=GIT"
                + "}");
  }

  @Test
  public void testEmptyUrl() {
    skylark.evalFails("git.origin( url = '')", "Invalid empty field 'url'");
  }

  @Test
  public void testEmptyUrlGithub() {
    skylark.evalFails("git.github_origin( url = '')", "Invalid empty field 'url'");
  }

  @Test
  public void testGitOriginWithEmptyRef() throws Exception {
    origin = skylark.eval("result",
        "result = git.origin(\n"
            + "    url = 'https://my-server.org/copybara',\n"
            + ")");
    assertThat(origin.toString())
        .isEqualTo(
            "GitOrigin{"
                + "repoUrl=https://my-server.org/copybara, "
                + "ref=null, "
                + "repoType=GIT"
                + "}");
  }

  @Test
  public void testGerritOrigin() throws Exception {
    origin = skylark.eval("result",
        "result = git.gerrit_origin(\n"
            + "    url = 'https://gerrit-server.org/copybara',\n"
            + "    ref = 'master',\n"
            + ")");
    assertThat(origin.toString())
        .isEqualTo(
            "GitOrigin{"
                + "repoUrl=https://gerrit-server.org/copybara, "
                + "ref=master, "
                + "repoType=GERRIT"
                + "}");
  }

  @Test
  public void testGithubOrigin() throws Exception {
    origin = skylark.eval("result",
        "result = git.github_origin(\n"
            + "    url = 'https://github.com/copybara',\n"
            + "    ref = 'master',\n"
            + ")");
    assertThat(origin.toString())
        .isEqualTo(
            "GitOrigin{"
                + "repoUrl=https://github.com/copybara, "
                + "ref=master, "
                + "repoType=GITHUB"
                + "}");
  }

  @Test
  public void testInvalidGithubUrl() throws Exception {
    try {
      skylark.eval("result",
          "result = git.github_origin(\n"
              + "    url = 'https://foo.com/copybara',\n"
              + "    ref = 'master',\n"
              + ")");
      fail();
    } catch (ValidationException expected) {
      console.assertThat()
          .onceInLog(MessageType.ERROR, ".*Invalid Github URL: https://foo.com/copybara.*");
    }
  }

  @Test
  public void testInvalidGithubUrlWithGithubString() throws Exception {
    try {
      skylark.eval("result",
          "result = git.github_origin(\n"
              + "    url = 'https://foo.com/github.com',\n"
              + "    ref = 'master',\n"
              + ")");
      fail();
    } catch (ValidationException expected) {
      console.assertThat()
          .onceInLog(MessageType.ERROR, ".*Invalid Github URL: https://foo.com/github.com.*");
    }
  }

  @Test
  public void testResolveWithUrl() throws Exception {
    assertThat(origin.resolve("master").getUrl()).isEqualTo(url);
  }

  @Test
  public void testCheckout() throws Exception {
    // Check that we get can checkout a branch
    newReader().checkout(origin.resolve("master"), checkoutDir);
    Path testFile = checkoutDir.resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");

    // Check that we track new commits that modify files
    Files.write(remote.resolve("test.txt"), "new content".getBytes(UTF_8));
    repo.add().files("test.txt").run();
    git("commit", "-m", "second commit");

    newReader().checkout(origin.resolve("master"), checkoutDir);

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("new content");

    // Check that we track commits that delete files
    Files.delete(remote.resolve("test.txt"));
    git("rm", "test.txt");
    git("commit", "-m", "third commit");

    newReader().checkout(origin.resolve("master"), checkoutDir);

    assertThat(Files.exists(testFile)).isFalse();
  }

  @Test
  public void testMergeIncludeFiles() throws Exception {
    repo.simpleCommand("branch", "foo");
    repo.forceCheckout("foo");
    Files.write(remote.resolve("bar.txt"), "".getBytes(UTF_8));
    repo.add().all().run();
    repo.simpleCommand("commit", "-m", "branch change");
    repo.forceCheckout("master");
    Files.write(remote.resolve("foo.txt"), "modified".getBytes(UTF_8));
    Files.delete(remote.resolve("test.txt"));
    repo.add().all().run();
    repo.simpleCommand("commit", "-m", "second");
    repo.simpleCommand("merge", "foo");

    ImmutableList<Change<GitRevision>> changes = newReader().changes(/*fromRef=*/null,
        origin.resolve("master")).getChangesAsListForTest();

    assertThat(changes.get(2).firstLineMessage()).contains("Merge");
    assertThat(changes.get(2).getChangeFiles()).containsExactly("bar.txt");

    assertThat(changes.get(1).firstLineMessage()).contains("second");
    assertThat(changes.get(1).getChangeFiles()).containsExactly("foo.txt", "test.txt");

    assertThat(changes.get(0).firstLineMessage()).contains("first");
    assertThat(changes.get(0).getChangeFiles()).containsExactly("test.txt");
  }

  @Test
  public void testCheckoutBranchWithRebase() throws Exception {
    // As part of the setup, a first commit created test.txt in master

    // Create branch that will be the ref to use
    createBranch("mybranch");
    // Checkout master and modify file
    repo.forceCheckout("master");
    Files.write(remote.resolve("test.txt"), "new content in master".getBytes(UTF_8));
    repo.add().files("test.txt").run();
    git("commit", "-m", "Second version of file");
    // Checkout branch and commit other files
    repo.forceCheckout("mybranch");
    Files.write(remote.resolve("foo.txt"), "Foo bar".getBytes(UTF_8));
    repo.add().files("foo.txt").run();
    git("commit", "-m", "Branch commit");

    options.gitOrigin.originRebaseRef = "master";
    newReader().checkout(origin.resolve("mybranch"), checkoutDir);

    assertThatPath(checkoutDir)
        .containsFile("test.txt", "new content in master") // Rebased contents
        .containsFile("foo.txt", "Foo bar")
        .containsNoMoreFiles();
  }

  @Test
  public void testCheckoutBranchWithInvalidRebaseRef() throws Exception {
    // As part of the setup, a first commit created test.txt in master

    // Create branch that will be the ref to use
    createBranch("mybranch");
    Files.write(remote.resolve("foo.txt"), "Foo bar".getBytes(UTF_8));
    repo.add().files("foo.txt").run();
    git("commit", "-m", "Branch commit");

    options.gitOrigin.originRebaseRef = "foo-bar";
    thrown.expect(CannotResolveRevisionException.class);
    newReader().checkout(origin.resolve("mybranch"), checkoutDir);
  }

  @Test
  public void testCheckoutBranchWithRebaseConflict() throws Exception {
    // As part of the setup, a first commit created test.txt in master

    // Create branch that will be the ref to use
    createBranch("mybranch");
    // Checkout master and modify file
    repo.forceCheckout("master");
    Files.write(remote.resolve("test.txt"), "new content in master".getBytes(UTF_8));
    repo.add().files("test.txt").run();
    git("commit", "-m", "Second version of file");
    // Checkout branch and make changes to the same file
    repo.forceCheckout("mybranch");
    Files.write(remote.resolve("test.txt"), "Content in my branch".getBytes(UTF_8));
    repo.add().files("test.txt").run();
    git("commit", "-m", "Branch commit");

    options.gitOrigin.originRebaseRef = "master";
    thrown.expect(RebaseConflictException.class);
    newReader().checkout(origin.resolve("mybranch"), checkoutDir);
  }

  @Test
  public void testCheckoutBranchNoRebase() throws Exception {
    // As part of the setup, a first commit created test.txt in master

    // Create branch that will be the ref to use
    createBranch("mybranch");
    // Checkout master and modify file
    repo.forceCheckout("master");
    Files.write(remote.resolve("test.txt"), "new content in master".getBytes(UTF_8));
    repo.add().files("test.txt").run();
    git("commit", "-m", "Second version of file");
    // Checkout branch and commit other files
    repo.forceCheckout("mybranch");
    Files.write(remote.resolve("foo.txt"), "Foo bar".getBytes(UTF_8));
    repo.add().files("foo.txt").run();
    git("commit", "-m", "Branch commit");

    newReader().checkout(origin.resolve("mybranch"), checkoutDir);

    assertThatPath(checkoutDir)
        .containsFile("test.txt", "some content") // Baseline contents
        .containsFile("foo.txt", "Foo bar")
        .containsNoMoreFiles();
  }

  @Test
  public void testResolveNonExistentFullSha1() throws Exception {
    thrown.expect(CannotResolveRevisionException.class);
    origin.resolve(Strings.repeat("a", 40));
  }

  @Test
  public void testResolveNonExistentRef() throws Exception {
    thrown.expect(CannotResolveRevisionException.class);
    origin.resolve("refs/for/copy/bara");
  }

  @Test
  public void testGitOriginWithHook() throws Exception {
    Path hook = Files.createTempFile("script", "script");
    Files.write(hook, "touch hook.txt".getBytes(UTF_8));

    Files.setPosixFilePermissions(hook, ImmutableSet.<PosixFilePermission>builder()
        .addAll(Files.getPosixFilePermissions(hook))
        .add(PosixFilePermission.OWNER_EXECUTE).build());

    options.gitOrigin.originCheckoutHook = hook.toAbsolutePath().toString();
    origin = origin();
    newReader().checkout(origin.resolve("master"), checkoutDir);
    assertThatPath(checkoutDir).containsFile("hook.txt", "");
  }

  @Test
  public void testGitOriginWithHookExitError() throws Exception {
    Path hook = Files.createTempFile("script", "script");
    Files.write(hook, "exit 1".getBytes(UTF_8));

    Files.setPosixFilePermissions(hook, ImmutableSet.<PosixFilePermission>builder()
        .addAll(Files.getPosixFilePermissions(hook))
        .add(PosixFilePermission.OWNER_EXECUTE).build());

    options.gitOrigin.originCheckoutHook = hook.toAbsolutePath().toString();
    origin = origin();
    Reader<GitRevision> reader = newReader();
    thrown.expect(RepoException.class);
    thrown.expectMessage("Error executing the git checkout hook");
    reader.checkout(origin.resolve("master"), checkoutDir);
  }

  @Test
  public void testCheckoutWithLocalModifications() throws Exception {
    GitRevision master = origin.resolve("master");
    Reader<GitRevision> reader = newReader();
    reader.checkout(master, checkoutDir);
    Path testFile = checkoutDir.resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");

    Files.delete(testFile);

    reader.checkout(master, checkoutDir);

    // The deletion in the checkoutDir should not matter, since we should override in the next
    // checkout
    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");
  }

  @Test
  public void testCheckoutOfARef() throws Exception {
    GitRevision reference = origin.resolve(firstCommitRef);
    newReader().checkout(reference, checkoutDir);
    Path testFile = checkoutDir.resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");
  }

  @Test
  public void testChanges() throws Exception {
    // Need to "round" it since git doesn't store the milliseconds
    ZonedDateTime beforeTime = ZonedDateTime.now(ZoneId.systemDefault()).minusSeconds(1);
    String author = "John Name <john@name.com>";
    singleFileCommit(author, "change2", "test.txt", "some content2");
    singleFileCommit(author, "change3", "test.txt", "some content3");
    singleFileCommit(author, "change4", "test.txt", "some content4");

    ImmutableList<Change<GitRevision>> changes = newReader()
        .changes(origin.resolve(firstCommitRef), origin.resolve("HEAD")).getChangesAsListForTest();

    assertThat(changes).hasSize(3);
    assertThat(changes.stream()
        .map(c -> c.getRevision().getUrl())
        .allMatch(c -> c.startsWith("file://")))
        .isTrue();
    assertThat(changes.get(0).getMessage()).isEqualTo("change2\n");
    assertThat(changes.get(1).getMessage()).isEqualTo("change3\n");
    assertThat(changes.get(2).getMessage()).isEqualTo("change4\n");
    for (Change<GitRevision> change : changes) {
      assertThat(change.getAuthor().getEmail()).isEqualTo("john@name.com");
      assertThat(change.getDateTime()).isAtLeast(beforeTime);
      assertThat(change.getDateTime())
          .isAtMost(ZonedDateTime.now(ZoneId.systemDefault()).plusSeconds(1));
    }
  }

  @Test
  public void testNoChanges() throws Exception {
    ChangesResponse<GitRevision> changes = newReader()
        .changes(origin.resolve(firstCommitRef), origin.resolve("HEAD"));

    assertThat(changes.isEmpty()).isTrue();
    assertThat(changes.getEmptyReason()).isEqualTo(EmptyReason.TO_IS_ANCESTOR);
  }

  @Test
  public void testChange() throws Exception {
    String author = "John Name <john@name.com>";
    singleFileCommit(author, "change2", "test.txt", "some content2");

    GitRevision lastCommitRef = getLastCommitRef();
    Change<GitRevision> change = newReader().change(lastCommitRef);

    assertThat(change.getAuthor().getEmail()).isEqualTo("john@name.com");
    assertThat(change.firstLineMessage()).isEqualTo("change2");
    assertThat(change.getRevision().asString()).isEqualTo(lastCommitRef.asString());
    assertThat(change.getRevision().getUrl()).startsWith("file://");
  }

  @Test
  public void testChangeMultiLabel() throws Exception {
    String commitMessage = ""
        + "I am a commit with a label happening twice\n"
        + "\n"
        + "foo: bar\n"
        + "\n"
        + "foo: baz\n";
    singleFileCommit("John Name <john@name.com>", commitMessage, "test.txt", "content");

    Change<GitRevision> change = newReader().change(getLastCommitRef());
    assertThat(change.getLabels()).containsEntry("foo", "bar");
    assertThat(change.getLabels()).containsEntry("foo", "baz");
    assertThat(Iterables.getLast(change.getLabels().get("foo"))).isEqualTo("baz");
  }

  @Test
  public void testChangeLabelWithSameValue() throws Exception {
    String commitMessage = ""
        + "I am a commit!\n"
        + "\n"
        + "foo: bar\n"
        + "\n"
        + "baz: bar\n";
    singleFileCommit("John Name <john@name.com>", commitMessage, "test.txt", "content");

    assertThat(newReader().change(getLastCommitRef()).getLabels())
        .containsExactlyEntriesIn(ImmutableMultimap.of("foo", "bar", "baz", "bar"));
  }

  @Test
  public void testNoChange() throws Exception {
    // This is needed to initialize the local repo
    origin.resolve(firstCommitRef);

    thrown.expect(CannotResolveRevisionException.class);
    thrown.expectMessage("Cannot find references: [foo]");

    origin.resolve("foo");
  }

  @Test
  public void testVisit() throws Exception {
    String author = "John Name <john@name.com>";
    singleFileCommit(author, "one", "test.txt", "some content1");
    singleFileCommit(author, "two", "test.txt", "some content2");
    singleFileCommit(author, "three", "test.txt", "some content3");
    GitRevision lastCommitRef = getLastCommitRef();
    List<Change<?>> visited = new ArrayList<>();
    newReader().visitChanges(lastCommitRef,
        input -> {
          visited.add(input);
          return input.firstLineMessage().equals("three")
              ? VisitResult.CONTINUE
              : VisitResult.TERMINATE;
        });

    assertThat(visited).hasSize(2);
    assertThat(visited.get(0).firstLineMessage()).isEqualTo("three");
    assertThat(visited.get(1).firstLineMessage()).isEqualTo("two");
  }

  @Test
  public void testVisitOutsideRoot() throws Exception {
    String author = "John Name <john@name.com>";
    singleFileCommit(author, "two", "bar/test.txt", "some content2");
    String first = Iterables.getOnlyElement(repo.log("HEAD").withLimit(1).run()).getCommit()
        .getSha1();
    singleFileCommit(author, "three", "foo/test.txt", "some content3");
    singleFileCommit(author, "four", "bar/test.txt", "some content3");
    GitRevision lastCommitRef = getLastCommitRef();

    originFiles = createGlob(ImmutableList.of("foo/**"));
    List<String> changes = new ArrayList<>();
    newReader().visitChanges(lastCommitRef, input -> {
      changes.add(input.getMessage());
      return VisitResult.CONTINUE;
    });
    assertThat(changes).isEqualTo(ImmutableList.of("three\n"));
  }

  @Test
  public void testFirstParent() throws Exception {
    options.git.visitChangePageSize = 3;
    createBranchMerge("John Name <john@name.com>");
    GitRevision lastCommitRef = getLastCommitRef();
    List<Change<?>> visited = new ArrayList<>();
    Reader<GitRevision> reader = origin().newReader(originFiles, authoring);
    reader.visitChanges(lastCommitRef,
        input -> {
          visited.add(input);
          return VisitResult.CONTINUE;
        });

    // We don't visit 'feature' branch since the visit is using --first-parent
    assertThat(visited).hasSize(4);
    assertThat(visited.get(0).firstLineMessage()).isEqualTo("Merge branch 'feature'");
    assertThat(visited.get(1).firstLineMessage()).isEqualTo("master2");
    assertThat(visited.get(2).firstLineMessage()).isEqualTo("master1");
    assertThat(visited.get(3).firstLineMessage()).isEqualTo("first file");

    ImmutableList<Change<GitRevision>> changes = reader.changes(/*fromRef=*/null, lastCommitRef)
        .getChangesAsListForTest();
    assertThat(Lists.transform(changes.reverse(), Change::getRevision)).isEqualTo(
        Lists.transform(visited, Change::getRevision));
    assertThat(changes.reverse().get(0).isMerge()).isTrue();
    assertThat(visited.get(0).isMerge()).isTrue();

    visited.clear();
    moreOriginArgs = "first_parent = False,\n";
    reader = origin().newReader(originFiles, authoring);
    reader.visitChanges(lastCommitRef,
        input -> {
          visited.add(input);
          return VisitResult.CONTINUE;
        });

    // Now because we don't use --first-parent we visit feature branch.
    assertThat(visited).hasSize(6);
    // First commit is the merge
    assertThat(visited.get(0).firstLineMessage()).isEqualTo("Merge branch 'feature'");
    // The rest can come in a different order depending on the time difference, as git log
    // ordering is undefined for same time.
    assertThat(Lists.transform(visited, Change::firstLineMessage)).containsExactly(
        "Merge branch 'feature'", "master2", "change3", "master1", "change2", "first file");

    changes = reader.changes(/*fromRef=*/null, lastCommitRef).getChangesAsListForTest();
    assertThat(Lists.transform(changes.reverse(), Change::getRevision)).isEqualTo(
        Lists.transform(visited, Change::getRevision));
    assertThat(changes.reverse().get(0).isMerge()).isTrue();
    assertThat(visited.get(0).isMerge()).isTrue();
  }

  /**
   * Check that we can overwrite the git url using the CLI option and that we show a message
   */
  @Test
  public void testGitUrlOverwrite() throws Exception {
    createTestRepo(Files.createTempDirectory("cliremote"));
    git("init");
    Files.write(remote.resolve("cli_remote.txt"), "some change".getBytes(UTF_8));
    repo.add().files("cli_remote.txt").run();
    git("commit", "-m", "a change from somewhere");

    TestingConsole testConsole = new TestingConsole();
    options.setConsole(testConsole);
    origin = origin();

    String newUrl = "file://" + remote.toFile().getAbsolutePath();
    Reader<GitRevision> reader = newReader();
    Change<GitRevision> cliHead = reader.change(origin.resolve(newUrl));

    reader.checkout(cliHead.getRevision(), checkoutDir);

    assertThat(cliHead.firstLineMessage()).isEqualTo("a change from somewhere");

    assertThatPath(checkoutDir)
        .containsFile("cli_remote.txt", "some change")
        .containsNoMoreFiles();

    testConsole.assertThat()
        .onceInLog(MessageType.WARNING,
            "Git origin URL overwritten in the command line as " + newUrl);
  }

  @Test
  public void testChangesMerge() throws Exception {
    // Need to "round" it since git doesn't store the milliseconds
    ZonedDateTime beforeTime = ZonedDateTime.now(ZoneId.systemDefault()).minusSeconds(1);

    String author = "John Name <john@name.com>";
    createBranchMerge(author);

    ImmutableList<Change<GitRevision>> changes = newReader()
        .changes(origin.resolve(firstCommitRef), origin.resolve("HEAD")).getChangesAsListForTest();

    assertThat(changes).hasSize(3);
    assertThat(changes.get(0).getMessage()).isEqualTo("master1\n");
    assertThat(changes.get(1).getMessage()).isEqualTo("master2\n");
    assertThat(changes.get(2).getMessage()).isEqualTo("Merge branch 'feature'\n");
    for (Change<GitRevision> change : changes) {
      assertThat(change.getAuthor().getEmail()).isEqualTo("john@name.com");
      assertThat(change.getDateTime()).isAtLeast(beforeTime);
      assertThat(change.getDateTime())
          .isAtMost(ZonedDateTime.now(ZoneId.systemDefault()).plusSeconds(1));
    }
  }

  @Test
  public void testChangesMergeNoop() throws Exception {
    ImmutableList<? extends Change<?>> includedChanges = checkChangesMergeNoop(false);
    assertThat(includedChanges.stream().map(Change::getMessage).collect(Collectors.toList()))
        .containsExactly(
            "Merge branch 'feature1'\n",
            "feature1\n",
            "main_branch_change\n");
  }

  @Test
  public void testChangesMergeNoop_importNoopChanges() throws Exception {
    ImmutableList<? extends Change<?>> includedChanges = checkChangesMergeNoop(true);
    assertThat(includedChanges.stream().map(Change::getMessage).collect(Collectors.toList()))
        .containsExactly(
            "Merge branch 'feature1'\n",
            "feature1\n",
            "main_branch_change\n",
            "change1\n",
            "change2\n",
            "change3\n",
            "exclude1\n",
            "Merge branch 'feature2'\n");
  }

  @SuppressWarnings("unchecked")
  private ImmutableList<? extends Change<?>> checkChangesMergeNoop(boolean importNoopChanges)
      throws Exception {
    RecordsProcessCallDestination destination = new RecordsProcessCallDestination();
    options.testingOptions.destination = destination;

    String author = "John Name <john@name.com>";
    singleFileCommit(author, "base", "base.txt", "");
    options.setLastRevision(repo.parseRef("master"));
    // Don't remove or add an include change before this commit. This allow us to test that we
    // traverse parents and not children:
    singleFileCommit(author, "exclude1", "exclude1", "");
    git("branch", "feature1");
    git("branch", "feature2");
    singleFileCommit(author, "main_branch_change", "one.txt", "");
    Thread.sleep(1100); // Make sure one_change is shown before in git log.
    git("checkout", "feature1");
    singleFileCommit(author, "feature1", "feature1.txt", "");
    git("checkout", "master");
    git("merge", "master", "feature1");

    String feature1Merge = repo.parseRef("master");

    Thread.sleep(1100); // Make sure one_change is shown before in git log.
    git("checkout", "feature2");
    singleFileCommit(author, "change1", "base.txt", "base");
    singleFileCommit(author, "change2", "base.txt", ""); // Revert
    singleFileCommit(author, "change3", "exclude.txt", "I should be excluded");
    git("checkout", "master");
    git("merge", "master", "feature2");
    String headSha1 = repo.parseRef("master");

    Workflow<GitRevision, Revision> wf = (Workflow<GitRevision, Revision>) skylark.loadConfig(""
        + "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin = git.origin(\n"
        + "         url = '" + url + "',\n"
        + "         first_parent = False,\n"
        + "    ),\n"
        + "    origin_files = glob(['**'], exclude = ['exclude**']),\n"
        + "    destination = testing.destination(),\n"
        + "    migrate_noop_changes = " + (importNoopChanges ? "True" : "False") + ",\n"
        + "    authoring = authoring.pass_thru('example <example@example.com>'),\n"
        + ")\n").getMigration("default");

    wf.run(checkoutDir, ImmutableList.of("master"));

    String expected = importNoopChanges ? headSha1 : feature1Merge;
    String actual = Iterables.getLast(destination.processed).getOriginRef().asString();
    assertWithMessage(String.format("Expected:\n%s\nBut found:\n%s",
        Iterables.getOnlyElement(repo.log(expected).withLimit(1).run()),
        Iterables.getOnlyElement(repo.log(actual).withLimit(1).run())))
        .that(actual).isEqualTo(expected);
    return Iterables.getLast(destination.processed).getOriginChanges();
  }

  private void createBranchMerge(String author) throws Exception {
    git("branch", "feature");
    git("checkout", "feature");
    singleFileCommit(author, "change2", "test2.txt", "some content2");
    singleFileCommit(author, "change3", "test2.txt", "some content3");
    git("checkout", "master");
    singleFileCommit(author, "master1", "test.txt", "some content2");
    singleFileCommit(author, "master2", "test.txt", "some content3");
    git("merge", "master", "feature");
    // Change merge author
    git("commit", "--amend", "--author=" + author, "--no-edit");
  }

  @Test
  public void canReadTimestamp() throws Exception {
    Files.write(remote.resolve("test2.txt"), "some more content".getBytes(UTF_8));
    repo.add().files("test2.txt").run();
    git("commit", "-m", "second file", "--date=1400110011");
    GitRevision master = origin.resolve("master");
    Instant timestamp = master.readTimestamp().toInstant();
    assertThat(timestamp).isNotNull();
    assertThat(timestamp.getEpochSecond()).isEqualTo(1400110011L);
  }

  @Test
  public void testColor() throws Exception {
    git("config", "--global", "color.ui", "always");

    GitRevision firstRef = origin.resolve(firstCommitRef);

    Files.write(remote.resolve("test.txt"), "new content".getBytes(UTF_8));
    repo.add().files("test.txt").run();
    git("commit", "-m", "second commit");
    GitRevision secondRef = origin.resolve("HEAD");

    Reader<GitRevision> reader = newReader();
    assertThat(reader.change(firstRef).getMessage()).contains("first file");
    assertThat(reader.changes(null, secondRef).getChangesAsListForTest()).hasSize(2);
    assertThat(reader.changes(firstRef, secondRef).getChangesAsListForTest()).hasSize(1);
  }

  @Test
  public void testGitOriginTag() throws Exception {
    git("tag", "-m", "This is a tag", "0.1");

    Instant instant = origin.resolve("0.1").readTimestamp().toInstant();

    assertThat(instant).isEqualTo(Instant.parse(commitTime));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void doNotCountCommitsOutsideOfOriginFileRoots() throws Exception {
    Files.write(remote.resolve("excluded_file.txt"), "some content".getBytes(UTF_8));
    repo.add().files("excluded_file.txt").run();
    git("commit", "-m", "excluded_file", "--date", commitTime);
    // Note that one of the roots looks like a flag for "git log"
    originFiles = createGlob(ImmutableList.of("include/**", "--parents/**"), ImmutableList.of());
    RecordsProcessCallDestination destination = new RecordsProcessCallDestination();
    options.testingOptions.destination = destination;
    options.setForce(true);

    Path workdir = Files.createTempDirectory("workdir");
    // No files are in the included roots - make sure we can get an empty list of changes.
    GitRevision firstRef = origin.resolve(firstCommitRef);
    options.setLastRevision(firstCommitRef);

    String config = ""
        + "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin = git.origin(\n"
        + "         url = 'file://" + repo.getGitDir() + "',\n"
        + "         ref = 'other',\n"
        + "    ),\n"
        + "    origin_files = glob(['include/**', '--parents/**']),\n"
        + "    destination = testing.destination(),\n"
        + "    mode = 'ITERATIVE',\n"
        + "    authoring = authoring.pass_thru('example <example@example.com>'),\n"
        + ")\n";

    try {
      skylark.loadConfig(config).getMigration("default").run(workdir, ImmutableList.of("HEAD"));
      fail();
    } catch (EmptyChangeException expected) {
      // should fail
    }

    // Now add a file in an included root and make sure we get that change from the Reader.
    Files.createDirectories(remote.resolve("--parents"));
    Files.write(remote.resolve("--parents/included_file.txt"), "some content".getBytes(UTF_8));
    repo.add().files("--parents/included_file.txt").run();
    git("commit", "-m", "included_file", "--date", commitTime);
    String firstExpected = repo.parseRef("HEAD");
    skylark.loadConfig(config).getMigration("default").run(workdir, ImmutableList.of("HEAD"));

    GitRevision firstIncludedRef =
        (GitRevision) Iterables.getOnlyElement(destination.processed).getOriginRef();

    assertThat(firstIncludedRef.getSha1()).isEqualTo(firstExpected);

    // Add an excluded file, and make sure the commit is skipped.
    Files.write(remote.resolve("excluded_file_2.txt"), "some content".getBytes(UTF_8));
    repo.add().files("excluded_file_2.txt").run();
    git("commit", "-m", "excluded_file_2", "--date", commitTime);

    destination.processed.clear();

    skylark.loadConfig(config).getMigration("default").run(workdir, ImmutableList.of("HEAD"));

    firstIncludedRef =
        (GitRevision) Iterables.getOnlyElement(destination.processed).getOriginRef();
    // Still only change is the one that changed included files.
    assertThat(firstIncludedRef.getSha1()).isEqualTo(firstExpected);

    // Add another included file, and make sure we only get the 2 included changes, and the
    // intervening excluded one is absent.
    Files.write(remote.resolve("--parents/included_file_2.txt"), "some content".getBytes(UTF_8));
    repo.add().files("--parents/included_file_2.txt").run();
    git("commit", "-m", "included_file_2", "--date", commitTime);

    String secondExpected = repo.parseRef("HEAD");

    destination.processed.clear();

    skylark.loadConfig(config).getMigration("default").run(workdir, ImmutableList.of("HEAD"));

    assertThat(destination.processed).hasSize(2);

    assertThat(destination.processed.get(0).getOriginRef().asString()).isEqualTo(firstExpected);
    assertThat(destination.processed.get(1).getOriginRef().asString()).isEqualTo(secondExpected);
  }

  @Test
  public void doNotSkipCommitsBecauseTreeIsIdenticalInSimpleRootMode() throws Exception {
    // For legacy compatibility, if origin_files includes the repo root (roots = [""]), we still
    // include commits even if they don't change the tree. We exclude such commits if the roots are
    // limited.
    git("commit", "-m", "empty_commit", "--date", commitTime, "--allow-empty");
    GitRevision firstRef = origin.resolve(firstCommitRef);
    List<Change<GitRevision>> changes = newReader().changes(firstRef, origin.resolve("HEAD"))
        .getChangesAsListForTest();
    assertThat(Iterables.getOnlyElement(changes).getMessage()).contains("empty_commit");
  }

  @Test
  public void includeBranchCommitLogs() throws Exception {
    git("checkout", "-b", "the-branch");
    Files.write(remote.resolve("branch-file.txt"), new byte[0]);
    git("add", "branch-file.txt");
    git("commit", "-m", "i hope this is included in the migrated message!");
    git("checkout", "master");

    // Make a commit on mainline so that Git doesn't turn this into a fast-forward.
    Files.write(remote.resolve("mainline-file.txt"), new byte[0]);
    git("add", "mainline-file.txt");
    git("commit", "-m", "mainline message!");
    git("merge", "the-branch");

    moreOriginArgs = "include_branch_commit_logs = True";
    origin = origin();

    GitRevision firstRef = origin.resolve(firstCommitRef);
    List<Change<GitRevision>> changes = newReader().changes(firstRef, origin.resolve("HEAD"))
        .getChangesAsListForTest();
    assertThat(changes).hasSize(2);

    assertThat(changes.get(0).getMessage())
        .contains("mainline message!");
    assertThat(changes.get(1).getMessage())
        .contains(ChangeReader.BRANCH_COMMIT_LOG_HEADING);
    assertThat(changes.get(1).getMessage())
        .contains("i hope this is included in the migrated message!");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void branchCommitLogsOnlyCoverIncludedOriginFileRoots() throws Exception {
    String excludedMessage = "i hope this IS NOT included in the migrated message!";

    // Make two commits on 'the-branch' branch, one being in an excluded directory, the other
    // included.
    git("checkout", "-b", "the-branch");
    Files.write(remote.resolve("branch-file.txt"), new byte[0]);
    git("add", "branch-file.txt");
    git("commit", "-m", excludedMessage);

    Files.createDirectories(remote.resolve("include"));
    Files.write(remote.resolve("include/branch-file.txt"), new byte[0]);
    git("add", "include/branch-file.txt");
    git("commit", "-m", "i hope this is included@@@");

    git("checkout", "master");

    // Make a commit on mainline so that Git doesn't turn this into a fast-forward.
    Files.createDirectories(remote.resolve("include"));
    Files.write(remote.resolve("include/mainline-file.txt"), new byte[0]);
    git("add", "include/mainline-file.txt");
    git("commit", "-m", "mainline message!");
    git("merge", "the-branch");

    options.setForce(true);
    RecordsProcessCallDestination destination = new RecordsProcessCallDestination();
    options.testingOptions.destination = destination;
    options.setLastRevision(firstCommitRef);
    Workflow<GitRevision, Revision> wf = (Workflow<GitRevision, Revision>) skylark.loadConfig(""
        + "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin = git.origin(\n"
        + "         url = '" + url + "',\n"
        + "         include_branch_commit_logs = True,\n"
        + "    ),\n"
        + "    origin_files = glob(['include/**']),\n"
        + "    destination = testing.destination(),\n"
        + "    mode = 'ITERATIVE',\n"
        + "    authoring = authoring.pass_thru('example <example@example.com>'),\n"
        + ")\n").getMigration("default");

    wf.run(Files.createTempDirectory("foo"), ImmutableList.of("HEAD"));
    List<ProcessedChange> changes = destination.processed;
    assertThat(changes).hasSize(2);
    assertThat(changes.get(0).getChangesSummary()).contains("mainline message!");
    assertThat(changes.get(1).getChangesSummary()).doesNotContain(excludedMessage);
    assertThat(changes.get(1).getChangesSummary()).contains("i hope this is included@@@");
  }

  @Test
  public void doesNotIncludeBranchCommitLogHeadingForNonMergeCommits() throws Exception {
    Files.write(remote.resolve("foofile.txt"), new byte[0]);
    git("add", "foofile.txt");
    git("commit", "-m", "i hope this is included in the migrated message!");
    moreOriginArgs = "include_branch_commit_logs = True";
    origin = origin();

    GitRevision firstRef = origin.resolve(firstCommitRef);
    List<Change<GitRevision>> changes = newReader().changes(firstRef, origin.resolve("HEAD"))
        .getChangesAsListForTest();
    String message = Iterables.getOnlyElement(changes).getMessage();
    assertThat(message).doesNotContain(ChangeReader.BRANCH_COMMIT_LOG_HEADING);
    assertThat(message).contains("i hope this is included in the migrated message!");
  }

  @Test
  public void testDescribe() throws RepoException, ValidationException {
    ImmutableMultimap<String, String> actual = origin.describe(Glob.ALL_FILES);
    assertThat(actual.get("type")).containsExactly("git.origin");
    assertThat(actual.get("repoType")).containsExactly("GIT");
  }

  @Test
  public void testCredentials() throws Exception {
    Path credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, "https://user:SECRET@somehost.com".getBytes(UTF_8));
    options.git.credentialHelperStorePath = credentialsFile.toString();

    GitRepository repository = origin().getRepository();
    UserPassword result = repository
        .credentialFill("https://somehost.com/foo/bar");

    assertThat(result.getUsername()).isEqualTo("user");
    assertThat(result.getPassword_BeCareful()).isEqualTo("SECRET");
  }

  private GitRevision getLastCommitRef() throws RepoException, ValidationException {
    String head = repo.parseRef("HEAD");
    String lastCommit = head.substring(0, head.length() -1);
    return origin.resolve(lastCommit);
  }

  private void singleFileCommit(String author, String commitMessage, String fileName,
      String fileContent) throws Exception {
    Path path = remote.resolve(fileName);
    Files.createDirectories(path.getParent());
    Files.write(path, fileContent.getBytes(UTF_8));
    repo.add().files(fileName).run();
    git("commit", "-m", commitMessage, "--author=" + author);
  }

  private void createBranch(String branchName) throws RepoException {
    repo.simpleCommand("checkout", "-b", branchName);
  }
}
