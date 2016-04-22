// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.git.GitDestination.Yaml;
import com.google.copybara.git.testing.GitTesting;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class GitDestinationTest {

  private Yaml yaml;
  private Path repoGitDir;
  private Path workdir;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws Exception {
    yaml = new Yaml();
    repoGitDir = Files.createTempDirectory("GitDestinationTest-repoGitDir");
    yaml.setUrl("file://" + repoGitDir);
    git("init", "--bare", repoGitDir.toString());

    workdir = Files.createTempDirectory("GitDestinationTest-workdir");
  }

  private GitRepository repo() {
    return new GitRepository("git", repoGitDir, /*workTree=*/null, /*verbose=*/true);
  }

  private String git(String... argv) throws RepoException {
    return repo()
        .git(repoGitDir, argv)
        .getStdout();
  }

  @Test
  public void errorIfPushToRefMissing() throws ConfigValidationException {
    yaml.setPullFromRef("master");
    yaml.setUrl("file:///foo");
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("pushToRef");
    destinationFirstCommit();
  }

  private GitDestination destinationFirstCommit() throws ConfigValidationException {
    GitOptions gitOptions = new GitOptions();
    gitOptions.gitFirstCommit = true;
    return yaml.withOptions(
        new Options(ImmutableList.of(gitOptions, new GeneralOptions(workdir, /*verbose=*/true))));
  }

  private GitDestination destination() throws ConfigValidationException {
    return yaml.withOptions(new Options(
        ImmutableList.of(new GitOptions(), new GeneralOptions(workdir, /*verbose=*/true))));
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
        .contains("\n    " + Origin.COMMIT_ORIGIN_REFERENCE_FIELD + ": " + originRef + "\n");
  }

  @Test
  public void processFirstCommit() throws Exception {
    yaml.setPullFromRef("testPullFromRef");
    yaml.setPushToRef("testPushToRef");
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    destinationFirstCommit().process(workdir, "origin_ref", /*timestamp=*/424242420);

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "testPushToRef");
    assertThat(showResult).contains("some content");

    assertFilesInDir(1, "testPushToRef", ".");
    assertCommitCount(1, "testPushToRef");

    assertCommitHasOrigin("testPushToRef", "origin_ref");
  }

  @Test
  public void processFetchRefDoesntExist() throws Exception {
    yaml.setPullFromRef("testPullFromRef");
    yaml.setPushToRef("testPushToRef");
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    thrown.expect(RepoException.class);
    thrown.expectMessage("'testPullFromRef' doesn't exist");
    destination().process(workdir, "origin_ref", /*timestamp=*/424242420);
  }

  @Test
  public void processCommitDeletesAndAddsFiles() throws Exception {
    yaml.setPullFromRef("pullFromBar");
    yaml.setPushToRef("pushToFoo");

    Files.write(workdir.resolve("deleted_file"), "deleted content".getBytes());
    destinationFirstCommit().process(workdir, "origin_ref", /*timestamp=*/424242420);
    git("--git-dir", repoGitDir.toString(), "branch", "pullFromBar", "pushToFoo");

    workdir = Files.createTempDirectory("processCommitDeletesAndAddsFiles-workdir");
    Files.write(workdir.resolve("1.txt"), "content 1".getBytes());
    Files.createDirectories(workdir.resolve("subdir"));
    Files.write(workdir.resolve("subdir/2.txt"), "content 2".getBytes());
    destination().process(workdir, "origin_ref", /*timestamp=*/424242420);

    // Make sure original file was deleted.
    assertFilesInDir(2, "pushToFoo", ".");
    assertFilesInDir(1, "pushToFoo", "subdir");
    // Make sure both commits are present.
    assertCommitCount(2, "pushToFoo");

    assertCommitHasOrigin("pushToFoo", "origin_ref");
  }

  @Test
  public void previousImportReference() throws Exception {
    yaml.setPullFromRef("master");
    yaml.setPushToRef("master");

    Path file = workdir.resolve("test.txt");

    Files.write(file, "some content".getBytes());
    GitDestination destination1 = destinationFirstCommit();
    assertThat(destination1.getPreviousRef()).isNull();
    destination1.process(workdir, "first_commit", /*timestamp=*/424242420);
    assertCommitHasOrigin("master", "first_commit");

    Files.write(file, "some other content".getBytes());
    GitDestination destination2 = destination();
    assertThat(destination2.getPreviousRef()).isEqualTo("first_commit");
    destination2.process(workdir, "second_commit", /*timestamp=*/424242420);
    assertCommitHasOrigin("master", "second_commit");

    Files.write(file, "just more text".getBytes());
    GitDestination destination3 = destination();
    assertThat(destination3.getPreviousRef()).isEqualTo("second_commit");
    destination3.process(workdir, "third_commit", /*timestamp=*/424242420);
    assertCommitHasOrigin("master", "third_commit");
  }

  private void verifySpecifyAuthorField(String expected) throws Exception {
    yaml.setPullFromRef("master");
    yaml.setPushToRef("master");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    GitDestination destination = destinationFirstCommit();
    destination.process(workdir, "first_commit", /*timestamp=*/424242420);

    String[] commitLines = git("--git-dir", repoGitDir.toString(), "log", "-n1").split("\n");
    assertThat(commitLines[1]).isEqualTo("Author: " + expected);
  }

  @Test
  public void specifyAuthorField() throws Exception {
    String author = "Copybara Unit Tester <noreply@foo.bar>";
    yaml.setAuthor(author);
    verifySpecifyAuthorField(author);
  }

  @Test
  public void defaultAuthorFieldIsCopybara() throws Exception {
    verifySpecifyAuthorField("Copybara <noreply@google.com>");
  }

  private void checkAuthorFormatIsBad(String author) throws ConfigValidationException {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("author field must be in the form of 'Name <email@domain>'");
    yaml.setAuthor(author);
  }

  @Test
  public void validatesAuthorFieldFormat1() throws ConfigValidationException {
    checkAuthorFormatIsBad("foo");
  }

  @Test
  public void validatesAuthorFieldFormat2() throws ConfigValidationException {
    checkAuthorFormatIsBad("foo <a@>");
  }

  @Test
  public void validatesAuthorFieldFormat3() throws ConfigValidationException {
    checkAuthorFormatIsBad("foo <@b>");
  }

  @Test
  public void validatesAuthorFieldFormat4() throws ConfigValidationException {
    checkAuthorFormatIsBad("foo <a@b> foo");
  }

  @Test
  public void validatesAuthorFieldFormat5() throws ConfigValidationException {
    checkAuthorFormatIsBad(" <a@b>");
  }

  @Test
  public void writesOriginTimestampToAuthorField() throws Exception {
    yaml.setPullFromRef("master");
    yaml.setPushToRef("master");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    destinationFirstCommit().process(workdir, "first_commit", /*timestamp=*/1414141414);
    GitTesting.assertAuthorTimestamp(repo(), "master", 1414141414);

    Files.write(workdir.resolve("test2.txt"), "some more content".getBytes());
    destination().process(workdir, "second_commit", /*timestamp=*/1515151515);
    GitTesting.assertAuthorTimestamp(repo(), "master", 1515151515);
  }
}
