// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.git.GitRepository.CURRENT_PROCESS_ENVIRONMENT;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Destination.WriterResult;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.git.GerritDestination.Yaml;
import com.google.copybara.git.GerritDestination.Yaml.GerritProcessPushOutput;
import com.google.copybara.git.testing.GitTesting;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.DummyReference;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.LogConsole;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class GerritDestinationTest {

  private static final String CONFIG_NAME = "copybara_project";

  private Yaml yaml;
  private Path repoGitDir;
  private Path workdir;
  private OptionsBuilder options;
  private Console console;
  private ImmutableList<String> excludedDestinationPaths;

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
    excludedDestinationPaths = ImmutableList.of();
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

  private GerritDestination destination() throws ConfigValidationException {
    return yaml.withOptions(options.build(), CONFIG_NAME);
  }

  private String lastCommitChangeIdLine() throws Exception {
    String logOutput = git("log", "-n1", "refs/for/master");
    assertThat(logOutput)
        .contains("\n    " + DummyOrigin.LABEL_NAME
            + ": " + "origin_ref\n");
    String logLines[] = logOutput.split("\n");
    String changeIdLine = logLines[logLines.length - 1];
    assertThat(changeIdLine).matches("    Change-Id: I[0-9a-f]{40}$");
    return changeIdLine;
  }

  private void process(DummyReference originRef)
      throws ConfigValidationException, RepoException, IOException {
    WriterResult result = destination().newWriter()
        .write(TransformResults.of(workdir, originRef, excludedDestinationPaths), console);
    assertThat(result).isEqualTo(WriterResult.OK);
  }

  @Test
  public void gerritChangeIdChangesBetweenCommits() throws Exception {
    yaml.setFetch("master");

    Files.write(workdir.resolve("file"), "some content".getBytes());
    options.git.gitFirstCommit = true;
    process(new DummyReference("origin_ref"));

    String firstChangeIdLine = lastCommitChangeIdLine();

    Files.write(workdir.resolve("file2"), "some more content".getBytes());
    git("branch", "master", "refs/for/master");
    options.git.gitFirstCommit = false;
    process(new DummyReference("origin_ref"));

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
    process(new DummyReference("origin_ref"));
    assertThat(lastCommitChangeIdLine())
        .isEqualTo("    Change-Id: " + changeId);

    git("branch", "master", "refs/for/master");

    Files.write(workdir.resolve("file"), "some different content".getBytes());

    changeId = "Ibbbbbbbbbbccccccccccddddddddddeeeeeeeeee";
    options.git.gitFirstCommit = false;
    options.gerrit.gerritChangeId = changeId;
    process(new DummyReference("origin_ref"));
    assertThat(lastCommitChangeIdLine())
        .isEqualTo("    Change-Id: " + changeId);
  }

  @Test
  public void writesOriginTimestampToAuthorField() throws Exception {
    yaml.setFetch("master");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    options.git.gitFirstCommit = true;
    process(new DummyReference("first_commit").withTimestamp(355558888));
    GitTesting.assertAuthorTimestamp(repo(), "refs/for/master", 355558888);

    git("branch", "master", "refs/for/master");

    Files.write(workdir.resolve("test2.txt"), "some more content".getBytes());
    options.git.gitFirstCommit = false;
    process(new DummyReference("first_commit").withTimestamp(424242420));
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

  @Test
  public void testProcessPushOutput() {
    String gerritResponse = "Counting objects: 9, done.\n"
        + "Delta compression using up to 4 threads.\n"
        + "Compressing objects: 100% (6/6), done.\n"
        + "Writing objects: 100% (9/9), 3.20 KiB | 0 bytes/s, done.\n"
        + "Total 9 (delta 4), reused 0 (delta 0)\n"
        + "remote: Resolving deltas: 100% (4/4)\n"
        + "remote: Processing changes: updated: 1, done\n"
        + "remote:\n"
        + "remote: Updated Changes:\n"
        + "remote:   https://some.url.google.com/1234 This is a message\n"
        + "remote:\n"
        + "To sso://team/copybara-team/copybara\n"
        + " * [new branch]      HEAD -> refs/for/master%notify=NONE\n"
        + "<o> [master] ~/dev/copybara$\n";

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    GerritProcessPushOutput process = new GerritProcessPushOutput(
        LogConsole.readWriteConsole(System.in, new PrintStream(out)));

    process.process(gerritResponse);

    assertThat(out.toString())
        .contains("INFO: New Gerrit review created at https://some.url.google.com/1234");
  }

  @Test
  public void testPushToNonDefaultRef() throws Exception {
    yaml.setFetch("master");
    yaml.setPushToRefsFor("testPushToRef");
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    options.git.gitFirstCommit = true;
    process(new DummyReference("origin_ref"));

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "refs/for/testPushToRef");
    assertThat(showResult).contains("some content");
  }

  @Test
  public void testPushToNonMasterDefaultRef() throws Exception {
    yaml.setFetch("fooze");
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    options.git.gitFirstCommit = true;
    process(new DummyReference("origin_ref"));

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "refs/for/fooze");
    assertThat(showResult).contains("some content");
  }

  @Test
  public void canExcludeDestinationPathFromWorkflow() throws Exception {
    yaml.setFetch("master");

    Path scratchWorkTree = Files.createTempDirectory("GitDestinationTest-scratchWorkTree");
    Files.write(scratchWorkTree.resolve("excluded.txt"), "some content".getBytes(UTF_8));
    repo().withWorkTree(scratchWorkTree)
        .simpleCommand("add", "excluded.txt");
    repo().withWorkTree(scratchWorkTree)
        .simpleCommand("commit", "-m", "message");

    Files.write(workdir.resolve("normal_file.txt"), "some more content".getBytes(UTF_8));
    excludedDestinationPaths = ImmutableList.of("excluded.txt");
    process(new DummyReference("ref"));
    GitTesting.assertThatCheckout(repo(), "refs/for/master")
        .containsFile("excluded.txt", "some content")
        .containsFile("normal_file.txt", "some more content")
        .containsNoMoreFiles();
  }
}
