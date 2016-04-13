// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Preconditions;
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

@RunWith(JUnit4.class)
public class GerritDestinationTest {

  private Yaml yaml;
  private Path repoGitDir;
  private Path workdir;
  private GerritOptions gerritOptions;
  private GitOptions gitOptions;

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

  private String git(String... argv) throws RepoException {
    return new GitRepository("git", repoGitDir, /*workTree=*/null, /*verbose=*/true)
        .git(repoGitDir, argv)
        .getStdout();
  }

  private GerritDestination destination() {
    return yaml.withOptions(
        new Options(ImmutableList.of(gitOptions, gerritOptions, new GeneralOptions())));
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
    gitOptions.gitFirstCommit = true;
    destination().process(workdir);

    String firstChangeIdLine = lastCommitChangeIdLine();

    Files.write(workdir.resolve("file2"), "some more content".getBytes());
    git("branch", "master", "refs/for/master");
    gitOptions.gitFirstCommit = false;
    destination().process(workdir);

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
    destination().process(workdir);
    assertThat(lastCommitChangeIdLine())
        .isEqualTo("    Change-Id: " + changeId);

    git("branch", "master", "refs/for/master");

    Files.write(workdir.resolve("file"), "some different content".getBytes());

    changeId = "Ibbbbbbbbbbccccccccccddddddddddeeeeeeeeee";
    gitOptions.gitFirstCommit = false;
    gerritOptions.gerritChangeId = changeId;
    destination().process(workdir);
    assertThat(lastCommitChangeIdLine())
        .isEqualTo("    Change-Id: " + changeId);
  }
}
