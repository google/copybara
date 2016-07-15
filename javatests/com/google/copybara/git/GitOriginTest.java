// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.copybara.Author;
import com.google.copybara.Authoring;
import com.google.copybara.Authoring.AuthoringMappingMode;
import com.google.copybara.Change;
import com.google.copybara.Origin.ChangesVisitor;
import com.google.copybara.Origin.VisitResult;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.testing.FileSubjects;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.util.console.testing.TestingConsole.MessageType;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public class GitOriginTest {

  private static final Author DEFAULT_AUTHOR = new Author("Copybara", "no-reply@google.com");
  private static final Authoring DEFAULT_AUTHORING =
      new Authoring(DEFAULT_AUTHOR, AuthoringMappingMode.PASS_THRU, ImmutableSet.<String>of());

  private GitOrigin origin;
  private Path remote;
  private Path workdir;
  private String firstCommitRef;
  private GitOrigin.Yaml yaml;
  private OptionsBuilder options;
  private Map<String, String> env;

  @Rule
  public ExpectedException thrown = ExpectedException.none();
  private Path reposDir;
  private TestingConsole console;

  @Before
  public void setup() throws Exception {
    remote = Files.createTempDirectory("remote");
    workdir = Files.createTempDirectory("workdir");
    yaml = new GitOrigin.Yaml();
    yaml.setUrl("file://" + remote.toFile().getAbsolutePath());
    yaml.setRef("other");

    options = new OptionsBuilder();
    console = new TestingConsole();
    options = new OptionsBuilder().setConsole(console);

    reposDir = Files.createTempDirectory("repos_repo");
    options.git.gitRepoStorage = reposDir.toString();

    // Pass custom HOME directory so that we run an hermetic test and we
    // can add custom configuration to $HOME/.gitconfig.
    Path userHomeForTest = Files.createTempDirectory("home");
    env = Maps.newHashMap(System.getenv());
    env.put("HOME", userHomeForTest.toString());
    origin = yaml.withOptions(options.build(), DEFAULT_AUTHORING, env);

    git("init");
    Files.write(remote.resolve("test.txt"), "some content".getBytes());
    git("add", "test.txt");
    git("commit", "-m", "first file");
    String head = git("rev-parse", "HEAD");
    // Remove new line
    firstCommitRef = head.substring(0, head.length() -1);
  }

  private String git(String... params) throws RepoException {
    return origin.getRepository().git(remote, params).getStdout();
  }

  @Test
  public void testCheckout() throws IOException, RepoException {
    // Check that we get can checkout a branch
    origin.checkout(origin.resolve("master"), workdir);
    Path testFile = workdir.resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");

    // Check that we track new commits that modify files
    Files.write(remote.resolve("test.txt"), "new content".getBytes());
    git("add", "test.txt");
    git("commit", "-m", "second commit");

    origin.checkout(origin.resolve("master"), workdir);

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("new content");

    // Check that we track commits that delete files
    Files.delete(remote.resolve("test.txt"));
    git("rm", "test.txt");
    git("commit", "-m", "third commit");
    origin.checkout(origin.resolve("master"), workdir);

    assertThat(Files.exists(testFile)).isFalse();
  }

  @Test
  public void testCheckoutWithLocalModifications() throws IOException, RepoException {
    GitReference master = origin.resolve("master");
    origin.checkout(master, workdir);
    Path testFile = workdir.resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");

    Files.delete(testFile);

    origin.checkout(master, workdir);

    // The deletion in the workdir should not matter, since we should override in the next
    // checkout
    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");
  }

  @Test
  public void testCheckoutOfARef() throws IOException, RepoException {
    GitReference reference = origin.resolve(firstCommitRef);
    origin.checkout(reference, workdir);
    Path testFile = workdir.resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");
  }

  @Test
  public void testChanges() throws IOException, RepoException {
    // Need to "round" it since git doesn't store the milliseconds
    DateTime beforeTime = DateTime.now().minusSeconds(1);
    String author = "John Name <john@name.com>";
    singleFileCommit(author, "change2", "test.txt", "some content2");
    singleFileCommit(author, "change3", "test.txt", "some content3");
    singleFileCommit(author, "change4", "test.txt", "some content4");

    ImmutableList<Change<GitReference>> changes = origin
        .changes(origin.resolve(firstCommitRef), origin.resolve("HEAD"));

    assertThat(changes).hasSize(3);
    assertThat(changes.get(0).getMessage()).isEqualTo("change2\n");
    assertThat(changes.get(1).getMessage()).isEqualTo("change3\n");
    assertThat(changes.get(2).getMessage()).isEqualTo("change4\n");
    for (Change<GitReference> change : changes) {
      assertThat(change.getAuthor().toString()).isEqualTo(author);
      assertThat(change.getDate()).isAtLeast(beforeTime);
      assertThat(change.getDate()).isAtMost(DateTime.now().plusSeconds(1));
    }
  }

  @Test
  public void testNoChanges() throws IOException, RepoException {
    ImmutableList<Change<GitReference>> changes = origin
        .changes(origin.resolve(firstCommitRef), origin.resolve("HEAD"));

    assertThat(changes).isEmpty();
  }

  @Test
  public void testChange() throws IOException, RepoException {
    String author = "John Name <john@name.com>";
    singleFileCommit(author, "change2", "test.txt", "some content2");

    GitReference lastCommitRef = getLastCommitRef();
    Change<GitReference> change = origin.change(lastCommitRef);

    assertThat(change.getAuthor().toString()).isEqualTo(author);
    assertThat(change.firstLineMessage()).isEqualTo("change2");
    assertThat(change.getReference().asString()).isEqualTo(lastCommitRef.asString());
  }

  @Test
  public void testChangeMultiLabel() throws IOException, RepoException {
    String commitMessage = ""
        + "I am a commit with a label happening twice\n"
        + "\n"
        + "foo: bar\n"
        + "\n"
        + "foo: baz\n";
    singleFileCommit("John Name <john@name.com>", commitMessage, "test.txt", "content");

    Change<GitReference> change = origin.change(getLastCommitRef());
    // We keep the last label. The probability that the last one is a label and the first one
    // is just description is very high.
    assertThat(change.getLabels()).containsEntry("foo", "baz");
    assertThat(console.countTimesInLog(MessageType.WARNING, "Possible duplicate label 'foo'"
        + " happening multiple times in commit. Keeping only the last value: 'baz'\n"
        + "  Discarded value: 'bar'"))
        .isEqualTo(1);
  }

  @Test
  public void testNoChange() throws IOException, RepoException {
    // This is needed to initialize the local repo
    origin.resolve(firstCommitRef);

    thrown.expect(RepoException.class);
    thrown.expectMessage("Cannot find reference 'foo'");

    origin.resolve("foo");
  }

  @Test
  public void testVisit() throws IOException, RepoException {
    String author = "John Name <john@name.com>";
    singleFileCommit(author, "one", "test.txt", "some content1");
    singleFileCommit(author, "two", "test.txt", "some content2");
    singleFileCommit(author, "three", "test.txt", "some content3");
    GitReference lastCommitRef = getLastCommitRef();
    final List<Change<?>> visited = new ArrayList<>();
    origin.visitChanges(lastCommitRef,
        new ChangesVisitor() {
          @Override
          public VisitResult visit(Change<?> input) {
            visited.add(input);
            System.out.println(input.firstLineMessage().equals("three"));
            return input.firstLineMessage().equals("three")
                ? VisitResult.CONTINUE
                : VisitResult.TERMINATE;
          }
        });

    assertThat(visited).hasSize(2);
    assertThat(visited.get(0).firstLineMessage()).isEqualTo("three");
    assertThat(visited.get(1).firstLineMessage()).isEqualTo("two");
  }

  @Test
  public void testVisitMerge() throws IOException, RepoException {
    createBranchMerge("John Name <john@name.com>");
    GitReference lastCommitRef = getLastCommitRef();
    final List<Change<?>> visited = new ArrayList<>();
    origin.visitChanges(lastCommitRef,
        new ChangesVisitor() {
          @Override
          public VisitResult visit(Change<?> input) {
            visited.add(input);
            return VisitResult.CONTINUE;
          }
        });

    // We don't visit 'feature' branch since the visit is using --first-parent. Maybe we have
    // to revisit this in the future.
    assertThat(visited).hasSize(4);
    assertThat(visited.get(0).firstLineMessage()).isEqualTo("Merge branch 'feature'");
    assertThat(visited.get(1).firstLineMessage()).isEqualTo("master2");
    assertThat(visited.get(2).firstLineMessage()).isEqualTo("master1");
    assertThat(visited.get(3).firstLineMessage()).isEqualTo("first file");
  }

  @Test
  public void testWhitelisting() throws Exception {
    Authoring authoring = new Authoring(
        DEFAULT_AUTHOR, AuthoringMappingMode.WHITELIST, ImmutableSet.of("john@name.com"));
    origin = yaml.withOptions(options.build(), authoring, env);

    String author = "John Name <john@name.com>";
    singleFileCommit(author, "change2", "test.txt", "some content2");
    GitReference lastCommitRef = getLastCommitRef();
    Change<GitReference> change = origin.change(lastCommitRef);

    assertThat(change.getAuthor().toString()).isEqualTo(author);

    author = "Foo Bar <foo@bar.com>";
    singleFileCommit(author, "change3", "test.txt", "some content3");
    lastCommitRef = getLastCommitRef();
    change = origin.change(lastCommitRef);

    assertThat(change.getAuthor().toString()).isEqualTo(DEFAULT_AUTHOR.toString());
  }

  /**
   * Check that we can overwrite the git url using the CLI option and that we show a message
   */
  @Test
  public void testGitUrlOverwrite() throws ConfigValidationException, IOException, RepoException {
    remote = Files.createTempDirectory("cliremote");
    git("init");
    Files.write(remote.resolve("cli_remote.txt"), "some change".getBytes());
    git("add", "cli_remote.txt");
    git("commit", "-m", "a change from somewhere");

    TestingConsole testingConsole = new TestingConsole();
    options.setConsole(testingConsole);
    origin = yaml.withOptions(options.build(), DEFAULT_AUTHORING, env);

    String newUrl = "file://" + remote.toFile().getAbsolutePath();
    Change<GitReference> cliHead = origin.change(origin.resolve(newUrl));

    origin.checkout(cliHead.getReference(), workdir);

    assertThat(cliHead.firstLineMessage()).isEqualTo("a change from somewhere");

    assertAbout(FileSubjects.path())
        .that(workdir)
        .containsFile("cli_remote.txt", "some change")
        .containsNoMoreFiles();

    assertThat(testingConsole.countTimesInLog(MessageType.WARNING,
        "Git origin URL overwritten in the command line as " + newUrl)).isEqualTo(1);
  }

  @Test
  public void testChangesMerge() throws IOException, RepoException {
    // Need to "round" it since git doesn't store the milliseconds
    DateTime beforeTime = DateTime.now().minusSeconds(1);

    String author = "John Name <john@name.com>";
    createBranchMerge(author);

    ImmutableList<Change<GitReference>> changes = origin
        .changes(origin.resolve(firstCommitRef), origin.resolve("HEAD"));

    assertThat(changes).hasSize(3);
    assertThat(changes.get(0).getMessage()).isEqualTo("master1\n");
    assertThat(changes.get(1).getMessage()).isEqualTo("master2\n");
    assertThat(changes.get(2).getMessage()).isEqualTo("Merge branch 'feature'\n");
    for (Change<GitReference> change : changes) {
      assertThat(change.getAuthor().toString()).isEqualTo(author);
      assertThat(change.getDate()).isAtLeast(beforeTime);
      assertThat(change.getDate()).isAtMost(DateTime.now().plusSeconds(1));
    }
  }

  public void createBranchMerge(String author) throws RepoException, IOException {
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
  public void canReadTimestamp() throws IOException, RepoException {
    Files.write(remote.resolve("test2.txt"), "some more content".getBytes());
    git("add", "test2.txt");
    git("commit", "-m", "second file", "--date=1400110011");
    GitReference master = origin.resolve("master");
    assertThat(master.readTimestamp()).isEqualTo(1400110011L);
  }

  @Test
  public void testColor() throws RepoException, IOException {
    git("config", "--global", "color.ui", "always");

    GitReference firstRef = origin.resolve(firstCommitRef);

    Files.write(remote.resolve("test.txt"), "new content".getBytes());
    git("add", "test.txt");
    git("commit", "-m", "second commit");
    GitReference secondRef = origin.resolve("HEAD");

    assertThat(origin.change(firstRef).getMessage()).contains("first file");
    assertThat(origin.changes(null, secondRef)).hasSize(2);
    assertThat(origin.changes(firstRef, secondRef)).hasSize(1);
  }

  private GitReference getLastCommitRef() throws RepoException {
    String head = git("rev-parse", "HEAD");
    String lastCommit = head.substring(0, head.length() -1);
    return origin.resolve(lastCommit);
  }

  private void singleFileCommit(String author, String commitMessage, String fileName,
      String fileContent) throws IOException, RepoException {
    Files.write(remote.resolve(fileName), fileContent.getBytes());
    git("add", fileName);
    git("commit", "-m", commitMessage, "--author=" + author);
  }
}
