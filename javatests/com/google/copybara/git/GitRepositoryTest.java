package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;
import com.google.copybara.git.GitRepository.Yaml;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test class for {@link GitRepository}
 */
public class GitRepositoryTest {

  private GitRepository repo;
  private Path remote;
  private Path workdir;

  @Before
  public void setup() throws IOException, RepoException {
    Path reposDir = Files.createTempDirectory("repos_repo");
    remote = Files.createTempDirectory("remote");
    GitRepository.Yaml yaml = new Yaml();
    yaml.setUrl("file://" + remote.toFile().getAbsolutePath());
    yaml.setDefaultTrackingRef("other");

    GitOptions gitOptions = new GitOptions();
    gitOptions.gitRepoStorage = reposDir.toString();

    repo = yaml.withOptions(new Options(gitOptions, new GeneralOptions()));
    workdir = Files.createTempDirectory("workdir");

    repo.git(remote, "init");
    Files.write(remote.resolve("test.txt"), "some content".getBytes());
    repo.git(remote, "add", "test.txt");
    repo.git(remote, "commit", "-m", "first file");
  }

  @Test
  public void testCheckout() throws IOException, RepoException {
    // Check that we get can checkout a branch
    repo.checkoutReference("origin/master", workdir);
    Path testFile = workdir.resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");

    // Check that we track new commits that modify files
    Files.write(remote.resolve("test.txt"), "new content".getBytes());
    repo.git(remote, "add", "test.txt");
    repo.git(remote, "commit", "-m", "second commit");

    repo.checkoutReference("origin/master", workdir);

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("new content");

    // Check that we track commits that delete files
    Files.delete(remote.resolve("test.txt"));
    repo.git(remote, "rm", "test.txt");
    repo.git(remote, "commit", "-m", "third commit");

    repo.checkoutReference("origin/master", workdir);

    assertThat(Files.exists(testFile)).isFalse();
  }

  @Test
  public void testCheckoutWithLocalModifications() throws IOException, RepoException {
    repo.checkoutReference("origin/master", workdir);
    Path testFile = workdir.resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");

    Files.delete(testFile);

    repo.checkoutReference("origin/master", workdir);

    // The deletion in the workdir should not matter, since we should override in the next
    // checkout
    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");
  }

  @Test
  public void testCheckoutLocalBranch() throws IOException, RepoException {
    try {
      repo.checkoutReference("master", workdir);
      fail("Trying to checkout local (non-existent) refs should fail");
    } catch (RepoException e) {
      assertThat(e.getMessage()).contains("Ref 'master' does not exist");
    }
  }
}
