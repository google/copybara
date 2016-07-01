// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.copybara.Author;
import com.google.copybara.Authoring;
import com.google.copybara.Authoring.AuthoringMappingMode;
import com.google.copybara.Change;
import com.google.copybara.Origin.ChangesVisitor;
import com.google.copybara.Origin.ReferenceFiles;
import com.google.copybara.Origin.VisitResult;
import com.google.copybara.RepoException;
import com.google.copybara.testing.OptionsBuilder;

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

  @Before
  public void setup() throws Exception {
    remote = Files.createTempDirectory("remote");
    workdir = Files.createTempDirectory("workdir");
    yaml = new GitOrigin.Yaml();
    yaml.setUrl("file://" + remote.toFile().getAbsolutePath());
    yaml.setRef("other");

    options = new OptionsBuilder();
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
    origin.resolve("master").checkout(workdir);
    Path testFile = workdir.resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");

    // Check that we track new commits that modify files
    Files.write(remote.resolve("test.txt"), "new content".getBytes());
    git("add", "test.txt");
    git("commit", "-m", "second commit");

    origin.resolve("master").checkout(workdir);

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("new content");

    // Check that we track commits that delete files
    Files.delete(remote.resolve("test.txt"));
    git("rm", "test.txt");
    git("commit", "-m", "third commit");
    origin.resolve("master").checkout(workdir);

    assertThat(Files.exists(testFile)).isFalse();
  }

  @Test
  public void testCheckoutWithLocalModifications() throws IOException, RepoException {
    ReferenceFiles<GitOrigin> master = origin.resolve("master");
    master.checkout(workdir);
    Path testFile = workdir.resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");

    Files.delete(testFile);

    master.checkout(workdir);

    // The deletion in the workdir should not matter, since we should override in the next
    // checkout
    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");
  }

  @Test
  public void testCheckoutOfARef() throws IOException, RepoException {
    ReferenceFiles<GitOrigin> reference = origin.resolve(firstCommitRef);
    reference.checkout(workdir);
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

    ImmutableList<Change<GitOrigin>> changes = origin
        .changes(origin.resolve(firstCommitRef), origin.resolve("HEAD"));

    assertThat(changes).hasSize(3);
    assertThat(changes.get(0).getMessage()).isEqualTo("change2\n");
    assertThat(changes.get(1).getMessage()).isEqualTo("change3\n");
    assertThat(changes.get(2).getMessage()).isEqualTo("change4\n");
    for (Change<GitOrigin> change : changes) {
      assertThat(change.getAuthor().toString()).isEqualTo(author);
      assertThat(change.getDate()).isAtLeast(beforeTime);
      assertThat(change.getDate()).isAtMost(DateTime.now().plusSeconds(1));
    }
  }

  @Test
  public void testNoChanges() throws IOException, RepoException {
    ImmutableList<Change<GitOrigin>> changes = origin
        .changes(origin.resolve(firstCommitRef), origin.resolve("HEAD"));

    assertThat(changes).isEmpty();
  }

  @Test
  public void testChange() throws IOException, RepoException {
    String author = "John Name <john@name.com>";
    singleFileCommit(author, "change2", "test.txt", "some content2");

    ReferenceFiles<GitOrigin> lastCommitRef = getLastCommitRef();
    Change<GitOrigin> change = origin.change(lastCommitRef);

    assertThat(change.getAuthor().toString()).isEqualTo(author);
    assertThat(change.firstLineMessage()).isEqualTo("change2");
    assertThat(change.getReference().asString()).isEqualTo(lastCommitRef.asString());
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
    ReferenceFiles<GitOrigin> lastCommitRef = getLastCommitRef();
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
    ReferenceFiles<GitOrigin> lastCommitRef = getLastCommitRef();
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
    ReferenceFiles<GitOrigin> lastCommitRef = getLastCommitRef();
    Change<GitOrigin> change = origin.change(lastCommitRef);

    assertThat(change.getAuthor().toString()).isEqualTo(author);

    author = "Foo Bar <foo@bar.com>";
    singleFileCommit(author, "change3", "test.txt", "some content3");
    lastCommitRef = getLastCommitRef();
    change = origin.change(lastCommitRef);

    assertThat(change.getAuthor().toString()).isEqualTo(DEFAULT_AUTHOR.toString());
  }

  @Test
  public void testChangesMerge() throws IOException, RepoException {
    // Need to "round" it since git doesn't store the milliseconds
    DateTime beforeTime = DateTime.now().minusSeconds(1);

    String author = "John Name <john@name.com>";
    createBranchMerge(author);

    ImmutableList<Change<GitOrigin>> changes = origin
        .changes(origin.resolve(firstCommitRef), origin.resolve("HEAD"));

    assertThat(changes).hasSize(3);
    assertThat(changes.get(0).getMessage()).isEqualTo("master1\n");
    assertThat(changes.get(1).getMessage()).isEqualTo("master2\n");
    assertThat(changes.get(2).getMessage()).isEqualTo("Merge branch 'feature'\n");
    for (Change<GitOrigin> change : changes) {
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
    ReferenceFiles<GitOrigin> master = origin.resolve("master");
    assertThat(master.readTimestamp()).isEqualTo(1400110011L);
  }

  @Test
  public void testColor() throws RepoException, IOException {
    git("config", "--global", "color.ui", "always");

    ReferenceFiles<GitOrigin> firstRef = origin.resolve(firstCommitRef);

    Files.write(remote.resolve("test.txt"), "new content".getBytes());
    git("add", "test.txt");
    git("commit", "-m", "second commit");
    ReferenceFiles<GitOrigin> secondRef = origin.resolve("HEAD");

    assertThat(origin.change(firstRef).getMessage()).contains("first file");
    assertThat(origin.changes(null, secondRef)).hasSize(2);
    assertThat(origin.changes(firstRef, secondRef)).hasSize(1);
  }

  private ReferenceFiles<GitOrigin> getLastCommitRef() throws RepoException {
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
