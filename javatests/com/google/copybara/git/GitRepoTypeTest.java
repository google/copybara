// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.git.GitRepository.CURRENT_PROCESS_ENVIRONMENT;

import com.google.common.base.Strings;
import com.google.copybara.RepoException;
import com.google.copybara.testing.LogSubjects;
import com.google.copybara.util.CommandOutput;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.util.console.testing.TestingConsole.MessageType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

@RunWith(JUnit4.class)
public class GitRepoTypeTest {

  private Path repoGitDir;
  private Path fileRepoDir;
  private GitRepository testRepo;
  private GitRepository fileRepo;
  private String fileUrl;
  private TestingConsole console;
  private final Deque<String[]> interceptedFetches = new ArrayDeque<>();

  @Before
  public void setup() throws IOException, RepoException {
    repoGitDir = Files.createTempDirectory("testRepo");
    fileRepoDir = Files.createTempDirectory("fileRepo");
    // We mock by default to avoid accidental network calls.
    testRepo = new GitRepository(repoGitDir, null, /*verbose=*/true, CURRENT_PROCESS_ENVIRONMENT) {
      /**
       * Avoid calling github or any external network call
       */
      @Override
      public CommandOutput simpleCommand(String... argv) throws RepoException {
        if (argv[0].equals("fetch")) {
          interceptedFetches.add(argv);
          return new CommandOutput(new byte[]{}, new byte[]{});
        } else if (argv[0].equals("rev-parse")) {
          return new CommandOutput((Strings.repeat("0", 40) + "\n").getBytes(), new byte[]{});
        }
        return super.simpleCommand(argv);
      }
    };

    testRepo.initGitDir();
    prepareFileRepo();
    console = new TestingConsole();
  }

  private void disableFetchMocks() throws RepoException {
    testRepo = new GitRepository(repoGitDir, null, /*verbose=*/true, CURRENT_PROCESS_ENVIRONMENT);
    testRepo.initGitDir();
  }

  private void prepareFileRepo() throws RepoException, IOException {
    fileRepo = new GitRepository(fileRepoDir, fileRepoDir, /*verbose=*/true,
        CURRENT_PROCESS_ENVIRONMENT);
    fileRepo.git(fileRepoDir, "init");
    Files.write(fileRepoDir.resolve("foo"), new byte[]{});

    fileRepo.git(fileRepoDir, "add", "foo");
    fileRepo.git(fileRepoDir, "commit", "-m", "first commit");
    fileRepo.git(fileRepoDir, "branch", "first_commit");

    Files.write(fileRepoDir.resolve("bar"), new byte[]{});

    fileRepo.git(fileRepoDir, "add", "bar");
    fileRepo.git(fileRepoDir, "commit", "-m", "second commit");
    fileUrl = "file://" + fileRepoDir.toAbsolutePath();
  }

  @Test
  public void testResolveSha1() throws RepoException {
    disableFetchMocks();
    String sha1 = fileRepo.git(fileRepoDir, "rev-parse", "HEAD").getStdout().trim();
    assertThat(GitRepoType.GIT.resolveRef(testRepo, fileUrl, sha1, console).asString())
        .isEqualTo(sha1);
    assertAbout(LogSubjects.console())
        .that(console)
        .containsNoMoreMessages();
  }

  @Test
  public void testResolveRef() throws RepoException {
    disableFetchMocks();
    String sha1 = fileRepo.git(fileRepoDir, "rev-parse", "HEAD").getStdout().trim();
    assertThat(GitRepoType.GIT.resolveRef(testRepo, fileUrl, "master", console).asString())
        .isEqualTo(sha1);
    assertAbout(LogSubjects.console())
        .that(console)
        .containsNoMoreMessages();
  }

  @Test
  public void testResolveFileUrlAndRef() throws RepoException {
    disableFetchMocks();
    String firstCommitBranchSha1 = fileRepo.git(fileRepoDir, "rev-parse", "first_commit")
        .getStdout().trim();
    assertThat(GitRepoType.GIT.resolveRef(testRepo, fileUrl, fileUrl + " first_commit", console)
        .asString()).isEqualTo(firstCommitBranchSha1);
    assertUrlOverwritten();
  }

  @Test
  public void testGitResolveUrl() throws RepoException {
    assertThat(GitRepoType.GIT.resolveRef(testRepo, "dont use", "https://github.com/google/example",
        console).asString())
        .hasLength(40);
    assertFetch("https://github.com/google/example", "HEAD");
    assertUrlOverwritten();
  }

  @Test
  public void testGitResolveUrlAndRef() throws RepoException {
    assertThat(GitRepoType.GIT.resolveRef(testRepo, "dont use",
        "https://github.com/google/example master", console).asString())
        .hasLength(40);
    assertFetch("https://github.com/google/example", "master");
    assertUrlOverwritten();
  }

  @Test
  public void testGitResolvePullRequest() throws RepoException {
    assertThat(GitRepoType.GITHUB.resolveRef(testRepo, "https://github.com/google/example",
        "https://github.com/google/example/pull/1", console).asString())
        .hasLength(40);
    assertFetch("https://github.com/google/example", "refs/pull/1/head");
    assertAbout(LogSubjects.console())
        .that(console)
        .containsNoMoreMessages();
  }

  private void assertUrlOverwritten() {
    assertAbout(LogSubjects.console())
        .that(console)
        .matchesNext(MessageType.WARNING,
            "Git origin URL overwritten in the command line .*");
  }

  private void assertFetch(String url, String reference) {
    assertThat(interceptedFetches).hasSize(1);
    assertThat(interceptedFetches.getFirst())
        .isEqualTo(new String[]{"fetch", "-f", url, reference});
  }
}
