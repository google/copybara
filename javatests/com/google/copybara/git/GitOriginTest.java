// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Change;
import com.google.copybara.Origin.Reference;
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

@RunWith(JUnit4.class)
public class GitOriginTest {

  private GitOrigin origin;
  private Path remote;
  private OptionsBuilder options;
  private String firstCommitRef;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws Exception {
    remote = Files.createTempDirectory("remote");
    GitOrigin.Yaml yaml = new GitOrigin.Yaml();
    yaml.setUrl("file://" + remote.toFile().getAbsolutePath());
    yaml.setDefaultTrackingRef("other");

    options = new OptionsBuilder()
        .setWorkdirToRealTempDir();
    options.git.gitRepoStorage = Files.createTempDirectory("repos_repo").toString();

    origin = yaml.withOptions(options.build());

    git("init");
    Files.write(remote.resolve("test.txt"), "some content".getBytes());
    git("add", "test.txt");
    git("commit", "-m", "first file");
    String head = git("rev-parse", "HEAD");
    // Remove new line
    firstCommitRef = head.substring(0, head.length() -1);
  }

  private Path workdir() {
    return options.general.getWorkdir();
  }

  private String git(String... params) throws RepoException {
    return origin.getRepository().git(remote, params).getStdout();
  }

  @Test
  public void testCheckout() throws IOException, RepoException {
    // Check that we get can checkout a branch
    origin.resolve("master").checkout(workdir());
    Path testFile = workdir().resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");

    // Check that we track new commits that modify files
    Files.write(remote.resolve("test.txt"), "new content".getBytes());
    git("add", "test.txt");
    git("commit", "-m", "second commit");

    origin.resolve("master").checkout(workdir());

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("new content");

    // Check that we track commits that delete files
    Files.delete(remote.resolve("test.txt"));
    git("rm", "test.txt");
    git("commit", "-m", "third commit");
    origin.resolve("master").checkout(workdir());

    assertThat(Files.exists(testFile)).isFalse();
  }

  @Test
  public void testCheckoutWithLocalModifications() throws IOException, RepoException {
    Reference<GitOrigin> master = origin.resolve("master");
    master.checkout(workdir());
    Path testFile = workdir().resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");

    Files.delete(testFile);

    master.checkout(workdir());

    // The deletion in the workdir should not matter, since we should override in the next
    // checkout
    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");
  }

  @Test
  public void testCheckoutOfARef() throws IOException, RepoException {
    Reference<GitOrigin> reference = origin.resolve(firstCommitRef);
    reference.checkout(workdir());
    Path testFile = workdir().resolve("test.txt");

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
      assertThat(change.getAuthor()).isEqualTo(author);
      assertThat(change.getDate()).isAtLeast(beforeTime);
      assertThat(change.getDate()).isAtMost(DateTime.now().plusSeconds(1));
    }
  }

  private void singleFileCommit(String author, String commitMessage, String fileName,
      String fileContent) throws IOException, RepoException {
    Files.write(remote.resolve(fileName), fileContent.getBytes());
    git("add", fileName);
    git("commit", "-m", commitMessage, "--author=" + author);
  }

  @Test
  public void testChangesMerge() throws IOException, RepoException {
    // Need to "round" it since git doesn't store the milliseconds
    DateTime beforeTime = DateTime.now().minusSeconds(1);
    git("branch", "feature");
    git("checkout", "feature");
    String author = "John Name <john@name.com>";
    singleFileCommit(author, "change2", "test2.txt", "some content2");
    singleFileCommit(author, "change3", "test2.txt", "some content3");
    git("checkout", "master");
    singleFileCommit(author, "master1", "test.txt", "some content2");
    singleFileCommit(author, "master2", "test.txt", "some content3");
    git("merge", "master", "feature");
    // Change merge author
    git("commit", "--amend", "--author=" + author, "--no-edit");

    ImmutableList<Change<GitOrigin>> changes = origin
        .changes(origin.resolve(firstCommitRef), origin.resolve("HEAD"));

    assertThat(changes).hasSize(3);
    assertThat(changes.get(0).getMessage()).isEqualTo("master1\n");
    assertThat(changes.get(1).getMessage()).isEqualTo("master2\n");
    assertThat(changes.get(2).getMessage()).isEqualTo("Merge branch 'feature'\n");
    for (Change<GitOrigin> change : changes) {
      assertThat(change.getAuthor()).isEqualTo(author);
      assertThat(change.getDate()).isAtLeast(beforeTime);
      assertThat(change.getDate()).isAtMost(DateTime.now().plusSeconds(1));
    }
  }

  @Test
  public void canReadTimestamp() throws IOException, RepoException {
    Files.write(remote.resolve("test2.txt"), "some more content".getBytes());
    git("add", "test2.txt");
    git("commit", "-m", "second file", "--date=1400110011");
    Reference<GitOrigin> master = origin.resolve("master");
    assertThat(master.readTimestamp()).isEqualTo(1400110011L);
  }
}
