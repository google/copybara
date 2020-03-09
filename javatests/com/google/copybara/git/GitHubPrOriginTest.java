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
import static com.google.copybara.git.GitHubPROrigin.GITHUB_BASE_BRANCH;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_BASE_BRANCH_SHA1;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_PR_ASSIGNEE;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_PR_BODY;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_PR_HEAD_SHA;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_PR_NUMBER_LABEL;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_PR_TITLE;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_PR_URL;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_PR_USER;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static com.google.copybara.testing.git.GitTestUtil.mockResponse;
import static com.google.copybara.testing.git.GitTestUtil.mockResponseAndValidateRequest;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.gson.GsonFactory;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.copybara.Change;
import com.google.copybara.Origin.Baseline;
import com.google.copybara.Origin.Reader;
import com.google.copybara.Workflow;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.authoring.Authoring.AuthoringMappingMode;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.github.util.GitHubUtil;
import com.google.copybara.testing.FileSubjects;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.testing.git.GitTestUtil.CompleteRefValidator;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitHubPrOriginTest {

  private Path repoGitDir;
  private OptionsBuilder options;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;

  private final Authoring authoring = new Authoring(new Author("foo", "default@example.com"),
      AuthoringMappingMode.PASS_THRU, ImmutableSet.of());

  private Path workdir;
  private GitTestUtil gitUtil;

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
    gitUtil.mockRemoteGitRepos(new CompleteRefValidator());

    Path credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, "https://user:SECRET@github.com".getBytes(UTF_8));
    options.git.credentialHelperStorePath = credentialsFile.toString();

    skylark = new SkylarkTestExecutor(options);
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
        .newBareRepo(path, getGitEnv(), /*verbose=*/true, DEFAULT_TIMEOUT, /*noVerify*/ false);
  }

  @Test
  public void testNoCommandLineReference() throws Exception {
    ValidationException thrown =
        assertThrows(
            ValidationException.class,
            () ->
                githubPrOrigin(
                        "url = 'https://github.com/google/example'",
                        "required_labels = ['foo: yes', 'bar: yes']")
                    .resolve(null));
    assertThat(thrown).hasMessageThat().contains("A pull request reference is expected");
  }

  @Test
  public void testGitResolvePullRequest() throws Exception {
    mockPullRequestAndIssue(123, "open", "foo: yes", "bar: yes");
    checkResolve(
        githubPrOrigin(
            "url = 'https://github.com/google/example'",
            "required_labels = ['foo: yes', 'bar: yes']"),
        "https://github.com/google/example/pull/123",
        123);
  }

  @Test
  public void testGitResolveWithGitDescribe() throws Exception {
    mockPullRequestAndIssue(123, "open", "foo: yes", "bar: yes");
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/google/example");
    addFiles(remote, "first change", ImmutableMap.<String, String>builder()
        .put(123 + ".txt", "").build());
    String sha1 = remote.parseRef("HEAD");
    remote.simpleCommand("update-ref", GitHubUtil.asHeadRef(123), sha1);

    remote.simpleCommand("tag", "-m", "This is a tag", "1.0");

    GitRevision rev = githubPrOrigin(
        "url = 'https://github.com/google/example'",
        "required_labels = ['foo: yes', 'bar: yes']")
        .resolve("https://github.com/google/example/pull/123");

    assertThat(rev.associatedLabels().get("GIT_DESCRIBE_REQUESTED_VERSION")).containsExactly("1.0");
  }

  @Test
  public void testGitResolvePullRequestNumber() throws Exception {
    mockPullRequestAndIssue(123, "open", "foo: yes", "bar: yes");
    checkResolve(
        githubPrOrigin(
            "url = 'https://github.com/google/example'",
            "required_labels = ['foo: yes', 'bar: yes']"),
        "123",
        123);
  }

  @Test
  public void testEmptyUrl() {
    skylark.evalFails("git.github_pr_origin( url = '')", "Invalid empty field 'url'");
  }

  @Test
  public void testGitResolvePullRequestRawRef() throws Exception {
    mockPullRequestAndIssue(123, "open", "foo: yes", "bar: yes");
    checkResolve(
        githubPrOrigin(
            "url = 'https://github.com/google/example'",
            "required_labels = ['foo: yes', 'bar: yes']"),
        "refs/pull/123/head",
        123);
  }

  @Test
  public void testGitResolveSha1() throws Exception {
    mockPullRequestAndIssue(123, "open");

    GitHubPROrigin origin = githubPrOrigin(
        "url = 'https://github.com/google/example'");
    checkResolve(origin, "refs/pull/123/head", 123);

    // Test that we can resolve SHA-1 as long as they were fetched by the PR + base branch fetch.
    String sha1 = gitUtil.mockRemoteRepo("github.com/google/example").parseRef("HEAD");
    GitRevision rev = origin
        .resolve(sha1 + " not important review data");

    assertThat(rev.getSha1()).isEqualTo(sha1);
  }

  @Test
  public void testGitResolveNoLabelsRequired() throws Exception {
    mockPullRequestAndIssue(125, "open", "bar: yes");
    checkResolve(
        githubPrOrigin("url = 'https://github.com/google/example'", "required_labels = []"),
        "125",
        125);

    mockPullRequestAndIssue(126, "open");

    checkResolve(
        githubPrOrigin("url = 'https://github.com/google/example'", "required_labels = []"),
        "126",
        126);
  }

  @Test
  public void testGitResolveRequiredLabelsNotFound() throws Exception {
    mockPullRequestAndIssue(125, "open", "bar: yes");
    EmptyChangeException thrown =
        assertThrows(
            EmptyChangeException.class,
            () ->
              checkResolve(
                  githubPrOrigin(
                      "url = 'https://github.com/google/example'",
                      "required_labels = ['foo: yes', 'bar: yes']"),
                  "125",
                  125));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Cannot migrate http://github.com/google/example/pull/125 because it is missing the"
                + " following labels: [foo: yes]");
  }

  @Test
  public void testGitResolveRequiredLabelsNotFound_forceMigrate() throws Exception {
    options.githubPrOrigin.forceImport = true;
    mockPullRequestAndIssue(125, "open", "bar: yes");
    checkResolve(
        githubPrOrigin(
            "url = 'https://github.com/google/example'",
            "required_labels = ['foo: yes', 'bar: yes']"),
        "125",
        125);
  }

  @Test
  public void testLimitByBranch() throws Exception {
    // This should work since it returns a PR for master.
    mockPullRequestAndIssue(125, "open", "bar: yes");
    checkResolve(
        githubPrOrigin("url = 'https://github.com/google/example'", "branch = 'master'"),
        "125",
        125);

    mockPullRequestAndIssue(126, "open", "bar: yes");
    EmptyChangeException e =
        assertThrows(
            EmptyChangeException.class,
            () ->
              checkResolve(
                  githubPrOrigin("url = 'https://github.com/google/example'", "branch = 'other'"),
                  "126",
                  126));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "because its base branch is 'master', but the workflow is configured to only migrate"
                + " changes for branch 'other'");
  }

  @Test
  public void testGitResolveRequiredLabelsRetried() throws Exception {
    mockPullRequest(125, "open");

    mockIssue(
        125,
        issueResponse(125, "open"),
        issueResponse(125, "open", "foo: yes"),
        issueResponse(125, "open", "foo: yes", "bar: yes"));

    checkResolve(
        githubPrOrigin(
            "url = 'https://github.com/google/example'",
            "required_labels = ['foo: yes', 'bar: yes']",
            "retryable_labels = ['foo: yes', 'bar: yes']"),
        "125",
        125);

    verify(gitUtil.httpTransport(), times(3))
        .buildRequest("GET", "https://api.github.com/repos/google/example/issues/125");
  }

  @Test
  public void testGitResolveRequiredLabelsNotRetryable() throws Exception {
    mockPullRequestAndIssue(125, "open");
    EmptyChangeException thrown =
        assertThrows(
            EmptyChangeException.class,
            () ->
                checkResolve(
                    githubPrOrigin(
                        "url = 'https://github.com/google/example'",
                        "required_labels = ['foo: yes']"),
                    "125",
                    125));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Cannot migrate http://github.com/google/example/pull/125 because it is missing the"
                + " following labels: [foo: yes]");
  }

  @Test
  public void testAlreadyClosed_default() throws Exception {
    mockPullRequestAndIssue(125, "closed", "foo: yes");
    EmptyChangeException thrown =
        assertThrows(
            EmptyChangeException.class,
            () ->
                checkResolve(
                    githubPrOrigin("url = 'https://github.com/google/example'"), "125", 125));
    assertThat(thrown).hasMessageThat().contains("Pull Request 125 is not open");
  }

  @Test
  public void testAlreadyClosed_only_open() throws Exception {
    mockPullRequestAndIssue(125, "closed", "foo: yes");
    EmptyChangeException thrown =
        assertThrows(
            EmptyChangeException.class,
            () ->
                checkResolve(
                    githubPrOrigin("url = 'https://github.com/google/example', state = 'OPEN'"),
                    "125",
                    125));
    assertThat(thrown).hasMessageThat().contains("Pull Request 125 is not open");
  }

  @Test
  public void testAlreadyClosed_only_open_forceMigration() throws Exception {
    options.githubPrOrigin.forceImport = true;
    mockPullRequestAndIssue(125, "closed", "foo: yes");
    checkResolve(
        githubPrOrigin("url = 'https://github.com/google/example', state = 'OPEN'"), "125", 125);
  }

  @Test
  public void testAlreadyClosed_only_closed() throws Exception {
    mockPullRequestAndIssue(125, "open", "foo: yes");
    EmptyChangeException thrown =
        assertThrows(
            EmptyChangeException.class,
            () ->
                checkResolve(
                    githubPrOrigin("url = 'https://github.com/google/example', state = 'CLOSED'"),
                    "125",
                    125));
    assertThat(thrown).hasMessageThat().contains("Pull Request 125 is open");
  }

  @Test
  public void testGitResolveRequiredLabelsMixed() throws Exception {
    mockPullRequestAndIssue(125, "open", "foo: yes", "bar: yes");
    checkResolve(
        githubPrOrigin(
            "url = 'https://github.com/google/example'",
            "required_labels = ['foo: yes', 'bar: yes']",
            "retryable_labels = ['foo: yes']"),
        "125",
        125);
  }

  @Test
  public void testGitResolveInvalidReference() throws Exception {
    ValidationException thrown =
        assertThrows(
            ValidationException.class,
            () ->
                checkResolve(
                    githubPrOrigin("url = 'https://github.com/google/example'"), "master", 125));
    assertThat(thrown)
        .hasMessageThat()
        .contains("'master' is not a valid reference for a GitHub Pull Request");
  }

  @Test
  public void testChanges() throws Exception {
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/google/example");
    addFiles(remote, "base", ImmutableMap.<String, String>builder()
        .put("test.txt", "a").build());
    String base = remote.parseRef("HEAD");
    addFiles(remote, "one", ImmutableMap.<String, String>builder()
        .put("test.txt", "b").build());
    addFiles(remote, "two", ImmutableMap.<String, String>builder()
        .put("test.txt", "c").build());
    String prHeadSha1 = remote.parseRef("HEAD");
    remote.simpleCommand("update-ref", GitHubUtil.asHeadRef(123), prHeadSha1);

    withTmpWorktree(remote).simpleCommand("reset", "--hard", "HEAD~2"); // master = base commit.

    addFiles(remote, "master change", ImmutableMap.<String, String>builder()
        .put("other.txt", "").build());
    remote.simpleCommand("update-ref", GitHubUtil.asMergeRef(123), remote.parseRef("HEAD"));

    mockPullRequestAndIssue(123, "open");

    GitHubPROrigin origin = githubPrOrigin(
        "url = 'https://github.com/google/example'");

    Reader<GitRevision> reader = origin.newReader(Glob.ALL_FILES, authoring);

    GitRevision prHead = origin.resolve("123");
    assertThat(prHead.getSha1()).isEqualTo(prHeadSha1);
    ImmutableList<Change<GitRevision>> changes =
        reader.changes(origin.resolve(base), prHead).getChanges();

    assertThat(Lists.transform(changes, Change::getMessage))
        .isEqualTo(Lists.newArrayList("one\n", "two\n"));
    // Non-found baseline. We return all the changes between baseline and PR head.
    changes = reader.changes(origin.resolve(remote.parseRef("HEAD")), prHead).getChanges();

    // Even if the PR is outdated it should return only the changes in the PR by finding the
    // common ancestor.
    assertThat(Lists.transform(changes, Change::getMessage))
        .isEqualTo(Lists.newArrayList("one\n", "two\n"));
    assertThat(changes.stream()
        .map(c -> c.getRevision().getUrl())
        .allMatch(c -> c.startsWith("https://github.com/")))
        .isTrue();
  }

  @Test
  public void testCheckout() throws Exception {
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/google/example");
    String baseline1 = addFiles(remote, "base", ImmutableMap.<String, String>builder()
        .put("test.txt", "a").build());
    addFiles(remote, "one", ImmutableMap.<String, String>builder()
        .put("test.txt", "b").build());
    addFiles(remote, "two", ImmutableMap.<String, String>builder()
        .put("test.txt", "c").build());
    String prHeadSha1 = remote.parseRef("HEAD");
    remote.simpleCommand("update-ref", GitHubUtil.asHeadRef(123), prHeadSha1);

    withTmpWorktree(remote).simpleCommand("reset", "--hard", "HEAD~2"); // master = base commit.

    String baselineMerge = addFiles(remote, "master change", ImmutableMap.<String, String>builder()
        .put("other.txt", "").build());
    remote.simpleCommand("update-ref", GitHubUtil.asMergeRef(123), remote.parseRef("HEAD"));

    mockPullRequestAndIssue(123, "open");

    GitHubPROrigin origin = githubPrOrigin(
        "url = 'https://github.com/google/example'",
        "baseline_from_branch = True");

    GitRevision headPrRevision = origin.resolve("123");
    assertThat(headPrRevision.associatedLabels()).containsEntry(GITHUB_BASE_BRANCH, "master");
    assertThat(headPrRevision.associatedLabels()).containsEntry(GITHUB_BASE_BRANCH_SHA1, baseline1);
    assertThat(headPrRevision.associatedLabels()).containsEntry(GITHUB_PR_NUMBER_LABEL, "123");
    assertThat(headPrRevision.associatedLabels()).containsEntry(GITHUB_PR_TITLE, "test summary");
    assertThat(headPrRevision.associatedLabels()).containsEntry(GITHUB_PR_USER, "some_user");
    assertThat(headPrRevision.associatedLabels()).containsEntry(GITHUB_PR_ASSIGNEE, "assignee1");
    assertThat(headPrRevision.associatedLabels()).containsEntry(GITHUB_PR_ASSIGNEE, "assignee2");
    assertThat(headPrRevision.associatedLabels())
        .containsEntry(GITHUB_PR_HEAD_SHA, headPrRevision.getSha1());
    assertThat(headPrRevision.associatedLabels()).containsEntry(GITHUB_PR_BODY,
        "test summary\n\nMore text");

    Reader<GitRevision> reader = origin.newReader(Glob.ALL_FILES, authoring);
    Optional<Baseline<GitRevision>> baselineObj = reader.findBaseline(headPrRevision, "RevId");
    assertThat(baselineObj.isPresent()).isTrue();
    assertThat(baselineObj.get().getBaseline())
        .isEqualTo(baselineObj.get().getOriginRevision().getSha1());

    assertThat(baselineObj.get().getBaseline()).isEqualTo(baseline1);

    assertThat(
            reader
                .changes(baselineObj.get().getOriginRevision(), headPrRevision).getChanges()
                .size())
        .isEqualTo(2);

    assertThat(reader.findBaselinesWithoutLabel(headPrRevision, /*limit=*/ 1).get(0).getSha1())
        .isEqualTo(baseline1);

    reader.checkout(headPrRevision, workdir);

    FileSubjects.assertThatPath(workdir)
        .containsFile("test.txt", "c")
        .containsNoMoreFiles();

    // Now try with merge ref
    origin = githubPrOrigin(
        "url = 'https://github.com/google/example'",
        "use_merge = True",
        "baseline_from_branch = True");

    mockPullRequestAndIssue(123, "open");

    GitRevision mergePrRevision = origin.resolve("123");

    assertThat(mergePrRevision.associatedLabels()).containsEntry(GITHUB_BASE_BRANCH, "master");
    assertThat(mergePrRevision.associatedLabels())
        .containsEntry(GITHUB_BASE_BRANCH_SHA1, baselineMerge);
    assertThat(mergePrRevision.associatedLabels()).containsEntry(GITHUB_PR_NUMBER_LABEL, "123");
    assertThat(mergePrRevision.associatedLabels()).containsEntry(GITHUB_PR_TITLE, "test summary");
    assertThat(mergePrRevision.associatedLabels()).containsEntry(GITHUB_PR_BODY,
        "test summary\n\nMore text");
    assertThat(mergePrRevision.associatedLabels()).containsEntry(GITHUB_PR_URL,
        "http://some/pr/url/123");

    reader = origin.newReader(Glob.ALL_FILES, authoring);
    baselineObj = reader.findBaseline(mergePrRevision, "RevId");
    assertThat(baselineObj.isPresent()).isTrue();
    assertThat(baselineObj.get().getBaseline())
        .isEqualTo(baselineObj.get().getOriginRevision().getSha1());

    assertThat(baselineObj.get().getBaseline()).isEqualTo(baselineMerge);

    assertThat(
            reader
                .changes(baselineObj.get().getOriginRevision(), headPrRevision).getChanges()
                .size())
        .isEqualTo(2);

    assertThat(reader.findBaselinesWithoutLabel(mergePrRevision, /*limit=*/ 1).get(0).getSha1())
        .isEqualTo(baselineMerge);

    reader.checkout(mergePrRevision, workdir);

    FileSubjects.assertThatPath(workdir)
        .containsFile("other.txt", "")
        .containsNoMoreFiles();
  }

  @Test
  public void testHookForGitHubPr() throws Exception {
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/google/example");
    GitRepository destination = gitUtil.mockRemoteRepo("github.com/destination");
    addFiles(remote, "base", ImmutableMap.<String, String>builder().put("test.txt", "a").build());
    String lastRev = remote.parseRef("HEAD");
    addFiles(remote, "one", ImmutableMap.<String, String>builder().put("test.txt", "b").build());
    addFiles(remote, "two", ImmutableMap.<String, String>builder().put("test.txt", "c").build());

    String prHeadSha1 = remote.parseRef("HEAD");
    remote.simpleCommand("update-ref", GitHubUtil.asHeadRef(123), prHeadSha1);

    mockPullRequestAndIssue(123, "open");
    gitUtil.mockApi(
        eq("POST"),
        startsWith("https://api.github.com/repos/google/example/statuses/"),
        mockResponseAndValidateRequest(
            "{ state : 'success', context : 'the_context' }",
            content -> content.contains("Migration success at")));

    Path dest = Files.createTempDirectory("");
    options.folderDestination.localFolder = dest.toString();
    options.setWorkdirToRealTempDir();
    options.setForce(true);
    options.setLastRevision(lastRev);
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";

    Workflow<GitRevision, ?> workflow =
        workflow(
            ""
                + "def update_commit_status(ctx):\n"
                + "    for effect in ctx.effects:\n"
                + "        for origin_change in effect.origin_refs:\n"
                + "            if effect.type == 'CREATED' or effect.type == 'UPDATED':\n"
                + "                status = ctx.origin.create_status(\n"
                + "                    sha = origin_change.ref,\n"
                + "                    state = 'success',\n"
                + "                    context = 'copybara/import',\n"
                + "                    description = 'Migration success at ' "
                + "+ effect.destination_ref.id,\n"
                + "                )\n"
                + "core.workflow(\n"
                + "    name = 'default',\n"
                + "    origin = git.github_pr_origin(\n"
                + "        url = 'https://github.com/google/example',\n"
                + "    ),\n"
                + "    authoring = authoring.pass_thru('foo <foo@foo.com>'),\n"
                + "    destination = git.destination(\n"
                + "        url = '" + destination.getGitDir() + "'\n"
                + "    ),\n"
                + "    after_migration = [\n"
                + "        update_commit_status"
                + "    ]"
                + ")");

    workflow.run(workdir, ImmutableList.of("123"));

    verify(gitUtil.httpTransport(), times(2))
        .buildRequest(
            eq("POST"), startsWith("https://api.github.com/repos/google/example/statuses/"));
  }

  @Test
  public void testReviewApproversDescription() throws ValidationException {
    assertThat(createGitHubPrOrigin().describe(Glob.ALL_FILES)).containsExactly(
        "type", "git.github_pr_origin",
        "url", "https://github.com/google/example"
    );

    assertThat(createGitHubPrOrigin(
        "review_state = 'ANY'"
    ).describe(Glob.ALL_FILES)).containsExactly(
        "type", "git.github_pr_origin",
        "url", "https://github.com/google/example",
        "review_state", "ANY",
        "review_approvers", "MEMBER",
        "review_approvers", "OWNER",
        "review_approvers", "COLLABORATOR"
    );

    assertThat(createGitHubPrOrigin(
        "review_state = 'HEAD_COMMIT_APPROVED'",
        "review_approvers = ['MEMBER', 'OWNER']"
    ).describe(Glob.ALL_FILES)).containsExactly(
        "type", "git.github_pr_origin",
        "url", "https://github.com/google/example",
        "review_state", "HEAD_COMMIT_APPROVED",
        "review_approvers", "MEMBER",
        "review_approvers", "OWNER"
    );
  }

  @Test
  public void testReviewApprovers() throws Exception {
    GitRevision noReviews = checkReviewApprovers();
    assertThat(noReviews.associatedLabels())
        .doesNotContainKey(GitHubPROrigin.GITHUB_PR_REVIEWER_APPROVER);
    assertThat(noReviews.associatedLabels())
        .doesNotContainKey(GitHubPROrigin.GITHUB_PR_REVIEWER_OTHER);

    GitRevision any = checkReviewApprovers("review_state = 'ANY'");

    assertThat(any.associatedLabels().get(GitHubPROrigin.GITHUB_PR_REVIEWER_APPROVER))
        .containsExactly("APPROVED_MEMBER", "COMMENTED_OWNER", "APPROVED_COLLABORATOR");
    assertThat(any.associatedLabels().get(GitHubPROrigin.GITHUB_PR_REVIEWER_OTHER))
        .containsExactly("COMMENTED_OTHER");

    EmptyChangeException e =
        assertThrows(
            EmptyChangeException.class,
            () ->
                checkReviewApprovers(
                    "review_state = 'HEAD_COMMIT_APPROVED'",
                    "review_approvers = [\"MEMBER\", \"OWNER\"]"));
    assertThat(e).hasMessageThat().contains("missing the required approvals");

    GitRevision hasReviewers = checkReviewApprovers("review_state = 'ANY_COMMIT_APPROVED'",
        "review_approvers = [\"MEMBER\", \"OWNER\"]");

    assertThat(hasReviewers.associatedLabels().get(GitHubPROrigin.GITHUB_PR_REVIEWER_APPROVER))
        .containsExactly("APPROVED_MEMBER", "COMMENTED_OWNER");
    assertThat(hasReviewers.associatedLabels().get(GitHubPROrigin.GITHUB_PR_REVIEWER_OTHER))
        .containsExactly("COMMENTED_OTHER", "APPROVED_COLLABORATOR");

    GitRevision anyCommitApproved = checkReviewApprovers("review_state = 'HAS_REVIEWERS'",
        "review_approvers = [\"OWNER\"]");

    assertThat(anyCommitApproved.associatedLabels().get(GitHubPROrigin.GITHUB_PR_REVIEWER_APPROVER))
        .containsExactly("COMMENTED_OWNER");
    assertThat(anyCommitApproved.associatedLabels().get(GitHubPROrigin.GITHUB_PR_REVIEWER_OTHER))
        .containsExactly("APPROVED_MEMBER", "COMMENTED_OTHER", "APPROVED_COLLABORATOR");
  }


  @Test
  public void testHttprUrl() throws Exception {
    GitHubPROrigin val =
        skylark.eval(
            "origin", "origin = git.github_pr_origin(url = 'http://github.com/google/example')\n");
    assertThat(val.describe(Glob.ALL_FILES).get("url"))
        .contains("https://github.com/google/example");
  }

  @Test
  public void testDescribeBranch() throws Exception {
    GitHubPROrigin val =
        skylark.eval(
            "origin", "origin = git.github_pr_origin("
                + "url = 'http://github.com/google/example', branch = 'dev')\n");
    assertThat(val.describe(Glob.ALL_FILES).get("branch"))
        .contains("dev");
  }

  private GitRevision checkReviewApprovers(String... configLines)
      throws RepoException, IOException, ValidationException {
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/google/example");
    addFiles(remote, "base", ImmutableMap.<String, String>builder().put("test.txt", "a").build());
    addFiles(remote, "one", ImmutableMap.<String, String>builder().put("test.txt", "b").build());
    addFiles(remote, "two", ImmutableMap.<String, String>builder().put("test.txt", "c").build());

    String prHeadSha1 = remote.parseRef("HEAD");
    remote.simpleCommand("update-ref", GitHubUtil.asHeadRef(123), prHeadSha1);

    mockPullRequestAndIssue(123, "open");

    gitUtil.mockApi(
        "GET",
        "https://api.github.com/repos/google/example/pulls/123/reviews?per_page=100",
        mockResponse(
            toJson(
                ImmutableList.of(
                    ImmutableMap.of(
                        "user",
                        ImmutableMap.of("login", "APPROVED_COLLABORATOR"),
                        "state",
                        "APPROVED",
                        "author_association",
                        "COLLABORATOR",
                        "commit_id",
                        prHeadSha1),
                    ImmutableMap.of(
                        "user",
                        ImmutableMap.of("login", "APPROVED_MEMBER"),
                        "state",
                        "APPROVED",
                        "author_association",
                        "MEMBER",
                        "commit_id",
                        Strings.repeat("0", 40)),
                    ImmutableMap.of(
                        "user",
                        ImmutableMap.of("login", "COMMENTED_OWNER"),
                        "state",
                        "COMMENTED",
                        "author_association",
                        "OWNER",
                        "commit_id",
                        prHeadSha1),
                    ImmutableMap.of(
                        "user",
                        ImmutableMap.of("login", "COMMENTED_OTHER"),
                        "state",
                        "COMMENTED",
                        "author_association",
                        "NONE",
                        "commit_id",
                        prHeadSha1)))));

    GitHubPROrigin origin = createGitHubPrOrigin(configLines);

    return origin.resolve("123");
  }

  private GitHubPROrigin createGitHubPrOrigin(String... configLines) throws ValidationException {
    return skylark.eval("origin", "origin = "
        + "git.github_pr_origin(\n"
        + "    url = 'https://github.com/google/example',\n"
        + (configLines.length == 0 ? "" : "    " + Joiner.on(",\n    ").join(configLines) + ",\n")
        + ")\n");
  }

  private String toJson(Object obj) {
    try {
      return GsonFactory.getDefaultInstance().toPrettyString(obj);
    } catch (IOException e) {
      // Unexpected
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private Workflow<GitRevision, ?> workflow(String config) throws IOException, ValidationException {
    return (Workflow<GitRevision, ?>) skylark.loadConfig(
        new MapConfigFile(ImmutableMap.of("copy.bara.sky", config.getBytes()), "copy.bara.sky"))
        .getMigration("default");
  }

  @Test
  public void testMerge() throws Exception {
    GitRepository remote = withTmpWorktree(gitUtil.mockRemoteRepo("github.com/google/example"));
    addFiles(remote, "base", ImmutableMap.<String, String>builder()
        .put("a.txt", "").build());
    remote.simpleCommand("branch", "foo");
    remote.forceCheckout("foo");
    addFiles(remote, "one", ImmutableMap.<String, String>builder()
        .put("a.txt", "").put("b.txt", "").build());
    addFiles(remote, "two", ImmutableMap.<String, String>builder()
        .put("a.txt", "").put("b.txt", "").put("c.txt", "").build());
    remote.forceCheckout("master");
    addFiles(remote, "master change", ImmutableMap.<String, String>builder()
        .put("a.txt", "").put("d.txt", "").build());
    remote.simpleCommand("merge", "foo");
    remote.simpleCommand("update-ref", GitHubUtil.asHeadRef(123), remote.parseRef("foo"));
    remote.simpleCommand("update-ref", GitHubUtil.asMergeRef(123), remote.parseRef("master"));


    GitHubPROrigin origin = githubPrOrigin(
        "url = 'https://github.com/google/example'",
        "use_merge = True");

    mockPullRequestAndIssue(123, "open");

    origin.newReader(Glob.ALL_FILES, authoring).checkout(origin.resolve("123"), workdir);

    FileSubjects.assertThatPath(workdir)
        .containsFiles("a.txt", "b.txt", "c.txt", "d.txt")
        .containsNoMoreFiles();

    GitRevision mergeRevision = origin.resolve("123");

    // integrate SHA needs to be  HEAD ref of the PR, not the (moving) merge sha-1. This is
    // going to be used for doing a merge later, so at best it would do a double-merge and
    // in the worse case it wouldn't find the merge sha-1 since baseline branch could have
    // already moved.
    assertThat(mergeRevision.associatedLabels().get(GitModule.DEFAULT_INTEGRATE_LABEL))
        .contains(String.format(
            "https://github.com/google/example/pull/123 from googletestuser:example-branch %s",
            remote.resolveReference(GitHubUtil.asHeadRef(123)).getSha1()));

    Reader<GitRevision> reader = origin.newReader(Glob.ALL_FILES, authoring);
    assertThat(
            Lists.transform(
                reader.changes(/*fromRef=*/ null, mergeRevision).getChanges(),
                Change::getMessage))
        .isEqualTo(Lists.newArrayList("base\n", "one\n", "two\n", "Merge branch 'foo'\n"));

    // Simulate fast-forward
    remote.simpleCommand("update-ref", GitHubUtil.asMergeRef(123), remote.parseRef("foo"));

    assertThat(Lists.transform(
        reader.changes(/*fromRef=*/null, origin.resolve("123")).getChanges(),
        Change::getMessage))
        .isEqualTo(Lists.newArrayList("base\n", "one\n", "two\n"));
  }

  @Test
  public void testCheckout_noMergeRef() throws Exception {
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/google/example");
    addFiles(remote, "base", ImmutableMap.<String, String>builder()
        .put("test.txt", "a").build());
    String prHeadSha1 = remote.parseRef("HEAD");
    remote.simpleCommand("update-ref", GitHubUtil.asHeadRef(123), prHeadSha1);

    mockPullRequestAndIssue(123, "open");

    // Now try with merge ref
    GitHubPROrigin origin = githubPrOrigin(
        "url = 'https://github.com/google/example'",
        "use_merge = True");

    CannotResolveRevisionException thrown =
        assertThrows(
            CannotResolveRevisionException.class,
            () ->
                origin
                    .newReader(Glob.ALL_FILES, authoring)
                    .checkout(origin.resolve("123"), workdir));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Cannot find a merge reference for Pull Request 123");
  }

  private void checkResolve(GitHubPROrigin origin, String reference, int prNumber)
      throws RepoException, IOException, ValidationException {
    GitRepository remote = gitUtil.mockRemoteRepo("github.com/google/example");
    addFiles(remote, "first change", ImmutableMap.<String, String>builder()
        .put(prNumber + ".txt", "").build());
    String sha1 = remote.parseRef("HEAD");
    remote.simpleCommand("update-ref", GitHubUtil.asHeadRef(prNumber), sha1);

    GitRevision rev = origin.resolve(reference);
    assertThat(rev.asString()).hasLength(40);
    assertThat(rev.contextReference()).isEqualTo(GitHubUtil.asHeadRef(prNumber));
    assertThat(rev.associatedLabels()).containsEntry(GITHUB_PR_NUMBER_LABEL,
        Integer.toString(prNumber));
    assertThat(rev.associatedLabels()).containsEntry(GitModule.DEFAULT_INTEGRATE_LABEL,
        "https://github.com/google/example/pull/" + prNumber
            + " from googletestuser:example-branch " + sha1);
  }

  private String addFiles(GitRepository remote, String msg, Map<String, String> files)
      throws IOException, RepoException {
    GitRepository tmpRepo = withTmpWorktree(remote);

    for (Entry<String, String> entry : files.entrySet()) {
      Path file = tmpRepo.getWorkTree().resolve(entry.getKey());
      Files.createDirectories(file.getParent());
      Files.write(file, entry.getValue().getBytes(UTF_8));
    }

    tmpRepo.add().all().run();
    tmpRepo.simpleCommand("commit", "-m", msg);
    return Iterables.getOnlyElement(tmpRepo.log("HEAD").withLimit(1).run()).getCommit().getSha1();
  }

  private GitRepository withTmpWorktree(GitRepository remote) throws IOException {
    return remote.withWorkTree(Files.createTempDirectory("temp"));
  }

  private GitHubPROrigin githubPrOrigin(String... lines) throws ValidationException {
    return skylark.eval("r", "r = git.github_pr_origin("
        + "    " + Joiner.on(",\n    ").join(lines) + ",\n)");
  }

  public void mockPullRequestAndIssue(int prNumber, String state, String... labels)
      throws IOException {
    mockPullRequest(prNumber, state);
    mockIssue(prNumber, issueResponse(prNumber, state, labels));
  }

  private void mockPullRequest(int prNumber, String state) throws IOException {
    mockPullRequest(gitUtil, prNumber, state);
  }

  /** Used internally */
  public static void mockPullRequest(GitTestUtil gitUtil, int prNumber, String state)
      throws IOException {
    String content =
        "{\n"
            + "  \"id\": 1,\n"
            + "  \"number\": "
            + prNumber
            + ",\n"
            + "  \"state\": \""
            + state
            + "\",\n"
            + "  \"title\": \"test summary\",\n"
            + "  \"body\": \"test summary\n\nMore text\",\n"
            + "  \"html_url\": \"http://some/pr/url/"
            + prNumber
            + "\",\n"
            + "  \"head\": {\n"
            + "    \"label\": \"googletestuser:example-branch\",\n"
            + "    \"ref\": \"example-branch\"\n"
            + "   },\n"
            + "  \"base\": {\n"
            + "    \"label\": \"google:master\",\n"
            + "    \"ref\": \"master\"\n"
            + "   },\n"
            + "  \"user\": {\n"
            + "    \"login\": \"some_user\"\n"
            + "   },\n"
            + "  \"assignees\": [\n"
            + "    {\n"
            + "      \"login\": \"assignee1\"\n"
            + "    },\n"
            + "    {\n"
            + "      \"login\": \"assignee2\"\n"
            + "    }\n"
            + "  ]\n"
            + "}";
    gitUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/repos/google/example/pulls/" + prNumber),
        mockResponse(content));
  }

  private void mockIssue(int number, LowLevelHttpRequest first, LowLevelHttpRequest... rest)
      throws IOException {
    gitUtil.mockApi(
        eq("GET"), eq("https://api.github.com/repos/google/example/issues/" + number), first, rest);
  }

  private LowLevelHttpRequest issueResponse(int number, String state, String... labels) {
    StringBuilder result =
        new StringBuilder(
            "{\n"
                + "  \"id\": 1,\n"
                + "  \"number\": "
                + number
                + ",\n"
                + "  \"state\": \""
                + state
                + "\",\n"
                + "  \"title\": \"test summary\",\n"
                + "  \"body\": \"test summary\"\n,"
                + "  \"labels\": [\n");
    for (String label : labels) {
      result
          .append(
              "    {\n"
                  + "    \"id\": 111111,\n"
                  + "    \"url\": "
                  + "\"https://api.github.com/repos/google/example/labels/foo:%20yes\",\n"
                  + "    \"name\": \"")
          .append(label)
          .append("\",\n")
          .append("    \"color\": \"009800\",\n")
          .append("    \"default\": false\n")
          .append("  },\n");
    }
    result.append("  ]\n" + "}");
    return mockResponse(result.toString());
  }
}
