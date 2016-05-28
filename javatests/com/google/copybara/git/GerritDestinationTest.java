// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.git.GerritDestination.Yaml;
import com.google.copybara.git.testing.GitTesting;
import com.google.copybara.testing.MockReference;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.util.console.Console;

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

  private static final String CONFIG_NAME = "copybara_project";
  private static final String COMMIT_MSG = "Commit!\n";
  private Yaml yaml;
  private Path repoGitDir;
  private Path workdir;
  private OptionsBuilder options;
  private Console console;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws Exception {
    yaml = new Yaml();
    repoGitDir = Files.createTempDirectory("GitDestinationTest-repoGitDir");
    workdir = Files.createTempDirectory("workdir");
    yaml.setUrl("file://" + repoGitDir);
    git("init", "--bare", repoGitDir.toString());

    options = new OptionsBuilder();
    options.git.gitCommitterEmail = "commiter@email";
    options.git.gitCommitterName = "Bara Kopi";
    console = options.general.console();
  }

  private GitRepository repo() {
    return new GitRepository(repoGitDir, /*workTree=*/null, /*verbose=*/true);
  }

  private String git(String... argv) throws RepoException {
    return repo()
        .git(repoGitDir, argv)
        .getStdout();
  }

  private GerritDestination destination() throws ConfigValidationException {
    return yaml.withOptions(options.build(), CONFIG_NAME);
  }

  private String lastCommitChangeIdLine() throws Exception {
    String logOutput = git("log", "-n1", "refs/for/master");
    assertThat(logOutput)
        .contains("\n    " + MockReference.MOCK_LABEL_REV_ID
            + ": " + "origin_ref\n");
    String logLines[] = logOutput.split("\n");
    String changeIdLine = logLines[logLines.length - 1];
    assertThat(changeIdLine).matches("    Change-Id: I[0-9a-f]{40}$");
    return changeIdLine;
  }

  @Test
  public void gerritChangeIdChangesBetweenCommits() throws Exception {
    yaml.setFetch("master");

    Files.write(workdir.resolve("file"), "some content".getBytes());
    options.git.gitFirstCommit = true;
    destination().process(workdir, new MockReference("origin_ref"),
        /*timestamp=*/424242420, COMMIT_MSG, console);

    String firstChangeIdLine = lastCommitChangeIdLine();

    Files.write(workdir.resolve("file2"), "some more content".getBytes());
    git("branch", "master", "refs/for/master");
    options.git.gitFirstCommit = false;
    destination().process(workdir, new MockReference("origin_ref"),
        /*timestamp=*/424242420, COMMIT_MSG, console);

    assertThat(firstChangeIdLine)
        .isNotEqualTo(lastCommitChangeIdLine());
  }

  @Test
  public void specifyChangeId() throws Exception {
    yaml.setFetch("master");

    Files.write(workdir.resolve("file"), "some content".getBytes());

    String changeId = "Iaaaaaaaaaabbbbbbbbbbccccccccccdddddddddd";
    options.git.gitFirstCommit = true;
    options.gerrit.gerritChangeId = changeId;
    destination().process(workdir, new MockReference("origin_ref"),
        /*timestamp=*/424242420, COMMIT_MSG, console);
    assertThat(lastCommitChangeIdLine())
        .isEqualTo("    Change-Id: " + changeId);

    git("branch", "master", "refs/for/master");

    Files.write(workdir.resolve("file"), "some different content".getBytes());

    changeId = "Ibbbbbbbbbbccccccccccddddddddddeeeeeeeeee";
    options.git.gitFirstCommit = false;
    options.gerrit.gerritChangeId = changeId;
    destination().process(workdir, new MockReference("origin_ref"),
        /*timestamp=*/424242420, COMMIT_MSG, console);
    assertThat(lastCommitChangeIdLine())
        .isEqualTo("    Change-Id: " + changeId);
  }

  private void verifySpecifyAuthorField(String expected) throws Exception {
    yaml.setFetch("master");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    options.git.gitFirstCommit = true;
    destination().process(workdir, new MockReference("first_commit"),
        /*timestamp=*/424242420, COMMIT_MSG, console);

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
  public void writesOriginTimestampToAuthorField() throws Exception {
    yaml.setFetch("master");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    options.git.gitFirstCommit = true;
    destination().process(workdir, new MockReference("first_commit"),
        /*timestamp=*/355558888, COMMIT_MSG, console);
    GitTesting.assertAuthorTimestamp(repo(), "refs/for/master", 355558888);

    git("branch", "master", "refs/for/master");

    Files.write(workdir.resolve("test2.txt"), "some more content".getBytes());
    options.git.gitFirstCommit = false;
    destination().process(workdir, new MockReference("first_commit"),
        /*timestamp=*/424242420, COMMIT_MSG, console);
    GitTesting.assertAuthorTimestamp(repo(), "refs/for/master", 424242420);
  }

  @Test
  public void validationErrorForMissingPullFromRef() throws Exception {
    yaml = new Yaml();
    yaml.setUrl("file:///foo");
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("missing required field 'fetch'");
    destination();
  }

  @Test
  public void validationErrorForMissingUrl() throws Exception {
    yaml = new Yaml();
    yaml.setFetch("master");
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("missing required field 'url'");
    destination();
  }
}
