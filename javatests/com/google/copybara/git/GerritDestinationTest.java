// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.git.GerritDestination.Yaml;
import com.google.copybara.git.GitRepository;
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
public class GerritDestinationTest {

  private Yaml yaml;
  private Path repoGitDir;
  private Path workdir;
  private GerritOptions gerritOptions;
  private GitOptions gitOptions;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws Exception {
    yaml = new Yaml();
    repoGitDir = Files.createTempDirectory("GitDestinationTest-repoGitDir");
    yaml.setUrl("file://" + repoGitDir);
    git("init", "--bare", repoGitDir.toString());

    workdir = Files.createTempDirectory("GitDestinationTest-workdir");
    gerritOptions = new GerritOptions();
    gitOptions = new GitOptions();
  }

  private GitRepository repo() {
    return new GitRepository("git", repoGitDir, /*workTree=*/null, /*verbose=*/true);
  }

  private String git(String... argv) throws RepoException {
    return repo()
        .git(repoGitDir, argv)
        .getStdout();
  }

  private GerritDestination destination() {
    return yaml.withOptions(new Options(ImmutableList.of(
        gitOptions, gerritOptions, new GeneralOptions(workdir, /*verbose=*/true))));
  }

  private String lastCommitChangeIdLine() throws Exception {
    String logOutput = git("log", "-n1", "refs/for/master");
    assertThat(logOutput)
        .contains("\n    " + Origin.COMMIT_ORIGIN_REFERENCE_FIELD + ": " + "origin_ref\n");
    String logLines[] = logOutput.split("\n");
    String changeIdLine = logLines[logLines.length - 1];
    assertThat(changeIdLine).matches("    Change-Id: I[0-9a-f]{40}$");
    return changeIdLine;
  }

  @Test
  public void gerritChangeIdChangesBetweenCommits() throws Exception {
    yaml.setPullFromRef("master");

    Files.write(workdir.resolve("file"), "some content".getBytes());
    gitOptions.gitFirstCommit = true;
    destination().process(workdir, "origin_ref", /*timestamp=*/424242420);

    String firstChangeIdLine = lastCommitChangeIdLine();

    Files.write(workdir.resolve("file2"), "some more content".getBytes());
    git("branch", "master", "refs/for/master");
    gitOptions.gitFirstCommit = false;
    destination().process(workdir, "origin_ref", /*timestamp=*/424242420);

    assertThat(firstChangeIdLine)
        .isNotEqualTo(lastCommitChangeIdLine());
  }

  @Test
  public void specifyChangeId() throws Exception {
    yaml.setPullFromRef("master");

    Files.write(workdir.resolve("file"), "some content".getBytes());

    String changeId = "Iaaaaaaaaaabbbbbbbbbbccccccccccdddddddddd";
    gitOptions.gitFirstCommit = true;
    gerritOptions.gerritChangeId = changeId;
    destination().process(workdir, "origin_ref", /*timestamp=*/424242420);
    assertThat(lastCommitChangeIdLine())
        .isEqualTo("    Change-Id: " + changeId);

    git("branch", "master", "refs/for/master");

    Files.write(workdir.resolve("file"), "some different content".getBytes());

    changeId = "Ibbbbbbbbbbccccccccccddddddddddeeeeeeeeee";
    gitOptions.gitFirstCommit = false;
    gerritOptions.gerritChangeId = changeId;
    destination().process(workdir, "origin_ref", /*timestamp=*/424242420);
    assertThat(lastCommitChangeIdLine())
        .isEqualTo("    Change-Id: " + changeId);
  }

  private void verifySpecifyAuthorField(String expected) throws Exception {
    yaml.setPullFromRef("master");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    gitOptions.gitFirstCommit = true;
    destination().process(workdir, "first_commit", /*timestamp=*/424242420);

    String[] commitLines = git("--git-dir", repoGitDir.toString(), "log", "-n1", "refs/for/master")
        .split("\n");
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

  private void checkAuthorFormatIsBad(String author) {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("author field must be in the form of 'Name <email@domain>'");
    yaml.setAuthor(author);
  }

  @Test
  public void validatesAuthorFieldFormat1() {
    checkAuthorFormatIsBad("foo");
  }

  @Test
  public void validatesAuthorFieldFormat2() {
    checkAuthorFormatIsBad("foo <a@>");
  }

  @Test
  public void writesOriginTimestampToAuthorField() throws Exception {
    yaml.setPullFromRef("master");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    gitOptions.gitFirstCommit = true;
    destination().process(workdir, "first_commit", /*timestamp=*/355558888);
    GitTesting.assertAuthorTimestamp(repo(), "refs/for/master", 355558888);

    git("branch", "master", "refs/for/master");

    Files.write(workdir.resolve("test2.txt"), "some more content".getBytes());
    gitOptions.gitFirstCommit = false;
    destination().process(workdir, "first_commit", /*timestamp=*/424242420);
    GitTesting.assertAuthorTimestamp(repo(), "refs/for/master", 424242420);
  }
}
