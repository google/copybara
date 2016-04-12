// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.git.GerritDestination.Yaml;
import com.google.copybara.util.CommandUtil;
import com.google.devtools.build.lib.shell.CommandException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

  private String git(String... argv) throws CommandException {
    List<String> allArgs = new ArrayList<>();
    allArgs.add("git");
    allArgs.addAll(Arrays.asList(argv));
    return CommandUtil.execv(repoGitDir, allArgs);
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
    String logOutput = git("--git-dir", repoGitDir.toString(), "log", "-n1", "refs/for/master");
    String logLines[] = logOutput.split("\n");
    String changeIdLine = logLines[logLines.length - 1];
    assertThat(changeIdLine).matches("    Change-Id: I[0-9a-f]{40}$");
    return changeIdLine;
  }

  @Test
  public void includeGerritChangeId() throws Exception {
    yaml.setPullFromRef("master");

    Files.write(workdir.resolve("file"), "some content".getBytes());
    destinationFirstCommit().process(workdir);

    String firstChangeIdLine = lastCommitChangeIdLine();

    Files.write(workdir.resolve("file2"), "some more content".getBytes());
    git("--git-dir", repoGitDir.toString(), "branch", "master", "refs/for/master");
    destination().process(workdir);

    assertThat(firstChangeIdLine)
        .isNotEqualTo(lastCommitChangeIdLine());
  }
}

