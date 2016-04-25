// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin.Reference;
import com.google.copybara.RepoException;

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
  private Path workdir;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws Exception {
    Path reposDir = Files.createTempDirectory("repos_repo");
    remote = Files.createTempDirectory("remote");
    GitOrigin.Yaml yaml = new GitOrigin.Yaml();
    yaml.setUrl("file://" + remote.toFile().getAbsolutePath());
    yaml.setDefaultTrackingRef("other");

    GitOptions gitOptions = new GitOptions();
    gitOptions.gitRepoStorage = reposDir.toString();

    workdir = Files.createTempDirectory("workdir");
    origin = yaml.withOptions(
        new Options(ImmutableList.of(gitOptions, new GeneralOptions(workdir, /*verbose=*/true))));

    git("init");
    Files.write(remote.resolve("test.txt"), "some content".getBytes());
    git("add", "test.txt");
    git("commit", "-m", "first file");
  }

  private void git(String... params) throws RepoException {
    origin.getRepository().git(remote, params);
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
    Reference<GitOrigin> master = origin.resolve("master");
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
  public void canReadTimestamp() throws IOException, RepoException {
    Files.write(remote.resolve("test2.txt"), "some more content".getBytes());
    git("add", "test2.txt");
    git("commit", "-m", "second file", "--date=1400110011");
    Reference<GitOrigin> master = origin.resolve("master");
    assertThat(master.readTimestamp()).isEqualTo(1400110011L);
  }
}
