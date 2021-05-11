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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.copybara.git.GitIntegrateChanges.Strategy.FAKE_MERGE;
import static com.google.copybara.git.GitIntegrateChanges.Strategy.FAKE_MERGE_AND_INCLUDE_FILES;
import static com.google.copybara.git.GitIntegrateChanges.Strategy.INCLUDE_FILES;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.copybara.Destination.DestinationStatus;
import com.google.copybara.Destination.Writer;
import com.google.copybara.TransformResult;
import com.google.copybara.WriterContext;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RepoException;
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
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
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

  private Path workdir;
  private GitTestUtil gitUtil;
  private String primaryBranch;

  @Before
  public void setup() throws Exception {
    repoGitDir = Files.createTempDirectory("GitDestinationTest-repoGitDir");
    workdir = Files.createTempDirectory("workdir");

    git("init", "--bare", repoGitDir.toString());

    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console)
        .setOutputRootToTmpDir();

    gitUtil = new GitTestUtil(options);
    gitUtil.mockRemoteGitRepos();

    options.gitDestination = new GitDestinationOptions(options.general, options.git);
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";

    destinationFiles = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("ignore*"));
    options.setForce(true);

    url = "file://" + repoGitDir;
    skylark = new SkylarkTestExecutor(options);
    primaryBranch = repo().getPrimaryBranch();
  }

  @Test
  public void testNoIntegration() throws ValidationException, IOException, RepoException {
    migrateOriginChange(
        destination(
            "url = '" + url + "'",
            String.format("fetch = '%s'", primaryBranch),
            String.format("push = '%s'", primaryBranch),
            "integrates = []"),
        "Test change\n\n"
            + GitModule.DEFAULT_INTEGRATE_LABEL + "=http://should_not_be_used\n", "some content");

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", primaryBranch);
    assertThat(showResult).contains("some content");

    GitTesting.assertThatCheckout(repo(), primaryBranch)
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
  }

  @Test
  public void testDefaultIntegration() throws ValidationException, IOException, RepoException {
    Path repoPath = Files.createTempDirectory("test");
    GitRepository repo = GitRepository.newRepo(/*verbose*/ true, repoPath, getGitEnv()).init();
    GitRevision feature1 = singleChange(repoPath, repo, "ignore_me", "Feature1 change");
    repo.simpleCommand("branch", "feature1");
    GitRevision feature2 = singleChange(repoPath, repo, "ignore_me2", "Feature2 change");
    repo.simpleCommand("branch", "feature2");

    GitDestination destination = destinationWithDefaultIntegrates();
    migrateOriginChange(destination, "Base change\n", "not important");
    GitLogEntry previous = getLastMigratedChange(primaryBranch);

    migrateOriginChange(destination, "Test change\n"
        + "\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "=file://" + repo.getWorkTree().toString()
        + " feature1\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "=file://" + repo.getWorkTree().toString()
        + " feature2\n", "some content");

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", primaryBranch + "^1");
    assertThat(showResult).contains("some content");

    GitTesting.assertThatCheckout(repo(), primaryBranch)
        .containsFile("test.txt", "some content")
        .containsFile("ignore_me", "")
        .containsFile("ignore_me2", "")
        .containsNoMoreFiles();

    GitLogEntry feature1Merge = getLastMigratedChange(primaryBranch + "^1");

    assertThat(feature1Merge.getFiles()).containsExactly("test.txt", "ignore_me");

    assertThat(feature1Merge.getBody()).isEqualTo("Merge of " + feature1.getSha1() + "\n"
        + "\n"
        + DummyOrigin.LABEL_NAME + ": test\n");

    assertThat(Lists.transform(feature1Merge.getParents(), GitRevision::getSha1))
        .isEqualTo(Lists.newArrayList(previous.getCommit().getSha1(), feature1.getSha1()));

    GitLogEntry feature2Merge = getLastMigratedChange(primaryBranch);
    assertThat(feature2Merge.getBody()).isEqualTo("Merge of " + feature2.getSha1() + "\n"
        + "\n"
        + DummyOrigin.LABEL_NAME + ": test\n");

    assertThat(Lists.transform(feature2Merge.getParents(), GitRevision::getSha1))
        .isEqualTo(Lists.newArrayList(feature1Merge.getCommit().getSha1(), feature2.getSha1()));
  }

  /**
   * Checks that we can correctly read destination status (last fake merge commit created) after
   * an integration.
   *
   * <p>Don't change the file paths or contents of this test. It needs to be like this to generate
   * an empty diff with the feature branch (pure fake-merge).
   */
  @Test
  public void testDestinationStatus() throws ValidationException, IOException, RepoException {
    destinationFiles = Glob.createGlob(ImmutableList.of("foo/**"));
    Path gitDir = Files.createTempDirectory("gitdir");
    Path repoPath = Files.createTempDirectory("workdir");
    GitRepository repo = GitRepository.newBareRepo(gitDir, getGitEnv(), /*verbose=*/ true,
        DEFAULT_TIMEOUT, /*noVerify=*/ false)
        .init()
        .withWorkTree(repoPath);

    singleChange(repoPath, repo, "base.txt", "first change");
    repo.simpleCommand("branch", "feature1");
    repo.forceCheckout("feature1");
    GitRevision feature = singleChange(repoPath, repo, "foo/a", "Feature 1 change");
    repo.forceCheckout(primaryBranch);

    // Just so that the migration doesn't fail since the git repo is a non-bare repo.
    repo.forceCheckout("feature1");

    GitDestination destination = destination("url = 'file://" + repo.getGitDir() + "'", String.format("push='%s'", primaryBranch));

    migrateOriginChange(destination, "Test change\n"
        + "\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "=file://" + repo.getGitDir().toString()
        + " feature1\n", "foo/a", "", "the_rev");

    GitLogEntry featureMerge = getLastMigratedChange(primaryBranch, repo);

    assertThat(featureMerge.getBody()).isEqualTo("Merge of " + feature.getSha1() + "\n"
        + "\n"
        + DummyOrigin.LABEL_NAME + ": the_rev\n");
    WriterContext writerContext =
        new WriterContext("piper_to_github", "TEST", false, new DummyRevision("feature"),
            Glob.ALL_FILES.roots());
    DestinationStatus destinationStatus = destination.newWriter(writerContext)
        .getDestinationStatus(destinationFiles, DummyOrigin.LABEL_NAME);
    assertWithMessage(gitDir.toString()).that(destinationStatus.getBaseline()).isEqualTo("the_rev");
  }

  @Test
  public void testFakeMerge() throws ValidationException, IOException, RepoException {
    Path repoPath = Files.createTempDirectory("test");
    GitRepository repo = GitRepository.newRepo(/*verbose*/ true, repoPath, getGitEnv()).init();
    GitRevision feature1 = singleChange(repoPath, repo, "ignore_me", "Feature1 change");
    repo.simpleCommand("branch", "feature1");
    GitRevision feature2 = singleChange(repoPath, repo, "ignore_me2", "Feature2 change");
    repo.simpleCommand("branch", "feature2");

    GitDestination destination = destination(FAKE_MERGE);
    migrateOriginChange(destination, "Base change\n", "not important");
    GitLogEntry previous = getLastMigratedChange(primaryBranch);

    migrateOriginChange(destination, "Test change\n"
        + "\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "=file://" + repo.getWorkTree().toString()
        + " feature1\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "=file://" + repo.getWorkTree().toString()
        + " feature2\n", "some content");

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", primaryBranch + "^1");
    assertThat(showResult).contains("some content");

    GitTesting.assertThatCheckout(repo(), primaryBranch)
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();

    GitLogEntry feature1Merge = getLastMigratedChange(primaryBranch + "^1");
    assertThat(feature1Merge.getBody()).isEqualTo("Merge of " + feature1.getSha1() + "\n"
        + "\n"
        + DummyOrigin.LABEL_NAME + ": test\n");

    assertThat(Lists.transform(feature1Merge.getParents(), GitRevision::getSha1))
        .isEqualTo(Lists.newArrayList(previous.getCommit().getSha1(), feature1.getSha1()));

    GitLogEntry feature2Merge = getLastMigratedChange(primaryBranch);
    assertThat(feature2Merge.getBody()).isEqualTo("Merge of " + feature2.getSha1() + "\n"
        + "\n"
        + DummyOrigin.LABEL_NAME + ": test\n");

    assertThat(feature1Merge.getFiles()).containsExactly("test.txt");

    assertThat(Lists.transform(feature2Merge.getParents(), GitRevision::getSha1))
        .isEqualTo(Lists.newArrayList(feature1Merge.getCommit().getSha1(), feature2.getSha1()));
  }

  private GitDestination destination(Strategy strategy) throws ValidationException {
    return destination(
        "url = '" + url + "'", String.format("push='%s'", primaryBranch),
        "integrates = [git.integrate("
            + "         ignore_errors = False,"
            + "         strategy = '" + strategy + "',"
            + "    ),]"
    );
  }

  @Test
  public void testIncludeFiles() throws ValidationException, IOException, RepoException {
    Path repoPath = Files.createTempDirectory("test");
    GitRepository repo = GitRepository.newRepo(/*verbose*/ true, repoPath, getGitEnv()).init();
    singleChange(repoPath, repo, "ignore_me", "Feature1 change");
    repo.simpleCommand("branch", "feature1");
    singleChange(repoPath, repo, "ignore_me2", "Feature2 change");
    repo.simpleCommand("branch", "feature2");

    GitDestination destination = destination(INCLUDE_FILES);
    migrateOriginChange(destination, "Base change\n", "not important");
    GitLogEntry previous = getLastMigratedChange(primaryBranch);

    migrateOriginChange(destination, "Test change\n"
        + "\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "=file://" + repo.getWorkTree().toString()
        + " feature1\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "=file://" + repo.getWorkTree().toString()
        + " feature2\n", "some content");

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", primaryBranch);
    assertThat(showResult).contains("some content");

    GitTesting.assertThatCheckout(repo(), primaryBranch)
        .containsFile("test.txt", "some content")
        .containsFile("ignore_me", "")
        .containsFile("ignore_me2", "")
        .containsNoMoreFiles();

    GitLogEntry afterChange = getLastMigratedChange(primaryBranch);
    assertThat(afterChange.getBody()).isEqualTo("Test change\n"
        + "\n"
        + DummyOrigin.LABEL_NAME + ": test\n");

    assertThat(Lists.transform(afterChange.getParents(), GitRevision::getSha1))
        .isEqualTo(Lists.newArrayList(previous.getCommit().getSha1()));
  }

  @Test
  public void testIncludeFilesOutdatedBranch() throws Exception {
    Path repoPath = Files.createTempDirectory("test");
    GitRepository repo = repo().withWorkTree(repoPath);
    singleChange(repoPath, repo, "ignore_base.txt", "original", "base change");
    repo.simpleCommand("branch", "feature");
    singleChange(repoPath, repo, "ignore_base.txt", "modified", "more changes");
    repo.forceCheckout("feature");
    singleChange(repoPath, repo, "ignore_but_changed.txt", "feature", "external change");
    singleChange(repoPath, repo, "feature_file.txt", "feature", "internal change");
    GitRevision prHead = repo.resolveReference("HEAD");

    GitDestination destination = destination(FAKE_MERGE_AND_INCLUDE_FILES);
    migrateOriginChange(destination, "Base change\n", "test.txt", "not important", "test");
    GitLogEntry previous = getLastMigratedChange(primaryBranch);

    migrateOriginChange(destination, "Test change\n"
        + "\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "=file://" + repo.getGitDir().toString()
        + " feature\n", "feature_file.txt", "feature modified", "test");

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", primaryBranch);
    assertThat(showResult).contains("Merge of ");

    GitTesting.assertThatCheckout(repo, primaryBranch)
        .containsFile("test.txt", "not important")
        .containsFile("ignore_base.txt", "modified")
        .containsFile("ignore_but_changed.txt", "feature")
        .containsFile("feature_file.txt", "feature modified")
        .containsNoMoreFiles();

    GitLogEntry afterChange = getLastMigratedChange(primaryBranch);
    assertThat(afterChange.getBody()).isEqualTo("Merge of " + prHead.getSha1() + "\n"
        + "\n"
        + DummyOrigin.LABEL_NAME + ": test\n");

    assertThat(Lists.transform(afterChange.getParents(), GitRevision::getSha1))
        .isEqualTo(Lists.newArrayList(previous.getCommit().getSha1(), prHead.getSha1()));
  }

  @Test
  public void testGitHubSemiFakeMerge() throws ValidationException, IOException, RepoException {
    Path workTree = Files.createTempDirectory("test");
    GitRepository repo =
        gitUtil.mockRemoteRepo("github.com/example/test_repo").withWorkTree(workTree);

    GitRevision firstChange = singleChange(workTree, repo, "ignore_me", "Feature1 change");
    GitRevision secondChange = singleChange(workTree, repo, "ignore_me2", "Feature2 change");

    repo.simpleCommand("update-ref", "refs/pull/20/head", secondChange.getSha1());

    GitDestination destination = destinationWithDefaultIntegrates();
    GitLogEntry previous = createBaseDestinationChange(destination);

    GitHubPrIntegrateLabel labelObj = new GitHubPrIntegrateLabel(repo, options.general,
        "example/test_repo", 20, "some_user:1234-foo.bar.baz%3", secondChange.getSha1());

    assertThat(labelObj.getProjectId()).isEqualTo("example/test_repo");
    assertThat(labelObj.getPrNumber()).isEqualTo(20L);
    assertThat(labelObj.getOriginBranch()).isEqualTo("some_user:1234-foo.bar.baz%3");
    assertThat(labelObj.mergeMessage(ImmutableList.of()))
        .isEqualTo("Merge pull request #20 from some_user:1234-foo.bar.baz%3\n");

    String label = labelObj.toString();

    assertThat(label).isEqualTo("https://github.com/example/test_repo/pull/20"
        + " from some_user:1234-foo.bar.baz%3 " + secondChange.getSha1());

    migrateOriginChange(destination, "Test change\n"
        + "\n"
        + GitModule.DEFAULT_INTEGRATE_LABEL + "="
        + label
        + "\n", "some content");

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", primaryBranch);
    assertThat(showResult).contains("some content");

    GitTesting.assertThatCheckout(repo(), primaryBranch)
        .containsFile("test.txt", "some content")
        .containsFile("ignore_me", "")
        .containsFile("ignore_me2", "")
        .containsNoMoreFiles();

    GitLogEntry merge = getLastMigratedChange(primaryBranch);
    assertThat(merge.getBody()).isEqualTo(
        "Merge pull request #20 from some_user:1234-foo.bar.baz%3\n"
        + "\n"
        + "DummyOrigin-RevId: test\n");

    assertThat(Lists.transform(merge.getParents(), GitRevision::getSha1))
        .isEqualTo(Lists.newArrayList(previous.getCommit().getSha1(), secondChange.getSha1()));

    assertThat(console.getMessages().stream()
        .filter(e -> e.getType() == MessageType.WARNING)
        .collect(Collectors.toList())).isEmpty();

    label = new GitHubPrIntegrateLabel(repo, options.general,
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
    GitRepository repo = gitUtil.mockRemoteRepo("example.com/gerrit").withWorkTree(workTree);

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
    String showResult = git("--git-dir", repoGitDir.toString(), "show", primaryBranch);
    assertThat(showResult).contains("some content");

    GitTesting.assertThatCheckout(repo(), primaryBranch)
        .containsFile("test.txt", "some content")
        .containsFile("ignore_me", "")
        .containsNoMoreFiles();

    GitLogEntry merge = getLastMigratedChange(primaryBranch);
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
    GitRepository repo = gitUtil.mockRemoteRepo("example.com/gerrit").withWorkTree(workTree);

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
    String showResult = git("--git-dir", repoGitDir.toString(), "show", primaryBranch);
    assertThat(showResult).contains("some content");

    GitTesting.assertThatCheckout(repo(), primaryBranch)
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();

    GitLogEntry merge = getLastMigratedChange(primaryBranch);
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
    GitLogEntry previous = getLastMigratedChange(primaryBranch);

    console.clearMessages();
    return previous;
  }

  private GitDestination destinationWithDefaultIntegrates() throws ValidationException {
    return destination("url = '" + url + "'",
        String.format("push = '%s'", primaryBranch),
        String.format("fetch = '%s'", primaryBranch));
  }

  private GitRevision singleChange(Path workTree, GitRepository repo, String file, String msg)
      throws IOException, RepoException, CannotResolveRevisionException {
    String fileContent = "";
    return singleChange(workTree, repo, file, fileContent, msg);
  }

  private GitRevision singleChange(Path workTree, GitRepository repo, String file,
      String fileContent, String msg)
      throws IOException, RepoException, CannotResolveRevisionException {
    Path path = workTree.resolve(file);
    Files.createDirectories(path.getParent());
    Files.write(path, fileContent.getBytes(StandardCharsets.UTF_8));
    repo.add().all().run();
    repo.simpleCommand("commit", "-m", msg);
    return repo.resolveReference("HEAD");
  }

  private GitLogEntry getLastMigratedChange(String ref) throws RepoException {
    return getLastMigratedChange(ref, repo());
  }

  private GitLogEntry getLastMigratedChange(String ref, GitRepository repo) throws RepoException {
    return Iterables.getOnlyElement(repo.log(ref)
        .withLimit(1)
        .includeFiles(true).includeMergeDiff(true)
        .run());
  }

  @Test
  public void testBadLabel() throws ValidationException, IOException, RepoException {
    ValidationException e =
        assertThrows(ValidationException.class, () -> runBadLabel(/*ignoreErrors=*/ false));
    assertThat(e.getMessage()).contains("Error resolving file:///non_existent_repository");
  }

  @Test
  public void testBadLabel_ignoreErrors() throws ValidationException, IOException, RepoException {
    runBadLabel(/*ignoreErrors=*/true);

    GitTesting.assertThatCheckout(repo(), primaryBranch)
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
  }

  private void runBadLabel(boolean ignoreErrors)
      throws ValidationException, IOException, RepoException {
    GitDestination destination = destination(
        "url = '" + url + "'",
        String.format("fetch = '%s'", primaryBranch),
        String.format("push = '%s'", primaryBranch),
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
    migrateOriginChange(destination, summary, "test.txt", content, "test");
  }
  private void migrateOriginChange(GitDestination destination, String summary,
      String file, String content,
      String originRef) throws IOException, RepoException, ValidationException {
    WriterContext writerContext =
        new WriterContext("piper_to_github", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());
    Writer<GitRevision> writer = destination.newWriter(writerContext);

    Files.createDirectories(workdir.resolve(file).getParent());
    Files.write(workdir.resolve(file), content.getBytes(UTF_8));
    TransformResult result = TransformResults.of(workdir, new DummyRevision(originRef))
        .withSummary(summary);

    writer.write(result, destinationFiles, console);
  }

  private GitRepository repo() {
    return repoForPath(repoGitDir);
  }

  private GitRepository repoForPath(Path path) {
    return GitRepository.newBareRepo(
        path, getGitEnv(),  /*verbose=*/true, DEFAULT_TIMEOUT, /*noVerify=*/ false);
  }

  private String git(String... argv) throws RepoException {
    return repo()
        .git(repoGitDir, argv)
        .getStdout();
  }
}
