/*
 * Copyright (C) 2020 Google Inc.
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
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.copybara.exception.RepoException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.util.console.testing.TestingConsole;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

  @RunWith(JUnit4.class)
public class SameGitTreeTest {

    private GitRepository repository;
    private Path workdir;
    private Path gitDir;
    private OptionsBuilder options;
    private TestingConsole console;

    @Before
    public void setup() throws Exception {
      workdir = Files.createTempDirectory("workdir");
      gitDir = Files.createTempDirectory("gitdir");
      console = new TestingConsole();
      options = new OptionsBuilder()
          .setConsole(console)
          .setOutputRootToTmpDir();

      repository = GitRepository
          .newBareRepo(gitDir, getGitEnv(), /*verbose=*/true, DEFAULT_TIMEOUT, /*noVerify=*/ false)
          .withWorkTree(workdir);
      repository.init();
    }

    @Test
    public void hasSameTree() throws Exception {
      String branch = "test";
      // mock remote repo
      GitRepository mockRemoteRepo = repository.withWorkTree(workdir);
      mockRemoteRepo.init();
      mockRemoteRepo.simpleCommand("checkout", "-b", branch);
      Files.write(workdir.resolve("foo.txt"), new byte[]{});
      repository.add().files("foo.txt").run();
      mockRemoteRepo.simpleCommand("commit", "foo.txt", "-m", "message_a");
      String sha1 =  mockRemoteRepo.resolveReference("HEAD").getSha1();

      // mock local repo
      Path localWorkTree = Files.createTempDirectory("localWorkTree");
      Path localGitDir = Files.createTempDirectory("localGitDir");
      GitRepository localRepo = mockRepository(localGitDir, localWorkTree);
      localRepo.fetchSingleRef(mockRemoteRepo.getGitDir().toString(), branch, false);
      localRepo.forceCheckout(sha1);

      // mock the same sha1 at remote and local
      for (GitRepository repo : ImmutableList.of(localRepo, mockRemoteRepo)){
      Files.write(repo.getWorkTree().resolve("foo.txt"), "update content".getBytes(UTF_8));
        repo.simpleCommand("commit", "foo.txt", "-m", "update msg");
      }

      String remoteHeadSha1 = mockRemoteRepo.resolveReference("HEAD").getSha1();
      SameGitTree sameGitTree = new SameGitTree(repository, mockRemoteRepo.getGitDir().toString(),
          options.general, false);
      assertThat(sameGitTree.hasSameTree(remoteHeadSha1)).isTrue();
    }

    private GitRepository mockRepository(Path gitDir, Path workTree) throws RepoException {
      GitRepository repository = GitRepository.newBareRepo(gitDir,
          getGitEnv(), /*verbose=*/true, DEFAULT_TIMEOUT, /*noVerify=*/ false)
          .withWorkTree(workTree);
      return repository.init();
    }
  }
