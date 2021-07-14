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
import static com.google.copybara.git.GitRepository.newBareRepo;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static com.google.copybara.testing.git.GitTestUtil.ALWAYS_TRUE;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static com.google.copybara.testing.git.GitTestUtil.mockResponse;
import static com.google.copybara.testing.git.GitTestUtil.mockResponseAndValidateRequest;
import static com.google.copybara.testing.git.GitTestUtil.mockResponseWithStatus;
import static com.google.copybara.testing.git.GitTestUtil.writeFile;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.copybara.ChangeMessage;
import com.google.copybara.Changes;
import com.google.copybara.Destination.Writer;
import com.google.copybara.DestinationEffect;
import com.google.copybara.DestinationEffect.Type;
import com.google.copybara.LabelFinder;
import com.google.copybara.Metadata;
import com.google.copybara.MigrationInfo;
import com.google.copybara.TransformWork;
import com.google.copybara.WriterContext;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RedundantChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GerritDestination.ChangeIdPolicy;
import com.google.copybara.git.GerritDestination.GerritMessageInfo;
import com.google.copybara.git.GerritDestination.GerritWriteHook;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.git.gerritapi.GerritApiException;
import com.google.copybara.git.testing.GitTesting;
import com.google.copybara.testing.DummyEndpoint;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.FileSubjects.PathSubject;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.testing.git.GitTestUtil.MockRequestAssertion;
import com.google.copybara.testing.git.GitTestUtil.Validator;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Message;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public class GerritDestinationTest {

  // TODO(danielromero): Remove once we are sure that Gerrit has transitioned completely
  private static final String GERRIT_RESPONSE_OLD = "Counting objects: 9, done.\n"
      + "Delta compression using up to 4 threads.\n"
      + "Compressing objects: 100% (6/6), done.\n"
      + "Writing objects: 100% (9/9), 3.20 KiB | 0 bytes/s, done.\n"
      + "Total 9 (delta 4), reused 0 (delta 0)\n"
      + "remote: Resolving deltas: 100% (4/4)\n"
      + "remote: Processing changes: updated: 1, done\n"
      + "remote:\n"
      + "remote: Updated Changes:\n"
      + "remote:   https://some.url.google.com/1234 This is a message\n"
      + "remote:\n"
      + "To sso://team/copybara-team/copybara\n"
      + " * [new branch]      HEAD -> refs/for/master%notify=NONE\n"
      + "<o> [master] ~/dev/copybara$\n";
  private static final String GERRIT_RESPONSE = "Counting objects: 9, done.\n"
      + "Delta compression using up to 4 threads.\n"
      + "Compressing objects: 100% (6/6), done.\n"
      + "Writing objects: 100% (9/9), 3.20 KiB | 0 bytes/s, done.\n"
      + "Total 9 (delta 4), reused 0 (delta 0)\n"
      + "remote: Resolving deltas: 100% (4/4)\n"
      + "remote: Processing changes: updated: 1, done\n"
      + "remote:\n"
      + "remote: SUCCESS\n"
      + "remote:\n"
      + "remote:   https://some.url.google.com/1234 This is a message [NEW]\n"
      + "remote:\n"
      + "To sso://team/copybara-team/copybara\n"
      + " * [new branch]      HEAD -> refs/for/master%notify=NONE\n"
      + "<o> [master] ~/dev/copybara$\n";

  private static final String CONSTANT_CHANGE_ID = "I" + Strings.repeat("a", 40);
  private static final String BASE_URL = "https://user:SECRET@copybara-not-real.com";

  private String url;
  private String fetch;
  private String pushToRefsFor;
  private Path repoGitDir;
  private Path workdir;
  private OptionsBuilder options;
  private TestingConsole console;
  private ImmutableList<String> excludedDestinationPaths;
  private SkylarkTestExecutor skylark;
  private String primaryBranchMigration = "False";
  private GitTestUtil gitUtil;

  private static String lastCommitChangeIdLineForRef(String originRef,
      GitRepository repo, String gitRef) throws RepoException {
    GitLogEntry log = Iterables.getOnlyElement(repo.log(gitRef).withLimit(1).run());
    assertThat(log.getBody()).contains("\n" + DummyOrigin.LABEL_NAME + ": " + originRef + "\n");
    String line = null;
    for (LabelFinder label : ChangeMessage.parseMessage(log.getBody()).getLabels()) {
      if (label.isLabel(GerritDestination.CHANGE_ID_LABEL)) {
        assertWithMessage(log.getBody() + " has a Change-Id label that doesn't"
            + " match the regex").that(label.getValue()).matches("I[0-9a-f]{40}$");
        assertThat(line).isNull(); // Multiple Change-Ids are not allowed.
        line = label.getLine();
      }
    }
    assertWithMessage(
        "Cannot find " + GerritDestination.CHANGE_ID_LABEL + " in:\n" + log.getBody())
        .that(line).isNotNull();
    return line;
  }

  private String changeIdFromRequest(String url) {
    return url.replaceAll(".*(I[a-z0-9]{40}).*", "$1");
  }

  private GitRepository repo() {
    return GitRepository.newBareRepo(
        repoGitDir, getGitEnv(),  /*verbose=*/true, DEFAULT_TIMEOUT, /*noVerify=*/ false);
  }

  private String git(String... argv) throws RepoException {
    return repo()
        .git(repoGitDir, argv)
        .getStdout();
  }

  private GerritDestination destination(String... lines) throws ValidationException {
    return skylark.eval("result", "result = "
        + "git.gerrit_destination(\n"
        + "    url = '" + url + "',\n"
        + "    fetch = '" + fetch + "',\n"
        + "    primary_branch_migration = " +  primaryBranchMigration + ",\n"
        + (lines.length == 0 ? "" : "    " + Joiner.on(",\n    ").join(lines) + ",\n")
        + "    " + (pushToRefsFor == null ? "" : "push_to_refs_for = '" + pushToRefsFor + "',")
        + ")");
  }

  private static String lastCommitChangeIdLine(String ref, GitRepository repo) throws Exception {
    return lastCommitChangeIdLineForRef(ref, repo,
        getGerritRef(repo, "refs/for/master%.*" + ref));
  }

  /**
   * Given a reference like refs/heads/master returns a reference created in the fake gerrit
   * (Something like refs/heads/master%somelabel=value).
   */
  private static String getGerritRef(GitRepository repo, String refRegex)
      throws RepoException {
    Pattern compiled = Pattern.compile(refRegex);
    ImmutableSet<String> refs = repo.showRef().keySet();
    List<String> first = refs.stream()
        .filter(e -> compiled.matcher(e).find()).collect(Collectors.toList());
    assertWithMessage("Multiple refs found: " + first).that((first.size() < 2))
        .isTrue();
    if (first.isEmpty()) {
      assertWithMessage("Cannot find reference " + refRegex + " in repo. Refs:\n    "
          + Joiner.on("\n    ").join(refs)).fail();
    }
    return first.get(0);
  }

  private void process(DummyRevision originRef)
      throws ValidationException, RepoException, IOException {
    process(originRef, destination());
  }

  private void process(DummyRevision originRef, GerritDestination destination)
      throws ValidationException, RepoException, IOException {
    WriterContext writerContext =
        new WriterContext("GerritDestination", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());
    Writer<GitRevision> gitRevisionWriter = destination.newWriter(writerContext);
    // This is largely unused, except for the label finder.
    TransformWork work =
        new TransformWork(
            workdir,
            new Metadata("Desc", new Author("foo", "foo@foo.com"), ImmutableSetMultimap.of()),
            Changes.EMPTY,
            console,
            new MigrationInfo(DummyOrigin.LABEL_NAME, null),
            originRef,
            console -> new DummyEndpoint(),
            console -> gitRevisionWriter.getFeedbackEndPoint(console),
            () -> gitRevisionWriter.getDestinationReader(console, null, workdir)
        );
    ImmutableList<DestinationEffect> result =
        gitRevisionWriter
            .write(
                TransformResults.of(workdir, originRef).withLabelFinder(work::getAllLabels)
                    .withIdentity(originRef.asString()),
                Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths),
                console);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getErrors()).isEmpty();
    assertThat(result.get(0).getType()).isEqualTo(Type.CREATED);
    assertThat(result.get(0).getDestinationRef().getType()).isEqualTo("commit");
    assertThat(result.get(0).getDestinationRef().getId()).matches("[0-9a-f]{40}");
  }

  @Test
  public void gerritChangeIdChangesBetweenCommits() throws Exception {
    fetch = "master";

    gitUtil.mockApi(
        eq("GET"),
        eq("https://localhost:33333/changes/?q="
            + "hashtag:%22copybara_id_origin_ref_commiter@email%22%20AND"
            + "%20project:foo/bar%20AND%20status:NEW"),
        mockResponse("[]"));

    gitUtil.mockApi(
        eq("GET"),
        eq("https://localhost:33333/changes/?q="
            + "hashtag:%22copybara_id_origin_ref2_commiter@email%22%20AND"
            + "%20project:foo/bar%20AND%20status:NEW"),
        mockResponse("[]"));

    writeFile(workdir, "file", "some content");

    options.setForce(true);
    process(new DummyRevision("origin_ref"));

    String firstChangeIdLine = lastCommitChangeIdLine("origin_ref", repo());

    writeFile(workdir, "file2", "some more content");
    git("branch", "master", getGerritRef(repo(), "refs/for/master"));
    options.setForce(false);
    process(new DummyRevision("origin_ref2"));

    assertThat(firstChangeIdLine)
        .isNotEqualTo(lastCommitChangeIdLine("origin_ref2", repo()));
  }

  @Test
  public void specifyChangeId() throws Exception {
    fetch = "master";

    writeFile(workdir, "file", "some content");

    String changeId = "Iaaaaaaaaaabbbbbbbbbbccccccccccdddddddddd";
    options.setForce(true);
    options.gerrit.gerritChangeId = changeId;
    process(new DummyRevision("origin_ref"));
    assertThat(lastCommitChangeIdLine("origin_ref", repo()))
        .isEqualTo(GerritDestination.CHANGE_ID_LABEL + ": " + changeId);

    git("branch", "master", getGerritRef(repo(), "refs/for/master"));

    writeFile(workdir, "file", "some different content");

    changeId = "Ibbbbbbbbbbccccccccccddddddddddeeeeeeeeee";
    options.setForce(false);
    options.gerrit.gerritChangeId = changeId;
    process(new DummyRevision("origin_ref"));
    assertThat(lastCommitChangeIdLine("origin_ref", repo()))
        .isEqualTo(GerritDestination.CHANGE_ID_LABEL + ": " + changeId);
  }

  @Test
  public void testDefaultRefForPush() throws Exception {
    fetch = "foo";
    pushToRefsFor = null;

    String expectedChangeId = runForDefaults();
    assertThat(lastCommitChangeIdLineForRef("origin_ref", repo(),
        getGerritRef(repo(), "refs/for/foo")))
        .isEqualTo(expectedChangeId);
  }

  @Test
  public void testDefaultRefForPushFetchFlag() throws Exception {
    fetch = "bar";
    options.gitDestination.fetch = "foo";
    pushToRefsFor = null;

    String expectedChangeId = runForDefaults();
    assertThat(lastCommitChangeIdLineForRef("origin_ref", repo(),
        getGerritRef(repo(), "refs/for/foo")))
        .isEqualTo(expectedChangeId);
  }

  @Test
  public void testRefForPushFetchFlag() throws Exception {
    fetch = "bar";
    options.gitDestination.fetch = "foo";
    pushToRefsFor = "baz";

    String expectedChangeId = runForDefaults();
    assertThat(lastCommitChangeIdLineForRef("origin_ref", repo(),
        getGerritRef(repo(), "refs/for/baz")))
        .isEqualTo(expectedChangeId);
  }

  private String runForDefaults() throws IOException, ValidationException, RepoException {
    fakeOneCommitInDestination();
    git("branch", "foo");
    writeFile(workdir, "file", "some content");

    String changeId = "Iaaaaaaaaaabbbbbbbbbbccccccccccdddddddddd";
    options.gerrit.gerritChangeId = changeId;
    process(new DummyRevision("origin_ref"));
    return GerritDestination.CHANGE_ID_LABEL + ": " + changeId;
  }

  @Before
  public void setup() throws Exception {
    workdir = Files.createTempDirectory("workdir");

    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console)
        .setOutputRootToTmpDir();

    gitUtil = new GitTestUtil(options);
    Path credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, BASE_URL.getBytes(UTF_8));
    GitRepository repo = newBareRepo(Files.createTempDirectory("test_repo"),
        getGitEnv(), /*verbose=*/true, DEFAULT_TIMEOUT, /*noVerify=*/ false)
        .init()
        .withCredentialHelper("store --file=" + credentialsFile);
    gitUtil.mockRemoteGitRepos(new Validator(), repo);

    options.gitDestination = new GitDestinationOptions(options.general, options.git);
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";
    excludedDestinationPaths = ImmutableList.of();

    repoGitDir = gitUtil.mockRemoteRepo("localhost:33333/foo/bar").getGitDir();
    url = "https://localhost:33333/foo/bar";
    repo().init();

    skylark = new SkylarkTestExecutor(options);

    when(gitUtil
            .httpTransport()
            .buildRequest(eq("GET"), startsWith("https://localhost:33333/changes/")))
        .then(
            (Answer<LowLevelHttpRequest>)
                invocation -> {
                  String change = changeIdFromRequest((String) invocation.getArguments()[1]);
                  return mockResponse(
                      "["
                          + "{"
                          + "  change_id : \""
                          + change
                          + "\","
                          + "  status : \"NEW\""
                          + "}]");
                });

    gitUtil.mockApi(
        eq("GET"),
        eq("https://localhost:33333/changes/?q="
            + "hashtag:%22copybara_id_origin_ref_commiter@email%22%20AND"
            + "%20project:foo/bar%20AND%20status:NEW"),
        mockResponse("[]"));
  }

  private void mockNoChangesFound() throws IOException {
    gitUtil.mockApi(eq("GET"), startsWith("https://localhost:33333/changes/"), mockResponse("[]"));
  }

  @Test
  public void testChangeIdPolicyRequire() throws Exception {
    options.gerrit.gerritChangeId = null;
    options.gitDestination.nonFastForwardPush = true;
    String changeId = runChangeIdPolicy("Test message\n\nChange-Id: " + CONSTANT_CHANGE_ID + "\n",
        "change_id_policy = 'REQUIRE'");
    assertThat(changeId).isEqualTo(CONSTANT_CHANGE_ID);
    ValidationException e =
        assertThrows(
            ValidationException.class,
            () -> runChangeIdPolicy("Test message", "change_id_policy = 'REQUIRE'"));
    assertThat(e).hasMessageThat().contains("label not found in message");
  }

  @Test
  public void testChangeIdPolicyFailIfPresent() throws Exception {
    options.gerrit.gerritChangeId = null;
    options.gitDestination.nonFastForwardPush = true;
    String changeId = runChangeIdPolicy("Test message", "change_id_policy = 'FAIL_IF_PRESENT'");
    assertThat(changeId).isNotNull();
    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                runChangeIdPolicy(
                    "Test message\n\nChange-Id: " + CONSTANT_CHANGE_ID + "\n",
                    "change_id_policy = 'FAIL_IF_PRESENT'"));
    assertThat(e).hasMessageThat().contains("label found in message");
  }

  /**
   * Default is FAIL_IF_PRESENT behavior
   */
  @Test
  public void testChangeIdPolicyDefault() throws Exception {
    options.gerrit.gerritChangeId = null;
    options.gitDestination.nonFastForwardPush = true;
    String changeId = runChangeIdPolicy("Test message");
    assertThat(changeId).isNotNull();
    ValidationException e =
        assertThrows(
            ValidationException.class,
            () -> runChangeIdPolicy("Test message\n\nChange-Id: " + CONSTANT_CHANGE_ID + "\n"));
    assertThat(e).hasMessageThat().contains("label found in message");
  }

  @Test
  public void testChangeIdPolicyReplace() throws Exception {
    options.gerrit.gerritChangeId = null;
    options.gitDestination.nonFastForwardPush = true;
    String changeId = runChangeIdPolicy("Test message", "change_id_policy = 'REPLACE'");
    assertThat(changeId).isNotNull();
    changeId = runChangeIdPolicy("Test message\n\nChange-Id: " + CONSTANT_CHANGE_ID + "\n",
        "change_id_policy = 'REPLACE'");
    assertThat(changeId).isNotEqualTo(CONSTANT_CHANGE_ID);
  }

  @Test
  public void testChangeIdPolicyPassFlag() throws Exception {
    options.gerrit.gerritChangeId = CONSTANT_CHANGE_ID;
    options.gitDestination.nonFastForwardPush = true;
    String changeId = runChangeIdPolicy("Test message", "change_id_policy = 'REUSE'");
    // Flag wins over anything
    assertThat(changeId).isEqualTo(CONSTANT_CHANGE_ID);
  }

  @Test
  public void testChangeIdPolicyReuse() throws Exception {
    options.gerrit.gerritChangeId = null;
    options.gitDestination.nonFastForwardPush = true;
    String changeId = runChangeIdPolicy("Test message", "change_id_policy = 'REUSE'");
    assertThat(changeId).isNotNull();
    changeId = runChangeIdPolicy("Test message\n\nChange-Id: " + CONSTANT_CHANGE_ID + "\n",
        "change_id_policy = 'REUSE'");
    assertThat(changeId).isEqualTo(CONSTANT_CHANGE_ID);
  }

  private String runChangeIdPolicy(String summary, String... config) throws Exception {
    fetch = "master";

    writeFile(workdir, "file", "some content");

    options.setForce(true);

    url = "https://localhost:33333/foo/bar";
    GitRepository repo = gitUtil.mockRemoteRepo("localhost:33333/foo/bar");
    mockNoChangesFound();

    DummyRevision originRef = new DummyRevision("origin_ref");
    WriterContext writerContext =
        new WriterContext("GerritDestinationTest", "test", false, originRef,
            Glob.ALL_FILES.roots());
    List<DestinationEffect> result =
        destination(config)
            .newWriter(writerContext)
            .write(
                TransformResults.of(workdir, originRef)
                    .withSummary(summary)
                    .withIdentity(originRef.asString()),
                Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths),
                console);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getErrors()).isEmpty();

    return lastCommitChangeIdLine("origin_ref", repo).replace("Change-Id: ", "").trim();
  }

  @Test
  public void testGerritSubmit() throws Exception {
    options.gerrit.gerritChangeId = null;
    fetch = "master";
    pushToRefsFor = "master";
    writeFile(workdir, "file", "some content");

    options.setForce(true);

    url = "https://localhost:33333/foo/bar";
    GitRepository repo = gitUtil.mockRemoteRepo("localhost:33333/foo/bar");
    mockNoChangesFound();

    DummyRevision originRef = new DummyRevision("origin_ref");
    GerritDestination destination = destination("submit = True");
    Glob glob = Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths);
    WriterContext writerContext =
        new WriterContext("GerritDestinationTest", "test", false, originRef,
            Glob.ALL_FILES.roots());
    List<DestinationEffect> result =
        destination
            .newWriter(writerContext)
            .write(
                TransformResults.of(workdir, originRef)
                    .withSummary("Test message")
                    .withIdentity(originRef.asString()),
                glob,
                console);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getErrors()).isEmpty();

    String changeId = lastCommitChangeIdLineForRef("origin_ref", repo, "refs/heads/master")
        .replace("Change-Id: ", "").trim();

    assertThat(changeId).isNotNull();
    assertThat(destination.getType()).isEqualTo("git.destination");
    assertThat(destination.describe(glob).get("fetch")).isEqualTo(ImmutableSet.of("master"));
    assertThat(destination.describe(glob).get("push")).isEqualTo(ImmutableSet.of("master"));
  }

  @Test
  public void reuseChangeId() throws Exception {
    fetch = "master";

    writeFile(workdir, "file", "some content");

    options.setForce(true);
    options.gerrit.gerritChangeId = null;

    url = "https://localhost:33333/foo/bar";
    GitRepository repo = gitUtil.mockRemoteRepo("localhost:33333/foo/bar");
    mockNoChangesFound();

    process(new DummyRevision("origin_ref"));
    String changeId = lastCommitChangeIdLine("origin_ref", repo);
    assertThat(changeId).matches(GerritDestination.CHANGE_ID_LABEL + ": I[a-z0-9]+");
    LabelFinder labelFinder = new LabelFinder(changeId);

    writeFile(workdir, "file", "some different content");
    String expected =
        "https://localhost:33333/changes/?q=hashtag:%22copybara_id_origin_ref_commiter@email%22"
            + "%20AND%20project:foo/bar%20AND%20status:NEW";

    gitUtil.mockApi(
        "GET",
        expected,
        mockResponse(
            "["
                + "{"
                + "  change_id : \""
                + labelFinder.getValue()
                + "\","
                + "  status : \"NEW\""
                + "}]"));

    // Allow to push again in a non-fastforward way.
    repo.simpleCommand("update-ref", "-d", getGerritRef(repo, "refs/for/master"));
    process(new DummyRevision("origin_ref"));
    assertThat(lastCommitChangeIdLine("origin_ref", repo)).isEqualTo(changeId);
    assertThatGerritCheckout(repo, "refs/for/master")
        .containsFile("file", "some different content")
        .containsNoMoreFiles();
  }

  @Test
  public void testReviewerFieldWithTopic() throws Exception {
    pushToRefsFor = "master";
    writeFile(workdir, "file", "some content");
    fetch = "master";
    options.gerrit.gerritTopic = "testTopic";
    options.setForce(true);

    url = "https://localhost:33333/foo/bar";
    mockNoChangesFound();

    DummyRevision originRef = new DummyRevision("origin_ref");
    GerritDestination destination =
        destination("submit = False", "reviewers = [\"${SOME_REVIEWER}\"]");
    Glob glob = Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths);
    WriterContext writerContext =
        new WriterContext("GerritDestination", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());
    List<DestinationEffect> result =
        destination
            .newWriter(writerContext)
            .write(
                TransformResults.of(workdir, originRef)
                    .withSummary("Test message")
                    .withIdentity(originRef.asString())
                    .withLabelFinder(
                        e ->
                            e.equals("SOME_REVIEWER")
                                ? ImmutableList.of("foo@example.com")
                                : ImmutableList.of()),
                glob,
                console);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getErrors()).isEmpty();
    assertPushRef("refs/for/master%topic=testTopic,hashtag=copybara_id_origin_ref_commiter@email,"
        + "r=foo@example.com");
  }

  @Test
  public void configFailed_gerritSubmitFalse() {
    ValidationException expected = assertThrows(ValidationException.class,
        () -> destination("submit = False", "gerrit_submit = True"));
    assertThat(expected).hasMessageThat()
        .contains("Only set gerrit_submit if submit is true");
  }

  @Test
  public void gerritSubmit_success() throws Exception {
    options.gerrit.gerritChangeId = null;
    fetch = "master";
    writeFile(workdir, "file", "some content");
    url = BASE_URL + "/foo/bar";
    repoGitDir = gitUtil.mockRemoteRepo("user:SECRET@copybara-not-real.com/foo/bar").getGitDir();
    gitUtil.mockApi(eq("GET"), startsWith(BASE_URL +  "/changes/"),
        mockResponse(
            "["
                + "{"
                + "  change_id : \"Iaaaaaaaaaabbbbbbbbbbccccccccccdddddddddd\","
                + "  status : \"NEW\""
                + "}]")
        );
    AtomicBoolean submitCalled = new AtomicBoolean(false);
    AtomicBoolean reviewCalled = new AtomicBoolean(false);
    gitUtil.mockApi(
        eq("POST"),
        matches(BASE_URL + "/changes/.*/revisions/.*/review"),
        mockResponseAndValidateRequest("{\"labels\": { \"Code-Review\": 2}}",
            new MockRequestAssertion("Always true with side-effect",
                s -> {
                  reviewCalled.set(true);
                  return true;
                })));

    gitUtil.mockApi(
        eq("POST"),
        matches(BASE_URL + "/changes/.*/submit"),
        mockResponseAndValidateRequest(
            "{"
                + "  change_id : \"Iaaaaaaaaaabbbbbbbbbbccccccccccdddddddddd\","
                + "  status : \"submitted\""
                + "}",
            new MockRequestAssertion("Always true with side-effect",
                s -> {
                  submitCalled.set(true);
                  return true;
                }))
    );

    options.setForce(true);
    DummyRevision originRef = new DummyRevision("origin_ref");
    GerritDestination destination = destination("submit = True", "gerrit_submit = True");
    Glob glob = Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths);
    WriterContext writerContext =
        new WriterContext("GerritDestinationTest", "test", false, originRef,
            Glob.ALL_FILES.roots());
    List<DestinationEffect> result =
        destination
            .newWriter(writerContext)
            .write(
                TransformResults.of(workdir, originRef)
                    .withSummary("Test message")
                    .withIdentity(originRef.asString()),
                glob,
                console);

    assertThat(reviewCalled.get()).isTrue();
    assertThat(submitCalled.get()).isTrue();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getErrors()).isEmpty();

  }

  @Test
  public void gerritSubmit_plusTwoRestricted() throws Exception {
    options.gerrit.gerritChangeId = null;
    fetch = "master";
    writeFile(workdir, "file", "some content");
    url = BASE_URL + "/foo/bar";
    repoGitDir = gitUtil.mockRemoteRepo("user:SECRET@copybara-not-real.com/foo/bar").getGitDir();
    gitUtil.mockApi(eq("GET"), startsWith(BASE_URL +  "/changes/"),
        mockResponse(
            "["
                + "{"
                + "  change_id : \"Iaaaaaaaaaabbbbbbbbbbccccccccccdddddddddd\","
                + "  status : \"NEW\""
                + "}]")
    );
    AtomicBoolean submitCalled = new AtomicBoolean(false);
    AtomicBoolean reviewCalled = new AtomicBoolean(false);
    gitUtil.mockApi(
        eq("POST"),
        matches(BASE_URL + "/changes/.*/revisions/.*/review"),
        mockResponseWithStatus("Applying label \\\"Code-Review\\\": 2 is restricted\\n\\n", 401));

    gitUtil.mockApi(
        eq("POST"),
        matches(BASE_URL + "/changes/.*/submit"),
        mockResponseAndValidateRequest(
            "{"
                + "  change_id : \"Iaaaaaaaaaabbbbbbbbbbccccccccccdddddddddd\","
                + "  status : \"submitted\""
                + "}",
            new MockRequestAssertion("Always true with side-effect",
                s -> {
                  submitCalled.set(true);
                  return true;
                }))
    );

    options.setForce(true);
    DummyRevision originRef = new DummyRevision("origin_ref");
    GerritDestination destination = destination("submit = True", "gerrit_submit = True");
    Glob glob = Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths);
    WriterContext writerContext =
        new WriterContext("GerritDestinationTest", "test", false, originRef,
            Glob.ALL_FILES.roots());
    ValidationException validationException =
        assertThrows(ValidationException.class,
            () ->  destination
              .newWriter(writerContext)
              .write(
                  TransformResults.of(workdir, originRef)
                      .withSummary("Test message")
                      .withIdentity(originRef.asString()),
                  glob,
                  console));

    assertThat(validationException).hasMessageThat().contains("2 is restricted");
  }

  @Test
  public void gerritSubmit_noChange() throws Exception {
    options.gerrit.gerritChangeId = null;
    fetch = "master";
    writeFile(workdir, "file", "some content");
    url = BASE_URL + "/foo/bar";
    repoGitDir = gitUtil.mockRemoteRepo("user:SECRET@copybara-not-real.com/foo/bar").getGitDir();
    gitUtil.mockApi(eq("GET"), startsWith(BASE_URL +  "/changes/"),
        mockResponse("[]")
    );
    AtomicBoolean submitCalled = new AtomicBoolean(false);
    AtomicBoolean reviewCalled = new AtomicBoolean(false);
    gitUtil.mockApi(
        eq("POST"),
        matches(BASE_URL + "/changes/.*/revisions/.*/review"),
        mockResponseWithStatus("", 204,
            new MockRequestAssertion("Always true with side-effect",
                s -> {
                  reviewCalled.set(true);
                  return true;
                })));
    gitUtil.mockApi(
        eq("POST"),
        matches(BASE_URL + "/changes/.*/submit"),
        mockResponseWithStatus("", 204,
            new MockRequestAssertion("Always true with side-effect",
                s -> {
                  submitCalled.set(true);
                  return true;
                }))
    );

    options.setForce(true);
    DummyRevision originRef = new DummyRevision("origin_ref");
    GerritDestination destination = destination("submit = True", "gerrit_submit = True");
    Glob glob = Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths);
    WriterContext writerContext =
        new WriterContext("GerritDestinationTest", "test", false, originRef,
            Glob.ALL_FILES.roots());
    List<DestinationEffect> result =
        destination
            .newWriter(writerContext)
            .write(
                TransformResults.of(workdir, originRef)
                    .withSummary("Test message")
                    .withIdentity(originRef.asString()),
                glob,
                console);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getErrors()).isEmpty();
    assertThat(submitCalled.get()).isFalse();
    assertThat(reviewCalled.get()).isFalse();
  }

  @Test
  public void gerritSubmit_fail() throws Exception {
    options.gerrit.gerritChangeId = null;
    fetch = "master";
    writeFile(workdir, "file", "some content");
    url = BASE_URL + "/foo/bar";
    repoGitDir = gitUtil.mockRemoteRepo("user:SECRET@copybara-not-real.com/foo/bar").getGitDir();
    gitUtil.mockApi(eq("GET"), startsWith(BASE_URL +  "/changes/"),
        mockResponse(
            "["
                + "{"
                + "  change_id : \"12345\","
                + "  status : \"NEW\""
                + "}]")
    );
    gitUtil.mockApi(
        eq("POST"),
        matches(BASE_URL + "/changes/.*/revisions/.*/review"),
        mockResponse("{\"labels\": { \"Code-Review\": 2}}"));

    gitUtil.mockApi(
        eq("POST"),
        matches(BASE_URL + "/changes/.*/submit"),
        mockResponseWithStatus(
            "Submit failed.", 403)
    );

    options.setForce(true);
    DummyRevision originRef = new DummyRevision("origin_ref");
    GerritDestination destination = destination("submit = True", "gerrit_submit = True");
    Glob glob = Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths);

    WriterContext writerContext =
        new WriterContext("GerritDestinationTest", "test", false, originRef,
            Glob.ALL_FILES.roots());

    GerritApiException gerritApiException =
        assertThrows(GerritApiException.class,
          () -> destination
              .newWriter(writerContext)
              .write(
                  TransformResults.of(workdir, originRef)
                      .withSummary("Test message")
                      .withIdentity(originRef.asString()),
                  glob,
                  console));

    assertThat(gerritApiException).hasMessageThat().contains("Submit failed");
  }

  @Test
  public void testReviewerFieldWithLabel() throws Exception {
    pushToRefsFor = "master%label=Foo";
    writeFile(workdir, "file", "some content");
    fetch = "master";
    options.setForce(true);

    url = "https://localhost:33333/foo/bar";
    mockNoChangesFound();

    DummyRevision originRef = new DummyRevision("origin_ref");
    GerritDestination destination =
        destination("submit = False", "reviewers = [\"${SOME_REVIEWER}\"]");
    Glob glob = Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths);
    WriterContext writerContext =
        new WriterContext("GerritDestination", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());
    List<DestinationEffect> result =
        destination
            .newWriter(writerContext)
            .write(
                TransformResults.of(workdir, originRef)
                    .withSummary("Test message")
                    .withIdentity(originRef.asString())
                    .withLabelFinder(
                        e ->
                            e.equals("SOME_REVIEWER")
                                ? ImmutableList.of("foo@example.com")
                                : ImmutableList.of()),
                glob,
                console);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getErrors()).isEmpty();
    assertPushRef("refs/for/master%label=Foo,hashtag=copybara_id_origin_ref_commiter@email,"
        + "r=foo@example.com");
  }

  private void assertPushRef(String ref) {
    ImmutableList<Message> messages = console.getMessages();
    for (Message message : messages) {
      if (message.getText().matches(".*Pushing to .*" + ref + ".*")) {
        return;
      }
    }
    assertWithMessage(String.format("'%s' not found in:\n    %s", ref,
        Joiner.on("\n    ").join(messages)))
        .fail();
  }

  @Test
  public void testCc() throws Exception {
    pushToRefsFor = "master";
    writeFile(workdir, "file", "some content");
    fetch = "master";
    options.gerrit.gerritTopic = "testTopic";
    options.setForce(true);

    url = "https://localhost:33333/foo/bar";
    mockNoChangesFound();

    DummyRevision originRef = new DummyRevision("origin_ref");
    GerritDestination destination =
        destination("submit = False", "cc = [\"${SOME_REVIEWER}\"]");
    Glob glob = Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths);
    WriterContext writerContext =
        new WriterContext("GerritDestination", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());
    List<DestinationEffect> result =
        destination
            .newWriter(writerContext)
            .write(
                TransformResults.of(workdir, originRef)
                    .withSummary("Test message")
                    .withIdentity(originRef.asString())
                    .withLabelFinder(
                        e ->
                            e.equals("SOME_REVIEWER")
                                ? ImmutableList.of("foo@example.com")
                                : ImmutableList.of()),
                glob,
                console);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getErrors()).isEmpty();
    assertPushRef("refs/for/master%topic=testTopic,hashtag=copybara_id_origin_ref_commiter@email,"
        + "cc=foo@example.com");
  }

  @Test
  public void testDescribe() throws Exception {
    pushToRefsFor = "master";
    fetch = "master";
    options.gerrit.gerritTopic = "testTopic";
    GerritDestination destination =
        destination("submit = False", "partial_fetch = True", "notify ='ALL'");
    Glob glob = Glob.createGlob(ImmutableList.of("foo/bar", "foo/bar1", "bar/foo"),
        excludedDestinationPaths);
    assertThat(destination.describe(glob).get("fetch")).isEqualTo(ImmutableSet.of("master"));
    // %topic and %notify should not be part of describe()
    assertThat(destination.describe(glob).get("push"))
        .isEqualTo(ImmutableSet.of("refs/for/master"));
    assertThat(destination.describe(glob).get("partialFetch"))
        .isEqualTo(ImmutableSet.of("true"));
    assertThat(destination.describe(glob).get("root"))
        .isEqualTo(ImmutableSet.of("foo", "bar"));
  }

  @Test
  public void testSubmitAndDisableNotifications() {
    ValidationException expected =
        assertThrows(
            ValidationException.class, () -> destination("submit = True", "notify = 'OWNER'"));
    assertThat(expected)
        .hasMessageThat()
        .contains("Cannot set 'notify' with 'submit = True' in git.gerrit_destination()");
  }

  @Test
  public void testSubmitAndTopic() {
    ValidationException expected =
        assertThrows(
            ValidationException.class,
            () -> destination("submit = True", "topic = 'test_${CONTEXT_REFERENCE}'"));
    assertThat(expected)
        .hasMessageThat()
        .contains("Cannot set 'topic' with 'submit = True' in git.gerrit_destination()");
  }

  @Test
  public void testLabels() throws Exception {
    pushToRefsFor = "master";
    writeFile(workdir, "file", "some content");
    fetch = "master";
    options.gerrit.gerritTopic = "testTopic";
    options.setForce(true);

    url = "https://localhost:33333/foo/bar";
    mockNoChangesFound();

    DummyRevision originRef = new DummyRevision("origin_ref");
    GerritDestination destination =
        destination("submit = False", "labels = [\"${SOME_LABELS}\"]");
    Glob glob = Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths);
    WriterContext writerContext =
        new WriterContext("GerritDestination", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());
    List<DestinationEffect> result =
        destination
            .newWriter(writerContext)
            .write(
                TransformResults.of(workdir, originRef)
                    .withSummary("Test message")
                    .withIdentity(originRef.asString())
                    .withLabelFinder(
                        e ->
                            e.equals("SOME_LABELS")
                                ? ImmutableList.of("Foo+1", "Bar-1")
                                : ImmutableList.of()),
                glob,
                console);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getErrors()).isEmpty();
    assertPushRef("refs/for/master%topic=testTopic,hashtag=copybara_id_origin_ref_commiter@email,"
        + "label=Foo\\+1,label=Bar-1");
  }

  @Test
  public void testDisableNotifications() throws Exception {
    pushToRefsFor = "master";
    fetch = "master";
    options.setForce(true);

    Path workTree = Files.createTempDirectory("populate");
    GitRepository repo = repo().withWorkTree(workTree);
    writeFile(workTree, "file.txt", "some content");
    repo.add().all().run();
    repo.simpleCommand("commit", "-m", "Some commit");
    writeFile(workdir, "file.txt", "new content");

    DummyRevision originRef = new DummyRevision("origin_ref");
    WriterContext writerContext =
        new WriterContext("GerritDestination", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());
    ImmutableList<DestinationEffect> result =
        destination("submit = False", "notify = 'NONE'")
            .newWriter(writerContext)
            .write(
                TransformResults.of(workdir, originRef).withIdentity(originRef.asString()),
                Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths),
                console);
    assertThat(result).hasSize(1);

    assertThatGerritCheckout(repo(),
        "refs/for/master%notify=NONE,hashtag=copybara_id_origin_ref_commiter@email")
        .containsFile("file.txt", "new content")
        .containsNoMoreFiles();
  }

  @Test
  public void testReviewerFieldWithNoTopic() throws Exception {
    pushToRefsFor = "master";
    writeFile(workdir, "file", "some content");
    fetch = "master";
    options.setForce(true);

    url = "https://localhost:33333/foo/bar";
    mockNoChangesFound();

    DummyRevision originRef = new DummyRevision("origin_ref");
    GerritDestination destination =
        destination("submit = False", "reviewers = [\"${SOME_REVIEWER}\"]");
    Glob glob = Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths);
    WriterContext writerContext =
        new WriterContext("GerritDestination", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());
    List<DestinationEffect> result = destination
        .newWriter(writerContext)
        .write(
            TransformResults.of(workdir, originRef)
                .withSummary("Test message")
                .withIdentity(originRef.asString())
                .withLabelFinder (e -> e.equals("SOME_REVIEWER")
                    ? ImmutableList.of("foo@example.com")
                    : ImmutableList.of()),
            glob,
            console);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getErrors()).isEmpty();
    assertPushRef("refs/for/master%hashtag=copybara_id_origin_ref_commiter@email,"
        + "r=foo@example.com");
  }

  @Test
  public void testReviewersFieldWithTopic() throws Exception {
    pushToRefsFor = "master";
    writeFile(workdir, "file", "some content");
    fetch = "master";
    options.gerrit.gerritTopic = "testTopic";
    options.setForce(true);

    url = "https://localhost:33333/foo/bar";
    mockNoChangesFound();

    DummyRevision originRef = new DummyRevision("origin_ref");
    GerritDestination destination =
        destination("submit = False", "reviewers = [\"${SOME_REVIEWER}\"]");
    Glob glob = Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths);
    WriterContext writerContext =
        new WriterContext("GerritDestination", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());
    List<DestinationEffect> result = destination
        .newWriter(writerContext)
        .write(
            TransformResults.of(workdir, originRef)
                .withSummary("Test message")
                .withIdentity(originRef.asString())
                .withLabelFinder(e -> e.equals("SOME_REVIEWER")
                    ? ImmutableList.of("foo@example.com", "bar@example.com")
                    : ImmutableList.of()),
            glob,
            console);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getErrors()).isEmpty();
    assertPushRef("refs/for/master%topic=testTopic,hashtag=copybara_id_origin_ref_commiter@email,"
        + "r=foo@example.com,r=bar@example.com");
  }

  @Test
  public void changeExists() throws Exception {
    fetch = "master";

    writeFile(workdir, "file", "some content");

    options.setForce(true);
    process(new DummyRevision("origin_ref"));
    // ChangeId is randomly generated.
    assertThat(lastCommitChangeIdLine("origin_ref", repo())).isNotEmpty();
  }

  @Test
  public void testFieldWithoutValue() throws Exception {
    fetch = "master%wip";
    writeFile(workdir, "file", "some content");
    options.setForce(true);
    String secondChangeId = "I" + Hashing.sha1()
        .newHasher()
        .putString("origin_ref", StandardCharsets.UTF_8)
        .putString(options.gitDestination.committerEmail, StandardCharsets.UTF_8)
        .putInt(1)
        .hash();

    gitUtil.mockApi(
        eq("GET"),
        eq("https://localhost:33333/changes/?q="
            + "hashtag:%22copybara_id_origin_ref_commiter@email%22%20AND"
            + "%20project:foo/bar%20AND%20status:NEW"),
        mockResponse(
            String.format("[{  change_id : \"%s\",  status : \"NEW\"}]", secondChangeId)));

    process(new DummyRevision("origin_ref"));
    String master = getGerritRef(repo(), "refs/for/master");

    assertThat(master).isEqualTo(
        "refs/for/master%wip,hashtag=copybara_id_origin_ref_commiter@email");
  }

  @Test
  public void testHashTagFound() throws Exception {
    fetch = "master";
    writeFile(workdir, "file", "some content");
    options.setForce(true);
    String secondChangeId = "I" + Hashing.sha1()
        .newHasher()
        .putString("origin_ref", StandardCharsets.UTF_8)
        .putString(options.gitDestination.committerEmail, StandardCharsets.UTF_8)
        .putInt(1)
        .hash();

    gitUtil.mockApi(
        eq("GET"),
        eq("https://localhost:33333/changes/?q="
            + "hashtag:%22copybara_id_origin_ref_commiter@email%22%20AND"
            + "%20project:foo/bar%20AND%20status:NEW"),
        mockResponse(
            String.format("[{  change_id : \"%s\",  status : \"NEW\"}]", secondChangeId)));

    process(new DummyRevision("origin_ref"));
    assertThat(lastCommitChangeIdLine("origin_ref", repo()))
        .isEqualTo(GerritDestination.CHANGE_ID_LABEL + ": " + secondChangeId);
  }

  @Test
  public void testDryRunOnGetActiveChanges() throws Exception {
    fetch = "master";
    writeFile(workdir, "file", "some content");
    options.setForce(true);
    options.general.dryRunMode = true;
    gitUtil.mockApi(
        eq("GET"),
        eq("https://localhost:33333/changes/?q="
            + "hashtag:%22copybara_id_origin_ref_commiter@email%22%20AND"
            + "%20project:foo/bar%20AND%20status:NEW"),
        mockResponseWithStatus("That’s an error: Gerrit Code Review is not enabled.",
            404, ALWAYS_TRUE));

    process(new DummyRevision("origin_ref"));

    console.assertThat().logContains(MessageType.WARNING,
        ".*Failed querying the hash tag from gerrit changes. Reason.*");
  }

  @Test
  public void testTopicFlag() throws Exception {
    fetch = "master";
    options.gerrit.gerritTopic = "testTopic";

    verifyTopic(destination(), /*expectedRef*/ "refs/for/master%topic=testTopic");
  }

  @Test
  public void testTopicField() throws Exception {
    fetch = "master";

    verifyTopic(
        destination("topic = 'test_${CONTEXT_REFERENCE}'"),
        /*expectedRef*/ "refs/for/master%topic=test_1234");
  }

  @Test
  public void testTopicFlagTakesPrecedence() throws Exception {
    fetch = "master";
    options.gerrit.gerritTopic = "testTopic";

    verifyTopic(
        destination("topic = 'test_${CONTEXT_REFERENCE}'"),
        /*expectedRef*/ "refs/for/master%topic=testTopic");
  }

  @Test
  public void testEmptyReviewersField() throws Exception {
    pushToRefsFor = "master";
    writeFile(workdir, "file", "some content");
    fetch = "master";
    options.gerrit.gerritTopic = "testTopic";
    options.setForce(true);

    url = "https://localhost:33333/foo/bar";
    mockNoChangesFound();

    DummyRevision originRef = new DummyRevision("origin_ref");
    GerritDestination destination =
        destination("submit = False", "reviewers = [\"${SOME_REVIEWER}\"]");
    Glob glob = Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths);
    WriterContext writerContext =
        new WriterContext("GerritDestination", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots());
    List<DestinationEffect> result = destination
        .newWriter(writerContext)
        .write(
            TransformResults.of(workdir, originRef)
                .withSummary("Test message")
                .withIdentity(originRef.asString()),
            glob,
            console);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getErrors()).isEmpty();
    assertPushRef("refs/for/master%topic=testTopic");
  }

  @Test
  public void writesOriginTimestampToAuthorField() throws Exception {
    fetch = "master";

    writeFile(workdir, "test.txt", "some content");
    options.setForce(true);

    ZonedDateTime time1 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(355558888),
                                                  ZoneId.of("-08:00"));
    process(new DummyRevision("first_commit").withTimestamp(time1));
    GitTesting.assertAuthorTimestamp(repo(), getGerritRef(repo(), "refs/for/master"), time1);

    git("branch", "master", getGerritRef(repo(), "refs/for/master"));

    writeFile(workdir, "test2.txt", "some more content");
    options.setForce(false);
    ZonedDateTime time2 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(424242420),
                                                  ZoneId.of("-08:00"));
    process(new DummyRevision("first_commit").withTimestamp(
        time2));
    GitTesting.assertAuthorTimestamp(repo(), getGerritRef(repo(), "refs/for/master"), time2);
  }

  @Test
  public void validationErrorForMissingPullFromRef() {
    skylark.evalFails(
        "git.gerrit_destination(\n" + "    url = 'file:///foo',\n" + ")",
        "missing 1 required positional argument: fetch");
  }

  @Test
  public void validationErrorForMissingUrl() {
    skylark.evalFails(
        "git.gerrit_destination(\n" + "    fetch = 'master',\n" + ")",
        "missing 1 required positional argument: url");
  }

  @Test
  public void testProcessPushOutput_new_oldFormat() throws Exception {
    testProcesPushOutput(
        /*newReview*/ true,
        Type.CREATED, GERRIT_RESPONSE_OLD,
        "New Gerrit review created at https://some.url.google.com/1234"
    );
  }

  @Test
  public void testProcessPushOutput_existing_oldFormat() throws Exception {
    testProcesPushOutput(/*newReview*/ false,
        Type.UPDATED, GERRIT_RESPONSE_OLD,
        "Updated existing Gerrit review at https://some.url.google.com/1234"
    );
  }

  @Test
  public void testProcessPushOutput_new() throws Exception {
    testProcesPushOutput(
        /*newReview*/ true,
        Type.CREATED, GERRIT_RESPONSE,
        "New Gerrit review created at https://some.url.google.com/1234"
    );
  }

  @Test
  public void testProcessPushOutput_existing() throws Exception {
    testProcesPushOutput(/*newReview*/ false,
        Type.UPDATED, GERRIT_RESPONSE,
        "Updated existing Gerrit review at https://some.url.google.com/1234"
    );
  }

  private void testProcesPushOutput(boolean newReview, Type created, String gerritResponse,
      String expectedConsoleMessage)
      throws IOException, RepoException, ValidationException {
    GerritWriteHook process =
        new GerritWriteHook(
            options.general,
            options.gerrit,
            "http://example.com/foo",
            new Author("foo", "foo@example.com"),
            ImmutableList.of(),
            ImmutableList.of(),
            ChangeIdPolicy.REPLACE,
            /*allowEmptyDiffPatchSet=*/ true,
            /*labels*/ ImmutableList.of(),
            /*endpointChecker=*/ null,
            /*notifyOption*/ null,
            /*topicTemplate*/ null,
            /*partialFetch*/ false,
            /*gerritSubmit*/ false,
            /*primaryBranchMigrationMode*/ false);
    fakeOneCommitInDestination();

    ImmutableList<DestinationEffect> result = process.afterPush(
        gerritResponse, new GerritMessageInfo(ImmutableList.of(), newReview,
            CONSTANT_CHANGE_ID),
        repo().resolveReference("HEAD"), Changes.EMPTY.getCurrent());

    console.assertThat().onceInLog(MessageType.INFO,
        expectedConsoleMessage);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getErrors()).isEmpty();
    assertThat(result.get(0).getType()).isEqualTo(Type.CREATED);
    assertThat(result.get(0).getDestinationRef().getType()).isEqualTo("commit");
    assertThat(result.get(0).getDestinationRef().getId()).matches("[0-9a-f]{40}");

    assertThat(result.get(1).getSummary())
        .contains(expectedConsoleMessage);
    assertThat(result.get(1).getType()).isEqualTo(created);
    assertThat(result.get(1).getDestinationRef().getId()).isEqualTo("1234");
    assertThat(result.get(1).getDestinationRef().getType()).isEqualTo("gerrit_review");
    assertThat(result.get(1).getDestinationRef().getUrl())
        .isEqualTo("https://some.url.google.com/1234");
  }

  private void fakeOneCommitInDestination() throws IOException, RepoException, ValidationException {
    Path scratchTree = Files.createTempDirectory("workdir");
    GitRepository repo = repo().withWorkTree(scratchTree);
    Files.write(scratchTree.resolve("foo"), new byte[0]);
    repo.add().all().run();
    repo.commit("foo <foo@foo.com>", ZonedDateTime.now(ZoneId.systemDefault()), "not important");
  }

  @Test
  public void testPushToNonDefaultRef() throws Exception {
    fetch = "master";
    pushToRefsFor = "testPushToRef";
    writeFile(workdir, "test.txt", "some content");
    options.setForce(true);
    process(new DummyRevision("origin_ref"));

    GitRepository repo = repo();
    // Make sure commit adds new text
    String showResult = repo.simpleCommand("show",
        getGerritRef(repo, "refs/for/testPushToRef"))
        .getStdout();
    assertThat(showResult).contains("some content");
  }

  @Test
  public void testPushToNonMasterDefaultRef() throws Exception {
    fetch = "fooze";
    writeFile(workdir, "test.txt", "some content");
    options.setForce(true);
    process(new DummyRevision("origin_ref"));

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show",
        getGerritRef(repo(), "refs/for/fooze"));
    assertThat(showResult).contains("some content");
  }

  private void verifyTopic(GerritDestination destination, String expectedRef)
      throws IOException, ValidationException, RepoException {
    options.setForce(true);
    writeFile(workdir, "file", "some content");
    process(
        new DummyRevision("origin_ref").withContextReference("1234"),
        destination);
    assertPushRef(expectedRef);
  }

  @Test
  public void testPushToAutoDetectedRef() throws Exception {
    Path scratchWorkTree =
        Files.createTempDirectory("GitDestinationTest-testPushToAutoDetectedRef");
    writeFile(scratchWorkTree, "excluded.txt", "something else");
    repo().withWorkTree(scratchWorkTree)
        .add().files("excluded.txt").run();
    repo().withWorkTree(scratchWorkTree)
        .simpleCommand("commit", "-m", "message");
    writeFile(workdir, "test.txt", "some content");
    pushToRefsFor = repo().getPrimaryBranch().equals("main") ? "master" : "main";
    options.setForce(true);
    fetch = pushToRefsFor;
    primaryBranchMigration = "True";
    process(new DummyRevision("origin_ref"));

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show",
        getGerritRef(repo(), String.format("refs/for/%s.*", repo().getPrimaryBranch())));
    assertThat(showResult).contains("some content");
  }

  @Test
  public void canExcludeDestinationPathFromWorkflow() throws Exception {
    fetch = repo().getPrimaryBranch();

    Path scratchWorkTree = Files.createTempDirectory("GitDestinationTest-scratchWorkTree");
    writeFile(scratchWorkTree, "excluded.txt", "some content");
    repo().withWorkTree(scratchWorkTree)
        .add().files("excluded.txt").run();
    repo().withWorkTree(scratchWorkTree)
        .simpleCommand("commit", "-m", "message");

    writeFile(workdir, "normal_file.txt", "some more content");
    excludedDestinationPaths = ImmutableList.of("excluded.txt");
    process(new DummyRevision("ref"));
    assertThatGerritCheckout(repo(), "refs/for/" + fetch)
        .containsFile("excluded.txt", "some content")
        .containsFile("normal_file.txt", "some more content")
        .containsNoMoreFiles();
  }

  private PathSubject assertThatGerritCheckout(GitRepository repo, String ref)
      throws IOException, RepoException {
    Path tempWorkTree = Files.createTempDirectory("assertAboutCheckout");
    repo.withWorkTree(tempWorkTree).forceCheckout(getGerritRef(repo, ref));
    return assertThatPath(tempWorkTree);
  }

  @Test
  public void testNoAllowEmptyPatchSet_delete() throws Exception {
    Path workTree = Files.createTempDirectory("populate");
    GitRepository repo = repo().withWorkTree(workTree);

    writeFile(workTree, "foo/bar/baz/foo.txt", "content!");
    writeFile(workTree, "other.txt", "not important");
    repo.add().all().run();
    repo.simpleCommand("commit", "-m", "Old parent");

    GitRevision oldParent = repo.resolveReference("HEAD");

    Files.delete(workTree.resolve("foo/bar/baz/foo.txt"));
    repo.add().all().run();
    repo.simpleCommand("commit", "-m", "previous patchset");

    GitRevision currentRev = repo.resolveReference("HEAD");
    repo.simpleCommand("update-ref", "refs/changes/10/12310/1", currentRev.getSha1());
    repo.simpleCommand("reset", "--hard", "HEAD~1");

    mockChangeFound(currentRev, 12310);

    runAllowEmptyPatchSetFalse(oldParent.getSha1());
  }

  @Test
  public void testNoAllowEmptyPatchSet() throws Exception {
    // TODO(b/111567027): Add a test setting options.gitDestination.localRepoPath = some_folder.
    // Currently it doesn't work for unrelated reasons (refs/for/master is not a branch name
    // so we gene
    Path workTree = Files.createTempDirectory("populate");
    GitRepository repo = repo().withWorkTree(workTree);

    writeFile(workTree, "foo.txt", ""
        + "Base file\n"
        + "This is the original content\n"
        + "\n"
        + "This is a common part\n"
        + "That needs to be changed\n"
        + "And this remains constant\n");

    writeFile(workTree, "non_relevant.txt", "foo");

    repo.add().all().run();
    repo.simpleCommand("commit", "-m", "Old parent");
    GitRevision oldParent = repo.resolveReference("HEAD");

    String firstChange = ""
        + "Base file\n"
        + "This is the original content\n"
        + "\n"
        + "This is a common part\n"
        + "The content is changed\n"
        + "And this remains constant\n";
    writeFile(workdir, "foo.txt", firstChange);

    writeFile(workdir, "non_relevant.txt", "foo");

    mockNoChangesFound();

    runAllowEmptyPatchSetFalse(oldParent.getSha1());

    String primaryBranch = repo.getPrimaryBranch();
    assertThatGerritCheckout(repo(), "refs/for/" + primaryBranch)
        .containsFile("non_relevant.txt", "foo")
        .containsFile("foo.txt", firstChange)
        .containsNoMoreFiles();

    GitRevision currentRev = repo.resolveReference(getGerritRef(repo, "refs/for/" + primaryBranch));
    repo.simpleCommand("update-ref", "refs/changes/10/12310/1", currentRev.getSha1());
    // To avoid the non-fastforward. In real Gerrit this is not a problem
    repo.simpleCommand("update-ref", "-d", getGerritRef(repo, "refs/for/" + primaryBranch));

    repo.forceCheckout(primaryBranch);

    writeFile(workTree, "foo.txt", ""
        + "Base file modified\n"
        + "This is the modified content\n"
        + "This is the modified content\n"
        + "This is the modified content\n"
        + "\n"
        + "This is a common part\n"
        + "That needs to be changed\n"
        + "And this remains constant\n");

    writeFile(workTree, "non_relevant.txt", "bar");

    repo.add().all().run();
    repo.simpleCommand("commit", "-m", "New parent");

    GitRevision newParent = repo.resolveReference("HEAD");

    String secondChange = ""
        + "Base file modified\n"
        + "This is the modified content\n"
        + "This is the modified content\n"
        + "This is the modified content\n"
        + "\n"
        + "This is a common part\n"
        + "The content is changed\n"
        + "And this remains constant\n";

    writeFile(workdir, "foo.txt", secondChange);
    writeFile(workdir, "non_relevant.txt", "bar");

    mockChangeFound(currentRev, 12310);

    RedundantChangeException e =
        assertThrows(
            RedundantChangeException.class, () -> runAllowEmptyPatchSetFalse(newParent.getSha1()));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Skipping creating a new Gerrit PatchSet for change 12310 since the diff is the same"
                + " from the previous PatchSet");
    // No push happened
    assertThat(repo().refExists("refs/for/" + primaryBranch)).isFalse();

    String thirdChange = ""
        + "Base file modified\n"
        + "This is the modified content\n"
        + "This is the modified content\n"
        + "This is the modified content\n"
        + "\n"
        + "This is a common part--> But we are changing it now\n"
        + "The content is changed\n"
        + "And this remains constant\n";
    writeFile(workdir, "foo.txt", thirdChange);

    writeFile(workdir, "non_relevant.txt", "bar");
    runAllowEmptyPatchSetFalse(newParent.getSha1());

    assertThatGerritCheckout(repo(), "refs/for/" + primaryBranch)
        .containsFile("non_relevant.txt", "bar")
        .containsFile("foo.txt", thirdChange)
        .containsNoMoreFiles();
  }

  private void mockChangeFound(GitRevision currentRev, int changeNum) throws IOException {
    when(gitUtil
            .httpTransport()
            .buildRequest(eq("GET"), startsWith("https://localhost:33333/changes/")))
        .then(
            (Answer<LowLevelHttpRequest>)
                invocation -> {
                  String change = changeIdFromRequest((String) invocation.getArguments()[1]);
                  return mockResponse(
                      String.format(
                          "[{"
                              + "change_id : '%s',"
                              + "status : 'NEW',"
                              + "_number : '" + changeNum + "',"
                              + "current_revision = '%s'}]",
                          change, currentRev.getSha1()));
                });
  }

  private void runAllowEmptyPatchSetFalse(String baseline)
      throws ValidationException, RepoException, IOException {
    fetch = repo().getPrimaryBranch();
    DummyRevision originRef = new DummyRevision("origin_ref");
    WriterContext writerContext =
        new WriterContext("GerritDestinationTest", "test", false, originRef,
            Glob.ALL_FILES.roots());
    ImmutableList<DestinationEffect> result =
        destination("allow_empty_diff_patchset = False")
            .newWriter(writerContext)
            .write(
                TransformResults.of(workdir, originRef)
                    .withIdentity(originRef.asString())
                    .withBaseline(baseline),
                Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths),
                console);
    assertThat(result).hasSize(1);
  }
}
