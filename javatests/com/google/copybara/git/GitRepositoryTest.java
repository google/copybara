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
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.authoring.Author;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.git.GitRepository.GitObjectType;
import com.google.copybara.git.GitRepository.StatusFile;
import com.google.copybara.git.GitRepository.TreeElement;
import com.google.copybara.testing.OptionsBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitRepositoryTest {

  private static final Author COMMITER = new Author("Commit Bara", "commitbara@example.com");
  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private GitRepository repository;
  private Path workdir;
  private OptionsBuilder options;

  @Before
  public void setup() throws Exception {
    options = new OptionsBuilder()
        .setOutputRootToTmpDir();
    workdir = Files.createTempDirectory("workdir");
    this.repository = GitRepository.initScratchRepo(
        /*verbose=*/true, getGitEnv(), options.general.getTmpDirectoryFactory())
        .withWorkTree(workdir);
  }

  @Test
  public void testShowRef() throws RepoException, IOException {
    GitRepository repo = repository.withWorkTree(workdir);
    repo.initGitDir();
    ImmutableMap<String, GitRevision> before = repo.showRef();

    assertThat(before).isEmpty();

    Files.write(workdir.resolve("foo.txt"), new byte[]{});
    repository.add().files("foo.txt").run();
    repo.simpleCommand("commit", "foo.txt", "-m", "message");
    repo.simpleCommand("branch", "bar");
    ImmutableMap<String, GitRevision> after = repo.showRef();

    assertThat(after.keySet()).containsExactly("refs/heads/master", "refs/heads/bar");

    // All the refs point to the same commit.
    assertThat(ImmutableSet.of(after.values())).hasSize(1);
  }

  @Test
  public void testStatus() throws RepoException, IOException {
    GitRepository dest = GitRepository.bareRepo(Files.createTempDirectory("destDir"),
        getGitEnv(), /*verbose=*/true);
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

  /**
   * Regression that tests that we convert the commit message to use only '\n' as line separator.
   * Many parts of the system use '\n' as separator.
   *
   * While this is contentious, Copybara already has an opinionated way of constructing messages
   * (For example labels).
   */
  @Test
  public void testLogCrlf() throws Exception {
    Files.write(workdir.resolve("foo.txt"), new byte[]{});
    repository.add().files("foo.txt").run();

    Path message = Files.createTempFile("message", "");
    Files.write(message, "message\r\nbar\r\n".getBytes(UTF_8));
    System.err.println("message " + message);
    repository.simpleCommand("commit", "-m", "fooo");
    String tree = repository.simpleCommand("log", "--format=%T", "-1").getStdout().trim();
    // Create a new commit using 'commit-tree' since 'commit' rewrites the message removing
    // \r in unix environment. No matter if you disable core.autocrlf, core.eol, etc.
    String change = repository.simpleCommand("commit-tree", tree, "-F", message.toString())
        .getStdout().trim();

    assertThat(Iterables.getLast(repository.log(change).run()).getBody())
        .isEqualTo("message\nbar\n");
  }

  @Test
  public void testLog() throws Exception {
    checkLog(/*body=*/true, /*includeFiles=*/true);
    checkLog(/*body=*/true, /*includeFiles=*/false);
    checkLog(/*body=*/false, /*includeFiles=*/true);
    checkLog(/*body=*/false, /*includeFiles=*/false);
  }

  private void checkLog(boolean body, boolean includeFiles) throws IOException, RepoException,
      ValidationException {
    workdir = Files.createTempDirectory("workdir");
    this.repository = GitRepository.initScratchRepo(
        /*verbose=*/true, getGitEnv(), options.general.getTmpDirectoryFactory())
        .withWorkTree(workdir);
    Files.write(workdir.resolve("foo.txt"), "foo fooo fooo".getBytes(UTF_8));
    repository.add().files("foo.txt").run();
    ZonedDateTime date = ZonedDateTime.now(ZoneId.of("-07:00"))
        .truncatedTo(ChronoUnit.SECONDS);
    ZonedDateTime date2 = date.plus(1, ChronoUnit.SECONDS)
        .withZoneSameInstant(ZoneId.of("-05:00"));

    repository.commit("Foo <foo@bara.com>", date, "message");

    // Test rename to check that we use --name-only with --no-renames
    Files.move(workdir.resolve("foo.txt"), workdir.resolve("bar.txt"));
    Files.write(workdir.resolve("baz.txt"), "baz baz baz".getBytes(UTF_8));
    repository.add().all().run();
    repository.commit("Bar <bar@bara.com>", date2, "message\n\nand\nparagraph");
    ImmutableList<GitLogEntry> entries = repository.log("master")
        .includeBody(body)
        .includeFiles(includeFiles)
        .run();

    assertThat(entries.size()).isEqualTo(2);

    assertThat(entries.get(0).getBody()).isEqualTo(body ? "message\n\nand\nparagraph\n" : null);
    assertThat(entries.get(1).getBody()).isEqualTo(body ? "message\n" : null);

    assertThat(entries.get(0).getAuthor()).isEqualTo(new Author("Bar", "bar@bara.com"));
    assertThat(entries.get(0).getCommitter()).isEqualTo(COMMITER);

    assertThat(entries.get(0).getAuthorDate()).isEqualTo(date2);
    assertThat(entries.get(1).getAuthor()).isEqualTo(new Author("FOO", "foo@bara.com"));
    assertThat(entries.get(1).getCommitter()).isEqualTo(COMMITER);
    assertThat(entries.get(1).getAuthorDate()).isEqualTo(date);
    assertThat(entries.get(0).getParents()).containsExactly(entries.get(1).getCommit());
    assertThat(entries.get(1).getParents()).isEmpty();

    if (includeFiles) {
      assertThat(entries.get(0).getFiles()).containsExactly("foo.txt", "bar.txt", "baz.txt");
      assertThat(entries.get(1).getFiles()).containsExactly("foo.txt");
    } else {
      assertThat(entries.get(0).getFiles()).isNull();
      assertThat(entries.get(1).getFiles()).isNull();
    }
  }

  @Test
  public void testFetch() throws Exception {
    GitRepository dest = GitRepository.bareRepo(Files.createTempDirectory("destDir"),
        getGitEnv(), /*verbose=*/true);
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
    thrown.expect(RepoException.class);
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

  @Test
  public void validateUrl() throws RepoException {
    GitRepository.validateUrl("ssh://git@github.com:foo/foo.git");
    GitRepository.validateUrl("https://github.com/foo/foo");
    GitRepository.validateUrl("protocol://some/url");
    GitRepository.validateUrl("git@github.com:foo/foo.git");
  }

  @Test
  public void invalidUrl() throws RepoException {
    thrown.expect(RepoException.class);
    thrown.expectMessage("URL 'lalala' is not valid");
    GitRepository.validateUrl("lalala");
  }

  @Test
  public void testLsRemote() throws Exception {

    Files.write(workdir.resolve("foo.txt"), new byte[] {});
    repository.add().files("foo.txt").run();
    repository.simpleCommand("commit", "foo.txt", "-m", "message");
    repository.simpleCommand("branch", "b1");

    Map<String, String> refsToShas =
        GitRepository.lsRemote("file://" + repository.getGitDir(), Collections.emptyList());
    assertThat(refsToShas.size()).isEqualTo(3);
    String headSha = refsToShas.get("HEAD");
    assertThat(refsToShas.get("refs/heads/b1")).isEqualTo(headSha);
    assertThat(refsToShas.get("refs/heads/master")).isEqualTo(headSha);

    repository.simpleCommand("checkout", "b1");
    Files.write(workdir.resolve("boo.txt"), new byte[] {});
    repository.add().files("boo.txt").run();
    repository.simpleCommand("commit", "boo.txt", "-m", "message");
    refsToShas =
        GitRepository.lsRemote("file://" + repository.getGitDir(), Collections.emptyList());
    assertThat(refsToShas.size()).isEqualTo(3);
    assertThat(refsToShas.get("refs/heads/b1")).isNotEqualTo(headSha);
  }

  @Test
  public void testLsTreeWithReviewContext() throws Exception {

    Files.write(Files.createDirectories(workdir.resolve("foo")).resolve("foo.txt"), new byte[] {});
    repository.add().files("foo/foo.txt").run();
    repository.simpleCommand("commit", "foo/foo.txt", "-m", "message");
    GitRevision rev = new GitRevision(repository, repository.parseRef("HEAD"),
                                       "this is review text", /*reference=*/null,
                                       ImmutableMap.of());
    ImmutableList<TreeElement> result = repository.lsTree(rev, "foo/");
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getPath()).isEqualTo("foo/foo.txt");
    assertThat(result.get(0).getType()).isEqualTo(GitObjectType.BLOB);
  }
}
