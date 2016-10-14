/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Strings;
import com.google.copybara.RepoException;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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
    testRepo = new GitRepository(repoGitDir, null, /*verbose=*/true, System.getenv()) {
      @Override
      public GitReference fetchSingleRef(String url, String ref) throws RepoException {
        interceptedFetches.add(new String[]{url, ref});
        return new GitReference(this, Strings.repeat("0", 40));
      }
    };

    testRepo.initGitDir();
    prepareFileRepo();
    console = new TestingConsole();
  }

  private void disableFetchMocks() throws RepoException {
    testRepo = new GitRepository(repoGitDir, null, /*verbose=*/true, System.getenv());
    testRepo.initGitDir();
  }

  private void prepareFileRepo() throws RepoException, IOException {
    fileRepo = GitRepository.initScratchRepo( /*verbose=*/true, fileRepoDir, System.getenv());
    Files.write(fileRepoDir.resolve("foo"), new byte[]{});

    fileRepo.add().files("foo").run();
    fileRepo.git(fileRepoDir, "commit", "-m", "first commit");
    fileRepo.git(fileRepoDir, "branch", "first_commit");

    Files.write(fileRepoDir.resolve("bar"), new byte[]{});

    fileRepo.add().files("bar").run();
    fileRepo.git(fileRepoDir, "commit", "-m", "second commit");
    fileUrl = "file://" + fileRepoDir.toAbsolutePath();
  }

  @Test
  public void testResolveSha1() throws RepoException {
    disableFetchMocks();
    String sha1 = fileRepo.git(fileRepoDir, "rev-parse", "HEAD").getStdout().trim();
    assertThat(GitRepoType.GIT.resolveRef(testRepo, fileUrl, sha1, console).asString())
        .isEqualTo(sha1);
    console.assertThat()
        .containsNoMoreMessages();
  }

  @Test
  public void testResolveRef() throws RepoException {
    disableFetchMocks();
    String sha1 = fileRepo.git(fileRepoDir, "rev-parse", "HEAD").getStdout().trim();
    assertThat(GitRepoType.GIT.resolveRef(testRepo, fileUrl, "master", console).asString())
        .isEqualTo(sha1);
    console.assertThat()
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
    console.assertThat()
        .containsNoMoreMessages();
  }

  private void assertUrlOverwritten() {
    console.assertThat()
        .matchesNext(MessageType.WARNING,
            "Git origin URL overwritten in the command line .*");
  }

  private void assertFetch(String url, String reference) {
    assertThat(interceptedFetches).hasSize(1);
    assertThat(interceptedFetches.getFirst()).isEqualTo(new String[]{url, reference});
  }
}
