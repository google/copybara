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
import static com.google.copybara.git.GitRepository.StatusCode.DELETED;
import static com.google.copybara.git.GitRepository.StatusCode.MODIFIED;
import static com.google.copybara.git.GitRepository.StatusCode.RENAMED;
import static com.google.copybara.git.GitRepository.StatusCode.UNMODIFIED;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.CannotResolveReferenceException;
import com.google.copybara.RepoException;
import com.google.copybara.git.GitRepository.StatusFile;
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
  public void testStatus() throws RepoException, IOException {
    GitRepository dest = GitRepository.bareRepo(Files.createTempDirectory("destDir"),
        System.getenv(), /*verbose=*/true);
    dest.initGitDir();

    Files.write(workdir.resolve("renamed"), "renamed".getBytes(UTF_8));
    Files.write(workdir.resolve("deleted"), "deleted".getBytes(UTF_8));
    Files.write(workdir.resolve("deleted2"), "deleted2".getBytes(UTF_8));
    Files.write(workdir.resolve("modified1"), "modified".getBytes(UTF_8));
    Files.write(workdir.resolve("modified2"), "modified".getBytes(UTF_8));
    Files.write(workdir.resolve("modified3"), "modified".getBytes(UTF_8));
    Files.write(workdir.resolve("unmodified"), "unmodified".getBytes(UTF_8));

    repository.add().files(
        "renamed",
        "deleted", "deleted2",
        "modified1", "modified2", "modified3",
        "unmodified")
        .run();

    repository.simpleCommand("commit", "-a", "-m", "message");

    Files.delete(workdir.resolve("deleted"));
    repository.simpleCommand("rm", "deleted2");
    repository.simpleCommand("mv", "renamed", "renamed2");

    Files.write(workdir.resolve("modified1"), "modifiedxxx".getBytes(UTF_8));
    Files.write(workdir.resolve("modified2"), "modifiedxxx".getBytes(UTF_8));
    Files.write(workdir.resolve("modified3"), "modifiedxxx".getBytes(UTF_8));

    repository.add().files("modified1", "modified2").run();

    Files.write(workdir.resolve("modified2"), "modifiedyyy".getBytes(UTF_8));

    for (StatusFile statusFile : repository.status()) {
      System.out.println(statusFile);
    }
    assertThat(repository.status()).containsExactly(
        new StatusFile("renamed", "renamed2", RENAMED, UNMODIFIED),
        new StatusFile("deleted", /*newFileName=*/null, UNMODIFIED, DELETED),
        new StatusFile("deleted2", /*newFileName=*/null, DELETED, UNMODIFIED),
        new StatusFile("modified1", /*newFileName=*/null, MODIFIED, UNMODIFIED),
        new StatusFile("modified2", /*newFileName=*/null, MODIFIED, MODIFIED),
        new StatusFile("modified3", /*newFileName=*/null, UNMODIFIED, MODIFIED)
    );

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
