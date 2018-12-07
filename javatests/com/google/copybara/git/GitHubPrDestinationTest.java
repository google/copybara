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
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static com.google.copybara.testing.git.GitTestUtil.mockResponse;
import static com.google.copybara.testing.git.GitTestUtil.mockResponseAndValidateRequest;
import static com.google.copybara.testing.git.GitTestUtil.writeFile;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.copybara.Destination.DestinationStatus;
import com.google.copybara.Destination.Writer;
import com.google.copybara.Revision;
import com.google.copybara.WriterContext;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.Identity;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitHubPrDestinationTest {

  private Path repoGitDir;
  private OptionsBuilder options;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;
  private GitTestUtil gitUtil;

  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  private Path workdir;

  @Before
  public void setup() throws Exception {
    repoGitDir = Files.createTempDirectory("GitHubPrDestinationTest-repoGitDir");
    workdir = Files.createTempDirectory("workdir");

    git("init", "--bare", repoGitDir.toString());
    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console)
        .setOutputRootToTmpDir();

    gitUtil = new GitTestUtil(options);
    gitUtil.mockRemoteGitRepos();

    Path credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, "https://user:SECRET@github.com".getBytes(UTF_8));
    options.git.credentialHelperStorePath = credentialsFile.toString();

    options.gitDestination = new GitDestinationOptions(options.general, options.git);
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void testWrite_noContextReference() throws ValidationException {
    WriterContext writerContext =
        new WriterContext("piper_to_github_pr", "TEST",
            false, new DummyRevision("feature", null));
    GitHubPrDestination d = skylark.eval(
        "r", "r = git.github_pr_destination(" + "    url = 'https://github.com/foo'" + ")");
    thrown.expect(ValidationException.class);
    thrown.expectMessage("git.github_pr_destination is incompatible with the current origin. Origin has to be"
        + " able to provide the contextReference or use '--github-destination-pr-branch' flag");
    d.newWriter(writerContext);
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
            req ->
                req.equals(
                    "{\"base\":\"master\","
                        + "\"body\":\"custom body\","
                        + "\"head\":\"feature\","
                        + "\"title\":\"custom title\"}")));

    GitHubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = 'https://github.com/foo', \n"
        + "    title = 'custom title',\n"
        + "    body = 'custom body',\n"
        + ")");
    WriterContext writerContext =
        new WriterContext(
            /*workflowName=*/"piper_to_github",
            /*workflowIdentityUser=*/"TEST",
            /*dryRun=*/false,
            new DummyRevision("feature", "feature"));
    Writer<GitRevision> writer = d.newWriter(writerContext);
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/foo");
    addFiles(remote, null, "first change", ImmutableMap.<String, String>builder()
        .put("foo.txt", "").build());

    writeFile(this.workdir, "test.txt", "some content");
    writer.write(
        TransformResults.of(this.workdir, new DummyRevision("one")), Glob.ALL_FILES, console);

    verify(gitUtil.httpTransport(), times(1))
        .buildRequest("GET", getPullRequestsUrl("feature"));
    verify(gitUtil.httpTransport(), times(1))
        .buildRequest("POST", "https://api.github.com/repos/foo/pulls");
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
            req ->
                req.equals(
                    "{\"base\":\"master\",\"body\":\"Internal change.\",\"head\":\"feature\","
                        + "\"title\":\"Internal change.\"}")));

    GitHubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = 'https://github.com/foo'"
        + ")");

    WriterContext writerContext =
        new WriterContext(
            "piper_to_github",
            "test",
            /*dryRun=*/false,
            new DummyRevision("feature", "feature"));

    Writer<GitRevision> writer = d.newWriter(writerContext);

    GitRepository remote = gitUtil.mockRemoteRepo("github.com/foo");
    addFiles(remote, null, "first change", ImmutableMap.<String, String>builder()
        .put("foo.txt", "").build());

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

  private void mockNoPullRequestsGet(String branchName) throws IOException {
    gitUtil.mockApi("GET", getPullRequestsUrl(branchName), mockResponse("[]"));
  }

  @Test
  public void testHttpUrl() throws Exception {
    GitHubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = 'http://github.com/foo', \n"
        + "    title = 'custom title',\n"
        + "    body = 'custom body',\n"
        + ")");
    assertThat(d.describe(Glob.ALL_FILES).get("name")).contains("https://github.com/foo");
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
            req ->
                req.equals(
                    "{\"base\":\"master\",\"body\":\"test summary\",\"head\":\""
                        + "feature"
                        + "\",\"title\":\"test summary\"}")));

    GitHubPrDestination d =
        skylark.eval(
            "r", "r = git.github_pr_destination(" + "    url = 'https://github.com/foo'" + ")");

    Writer<GitRevision> writer = d.newWriter(new WriterContext(
        /*workflowName=*/"piper_to_github_pr",
        /*workflowIdentityUser=*/"TEST",
        /*dryRun=*/false,
        revision));

    GitRepository remote = gitUtil.mockRemoteRepo("github.com/foo");
    addFiles(remote, null, "first change", ImmutableMap.<String, String>builder()
        .put("foo.txt", "").build());

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
    writer = d.newWriter(new WriterContext(
        /*workflowName=*/"piper_to_github_pr",
        /*workflowIdentityUser=*/"TEST",
        /*dryRun=*/ false,
        revision));

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
    try {
      checkFindProject("https://github.com", "foo");
      fail();
    } catch (ValidationException e) {
      console.assertThat().onceInLog(MessageType.ERROR,
          ".*'https://github.com' is not a valid GitHub url.*");
    }
  }

  @Test
  public void testIntegratesCanBeRemoved() throws Exception {
    GitHubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = '" + "https://github.com/foo" + "',"
        + "    destination_ref = 'other',"
        + ")");

    assertThat(ImmutableList.copyOf(d.getIntegrates())).isEqualTo(GitModule.DEFAULT_GIT_INTEGRATES);

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
  public void testWriteNoMaster() throws ValidationException, IOException, RepoException {
    GitHubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = 'https://github.com/foo',"
        + "    destination_ref = 'other',"
        + ")");
    DummyRevision dummyRevision = new DummyRevision("dummyReference", "feature");
    WriterContext writerContext =
        new WriterContext( /*workflowName=*/"piper_to_github_pr", /*workflowIdentityUser=*/"TEST",
            /*dryRun=*/false, dummyRevision);
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
            req ->
                req.equals(
                    "{\"base\":\"other\",\"body\":\"test summary\",\"head\":\""
                        + branchName
                        + "\",\"title\":\"test summary\"}")));

    Writer<GitRevision> writer = d.newWriter(writerContext);
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/foo");
    addFiles(remote, "master", "first change", ImmutableMap.<String, String>builder()
        .put("foo.txt", "").build());

    addFiles(remote, "other", "second change", ImmutableMap.<String, String>builder()
        .put("foo.txt", "test").build());

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
  public void testBranchNameFromUserWithLabel() throws ValidationException, IOException, RepoException {
    testBranchNameFromUser("test${CONTEXT_REFERENCE}", "test_feature", "&feature");
  }

  @Test
  public void testBranchNameFromUserWithConstantString() throws ValidationException, IOException, RepoException {
    testBranchNameFromUser("test_my_branch", "test_my_branch", "feature");
  }

  @Test
  public void testBranchNameFromUserWithAbnormalCharacters() throws ValidationException, IOException, RepoException {
    testBranchNameFromUser("test*my&special%characters^branch@name", "test_my_special_characters_branch_name", "feature");
  }

  private void testBranchNameFromUser(String branchNameFromUser, String expectedBranchName, String contextReference) throws ValidationException, IOException, RepoException {
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
            req ->
                req.equals(
                    "{\"base\":\"other\",\"body\":\"test summary\",\"head\":\""
                        + expectedBranchName
                        + "\",\"title\":\"test summary\"}")));

    WriterContext writerContext =
        new WriterContext(
            /*workflowName=*/ "piper_to_github_pr",
            /*workflowIdentityUser=*/ "TEST",
            /*dryRun=*/ false,
            dummyRevision);
    Writer<GitRevision> writer = d.newWriter(writerContext);
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/foo");
    addFiles(
        remote,
        "master",
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "").build());

    addFiles(
        remote,
        "other",
        "second change",
        ImmutableMap.<String, String>builder().put("foo.txt", "test").build());

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
        + "    url = 'https://github.com/foo'"
        + ")");
    WriterContext writerContext = new WriterContext(
        "piper_to_github", "TEST", /*dryRun=*/false, new DummyRevision("feature", "feature"));
    Writer<GitRevision> writer = d.newWriter(writerContext);

    GitRepository remote = gitUtil.mockRemoteRepo("github.com/foo");
    addFiles(remote, "master", "first change\n\nDummyOrigin-RevId: baseline",
        ImmutableMap.<String, String>builder()
            .put("foo.txt", "").build());

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

  private void addFiles(GitRepository remote, String branch, String msg, Map<String, String> files)
      throws IOException, RepoException {
    Path temp = Files.createTempDirectory("temp");
    GitRepository tmpRepo = remote.withWorkTree(temp);
    if (branch != null) {
      if (tmpRepo.refExists(branch)) {
        tmpRepo.simpleCommand("checkout", branch);
      } else if (!branch.equals("master")) {
        tmpRepo.simpleCommand("branch", branch);
        tmpRepo.simpleCommand("checkout", branch);
      }
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
    return GitRepository.newBareRepo(path, getGitEnv(),  /*verbose=*/true, DEFAULT_TIMEOUT);
  }

  private static String getPullRequestsUrl(String branchName) {
    return "https://api.github.com/repos/foo/pulls?per_page=100&head=foo:" + branchName;
  }
}
