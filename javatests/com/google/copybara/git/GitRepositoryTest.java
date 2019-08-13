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
import static com.google.copybara.git.GitRepository.StatusCode.UNTRACKED;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static com.google.copybara.testing.git.GitTestUtil.writeFile;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.git.GitRepository.GitObjectType;
import com.google.copybara.git.GitRepository.StatusFile;
import com.google.copybara.git.GitRepository.TreeElement;
import com.google.copybara.util.CommandOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitRepositoryTest {

  private static final Author COMMITER = new Author("Commit Bara", "commitbara@example.com");
  private static final int SOME_LARGE_INPUT_SIZE = 256_000;
  private static final String TEST_TAG_NAME = "test_v1";

  private GitRepository repository;
  private Path workdir;

  @Before
  public void setup() throws Exception {
    workdir = Files.createTempDirectory("workdir");
    repository = GitRepository
        .newBareRepo(Files.createTempDirectory("gitdir"), getGitEnv(), /*verbose=*/true,
            DEFAULT_TIMEOUT)
        .withWorkTree(workdir);
    repository.init();
  }

  @Test
  public void testShowRef() throws RepoException, IOException {
    GitRepository repo = repository.withWorkTree(workdir);
    repo.init();
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
  public void testShowDiff() throws Exception {
    GitRepository repo = repository.withWorkTree(workdir);
    repo.init();

    Files.write(workdir.resolve("foo.txt"), new byte[]{});
    repository.add().files("foo.txt").run();
    repo.simpleCommand("commit", "foo.txt", "-m", "message_a");
    ImmutableMap<String, GitRevision> revisionsFoo = repo.showRef();

    Files.write(workdir.resolve("bar.txt"), "change content".getBytes(UTF_8));
    repository.add().files("bar.txt").run();
    repo.simpleCommand("commit", "bar.txt", "-m", "message_s");
    ImmutableMap<String, GitRevision> revisionsBar = repo.showRef();

    String diff = repo.showDiff(revisionsFoo.values().asList().get(0).getSha1(),
        revisionsBar.values().asList().get(0).getSha1());
    assertThat("index 0000000..805c36b\n--- /dev/null\n").matches("(.*\n){2}");
    assertThat(diff).matches("(diff --git a/bar.txt b/bar.txt\nnew file mode 100644\n)"
        + "(.*\n){4}(\\+change content\n)(.*\n)");
  }

  @Test
  public void testBadCommitInLog() throws RepoException, IOException {
    Files.write(workdir.resolve("foo.txt"), new byte[]{});
    repository.add().files("foo.txt").run();
    //
    repository.simpleCommand("commit", "foo.txt", "-m", "message");
    GitLogEntry entry = Iterables.getOnlyElement(repository.log("HEAD").withLimit(1).run());

    String badCommit = "tree " + entry.getTree() + "\n"
        + "parent " + entry.getCommit().getSha1() + "\n"
        + "author Some User <example@example.com> 1528942829 --400\n"
        + "committer Some User <example@example.com> 1528942829 --400\n"
        + "\n"
        + "Allow to check and resolve symlinks";
    Files.write(workdir.resolve("commit"), badCommit.getBytes(UTF_8));
    String commitSha1 = repository.simpleCommand("hash-object", "-w", "-t", "commit",
        "--", workdir.resolve("commit")
            .toAbsolutePath().toString()).getStdout().trim();
    entry = Iterables.getOnlyElement(repository.log(commitSha1).withLimit(1).run());

    ZonedDateTime epoch = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);

    assertThat(entry.getAuthorDate()).isEquivalentAccordingToCompareTo(epoch);
    assertThat(entry.getCommitDate()).isEquivalentAccordingToCompareTo(epoch);
  }

  @Test
  public void testStatus() throws RepoException, IOException {
    GitRepository dest = GitRepository.newBareRepo(Files.createTempDirectory("destDir"),
        getGitEnv(), /*verbose=*/true, DEFAULT_TIMEOUT);
    dest.init();

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
  public void testForceClean() throws RepoException, IOException {
    GitRepository dest = GitRepository.newBareRepo(Files.createTempDirectory("destDir"),
        getGitEnv(), /*verbose=*/true, DEFAULT_TIMEOUT);
    dest.init();

    Files.write(Files.createDirectories(workdir.resolve("some/folder")).resolve("file.txt"),
        "".getBytes(UTF_8));

    repository.add().all().run();

    repository.simpleCommand("commit", "-a", "-m", "message");

    Files.write(Files.createDirectories(workdir.resolve("other/folder")).resolve("file.txt"),
        "".getBytes(UTF_8));

    assertThat(repository.status()).containsExactly(
        new StatusFile("other/", /*newFileName=*/null, UNTRACKED, UNTRACKED));

    repository.forceClean();

    assertThat(repository.status()).isEmpty();
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

  @Test
  public void testMerge() throws Exception {
    Files.write(workdir.resolve("foo.txt"), "".getBytes(UTF_8));
    repository.add().all().run();
    repository.simpleCommand("commit", "-m", "first");
    repository.simpleCommand("branch", "foo");
    repository.forceCheckout("foo");
    Files.write(workdir.resolve("bar.txt"), "".getBytes(UTF_8));
    repository.add().all().run();
    repository.simpleCommand("commit", "-m", "branch change");
    repository.forceCheckout("master");
    Files.write(workdir.resolve("foo.txt"), "modified".getBytes(UTF_8));
    repository.add().all().run();
    repository.simpleCommand("commit", "-m", "second");
    repository.simpleCommand("merge", "foo");

    ImmutableList<GitLogEntry> log = repository.log("master").includeFiles(true)
        .firstParent(true).run();

    assertThat(log.get(0).getBody()).contains("Merge");
    assertThat(log.get(0).getFiles()).isEmpty();

    log = repository.log("master").includeFiles(true).firstParent(true).includeMergeDiff(true)
        .run();
    assertThat(log.get(0).getBody()).contains("Merge");
    assertThat(log.get(0).getFiles()).containsExactly("bar.txt");

    log = repository.log("master").includeFiles(true).includeMergeDiff(true).run();

    assertThat(log.get(0).getBody()).contains("Merge");
    assertThat(log.get(0).getFiles()).containsExactly("bar.txt");
  }

  private void checkLog(boolean body, boolean includeFiles) throws IOException, RepoException,
      ValidationException {
    workdir = Files.createTempDirectory("workdir");
    this.repository = GitRepository.newBareRepo(Files.createTempDirectory("gitdir"),
        getGitEnv(), /*verbose=*/true, DEFAULT_TIMEOUT)
        .withWorkTree(workdir)
        .init();

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

    assertThat(
        repository.simpleCommand("cat-file", "-t", entries.get(0).getTree()).getStdout().trim())
        .isEqualTo("tree");

    if (includeFiles) {
      assertThat(entries.get(0).getFiles()).containsExactly("foo.txt", "bar.txt", "baz.txt");
      assertThat(entries.get(1).getFiles()).containsExactly("foo.txt");
    } else {
      assertThat(entries.get(0).getFiles()).isNull();
      assertThat(entries.get(1).getFiles()).isNull();
    }
  }

  @Test
  public void testMultipleEntriesForMergeDiff() throws Exception {
    createGraphOfCommits();

    ImmutableList<GitLogEntry> result = repository.log("master")
        .includeFiles(true)
        .includeMergeDiff(true)
        .firstParent(false)
        .run();

    // Three entries for the same commit. One per each branch merged
    assertThat(result.get(0).getCommit()).isEqualTo(result.get(1).getCommit());
    assertThat(result.get(1).getCommit()).isEqualTo(result.get(2).getCommit());
    assertThat(result.get(2).getCommit()).isNotEqualTo(result.get(3).getCommit());

    // But the first entry is the difference with the current branch
    assertThat(result.get(0).getFiles()).containsExactly("feature1.txt", "feature2.txt");

    // Amend the merge
    Files.write(workdir.resolve("other"), "content".getBytes(UTF_8));
    repository.add().files("other").run();
    repository.commit("Foo <bar@bara.com>", /*amend=*/true,
        ZonedDateTime.now(ZoneId.of("-07:00")).truncatedTo(ChronoUnit.SECONDS), "Merge");

    result = repository.log("master")
        .includeFiles(true)
        .includeMergeDiff(true)
        .firstParent(false)
        .withLimit(1)
        .run();

    // Still three entries
    assertThat(result).hasSize(3);
    // But the first entry is the difference with the current branch
    assertThat(result.get(0).getFiles()).containsExactly("feature1.txt", "feature2.txt", "other");
  }

  @Test
  public void testPagination() throws Exception {
    createGraphOfCommits();
    ImmutableList<GitLogEntry> singlePage = repository.log("master")
        .firstParent(false)
        .run();
    List<GitLogEntry> paged = new ArrayList<>();
    int skip = 0;
    for (int i = 0; i < 1000; i++) {
      ImmutableList<GitLogEntry> page = repository.log("master")
          .firstParent(false)
          .withLimit(3)
          .withSkip(skip)
          .run();
      if (page.size() == 0) {
        break;
      }
      paged.addAll(page);
      skip = paged.size();
    }
    assertThat(paged.toString()).isEqualTo(singlePage.toString());

    singlePage = repository.log("master")
        .includeFiles(true)
        .includeMergeDiff(true)
        .firstParent(false)
        .run();
    paged = new ArrayList<>();
    skip = 0;
    for (int i = 0; i < 1000; i++) {
      ImmutableList<GitLogEntry> page = repository.log("master")
          .includeFiles(true)
          .includeMergeDiff(true)
          .firstParent(false)
          .withLimit(3)
          .withSkip(skip)
          .run();
      if (page.size() == 0) {
        break;
      }
      paged.addAll(page);
      // Merge commit shows multiple entries when using -m and --name-only but first parent is
      // disabled. Each entry represents the parent file changes.
      skip += page.stream().map(e -> e.getCommit().getSha1()).collect(Collectors.toSet()).size();
    }
    assertThat(paged.toString()).isEqualTo(singlePage.toString());
  }

  private void createGraphOfCommits() throws Exception {
    for (int i = 0; i < 10; i++) {
      singleFileCommit("master_" + i, "foo.txt", "foo_" + i);
    }
    repository.simpleCommand("checkout", "-b", "feature1");
    singleFileCommit("feature1_first", "feature1.txt", "feature1_first");
    String feature1Second = repository.parseRef("HEAD");
    singleFileCommit("feature1_second", "feature1.txt", "feature1_second");
    for (int i = 0; i < 10; i++) {
      singleFileCommit("feature1_" + i, "feature1.txt", "feature1_" + i);
    }
    String feature1Head = repository.parseRef("HEAD");
    repository.forceCheckout(feature1Second);
    repository.simpleCommand("checkout", "-b", "feature2");
    for (int i = 0; i < 10; i++) {
      singleFileCommit("feature2_" + i, "feature2.txt", "feature2_" + i);
    }
    repository.simpleCommand("merge", feature1Head);
    for (int i = 10; i < 20; i++) {
      singleFileCommit("feature2_" + i, "feature2.txt", "feature2_" + i);
    }
    repository.forceCheckout("feature1");
    singleFileCommit("feature1_last", "feature1.txt", "feature1_last");
    repository.forceCheckout("master");
    singleFileCommit("master_last", "foo.txt", "master_last");
    repository.simpleCommand("merge", "feature1", "feature2");
  }

  private void singleFileCommit(String message, String file, String content) throws Exception {
    Path path = workdir.resolve(file);
    Files.createDirectories(path.getParent());
    Files.write(path, content.getBytes(UTF_8));
    repository.add().files(file).run();
    repository.commit("Foo <bar@bara.com>",
        ZonedDateTime.now(ZoneId.of("-07:00")).truncatedTo(ChronoUnit.SECONDS), message);
  }

  @Test
  public void testLogContainingEquals() throws Exception {
    Files.write(workdir.resolve("foo.txt"), "foo".getBytes(UTF_8));
    repository.add().files("foo.txt").run();
    ZonedDateTime date = ZonedDateTime.now(ZoneId.of("-07:00"))
        .truncatedTo(ChronoUnit.SECONDS);
    repository.commit("= Foo = <bar@bara.com>", date, "adding foo");

    ImmutableList<GitLogEntry> entries = repository.log("master").run();
    assertThat(entries.get(0).getAuthor()).isEqualTo(new Author("= Foo =", "bar@bara.com"));
  }

  @Test
  public void testFetch() throws Exception {
    GitRepository dest = GitRepository.newBareRepo(Files.createTempDirectory("destDir"),
        getGitEnv(), /*verbose=*/true, DEFAULT_TIMEOUT);
    dest.init();

    Files.write(workdir.resolve("foo.txt"), new byte[]{});
    repository.add().files("foo.txt").run();
    repository.simpleCommand("commit", "foo.txt", "-m", "message");
    repository.simpleCommand("branch", "deleted");
    repository.simpleCommand("branch", "unchanged");

    String fetchUrl = "file://" + repository.getGitDir();

    FetchResult result = dest.fetch(fetchUrl, /*prune=*/true, /*force=*/true,
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
  public void testFetchNonHeadSHA1() throws Exception {
    List<Iterable<String>> requestedFetches = new ArrayList<>();

    GitRepository dest = new GitRepository(Files.createTempDirectory("destDir"), /*workTree=*/null,
        true, getGitEnv(), DEFAULT_TIMEOUT) {

      @Override
      public FetchResult fetch(String url, boolean prune, boolean force, Iterable<String> refspecs)
          throws RepoException, ValidationException {
        requestedFetches.add(refspecs);
        return super.fetch(url, prune, force, refspecs);
      }
    }.init();

    Files.write(workdir.resolve("foo.txt"), "aaa".getBytes(UTF_8));
    repository.add().files("foo.txt").run();
    repository.simpleCommand("commit", "foo.txt", "-m", "message 1");

    try {
      dest.fetchSingleRef("file://" + repository.getGitDir(),
          "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
      fail();
    } catch (CannotResolveRevisionException ignore) {
      // ignored
    }

    // This is the important part of the test: We do two fetches, the first ones for the default
    // head and if it fails we do one for the ref
    assertThat(requestedFetches).isEqualTo(ImmutableList.of(
        ImmutableList.of(),
        ImmutableList.of("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa:refs/copybara_fetch/aaaaaaaaaa"
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")));
  }

  @Test
  public void testFetchInvalidGitRepo() throws Exception {
    GitRepository dest = GitRepository.newBareRepo(Files.createTempDirectory("destDir"),
        getGitEnv(), /*verbose=*/true, DEFAULT_TIMEOUT);
    dest.init();

    Path notAGitRepo = Files.createTempDirectory("not_a_git_repo");
    String fetchUrl = "file://" + notAGitRepo.toString();

    try {
      dest.fetch(fetchUrl, /*prune=*/ true, /*force=*/ true, ImmutableList.of("refs/*:refs/*"));
      fail();
    } catch (CannotResolveRevisionException expected) {
      // Expected
    }
  }

  @Test
  public void testCheckoutLocalBranch() throws Exception {
    try {
      repository.simpleCommand("checkout", "foo");
      fail("Expected an exception");
    } catch (RepoException expected) {
      assertThat(expected).hasMessageThat().contains("Cannot find reference 'foo'");
    }
  }

  @Test
  public void validateUrl() throws Exception {
    doValidateUrl("ssh://git@github.com:foo/foo.git");
    doValidateUrl("https://github.com/foo/foo");
    doValidateUrl("https://localhost:33333/foo/bar");
    doValidateUrl("https://localhost:33333/foo/bar?some_arg=1&other_arg=2");
    doValidateUrl("protocol://some/url");
    doValidateUrl("git@github.com:foo/foo.git");
    doValidateUrl("ssh://git@private.com:foo/foo.git");
    doValidateUrl("ssh://git@private.com/foo/foo.git");
    doValidateUrl("git@internal-git.mycompany.net:client_repo/mobile-apps.git");
    doValidateUrl("git@internal-git.mycompany.net:9811/client_repo/mobile-apps.git");
    doValidateUrl("git@internal-git.mycompany.net:9811:client_repo/mobile-apps.git");
    doValidateUrl("https://internal-git.mycompany.net/client_repo/mobile-apps.git");
    doValidateUrl("https://internal-git.mycompany.net/client_repo/mobile-apps");
    doValidateUrl("https://internal-git.mycompany.net:8911/client_repo/mobile-apps");

    // A folder is a valid url. We do a sanity check internally that the directory exist. See
    // #invalidUrl test for a failure case.
    doValidateUrl(workdir.toFile().getAbsolutePath());
    doValidateUrl("file://" + workdir.toFile().getAbsolutePath());
    doValidateUrl("file:///tmp/copybara-test/");
    doValidateUrl("file:///tmp/localhost:3333/");
  }

  @Test
  public void invalidUrl() throws Exception {
    try {
      String result = GitRepository.validateUrl("lalala");
      fail("Expected an exception, got: "  + result);
    } catch (RepoException expected) {
      assertThat(expected).hasMessageThat().contains("URL 'lalala' is not valid");
    }
  }

  @Test
  public void httpUrl() throws RepoException {
    try {
      String result = GitRepository.validateUrl("http://github.com/foo/foo");
      fail("Expected an exception, got: "  + result);
    } catch (ValidationException expected) {
      assertThat(expected).hasMessageThat()
          .contains("URL 'http://github.com/foo/foo' is not valid - should be using https");
    }
  }

  private void doValidateUrl(String url) throws Exception {
    assertThat(GitRepository.validateUrl(url)).isEqualTo(url);
  }

  @Test
  public void testLsRemote() throws Exception {
    Files.write(workdir.resolve("foo.txt"), new byte[] {});
    repository.add().files("foo.txt").run();
    repository.simpleCommand("commit", "foo.txt", "-m", "message");
    repository.simpleCommand("branch", "b1");

    Map<String, String> refsToShas =
        GitRepository.lsRemote(
            "file://" + repository.getGitDir(),
            Collections.emptyList(),
            new GitEnvironment(System.getenv()), /*maxLogLines*/
            -1);
    assertThat(refsToShas.size()).isEqualTo(3);
    String headSha = refsToShas.get("HEAD");
    assertThat(refsToShas.get("refs/heads/b1")).isEqualTo(headSha);
    assertThat(refsToShas.get("refs/heads/master")).isEqualTo(headSha);

    repository.simpleCommand("checkout", "b1");
    Files.write(workdir.resolve("boo.txt"), new byte[] {});
    repository.add().files("boo.txt").run();
    repository.simpleCommand("commit", "boo.txt", "-m", "message");
    refsToShas =
        GitRepository.lsRemote(
            "file://" + repository.getGitDir(),
            Collections.emptyList(),
            new GitEnvironment(System.getenv()), /*maxLogLines*/
            -1);
    assertThat(refsToShas.size()).isEqualTo(3);
    assertThat(refsToShas.get("refs/heads/b1")).isNotEqualTo(headSha);
  }

  @Test
  public void testLsTreeWithReviewContext() throws Exception {
    Files.write(Files.createDirectories(workdir.resolve("foo")).resolve("foo.txt"), new byte[] {});
    repository.add().files("foo/foo.txt").run();
    repository.simpleCommand("commit", "foo/foo.txt", "-m", "message");
    GitRevision rev = new GitRevision(repository, repository.parseRef("HEAD"),
        "this is review text", /*reference=*/null, ImmutableListMultimap.of(), /*url=*/null);
    ImmutableList<TreeElement> result = repository.lsTree(rev, "foo/");
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getPath()).isEqualTo("foo/foo.txt");
    assertThat(result.get(0).getType()).isEqualTo(GitObjectType.BLOB);
  }

  @Test
  public void commitWithLargeDescription() throws IOException, RepoException, ValidationException {
    String line = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789\n";
    int repeats = SOME_LARGE_INPUT_SIZE / line.getBytes(StandardCharsets.UTF_8).length;
    StringBuilder descBuilder = new StringBuilder();
    for (int i = 0; i < repeats; i++) {
      descBuilder.append(line);
    }
    Files.write(workdir.resolve("foo.txt"), new byte[] {});
    repository.add().files("foo.txt").run();
    ZonedDateTime date = ZonedDateTime.now(ZoneId.of("-07:00"))
        .truncatedTo(ChronoUnit.SECONDS);
    String description = descBuilder.toString();
    repository.commit("Foo <foo@bara.com>", date, description);

    ImmutableList<GitLogEntry> entries = repository.log("master")
        .includeBody(true)
        .includeFiles(false)
        .run();
    assertThat(Iterables.getOnlyElement(entries).getBody())
        .isEqualTo(description);
  }

  @Test
  public void testPush() throws Exception {
    GitRepository remote = GitRepository
        .newBareRepo(Files.createTempDirectory("remote"), getGitEnv(), /*verbose=*/true,
            DEFAULT_TIMEOUT)
        .init();
    Files.write(workdir.resolve("foo.txt"), new byte[]{});
    repository.add().files("foo.txt").run();
    repository.simpleCommand("commit", "-m", "message");

    String remoteUrl = "file:///" + remote.getGitDir();

    // Push the first version. Need to force because destination is empty
    repository.push()
        .withRefspecs(remoteUrl, ImmutableList.of(repository.createRefSpec("+master:master")))
        .run();

    assertThat(Iterables.transform(remote.log("master").run(), GitLogEntry::getBody))
        .containsExactly("message\n");

    Files.write(workdir.resolve("foo.txt"), "a".getBytes(UTF_8));
    repository.add().files("foo.txt").run();
    repository.simpleCommand("commit", "-m", "message2");

    // Try a simple push that is fast-forward
    repository.push()
        .withRefspecs(remoteUrl, ImmutableList.of(repository.createRefSpec("master:master")))
        .run();

    assertThat(Iterables.transform(remote.log("master").run(), GitLogEntry::getBody))
        .containsExactly("message2\n", "message\n");

    repository.simpleCommand("reset", "--hard", "HEAD~1");

    Files.write(workdir.resolve("foo.txt"), "a".getBytes(UTF_8));
    repository.add().files("foo.txt").run();
    repository.simpleCommand("commit", "-m", "message3");

    // We replaced the last commit. Should fail because we don't force
    try {
      repository.push()
          .withRefspecs(remoteUrl, ImmutableList.of(repository.createRefSpec("master:master")))
          .run();
      fail("Should fail because non-fastforward");
    } catch (RepoException e) {
      assertThat(e.getMessage()).contains("[rejected]");
    }

    // Now it works because we force the push
    repository.push()
        .withRefspecs(remoteUrl, ImmutableList.of(repository.createRefSpec("+master:master")))
        .run();

    assertThat(Iterables.transform(remote.log("master").run(), GitLogEntry::getBody))
        .containsExactly("message3\n", "message\n");
  }

  @Test
  public void testPushPrune() throws Exception {
    GitRepository remote = GitRepository.newBareRepo(Files.createTempDirectory("remote"),
        getGitEnv(), /*verbose=*/true, DEFAULT_TIMEOUT);
    remote.init();
    Files.write(workdir.resolve("foo.txt"), new byte[]{});
    repository.add().files("foo.txt").run();
    repository.simpleCommand("commit", "-m", "message");
    repository.simpleCommand("branch", "other");

    String remoteUrl = "file:///" + remote.getGitDir();

    repository.push()
        .withRefspecs(remoteUrl, ImmutableList.of(repository.createRefSpec("+*:*")))
        .run();

    assertThat(remote.refExists("master")).isTrue();
    assertThat(remote.refExists("other")).isTrue();

    repository.simpleCommand("branch", "-d", "other");

    repository.push()
        .withRefspecs(remoteUrl, ImmutableList.of(repository.createRefSpec("*:*")))
        .run();

    assertThat(remote.refExists("master")).isTrue();
    assertThat(remote.refExists("other")).isTrue();

    repository.push().prune(true)
        .withRefspecs(remoteUrl, ImmutableList.of(repository.createRefSpec("*:*")))
        .run();

    assertThat(remote.refExists("master")).isTrue();
    assertThat(remote.refExists("other")).isFalse();
  }

  @Test
  public void testLightWeightTag() throws Exception {
    setUpForTagTest(null);
    CommandOutput commandOutput = repository.simpleCommand("tag", "-n9");
    assertThat(commandOutput.getStdout()).matches(".*" + TEST_TAG_NAME + ".*message_1.*\\n");
  }

  @Test
  public void testAnnotatedTag() throws Exception {
    setUpForTagTest("message_2");
    CommandOutput commandOutput = repository.simpleCommand("tag", "-n9");
    assertThat(commandOutput.getStdout()).matches(
        ".*" + TEST_TAG_NAME + ".*message_2.*\\n");
  }

  @Test
  public void testTagWithExistingTag() throws Exception {
    try {
      setUpForTagTest("message_2");
      repository.tag(TEST_TAG_NAME).run();
      fail();
    } catch (RepoException e) {
      assertThat(e).hasMessageThat().contains("Stderr: fatal: tag 'test_v1' already exists");
    }
  }

  @Test
  public void testTagWithExistingTagAndForce() throws Exception {
    setUpForTagTest("message_2");
    repository.tag(TEST_TAG_NAME).withAnnotatedTag("message_3").force(true).run();
    CommandOutput commandOutput = repository.simpleCommand("tag", "-n9");
    String s = commandOutput.getStdout();
    assertThat(commandOutput.getStdout()).matches(
        ".*" + TEST_TAG_NAME + ".*message_3.*\\n");
  }

  private void setUpForTagTest(String tagMsg)
      throws Exception {
    Files.write(workdir.resolve("foo.txt"), new byte[]{});
    repository.add().files("foo.txt").run();
    repository.simpleCommand("commit", "-m", "message_1");
    if (tagMsg != null) {
      repository.tag(TEST_TAG_NAME).withAnnotatedTag(tagMsg).run();
    } else {
      repository.tag(TEST_TAG_NAME).run();
    }
  }
}
