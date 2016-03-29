package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.copybara.RepoException;

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
    repo = new GitRepository(reposDir, "git", false);
    workdir = Files.createTempDirectory("workdir");
    remote = Files.createTempDirectory("remote");

    repo.git(remote, "init");
    Files.write(remote.resolve("test.txt"), "some content".getBytes());
    repo.git(remote, "add", "test.txt");
    repo.git(remote, "commit", "-m", "first file");
  }

  @Test
  public void testCheckout() throws IOException, RepoException {
    // Check that we get can checkout a branch
    repo.prepare("file://" + remote.toFile().getAbsolutePath(), "origin/master", workdir);
    Path testFile = workdir.resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");

    // Check that we track new commits that modify files
    Files.write(remote.resolve("test.txt"), "new content".getBytes());
    repo.git(remote, "add", "test.txt");
    repo.git(remote, "commit", "-m", "second commit");

    repo.prepare("file://" + remote.toFile().getAbsolutePath(), "origin/master", workdir);

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("new content");

    // Check that we track commits that delete files
    Files.delete(remote.resolve("test.txt"));
    repo.git(remote, "rm", "test.txt");
    repo.git(remote, "commit", "-m", "third commit");

    repo.prepare("file://" + remote.toFile().getAbsolutePath(), "origin/master", workdir);

    assertThat(Files.exists(testFile)).isFalse();
  }

  @Test
  public void testCheckoutWithLocalModifications() throws IOException, RepoException {
    repo.prepare("file://" + remote.toFile().getAbsolutePath(), "origin/master", workdir);
    Path testFile = workdir.resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");

    Files.delete(testFile);

    repo.prepare("file://" + remote.toFile().getAbsolutePath(), "origin/master", workdir);

    // The deletion in the workdir should not matter, since we should override in the next
    // checkout
    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");
  }

  @Test
  public void testCheckoutLocalBranch() throws IOException, RepoException {
    try {
      repo.prepare("file://" + remote.toFile().getAbsolutePath(), "master", workdir);
      fail("Trying to checkout local (non-existent) refs should fail");
    } catch (RepoException e) {
      assertThat(e.getMessage()).contains("Ref 'master' does not exist");
    }
  }
}
