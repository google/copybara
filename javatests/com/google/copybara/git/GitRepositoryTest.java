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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.CannotResolveReferenceException;
import com.google.copybara.RepoException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitRepositoryTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private GitRepository repository;
  private Path workdir;

  @Before
  public void setup() throws Exception {
    workdir = Files.createTempDirectory("workdir");
    this.repository = GitRepository.initScratchRepo(/*verbose=*/true, System.getenv())
        .withWorkTree(workdir);
  }

  @Test
  public void testShowRef() throws RepoException, IOException {
    GitRepository repo = repository.withWorkTree(workdir);
    repo.initGitDir();
    ImmutableMap<String, GitReference> before = repo.showRef();

    assertThat(before).isEmpty();

    Files.write(workdir.resolve("foo.txt"), new byte[]{});
    repository.add().files("foo.txt").run();
    repo.simpleCommand("commit", "foo.txt", "-m", "message");
    repo.simpleCommand("branch", "bar");
    ImmutableMap<String, GitReference> after = repo.showRef();

    assertThat(after.keySet()).containsExactly("refs/heads/master", "refs/heads/bar");

    // All the refs point to the same commit.
    assertThat(ImmutableSet.of(after.values())).hasSize(1);
  }

  @Test
  public void testFetch() throws RepoException, IOException {
    GitRepository dest = GitRepository.bareRepo(Files.createTempDirectory("destDir"),
        System.getenv(), /*verbose=*/true);
    dest.initGitDir();

    Files.write(workdir.resolve("foo.txt"), new byte[]{});
    repository.add().files("foo.txt").run();
    repository.simpleCommand("commit", "foo.txt", "-m", "message");
    repository.simpleCommand("branch", "deleted");
    repository.simpleCommand("branch", "unchanged");

    String fetchUrl = "file://" + repository.getGitDir();

    FetchResult result = dest.fetch(fetchUrl,/*prune=*/true, /*force=*/true,
        ImmutableList.of("refs/*:refs/*"));

    assertThat(result.getDeleted()).isEmpty();
    assertThat(result.getUpdated()).isEmpty();
    assertThat(result.getInserted().keySet()).containsExactly(
        "refs/heads/master",
        "refs/heads/deleted",
        "refs/heads/unchanged");

    Files.write(workdir.resolve("foo.txt"), new byte[]{42});
    repository.simpleCommand("commit", "foo.txt", "-m", "message2");
    repository.simpleCommand("branch", "-D", "deleted");

    result = dest.fetch(fetchUrl, /*prune=*/true, /*force=*/true,
        ImmutableList.of("refs/*:refs/*"));

    assertThat(result.getDeleted().keySet()).containsExactly("refs/heads/deleted");
    assertThat(result.getUpdated().keySet()).containsExactly("refs/heads/master");
    assertThat(result.getInserted()).isEmpty();
  }

  @Test
  public void testCheckoutLocalBranch() throws Exception {
    thrown.expect(CannotResolveReferenceException.class);
    thrown.expectMessage("Cannot find reference 'foo'");
    repository.simpleCommand("checkout", "foo");
  }

  @Test
  public void testGitBinaryResolution() throws Exception {
    assertThat(GitRepository.resolveGitBinary(ImmutableMap.<String, String>of()))
        .isEqualTo("git");
    assertThat(GitRepository.resolveGitBinary(ImmutableMap.of("GIT_EXEC_PATH", "/some/path")))
        .isEqualTo("/some/path/git");
  }
}
