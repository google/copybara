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
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;

import com.google.common.base.Strings;
import com.google.copybara.GeneralOptions;
import com.google.copybara.RepoException;
import com.google.copybara.testing.OptionsBuilder;
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
  private GeneralOptions generalOptions;

  @Before
  public void setup() throws IOException, RepoException {
    repoGitDir = Files.createTempDirectory("testRepo");
    fileRepoDir = Files.createTempDirectory("fileRepo");
    // We mock by default to avoid accidental network calls.
    testRepo = new GitRepository(repoGitDir, null, /*verbose=*/true, getGitEnv()) {
      @Override
      public GitRevision fetchSingleRef(String url, String ref) throws RepoException {
        interceptedFetches.add(new String[]{url, ref});
        return new GitRevision(this, Strings.repeat("0", 40));
      }
    };

    testRepo.init();
    prepareFileRepo();
    console = new TestingConsole();
    generalOptions = new OptionsBuilder().setConsole(console).build().get(GeneralOptions.class);
  }

  private void disableFetchMocks() throws RepoException {
    testRepo = GitRepository.newBareRepo(repoGitDir, getGitEnv(),  /*verbose=*/true);
    testRepo.init();
  }

  private void prepareFileRepo() throws RepoException, IOException {
    fileRepo = GitRepository.newRepo(true, fileRepoDir, getGitEnv()).init(
    );
    Files.write(fileRepoDir.resolve("foo"), new byte[]{});

    fileRepo.add().files("foo").run();
    fileRepo.git(fileRepoDir, "commit", "-m", "first commit");
    fileRepo.git(fileRepoDir, "branch", "first_commit");

    Files.write(fileRepoDir.resolve("bar"), new byte[]{});

    fileRepo.add().files("bar").run();
    fileRepo.git(fileRepoDir, "commit", "-m", "second commit");
    fileUrl = "file://" + fileRepoDir.toAbsolutePath();

    Files.write(fileRepoDir.resolve("foobar"), new byte[]{});

    // Dirty hack to simulate Gerrit
    fileRepo.git(fileRepoDir,  "symbolic-ref", "refs/changes/04/1204/1", "HEAD");

  }

  @Test
  public void testResolveSha1() throws Exception {
    disableFetchMocks();
    String sha1 = fileRepo.parseRef("HEAD");
    GitRevision rev = GitRepoType.GIT.resolveRef(testRepo, fileUrl, sha1, generalOptions);
    assertThat(rev.asString()).isEqualTo(sha1);
    assertThat(rev.getSha1()).isEqualTo(sha1);
    assertThat(rev.getReviewReference()).isNull();

    console.assertThat().containsNoMoreMessages();
  }

  @Test
  public void testResolveSha1WithAdditionalReviewData() throws Exception {
    disableFetchMocks();
    String sha1 = fileRepo.parseRef("HEAD");
    GitRevision rev = GitRepoType.GIT.resolveRef(testRepo, fileUrl, sha1 + " more stuff",
                                                         generalOptions);
    assertThat(rev.asString()).isEqualTo(sha1 + " more stuff");
    assertThat(rev.getSha1()).isEqualTo(sha1);
    assertThat(rev.getReviewReference()).isEqualTo("more stuff");

    console.assertThat().containsNoMoreMessages();
  }

  @Test
  public void testResolveRef() throws Exception {
    disableFetchMocks();
    String sha1 = fileRepo.parseRef("HEAD");
    assertThat(GitRepoType.GIT.resolveRef(testRepo, fileUrl, "master", generalOptions).asString())
        .isEqualTo(sha1);
    console.assertThat()
        .containsNoMoreMessages();
  }

  @Test
  public void testResolveFileUrlAndRef() throws Exception {
    disableFetchMocks();
    String firstCommitBranchSha1 = fileRepo.parseRef("first_commit");
    assertThat(GitRepoType.GIT.resolveRef(testRepo, fileUrl, fileUrl + " first_commit",
        generalOptions).asString()).isEqualTo(firstCommitBranchSha1);
    assertUrlOverwritten();
  }

  @Test
  public void testGitResolveUrl() throws Exception {
    assertThat(GitRepoType.GIT.resolveRef(testRepo, "dont use", "https://github.com/google/example",
        generalOptions).asString())
        .hasLength(40);
    assertFetch("https://github.com/google/example", "HEAD");
    assertUrlOverwritten();
  }

  @Test
  public void testGitResolveUrlAndRef() throws Exception {
    assertThat(GitRepoType.GIT.resolveRef(testRepo, "dont use",
        "https://github.com/google/example master", generalOptions).asString())
        .hasLength(40);
    assertFetch("https://github.com/google/example", "master");
    assertUrlOverwritten();
  }

  @Test
  public void testResolveGerritPatch() throws Exception {
    disableFetchMocks();
    String sha1 = fileRepo.parseRef("HEAD");
    GitRevision rev = GitRepoType.GERRIT.resolveRef(testRepo, fileUrl, "1204", generalOptions);
    assertThat(rev.asString()).isEqualTo(sha1 + " PatchSet 1");
    assertThat(rev.getSha1()).isEqualTo(sha1);
    assertThat(rev.getReviewReference()).isEqualTo("PatchSet 1");

    console.assertThat().containsNoMoreMessages();
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
