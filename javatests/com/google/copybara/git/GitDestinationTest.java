// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.git.GitDestination.Yaml;
import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandException;
import com.google.devtools.build.lib.shell.CommandResult;
import com.google.devtools.build.lib.shell.SimpleKillableObserver;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
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
    execv("git", "init", "--bare", repoGitDir.toString());

    workdir = Files.createTempDirectory("GitDestinationTest-workdir");
  }

  private String execv(String... argv) throws CommandException, UnsupportedEncodingException {
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();

    Command command = new Command(
        argv,
        ImmutableMap.<String, String>of(),
        repoGitDir.toFile());
    CommandResult result =
        command.execute(new byte[0], new SimpleKillableObserver(), stdout, stderr, true);
    if (!result.getTerminationStatus().success()) {
      fail(result.toString());
    }
    return stdout.toString("UTF-8");
  }

  @Test
  public void errorIfPushToRefMissing() {
    yaml.setPullFromRef("master");
    yaml.setUrl("file:///foo");
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("pushToRef");
    destinationFirstCommit();
  }

  private GitDestination destinationFirstCommit() {
    GitOptions gitOptions = new GitOptions();
    gitOptions.gitFirstCommit = true;
    return yaml.withOptions(new Options(gitOptions, new GeneralOptions()));
  }

  private GitDestination destination() {
    return yaml.withOptions(new Options(new GitOptions(), new GeneralOptions()));
  }

  private void assertFilesInDir(int expected, String ref, String path) throws Exception {
    String lsResult =
        execv("git", "--git-dir", repoGitDir.toString(), "ls-tree", ref, path);
    assertThat(lsResult.split("\n")).hasLength(expected);
  }

  private void assertCommitCount(int expected, String ref) throws Exception {
    String logResult =
        execv("git", "--git-dir", repoGitDir.toString(), "log", "--oneline", ref);
    assertThat(logResult.split("\n")).hasLength(expected);
  }

  @Test
  public void processFirstCommit() throws Exception {
    yaml.setPullFromRef("testPullFromRef");
    yaml.setPushToRef("testPushToRef");
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    destinationFirstCommit().process(workdir);

    // Make sure commit adds new text
    String showResult =
        execv("git", "--git-dir", repoGitDir.toString(), "show", "testPushToRef");
    assertThat(showResult).contains("some content");

    assertFilesInDir(1, "testPushToRef", ".");
    assertCommitCount(1, "testPushToRef");
  }

  @Test
  public void processFetchRefDoesntExist() throws Exception {
    yaml.setPullFromRef("testPullFromRef");
    yaml.setPushToRef("testPushToRef");
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    thrown.expect(RepoException.class);
    thrown.expectMessage("'testPullFromRef' doesn't exist");
    destination().process(workdir);
  }

  @Test
  public void processCommitDeletesAndAddsFiles() throws Exception {
    yaml.setPullFromRef("pullFromBar");
    yaml.setPushToRef("pushToFoo");

    Files.write(workdir.resolve("deleted_file"), "deleted content".getBytes());
    destinationFirstCommit().process(workdir);
    execv("git", "--git-dir", repoGitDir.toString(), "branch", "pullFromBar", "pushToFoo");

    workdir = Files.createTempDirectory("processCommitDeletesAndAddsFiles-workdir");
    Files.write(workdir.resolve("1.txt"), "content 1".getBytes());
    Files.createDirectories(workdir.resolve("subdir"));
    Files.write(workdir.resolve("subdir/2.txt"), "content 2".getBytes());
    destination().process(workdir);

    // Make sure original file was deleted.
    assertFilesInDir(2, "pushToFoo", ".");
    assertFilesInDir(1, "pushToFoo", "subdir");
    // Make sure both commits are present.
    assertCommitCount(2, "pushToFoo");
  }
}
