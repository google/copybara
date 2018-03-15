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
import static com.google.copybara.git.GitIntegrateChanges.Strategy.FAKE_MERGE;
import static com.google.copybara.git.GitIntegrateChanges.Strategy.INCLUDE_FILES;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static org.junit.Assert.fail;

import com.google.api.client.http.HttpTransport;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.Destination.Writer;
import com.google.copybara.exception.RepoException;
import com.google.copybara.TransformResult;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitIntegrateChanges.Strategy;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.git.testing.GitTesting;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.testing.git.GitTestUtil.TestGitOptions;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitDestinationIntegrateTest {

  private static final String CHANGE_ID = "Id5287e977c0d840a6d84eb2c3c1841036c411890";

  private String url;

  private Path repoGitDir;
  private OptionsBuilder options;
  private TestingConsole console;
  private Glob destinationFiles;
  private SkylarkTestExecutor skylark;


  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  private Path workdir;
  private Path localHub;

  @Before
  public void setup() throws Exception {
    repoGitDir = Files.createTempDirectory("GitDestinationTest-repoGitDir");
    workdir = Files.createTempDirectory("workdir");

    localHub = Files.createTempDirectory("localRepos");

    git("init", "--bare", repoGitDir.toString());

    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console)
        .setOutputRootToTmpDir();
    options.git = new TestGitOptions(localHub, () -> options.general);

    options.github = new GithubOptions(() -> options.general, options.git) {
      @Override
      protected HttpTransport getHttpTransport() {
        return GitTestUtil.NO_GITHUB_API_CALLS;
      }
    };

    options.gitDestination = new GitDestinationOptions(() -> options.general, options.git);
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";

    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("ignore*"));
    options.setForce(true);

    url = "file://" + repoGitDir;
    skylark = new SkylarkTestExecutor(options, GitModule.class);
  }

  @Test
  public void testNoIntegration() throws ValidationException, IOException, RepoException {
    migrateOriginChange(
        destination(
            "url = '" + url + "'",
            "integrates = []"),
        "Test change\n\n"
            + GitModule.DEFAULT_INTEGRATE_LABEL + "=http://should_not_be_used\n", "some content");

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "master");
    assertThat(showResult).contains("some content");

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
  }

  @Test
  public void testDefaultIntegration() throws ValidationException, IOException, RepoException {
    Path repoPath = Files.createTempDirectory("test");
    GitRepository repo = GitRepository.newRepo(/*verbose=*/true, repoPath, getGitEnv())
        .init();
    GitRevision feature1 = singleChange(repoPath, repo, "ignore_me", "Feature1 change");
    repo.simpleCommand("branch", "feature1");
    GitRevision feature2 = singleChange(repoPath, repo, "ignore_me2", "Feature2 change");
    repo.simpleCommand("branch", "feature2");

    GitDestination destination = destinationWithDefaultIntegrates();
    migrateOriginChange(destination, "Base change\n", "not important");
    GitLogEntry previous = getLastMigratedChange("master");

    migrateOriginChange(destination, "Test change\n"
        + "\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "=file://" + repo.getWorkTree().toString()
        + " feature1\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "=file://" + repo.getWorkTree().toString()
        + " feature2\n", "some content");

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "master^1");
    assertThat(showResult).contains("some content");

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "some content")
        .containsFile("ignore_me", "")
        .containsFile("ignore_me2", "")
        .containsNoMoreFiles();

    GitLogEntry feature1Merge = getLastMigratedChange("master^1");

    assertThat(feature1Merge.getFiles()).containsExactly("test.txt", "ignore_me");

    assertThat(feature1Merge.getBody()).isEqualTo("Merge of " + feature1.getSha1() + "\n"
        + "\n"
        + DummyOrigin.LABEL_NAME + ": test\n");

    assertThat(Lists.transform(feature1Merge.getParents(), GitRevision::getSha1))
        .isEqualTo(Lists.newArrayList(previous.getCommit().getSha1(), feature1.getSha1()));

    GitLogEntry feature2Merge = getLastMigratedChange("master");
    assertThat(feature2Merge.getBody()).isEqualTo("Merge of " + feature2.getSha1() + "\n"
        + "\n"
        + DummyOrigin.LABEL_NAME + ": test\n");

    assertThat(Lists.transform(feature2Merge.getParents(), GitRevision::getSha1))
        .isEqualTo(Lists.newArrayList(feature1Merge.getCommit().getSha1(), feature2.getSha1()));
  }

  @Test
  public void testFakeMerge() throws ValidationException, IOException, RepoException {
    Path repoPath = Files.createTempDirectory("test");
    GitRepository repo = GitRepository.newRepo(/*verbose=*/true, repoPath, getGitEnv())
        .init();
    GitRevision feature1 = singleChange(repoPath, repo, "ignore_me", "Feature1 change");
    repo.simpleCommand("branch", "feature1");
    GitRevision feature2 = singleChange(repoPath, repo, "ignore_me2", "Feature2 change");
    repo.simpleCommand("branch", "feature2");

    GitDestination destination = destination(FAKE_MERGE);
    migrateOriginChange(destination, "Base change\n", "not important");
    GitLogEntry previous = getLastMigratedChange("master");

    migrateOriginChange(destination, "Test change\n"
        + "\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "=file://" + repo.getWorkTree().toString()
        + " feature1\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "=file://" + repo.getWorkTree().toString()
        + " feature2\n", "some content");

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "master^1");
    assertThat(showResult).contains("some content");

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();

    GitLogEntry feature1Merge = getLastMigratedChange("master^1");
    assertThat(feature1Merge.getBody()).isEqualTo("Merge of " + feature1.getSha1() + "\n"
        + "\n"
        + DummyOrigin.LABEL_NAME + ": test\n");

    assertThat(Lists.transform(feature1Merge.getParents(), GitRevision::getSha1))
        .isEqualTo(Lists.newArrayList(previous.getCommit().getSha1(), feature1.getSha1()));

    GitLogEntry feature2Merge = getLastMigratedChange("master");
    assertThat(feature2Merge.getBody()).isEqualTo("Merge of " + feature2.getSha1() + "\n"
        + "\n"
        + DummyOrigin.LABEL_NAME + ": test\n");

    assertThat(feature1Merge.getFiles()).containsExactly("test.txt");

    assertThat(Lists.transform(feature2Merge.getParents(), GitRevision::getSha1))
        .isEqualTo(Lists.newArrayList(feature1Merge.getCommit().getSha1(), feature2.getSha1()));
  }

  private GitDestination destination(Strategy strategy) throws ValidationException {
    return destination(
        "url = '" + url + "'",
        "integrates = [git.integrate("
            + "         ignore_errors = False,"
            + "         strategy = '" + strategy + "',"
            + "    ),]"
    );
  }

  @Test
  public void testIncludeFiles() throws ValidationException, IOException, RepoException {
    Path repoPath = Files.createTempDirectory("test");
    GitRepository repo = GitRepository.newRepo(/*verbose=*/true, repoPath, getGitEnv())
        .init();
    singleChange(repoPath, repo, "ignore_me", "Feature1 change");
    repo.simpleCommand("branch", "feature1");
    singleChange(repoPath, repo, "ignore_me2", "Feature2 change");
    repo.simpleCommand("branch", "feature2");

    GitDestination destination = destination(INCLUDE_FILES);
    migrateOriginChange(destination, "Base change\n", "not important");
    GitLogEntry previous = getLastMigratedChange("master");

    migrateOriginChange(destination, "Test change\n"
        + "\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "=file://" + repo.getWorkTree().toString()
        + " feature1\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "=file://" + repo.getWorkTree().toString()
        + " feature2\n", "some content");

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "master");
    assertThat(showResult).contains("some content");

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "some content")
        .containsFile("ignore_me", "")
        .containsFile("ignore_me2", "")
        .containsNoMoreFiles();

    GitLogEntry afterChange = getLastMigratedChange("master");
    assertThat(afterChange.getBody()).isEqualTo("Test change\n"
        + "\n"
        + DummyOrigin.LABEL_NAME + ": test\n");

    assertThat(Lists.transform(afterChange.getParents(), GitRevision::getSha1))
        .isEqualTo(Lists.newArrayList(previous.getCommit().getSha1()));
  }

  @Test
  public void testGitHubSemiFakeMerge() throws ValidationException, IOException, RepoException {
    Path workTree = Files.createTempDirectory("test");
    GitRepository repo = fakeHttpsRepo("github.com/example/test_repo").withWorkTree(workTree);

    GitRevision firstChange = singleChange(workTree, repo, "ignore_me", "Feature1 change");
    GitRevision secondChange = singleChange(workTree, repo, "ignore_me2", "Feature2 change");

    repo.simpleCommand("update-ref", "refs/pull/20/head", secondChange.getSha1());

    GitDestination destination = destinationWithDefaultIntegrates();
    GitLogEntry previous = createBaseDestinationChange(destination);

    String label = new GithubPRIntegrateLabel(repo, options.general,
        "example/test_repo", 20, "some_user:branch", secondChange.getSha1()).toString();

    assertThat(label).isEqualTo("https://github.com/example/test_repo/pull/20"
        + " from some_user:branch " + secondChange.getSha1());

    migrateOriginChange(destination, "Test change\n"
        + "\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "="
        + label
        + "\n", "some content");

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "master");
    assertThat(showResult).contains("some content");

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "some content")
        .containsFile("ignore_me", "")
        .containsFile("ignore_me2", "")
        .containsNoMoreFiles();

    GitLogEntry merge = getLastMigratedChange("master");
    assertThat(merge.getBody()).isEqualTo("Merge pull request #20 from some_user:branch\n"
        + "\n"
        + "DummyOrigin-RevId: test\n");

    assertThat(Lists.transform(merge.getParents(), GitRevision::getSha1))
        .isEqualTo(Lists.newArrayList(previous.getCommit().getSha1(), secondChange.getSha1()));

    assertThat(console.getMessages().stream()
        .filter(e -> e.getType() == MessageType.WARNING)
        .collect(Collectors.toList())).isEmpty();

    label = new GithubPRIntegrateLabel(repo, options.general,
        "example/test_repo", 20, "some_user:branch", firstChange.getSha1()).toString();
    assertThat(label).isEqualTo("https://github.com/example/test_repo/pull/20"
        + " from some_user:branch " + firstChange.getSha1());

    repo().withWorkTree(workTree).simpleCommand("reset", "--hard", "HEAD~1");
    migrateOriginChange(destination, "Test change\n"
        + "\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "="
        + label
        + "\n", "some content");

    assertThat(console.getMessages().stream()
        .filter(e -> e.getType() == MessageType.WARNING)
        .findAny().get().getText())
        .contains("has more changes after " + firstChange.getSha1());
  }

  @Test
  public void testGerritSemiFakeMerge() throws ValidationException, IOException, RepoException {
    Path workTree = Files.createTempDirectory("test");
    GitRepository repo = fakeHttpsRepo("example.com/gerrit").withWorkTree(workTree);

    String label = new GerritIntegrateLabel(repo, options.general, "https://example.com/gerrit",
        1020, 1, CHANGE_ID).toString();

    assertThat(label).isEqualTo("gerrit https://example.com/gerrit 1020 Patch Set 1 " + CHANGE_ID);

    GitRevision firstChange = singleChange(workTree, repo, "ignore_me", "Feature1 change");
    GitRevision secondChange = singleChange(workTree, repo, "ignore_me2", "Feature2 change");

    GitTestUtil.createFakeGerritNodeDbMeta(repo, 1020, CHANGE_ID);
    repo.simpleCommand("update-ref", "refs/changes/20/1020/1", firstChange.getSha1());
    repo.simpleCommand("update-ref", "refs/changes/20/1020/2", secondChange.getSha1());

    GitDestination destination = destinationWithDefaultIntegrates();
    GitLogEntry previous = createBaseDestinationChange(destination);

    migrateOriginChange(destination, "Test change\n"
        + "\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "="
        + label
        + "\n", "some content");

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "master");
    assertThat(showResult).contains("some content");

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "some content")
        .containsFile("ignore_me", "")
        .containsNoMoreFiles();

    GitLogEntry merge = getLastMigratedChange("master");
    assertThat(merge.getBody()).isEqualTo("Merge Gerrit change 1020 Patch Set 1\n"
        + "\n"
        + "DummyOrigin-RevId: test\n"
        + "Change-Id: " + CHANGE_ID + "\n");

    assertThat(Lists.transform(merge.getParents(), GitRevision::getSha1))
        .isEqualTo(Lists.newArrayList(previous.getCommit().getSha1(), firstChange.getSha1()));

    assertThat(console.getMessages().stream()
        .filter(e -> e.getType() == MessageType.WARNING)
        .findAny().get().getText())
        .contains("Change 1020 has more patch sets after Patch Set 1."
            + " Latest is Patch Set 2. Not all changes might be migrated");
  }

  @Test
  public void testGerritFakeMergeNoChangeId()
      throws ValidationException, IOException, RepoException {
    Path workTree = Files.createTempDirectory("test");
    GitRepository repo = fakeHttpsRepo("example.com/gerrit").withWorkTree(workTree);

    String label = new GerritIntegrateLabel(repo, options.general, "https://example.com/gerrit",
        1020, 1, /*changeId=*/null).toString();

    assertThat(label).isEqualTo("gerrit https://example.com/gerrit 1020 Patch Set 1");

    GitRevision firstChange = singleChange(workTree, repo, "ignore_me", "Feature1 change");

    repo.simpleCommand("update-ref", "refs/changes/20/1020/1", firstChange.getSha1());
    GitTestUtil.createFakeGerritNodeDbMeta(repo, 1020, CHANGE_ID);

    GitDestination destination = destination(FAKE_MERGE);
    GitLogEntry previous = createBaseDestinationChange(destination);

    migrateOriginChange(destination, "Test change\n"
        + "\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "="
        + label
        + "\n", "some content");

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "master");
    assertThat(showResult).contains("some content");

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();

    GitLogEntry merge = getLastMigratedChange("master");
    assertThat(merge.getBody()).isEqualTo("Merge Gerrit change 1020 Patch Set 1\n"
        + "\n"
        + "DummyOrigin-RevId: test\n");

    assertThat(Lists.transform(merge.getParents(), GitRevision::getSha1))
        .isEqualTo(Lists.newArrayList(previous.getCommit().getSha1(), firstChange.getSha1()));

    assertThat(console.getMessages().stream()
        .filter(e -> e.getType() == MessageType.WARNING)
        .findAny())
        .isEqualTo(Optional.empty());
  }

  private GitLogEntry createBaseDestinationChange(GitDestination destination)
      throws IOException, RepoException, ValidationException {
    migrateOriginChange(destination, "Base change\n", "not important");
    GitLogEntry previous = getLastMigratedChange("master");

    console.clearMessages();
    return previous;
  }

  private GitDestination destinationWithDefaultIntegrates() throws ValidationException {
    return destination("url = '" + url + "'");
  }

  private GitRevision singleChange(Path workTree, GitRepository repo, String file, String msg)
      throws IOException, RepoException, CannotResolveRevisionException {
    Files.write(workTree.resolve(file), new byte[0]);
    repo.add().all().run();
    repo.simpleCommand("commit", "-m", msg);
    return repo.resolveReference("HEAD");
  }

  private GitLogEntry getLastMigratedChange(String ref) throws RepoException {
    return Iterables.getOnlyElement(repo().log(ref)
        .withLimit(1)
        .includeFiles(true).includeMergeDiff(true)
        .run());
  }

  @Test
  public void testBadLabel() throws ValidationException, IOException, RepoException {
    try {
      runBadLabel(/*ignoreErrors=*/false);
      fail();
    } catch (ValidationException e) {
      assertThat(e.getMessage()
      ).contains("Error resolving file:///non_existent_repository");
    }
  }

  @Test
  public void testBadLabel_ignoreErrors() throws ValidationException, IOException, RepoException {
    runBadLabel(/*ignoreErrors=*/true);

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
  }

  private void runBadLabel(boolean ignoreErrors)
      throws ValidationException, IOException, RepoException {
    GitDestination destination = destination(
        "url = '" + url + "'",
        "integrates = [git.integrate( "
            + "        ignore_errors = " + (ignoreErrors ? "True" : "False")
            + "    ),]");
    migrateOriginChange(destination, "Test change\n"
        + "\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "=file:///non_existent_repository\n", "some content");
  }

  private GitDestination destination(String... args) throws ValidationException {
    return skylark.eval("r", "r = git.destination(\n"
        + "    " + Joiner.on(",\n    ").join(args)
        + "\n)");
  }

  private void migrateOriginChange(GitDestination destination, String summary, String content)
      throws IOException, RepoException, ValidationException {
    Writer<GitRevision> writer = destination.newWriter(destinationFiles,
        /*dryRun=*/false, /*groupId=*/null, /*oldWriter=*/null);

    Files.write(workdir.resolve("test.txt"), content.getBytes());
    TransformResult result = TransformResults.of(workdir, new DummyRevision("test"))
        .withSummary(summary);

    writer.write(result, console);
  }

  private GitRepository repo() {
    return repoForPath(repoGitDir);
  }

  private GitRepository repoForPath(Path path) {
    return GitRepository.newBareRepo(path, getGitEnv(),  /*verbose=*/true);
  }

  private GitRepository fakeHttpsRepo(String name) throws RepoException {
    GitRepository repo = GitRepository.newBareRepo(localHub.resolve(name),
        getGitEnv(),
        options.general.isVerbose());
    repo.init();
    return repo;
  }

  private String git(String... argv) throws RepoException {
    return repo()
        .git(repoGitDir, argv)
        .getStdout();
  }
}
