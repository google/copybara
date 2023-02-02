/*
 * Copyright (C) 2017 Google Inc.
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
import static com.google.copybara.git.GitModule.DEFAULT_INTEGRATE_LABEL;
import static com.google.copybara.testing.TransformWorks.toChange;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static com.google.copybara.testing.git.GitTestUtil.mockResponse;
import static com.google.copybara.testing.git.GitTestUtil.mockResponseAndValidateRequest;
import static com.google.copybara.testing.git.GitTestUtil.writeFile;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.copybara.Destination.DestinationStatus;
import com.google.copybara.Destination.Writer;
import com.google.copybara.WriterContext;
import com.google.copybara.authoring.Author;
import com.google.copybara.effect.DestinationEffect;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RedundantChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitIntegrateChanges.Strategy;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.revision.Changes;
import com.google.copybara.revision.Revision;
import com.google.copybara.testing.DummyChecker;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.testing.git.GitTestUtil.CompleteRefValidator;
import com.google.copybara.testing.git.GitTestUtil.MockRequestAssertion;
import com.google.copybara.util.Glob;
import com.google.copybara.util.Identity;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.starlark.java.eval.StarlarkList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitHubPrDestinationTest {

  private Path repoGitDir;
  private OptionsBuilder options;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;
  private GitTestUtil gitUtil;

  private Path workdir;
  private String primaryBranchMigration;
  @Nullable private String emptyDiffMergeStatus;

  @Before
  public void setup() throws Exception {
    repoGitDir = Files.createTempDirectory("GitHubPrDestinationTest-repoGitDir");
    workdir = Files.createTempDirectory("workdir");

    git("init", "--bare", repoGitDir.toString());
    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console)
        // Temporarily set this to true as the head git default branch is 'main'
        // while external git default branch is still 'master'
        .setForce(true)
        .setOutputRootToTmpDir();

    gitUtil = new GitTestUtil(options);
    gitUtil.mockRemoteGitRepos(new CompleteRefValidator());

    Path credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, "https://user:SECRET@github.com".getBytes(UTF_8));
    options.git.credentialHelperStorePath = credentialsFile.toString();

    options.gitDestination = new GitDestinationOptions(options.general, options.git);
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";
    skylark = new SkylarkTestExecutor(options);
    primaryBranchMigration = "False";
  }
  @Test
  public void testWrite_noContextReference() throws ValidationException {
    WriterContext writerContext =
        new WriterContext("piper_to_github_pr", "TEST", false, new DummyRevision("feature", null),
            Glob.ALL_FILES.roots());
    GitHubPrDestination d = skylark.eval(
        "r", "r = git.github_pr_destination(" + "    url = 'https://github.com/foo'" + ")");
    ValidationException thrown =
        assertThrows(ValidationException.class, () -> d.newWriter(writerContext));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "git.github_pr_destination is incompatible with the current origin."
                + " Origin has to be able to provide the contextReference or use"
                + " '--github-destination-pr-branch' flag");
  }

  @Test
  public void testCustomTitleAndBody()
      throws ValidationException, IOException, RepoException {
    options.githubDestination.destinationPrBranch = "feature";

    mockNoPullRequestsGet("feature");

    gitUtil.mockApi(
        "POST",
        "https://api.github.com/repos/foo/pulls",
        mockResponseAndValidateRequest(
            "{\n"
                + "  \"id\": 1,\n"
                + "  \"number\": 12345,\n"
                + "  \"state\": \"open\",\n"
                + "  \"title\": \"custom title\",\n"
                + "  \"body\": \"custom body\""
                + "}",
            MockRequestAssertion.equals("{\"base\":\"main\","
                        + "\"body\":\"custom body\","
                        + "\"draft\":false,"
                        + "\"head\":\"feature\","
                        + "\"title\":\"custom title\"}")));

    GitHubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = 'https://github.com/foo', \n"
        + "    title = 'custom title',\n"
        + "    body = 'custom body',\n"
        + "    destination_ref = 'main'"
        + ")");
    WriterContext writerContext =
        new WriterContext("piper_to_github", "TEST", false, new DummyRevision("feature", "feature"),
            Glob.ALL_FILES.roots());
    Writer<GitRevision> writer = d.newWriter(writerContext);
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/foo");
    addFiles(
        remote,
        null,
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "").buildOrThrow());

    writeFile(this.workdir, "test.txt", "some content");
    writer.write(
        TransformResults.of(this.workdir, new DummyRevision("one")), Glob.ALL_FILES, console);

    verify(gitUtil.httpTransport(), times(1))
        .buildRequest("GET", getPullRequestsUrl("feature"));
    verify(gitUtil.httpTransport(), times(1))
        .buildRequest("POST", "https://api.github.com/repos/foo/pulls");
  }

  @Test
  public void testCustomTitleAndBody_withUpdate()
      throws ValidationException, IOException, RepoException {
    options.githubDestination.destinationPrBranch = "feature";

    mockNoPullRequestsGet("feature");

    gitUtil.mockApi(
        "POST",
        "https://api.github.com/repos/foo/pulls",
        mockResponseAndValidateRequest(
            "{\n"
                + "  \"id\": 1,\n"
                + "  \"number\": 12345,\n"
                + "  \"state\": \"open\",\n"
                + "  \"title\": \"custom title\",\n"
                + "  \"body\": \"custom body\""
                + "}",
            MockRequestAssertion.equals("{\"base\":\"main\","
                + "\"body\":\"Body first a\","
                + "\"draft\":false,"
                + "\"head\":\"feature\","
                + "\"title\":\"Title first a\"}")));

    GitHubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = 'https://github.com/foo', \n"
        + "    title = 'Title ${aaa}',\n"
        + "    body = 'Body ${aaa}',\n"
        + "    destination_ref = 'main',\n"
        + "    update_description = True,\n"
        + ")");
    WriterContext writerContext =
        new WriterContext("piper_to_github", "TEST", false, new DummyRevision("feature", "feature"),
            Glob.ALL_FILES.roots());
    Writer<GitRevision> writer = d.newWriter(writerContext);
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/foo");
    addFiles(
        remote,
        null,
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "").buildOrThrow());

    writeFile(this.workdir, "test.txt", "some content");
    writer.write(
        TransformResults.of(this.workdir, new DummyRevision("one"))
            .withLabelFinder(Functions.forMap(
                ImmutableMap.of("aaa", ImmutableList.of("first a", "second a"))
            )), Glob.ALL_FILES, console);

    verify(gitUtil.httpTransport(), times(1))
        .buildRequest("GET", getPullRequestsUrl("feature"));
    verify(gitUtil.httpTransport(), times(1))
        .buildRequest("POST", "https://api.github.com/repos/foo/pulls");

    reset(gitUtil.httpTransport());

    gitUtil.mockApi(
        "GET",
        getPullRequestsUrl("feature"),
        mockResponse(
            ""
                + "[{\n"
                + "  \"id\": 1,\n"
                + "  \"number\": 12345,\n"
                + "  \"state\": \"open\",\n"
                + "  \"title\": \"test summary\",\n"
                + "  \"body\": \"test summary\","
                + "  \"head\": { \"ref\": \"feature\"},"
                + "  \"base\": { \"ref\": \"feature\"}"
                + "}]"));

    gitUtil.mockApi(
        "POST",
        "https://api.github.com/repos/foo/pulls/12345",
        mockResponseAndValidateRequest(
            "{\n"
                + "  \"id\": 1,\n"
                + "  \"number\": 12345,\n"
                + "  \"state\": \"open\",\n"
                + "  \"title\": \"custom title\",\n"
                + "  \"body\": \"custom body\""
                + "}",
                MockRequestAssertion.equals("{\"body\":\"Body first b\","
                        + "\"title\":\"Title first b\"}")));

    writeFile(this.workdir, "test.txt", "some other content");
    writer = d.newWriter(writerContext);
    writer.write(
        TransformResults.of(this.workdir, new DummyRevision("two"))
            .withLabelFinder(Functions.forMap(
                ImmutableMap.of("aaa", ImmutableList.of("first b", "second b"))
            )), Glob.ALL_FILES, console);

    verify(gitUtil.httpTransport(), times(1))
        .buildRequest("GET", getPullRequestsUrl("feature"));
    verify(gitUtil.httpTransport(), times(1))
        .buildRequest("POST", "https://api.github.com/repos/foo/pulls/12345");

  }

  @Test
  public void testWrite_primaryBranchMode()
      throws ValidationException, IOException, RepoException {
    primaryBranchMigration = "True";
    options.githubDestination.destinationPrBranch = "feature";
    checkWrite(new DummyRevision("dummyReference"));
  }

  @Test
  public void testWrite_destinationPrBranchFlag()
      throws ValidationException, IOException, RepoException {
    options.githubDestination.destinationPrBranch = "feature";
    checkWrite(new DummyRevision("dummyReference"));
  }

  @Test
  public void testTrimMessageForPrTitle()
      throws ValidationException, IOException, RepoException {
    options.githubDestination.destinationPrBranch = "feature";

    mockNoPullRequestsGet("feature");

    gitUtil.mockApi(
        "POST",
        "https://api.github.com/repos/foo/pulls",
        mockResponseAndValidateRequest(
            "{\n"
                + "  \"id\": 1,\n"
                + "  \"number\": 12345,\n"
                + "  \"state\": \"open\",\n"
                + "  \"title\": \"test summary\",\n"
                + "  \"body\": \"test summary\""
                + "}",
            MockRequestAssertion.equals(
                "{\"base\":\"main\",\"body\":\"Internal change.\\n"
                    + "\",\"draft\":false,\"head\":\"feature\",\"title\":\"Internal change.\"}")));

    GitHubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = 'https://github.com/foo',"
        + "    destination_ref = 'main'"
        + ")");

    WriterContext writerContext =
        new WriterContext("piper_to_github", "test", false, new DummyRevision("feature", "feature"),
            Glob.ALL_FILES.roots());

    Writer<GitRevision> writer = d.newWriter(writerContext);

    GitRepository remote = gitUtil.mockRemoteRepo("github.com/foo");
    addFiles(
        remote,
        null,
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "").buildOrThrow());

    writeFile(this.workdir, "test.txt", "some content");
    writer.write(TransformResults.of(this.workdir,
        new DummyRevision("one")).withSummary("\n\n\n\n\nInternal change."),
        Glob.ALL_FILES,
        console);

    verify(gitUtil.httpTransport(), times(1))
        .buildRequest("GET", getPullRequestsUrl("feature"));
    verify(gitUtil.httpTransport(), times(1))
        .buildRequest("POST", "https://api.github.com/repos/foo/pulls");
  }

  @Test
  public void testPrExistsButIsClosed()
      throws ValidationException, IOException, RepoException {
    options.githubDestination.destinationPrBranch = "feature";

    gitUtil.mockApi("GET", getPullRequestsUrl("feature"), mockResponse("[{"
        + "  \"id\": 1,\n"
        + "  \"number\": 12345,\n"
        + "  \"state\": \"closed\",\n"
        + "  \"title\": \"test summary\",\n"
        + "  \"body\": \"test summary\","
        + "  \"head\": {\"ref\": \"feature\"},"
        + "  \"base\": {\"ref\": \"base\"}"
        + "}]"));

    gitUtil.mockApi(
        "POST",
        "https://api.github.com/repos/foo/pulls/12345",
        mockResponseAndValidateRequest(
            "{\n"
                + "  \"id\": 1,\n"
                + "  \"number\": 12345,\n"
                + "  \"state\": \"open\",\n"
                + "  \"title\": \"test summary\",\n"
                + "  \"body\": \"test summary\""
                + "}",
            MockRequestAssertion.equals(
                "{\"state\":\"open\"}")));

    GitHubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = 'https://github.com/foo',"
        + "    destination_ref = 'main'"
        + ")");

    WriterContext writerContext =
        new WriterContext("piper_to_github", "test", false, new DummyRevision("feature", "feature"),
            Glob.ALL_FILES.roots());

    Writer<GitRevision> writer = d.newWriter(writerContext);

    GitRepository remote = gitUtil.mockRemoteRepo("github.com/foo");
    addFiles(
        remote,
        null,
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "").buildOrThrow());

    writeFile(this.workdir, "test.txt", "some content");
    writer.write(TransformResults.of(this.workdir,
        new DummyRevision("one")).withSummary("\n\n\n\n\nInternal change."),
        Glob.ALL_FILES,
        console);

    verify(gitUtil.httpTransport(), times(1))
        .buildRequest("GET", getPullRequestsUrl("feature"));
    verify(gitUtil.httpTransport(), times(1))
        .buildRequest("POST", "https://api.github.com/repos/foo/pulls/12345");
  }

  @Test
  public void testHttpUrl() throws Exception {
    GitHubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = 'http://github.com/foo', \n"
        + "    title = 'custom title',\n"
        + "    body = 'custom body',\n"
        + ")");
    assertThat(d.describe(Glob.ALL_FILES).get("url")).contains("https://github.com/foo");
  }

  @Test
  public void testCheckerInDescribe() throws Exception {
    options.testingOptions.checker = new DummyChecker(ImmutableSet.of("BAD"));

    GitHubPrDestination d =
        skylark.eval(
            "r",
            "r = git.github_pr_destination("
                + "    url = 'http://github.com/foo',"
                + "    checker = testing.dummy_checker()"
                + ")");
    assertThat(d.describe(Glob.ALL_FILES).get("checker")).contains(DummyChecker.class.getName());
  }

  private void checkWrite(Revision revision)
      throws ValidationException, RepoException, IOException {
    mockNoPullRequestsGet("feature");

    gitUtil.mockApi(
        "POST",
        "https://api.github.com/repos/foo/pulls",
        mockResponseAndValidateRequest(
            "{\n"
                + "  \"id\": 1,\n"
                + "  \"number\": 12345,\n"
                + "  \"state\": \"open\",\n"
                + "  \"title\": \"test summary\",\n"
                + "  \"body\": \"test summary\"\n"
                + "}",
            MockRequestAssertion.equals(
                    "{\"base\":\"main\",\"body\":\"test summary\\n\",\"draft\":false,\"head\":\""
                        + "feature"
                        + "\",\"title\":\"test summary\"}")));

    GitHubPrDestination d =
        skylark.eval(
            "r", "r = git.github_pr_destination(" + "    url = 'https://github.com/foo',"
                + "    destination_ref = 'main'" + ")");

    Writer<GitRevision> writer = d.newWriter(new WriterContext("piper_to_github_pr", "TEST", false,
        revision, Glob.ALL_FILES.roots()));
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/foo");
    addFiles(
        remote,
        null,
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "").buildOrThrow());

    writeFile(this.workdir, "test.txt", "some content");
    writer.write(
        TransformResults.of(this.workdir, new DummyRevision("one")), Glob.ALL_FILES, console);
    writeFile(this.workdir, "test.txt", "other content");
    writer.write(
        TransformResults.of(this.workdir, new DummyRevision("two")), Glob.ALL_FILES, console);

    writeFile(this.workdir, "test.txt", "and content");
    writer.write(TransformResults.of(this.workdir, new DummyRevision("three")),
        Glob.ALL_FILES, console);

    console.assertThat().timesInLog(1, MessageType.INFO,
        "Pull Request https://github.com/foo/pull/12345 created using branch 'feature'.");

    assertThat(remote.refExists("feature")).isTrue();
    assertThat(Iterables.transform(remote.log("feature").run(), GitLogEntry::getBody))
        .containsExactly("first change\n",
            "test summary\n"
                + "\n"
                + "DummyOrigin-RevId: one\n",
            "test summary\n"
                + "\n"
                + "DummyOrigin-RevId: two\n",
            "test summary\n"
                + "\n"
                + "DummyOrigin-RevId: three\n");

    // If we don't keep writer state (same as a new migration). We do a rebase of
    // all the changes.
    writer = d.newWriter(
        new WriterContext("piper_to_github_pr", "TEST", false, revision, Glob.ALL_FILES.roots()));

    writeFile(this.workdir, "test.txt", "and content");

    gitUtil.mockApi(
        "GET",
        getPullRequestsUrl("feature"),
        mockResponse(
            ""
                + "[{\n"
                + "  \"id\": 1,\n"
                + "  \"number\": 12345,\n"
                + "  \"state\": \"open\",\n"
                + "  \"title\": \"test summary\",\n"
                + "  \"body\": \"test summary\","
                + "  \"head\": { \"ref\": \"feature\"},"
                + "  \"base\": { \"ref\": \"feature\"}"
                + "}]"));

    writer.write(
        TransformResults.of(this.workdir, new DummyRevision("four")), Glob.ALL_FILES, console);

    assertThat(Iterables.transform(remote.log("feature").run(), GitLogEntry::getBody))
        .containsExactly("first change\n", "test summary\n" + "\n" + "DummyOrigin-RevId: four\n");
  }

  @Test
  public void testFindProject() throws ValidationException {
    checkFindProject("https://github.com/foo", "foo");
    checkFindProject("https://github.com/foo/bar", "foo/bar");
    checkFindProject("https://github.com/foo.git", "foo");
    checkFindProject("https://github.com/foo/", "foo");
    checkFindProject("git+https://github.com/foo", "foo");
    checkFindProject("git@github.com/foo", "foo");
    checkFindProject("git@github.com:org/internal_repo_name.git", "org/internal_repo_name");
    ValidationException e =
        assertThrows(
            ValidationException.class, () -> checkFindProject("https://github.com", "foo"));
    console
        .assertThat()
        .onceInLog(MessageType.ERROR, ".*'https://github.com' is not a valid GitHub url.*");
  }

  @Test
  public void testIntegratesCanBeRemoved() throws Exception {
    GitHubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = '" + "https://github.com/foo" + "',"
        + "    destination_ref = 'other',"
        + ")");

    assertThat(ImmutableList.copyOf(d.getIntegrates()))
        .isEqualTo(
            StarlarkList.immutableCopyOf(
                ImmutableList.of(
                    new GitIntegrateChanges(
                        DEFAULT_INTEGRATE_LABEL,
                        Strategy.FAKE_MERGE_AND_INCLUDE_FILES,
                        /*ignoreErrors=*/ true))));

    d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = '" + "https://github.com/foo" + "',"
        + "    destination_ref = 'other',"
        + "    integrates = [],"
        + ")");

    assertThat(d.getIntegrates()).isEmpty();
  }

  private void checkFindProject(String url, String project) throws ValidationException {
    GitHubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = '" + url + "',"
        + "    destination_ref = 'other',"
        + ")");

    assertThat(d.getProjectName()).isEqualTo(project);
  }

  @Test
  public void testWriteNomain() throws ValidationException, IOException, RepoException {
    GitHubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = 'https://github.com/foo',"
        + "    destination_ref = 'other',"
        + ")");
    DummyRevision dummyRevision = new DummyRevision("dummyReference", "feature");
    WriterContext writerContext =
        new WriterContext("piper_to_github_pr", "TEST", false, dummyRevision,
            Glob.ALL_FILES.roots());
    String branchName =
        Identity.computeIdentity(
            "OriginGroupIdentity",
            dummyRevision.contextReference(),
            writerContext.getWorkflowName(),
            "copy.bara.sky",
            writerContext.getWorkflowIdentityUser());

    mockNoPullRequestsGet(branchName);

    gitUtil.mockApi(
        "POST",
        "https://api.github.com/repos/foo/pulls",
        mockResponseAndValidateRequest(
            "{\n"
                + "  \"id\": 1,\n"
                + "  \"number\": 12345,\n"
                + "  \"state\": \"open\",\n"
                + "  \"title\": \"test summary\",\n"
                + "  \"body\": \"test summary\""
                + "}",
            MockRequestAssertion.equals(
                    "{\"base\":\"other\",\"body\":\"test summary\\n\",\"draft\":false,\"head\":\""
                        + branchName
                        + "\",\"title\":\"test summary\"}")));

    Writer<GitRevision> writer = d.newWriter(writerContext);
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/foo");
    addFiles(
        remote,
        "main",
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "").buildOrThrow());

    addFiles(
        remote,
        "other",
        "second change",
        ImmutableMap.<String, String>builder().put("foo.txt", "test").buildOrThrow());

    writeFile(this.workdir, "test.txt", "some content");
    writer.write(
        TransformResults.of(this.workdir, new DummyRevision("one")), Glob.ALL_FILES, console);

    assertThat(remote.refExists(branchName)).isTrue();
    assertThat(Iterables.transform(remote.log(branchName).run(), GitLogEntry::getBody))
        .containsExactly("first change\n", "second change\n",
        "test summary\n"
            + "\n"
            + "DummyOrigin-RevId: one\n");
  }

  @Test
  public void emptyChange() throws Exception {
    Writer<GitRevision> writer = getWriterForTestEmptyDiff();
    runEmptyChange(writer, "clean");
  }

  @Test
  public void emptyChangeBecauseUserConfigureStatus() throws Exception {
    emptyDiffMergeStatus = "DIRTY";
    Writer<GitRevision> writer = getWriterForTestEmptyDiff();
    runEmptyChange(writer, "clean");
  }

  private void runEmptyChange(Writer<GitRevision> writer, String prMergeableState)
      throws RepoException, IOException, CannotResolveRevisionException {
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/foo");
    addFiles(
        remote,
        null,
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "").buildOrThrow());
    String baseline = remote.resolveReference("HEAD").getSha1();
    addFiles(
        remote,
        "test_feature",
        "second change",
        ImmutableMap.<String, String>builder().put("foo.txt", "test").buildOrThrow());
    String changeHead = remote.resolveReference("HEAD").getSha1();
    gitUtil.mockApi("GET", getPullRequestsUrl("test_feature"), mockResponse("[{"
        + "  \"id\": 1,\n"
        + "  \"number\": 12345,\n"
        + "  \"state\": \"closed\",\n"
        + "  \"title\": \"test summary\",\n"
        + "  \"body\": \"test summary\","
        + "  \"head\": {\"sha\": \"" + changeHead + "\"},"
        + "  \"base\": {\"sha\": \"" + baseline + "\"}"
        + "}]"));
    gitUtil.mockApi("GET",
        "https://api.github.com/repos/foo/pulls/12345", mockResponse("{"
            + "  \"id\": 1,\n"
            + "  \"number\": 12345,\n"
            + "  \"state\": \"closed\",\n"
            + "  \"title\": \"test summary\",\n"
            + "  \"mergeable\": true,\n"
            + "  \"mergeable_state\": \"" + prMergeableState + "\",\n"
            + "  \"body\": \"test summary\","
            + "  \"head\": {\"sha\": \"" + changeHead + "\"},"
            + "  \"base\": {\"sha\": \"" + baseline + "\"}"
            + "}"));
    writeFile(this.workdir, "foo.txt", "test");

    RedundantChangeException e =
        assertThrows(
            RedundantChangeException.class, () -> writer.write(
                TransformResults.of(this.workdir, new DummyRevision("one")).withBaseline(baseline)
                    .withChanges(new Changes(
                        ImmutableList.of(
                            toChange(new DummyRevision("feature"),
                                new Author("Foo Bar", "foo@bar.com"))),
                ImmutableList.of()))
            .withLabelFinder(
                Functions.forMap(ImmutableMap.of("aaa", ImmutableList.of("first a", "second a")))),
        Glob.ALL_FILES,
        console));

    assertThat(e)
        .hasMessageThat()
        .contains("Skipping push to the existing pr https://github.com/foo/pull/12345 "
            + "as the change feature is empty.");
  }

  @Test
  public void emptyChangeButMergeableFalse() throws Exception {
    checkEmptyChangeButNonMergeable(false, "");
  }

  @Test
  public void emptyChangeButMergeableStateUnstable() throws Exception {
    emptyDiffMergeStatus = "CLEAN";
    String mergeableField = "  \"mergeable_state\": \"clean\",\n";
    checkEmptyChangeButNonMergeable(true, mergeableField);
  }

  private void checkEmptyChangeButNonMergeable(boolean mergeable, String mergeableStatusField)
      throws Exception {
    Writer<GitRevision> writer = getWriterForTestEmptyDiff();
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/foo");
    addFiles(
        remote,
        null,
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "").buildOrThrow());
    String baseline = remote.resolveReference("HEAD").getSha1();
    addFiles(
        remote,
        "test_feature",
        "second change",
        ImmutableMap.<String, String>builder().put("foo.txt", "test").buildOrThrow());
    String changeHead = remote.resolveReference("HEAD").getSha1();

    gitUtil.mockApi("GET", getPullRequestsUrl("test_feature"), mockResponse("[{"
        + "  \"id\": 1,\n"
        + "  \"number\": 12345,\n"
        + "  \"state\": \"open\",\n"
        + "  \"title\": \"test summary\",\n"
        + "  \"body\": \"test summary\","
        + "  \"head\": {\"sha\": \"" + changeHead + "\", \"ref\": \"test_feature\"},"
        + "  \"base\": {\"sha\": \"" + baseline + "\", \"ref\": \"master\"}"
        + "}]"));
    gitUtil.mockApi("GET",
        "https://api.github.com/repos/foo/pulls/12345", mockResponse("{"
            + "  \"id\": 1,\n"
            + "  \"number\": 12345,\n"
            + "  \"state\": \"closed\",\n"
            + "  \"title\": \"test summary\",\n"
            + "  \"mergeable\": \"" + mergeable + "\",\n"
            + mergeableStatusField
            + "  \"body\": \"test summary\","
            + "  \"head\": {\"sha\": \"" + changeHead + "\"},"
            + "  \"base\": {\"sha\": \"" + baseline + "\"}"
            + "}"));

    writeFile(this.workdir, "foo.txt", "test");
    ImmutableList<DestinationEffect> results = writer.write(
        TransformResults.of(this.workdir, new DummyRevision("one")).withBaseline(baseline)
            .withChanges(new Changes(
                ImmutableList.of(
                    toChange(new DummyRevision("feature"),
                        new Author("Foo Bar", "foo@bar.com"))),
                ImmutableList.of()))
            .withLabelFinder(
                Functions.forMap(ImmutableMap.of("aaa", ImmutableList.of("first a", "second a")))),
        Glob.ALL_FILES,
        console);

    assertThat(results.size()).isEqualTo(2);
  }

  @Test
  public void changeWithAllowEmptyDiff() throws Exception {
    Writer<GitRevision> writer = getWriterForTestEmptyDiff();
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/foo");
    addFiles(
        remote,
        null,
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "").buildOrThrow());
    String baseline = remote.resolveReference("HEAD").getSha1();

    String changeHead = remote.resolveReference("HEAD").getSha1();
    gitUtil.mockApi("GET", getPullRequestsUrl("test_feature"), mockResponse("[{"
        + "  \"id\": 1,\n"
        + "  \"number\": 12345,\n"
        + "  \"state\": \"open\",\n"
        + "  \"title\": \"test summary\",\n"
        + "  \"body\": \"test summary\","
        + "  \"head\": {\"sha\": \"" + changeHead + "\", \"ref\": \"test_feature\"},"
        + "  \"base\": {\"sha\": \"" + baseline + "\", \"ref\": \"master\"}"
        + "}]"));
    writeFile(this.workdir, "foo.txt", "test");

    ImmutableList<DestinationEffect> results =
        writer.write(
                TransformResults.of(this.workdir, new DummyRevision("one")).withBaseline(baseline)
                    .withChanges(new Changes(
                        ImmutableList.of(
                            toChange(new DummyRevision("feature"),
                                new Author("Foo Bar", "foo@bar.com"))),
                        ImmutableList.of()))
                    .withLabelFinder(
                        Functions.forMap(ImmutableMap.of("aaa",
                            ImmutableList.of("first a", "second a")))),
                Glob.ALL_FILES,
                console);

    assertThat(results.size()).isEqualTo(2);
  }

  private Writer<GitRevision> getWriterForTestEmptyDiff() throws Exception {
    gitUtil = new GitTestUtil(options);
    gitUtil.mockRemoteGitRepos();
    options.gitDestination = new GitDestinationOptions(options.general, options.git);
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";
    skylark = new SkylarkTestExecutor(options);
    options.githubDestination.destinationPrBranch = "test_feature";
    GitHubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = 'https://github.com/foo', \n"
        + "    title = 'Title ${aaa}',\n"
        + "    body = 'Body ${aaa}',\n"
        + "    allow_empty_diff = False,\n"
        + "    destination_ref = 'main',\n"
        + "    pr_branch = 'test_${CONTEXT_REFERENCE}',\n"
        + (emptyDiffMergeStatus != null
           ? "allow_empty_diff_merge_statuses = ['" + emptyDiffMergeStatus + "'],\n"
           : "")
        + "    primary_branch_migration = " +  primaryBranchMigration + ",\n"
        + ")");
    WriterContext writerContext =
        new WriterContext("piper_to_github", "TEST", false, new DummyRevision("feature", "feature"),
            Glob.ALL_FILES.roots());
    return d.newWriter(writerContext);
  }

  @Test
  public void testBranchNameFromUserWithLabel()
      throws ValidationException, IOException, RepoException {
    testBranchNameFromUser("test${CONTEXT_REFERENCE}", "test_feature", "&feature");
  }

  @Test
  public void testBranchNameFromUserWithConstantString()
      throws ValidationException, IOException, RepoException {
    testBranchNameFromUser("test_my_branch", "test_my_branch", "feature");
  }

  @Test
  public void testBranchNameFromUserWithAbnormalCharacters()
      throws ValidationException, IOException, RepoException {
    testBranchNameFromUser(
        "test*my&special%characters^branch@name",
        "test_my_special_characters_branch_name", "feature");
  }

  private void testBranchNameFromUser(String branchNameFromUser, String expectedBranchName,
      String contextReference)
      throws ValidationException, IOException, RepoException {
    GitHubPrDestination d =
        skylark.eval(
            "r",
            "r = git.github_pr_destination("
                + "    url = 'https://github.com/foo',"
                + "    destination_ref = 'other',"
                + "    pr_branch = " + "\'" + branchNameFromUser + "\',"
                + ")");
    DummyRevision dummyRevision = new DummyRevision("dummyReference", contextReference);

    mockNoPullRequestsGet(expectedBranchName);

    gitUtil.mockApi(
        "POST",
        "https://api.github.com/repos/foo/pulls",
        mockResponseAndValidateRequest(
            "{\n"
                + "  \"id\": 1,\n"
                + "  \"number\": 12345,\n"
                + "  \"state\": \"open\",\n"
                + "  \"title\": \"test summary\",\n"
                + "  \"body\": \"test summary\""
                + "}",
            MockRequestAssertion.equals(
                    "{\"base\":\"other\",\"body\":\"test summary\\n\",\"draft\":false,\"head\":\""
                        + expectedBranchName
                        + "\",\"title\":\"test summary\"}")));

    WriterContext writerContext =
        new WriterContext("piper_to_github_pr", "TEST", false, dummyRevision,
            Glob.ALL_FILES.roots());
    Writer<GitRevision> writer = d.newWriter(writerContext);
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/foo");
    addFiles(
        remote,
        "main",
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "").buildOrThrow());

    addFiles(
        remote,
        "other",
        "second change",
        ImmutableMap.<String, String>builder().put("foo.txt", "test").buildOrThrow());

    writeFile(this.workdir, "test.txt", "some content");
    writer.write(
        TransformResults.of(this.workdir, new DummyRevision("one")), Glob.ALL_FILES, console);

    assertThat(remote.refExists(expectedBranchName)).isTrue();
  }

  @Test
  public void testDestinationStatus() throws ValidationException, IOException, RepoException {
    options.githubDestination.createPullRequest = false;
    gitUtil.mockApi(
        anyString(),
        anyString(),
        new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            throw new AssertionError("No API calls allowed for this test");
          }
        });

    GitHubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = 'https://github.com/foo',"
        + "    destination_ref = 'main'"
        + ")");
    WriterContext writerContext = new WriterContext("piper_to_github", "TEST", false,
        new DummyRevision("feature", "feature"), Glob.ALL_FILES.roots());
    Writer<GitRevision> writer = d.newWriter(writerContext);

    GitRepository remote = gitUtil.mockRemoteRepo("github.com/foo");
    addFiles(
        remote,
        null,
        "first change\n\nDummyOrigin-RevId: baseline",
        ImmutableMap.<String, String>builder().put("foo.txt", "").buildOrThrow());

    DestinationStatus status = writer.getDestinationStatus(Glob.ALL_FILES, "DummyOrigin-RevId");

    assertThat(status.getBaseline()).isEqualTo("baseline");
    assertThat(status.getPendingChanges()).isEmpty();

    writeFile(this.workdir, "test.txt", "some content");
    writer.write(
        TransformResults.of(this.workdir, new DummyRevision("one")), Glob.ALL_FILES, console);

    // New writer since after changes it keeps state internally for ITERATIVE mode
    status = d.newWriter(writerContext).getDestinationStatus(Glob.ALL_FILES, "DummyOrigin-RevId");
    assertThat(status.getBaseline()).isEqualTo("baseline");
    // Not supported for now as we rewrite the whole branch history.
    assertThat(status.getPendingChanges()).isEmpty();
  }

  private void mockNoPullRequestsGet(String branchName) {
    gitUtil.mockApi("GET", getPullRequestsUrl(branchName), mockResponse("[]"));
  }

  private void addFiles(GitRepository remote, String branch, String msg, Map<String, String> files)
      throws IOException, RepoException {
    Path temp = Files.createTempDirectory("temp");
    GitRepository tmpRepo = remote.withWorkTree(temp);
    if (branch != null) {
      if (tmpRepo.refExists(branch)) {
        tmpRepo.simpleCommand("checkout", branch);
      } else if (!branch.equals("main")) {
        tmpRepo.branch(branch).run();
        tmpRepo.simpleCommand("checkout", branch);
      }
    } else {
      tmpRepo.simpleCommand("checkout", "-b", "main");
    }

    for (Entry<String, String> entry : files.entrySet()) {
      Path file = temp.resolve(entry.getKey());
      Files.createDirectories(file.getParent());
      Files.write(file, entry.getValue().getBytes(UTF_8));
    }

    tmpRepo.add().all().run();
    tmpRepo.simpleCommand("commit", "-m", msg);
  }

  private String git(String... argv) throws RepoException {
    return repo()
        .git(repoGitDir, argv)
        .getStdout();
  }

  private GitRepository repo() {
    return repoForPath(repoGitDir);
  }

  private GitRepository repoForPath(Path path) {
    return GitRepository
        .newBareRepo(path, getGitEnv(),  /*verbose=*/true, DEFAULT_TIMEOUT, /*noVerify=*/ false);
  }

  private static String getPullRequestsUrl(String branchName) {
    return "https://api.github.com/repos/foo/pulls?per_page=100&head=foo:" + branchName;
  }
}
