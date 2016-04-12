// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;
import com.google.copybara.git.GerritDestination.Yaml;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

@RunWith(JUnit4.class)
public class GerritDestinationTest {

  private Yaml yaml;
  private Path repoGitDir;
  private Path workdir;

  @Before
  public void setup() throws Exception {
    yaml = new Yaml();
    repoGitDir = Files.createTempDirectory("GitDestinationTest-repoGitDir");
    yaml.setUrl("file://" + repoGitDir);
    git("init", "--bare", repoGitDir.toString());

    workdir = Files.createTempDirectory("GitDestinationTest-workdir");
  }

  private String git(String... argv) throws RepoException {
    return new GitRepository("git", repoGitDir, /*workTree=*/null, /*verbose=*/true)
        .git(repoGitDir, argv)
        .getStdout();
  }

  private GerritDestination destinationFirstCommit() {
    GitOptions gitOptions = new GitOptions();
    gitOptions.gitFirstCommit = true;
    return yaml.withOptions(new Options(ImmutableList.of(gitOptions, new GeneralOptions())));
  }

  private GerritDestination destination() {
    return yaml.withOptions(new Options(ImmutableList.of(new GitOptions(), new GeneralOptions())));
  }

  private String lastCommitChangeIdLine() throws Exception {
    String logOutput = git("log", "-n1", "refs/for/master");
    String logLines[] = logOutput.split("\n");
    String changeIdLine = logLines[logLines.length - 1];
    assertThat(changeIdLine).matches("    Change-Id: I[0-9a-f]{40}$");
    return changeIdLine;
  }

  @Test
  public void gerritChangeIdChangesBetweenCommits() throws Exception {
    yaml.setPullFromRef("master");

    Files.write(workdir.resolve("file"), "some content".getBytes());
    destinationFirstCommit().process(workdir);

    String firstChangeIdLine = lastCommitChangeIdLine();

    Files.write(workdir.resolve("file2"), "some more content".getBytes());
    git("branch", "master", "refs/for/master");
    destination().process(workdir);

    assertThat(firstChangeIdLine)
        .isNotEqualTo(lastCommitChangeIdLine());
  }

  @Test
  public void changeIdIsDeterministicForFirstCommit() throws Exception {
    Set<String> changeIds = new HashSet<>();
    for (int i = 0; i < 3; i++) {
      setup();
      yaml.setPullFromRef("master");
      Files.write(workdir.resolve("file"), "some content".getBytes());
      destinationFirstCommit().process(workdir);
      changeIds.add(lastCommitChangeIdLine());
    }
    assertThat(changeIds).hasSize(1);

    // Changing the file content causes a different changeId to be generated.
    for (int i = 0; i < 3; i++) {
      setup();
      yaml.setPullFromRef("master");
      Files.write(workdir.resolve("file"), "content different from before".getBytes());
      destinationFirstCommit().process(workdir);
      changeIds.add(lastCommitChangeIdLine());
    }
    assertThat(changeIds).hasSize(2);
  }

  @Test
  public void changeIdIsDeterministicForSecondCommit() throws Exception {
    yaml.setPullFromRef("master");
    Files.write(workdir.resolve("file"), "some content".getBytes());
    destinationFirstCommit().process(workdir);

    git("branch", "master", "refs/for/master");
    Files.write(workdir.resolve("file"), "some more content".getBytes());
    Set<String> changeIds = new HashSet<>();
    for (int i = 0; i < 3; i++) {
      destination().process(workdir);
      changeIds.add(lastCommitChangeIdLine());
    }

    assertThat(changeIds).hasSize(1);
  }
}
