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
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.copybara.ChangeMessage;
import com.google.copybara.Changes;
import com.google.copybara.DestinationEffect;
import com.google.copybara.DestinationEffect.Type;
import com.google.copybara.LabelFinder;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GerritDestination.ChangeIdPolicy;
import com.google.copybara.git.GerritDestination.GerritMessageInfo;
import com.google.copybara.git.GerritDestination.GerritWriteHook;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.git.gerritapi.GerritApiTransportImpl;
import com.google.copybara.git.testing.GitTesting;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.testing.git.GitApiMockHttpTransport;
import com.google.copybara.testing.git.GitTestUtil.TestGitOptions;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.LogConsole;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GerritDestinationTest {

  private static final String GERRIT_RESPONSE = "Counting objects: 9, done.\n"
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
  private static final String CONSTANT_CHANGE_ID = "I" + Strings.repeat("a", 40);
  private static final GitApiMockHttpTransport NO_CHANGE_FOUND_MOCK =
      new GitApiMockHttpTransport() {
        @Override
        public String getContent(String method, String url, MockLowLevelHttpRequest request) {
          // No changes found
          return "[]";
        }
      };

  private String url;
  private String fetch;
  private String pushToRefsFor;
  private Path repoGitDir;
  private Path workdir;
  private OptionsBuilder options;
  private TestingConsole console;
  private ImmutableList<String> excludedDestinationPaths;
  private SkylarkTestExecutor skylark;

  @Rule
  public ExpectedException thrown = ExpectedException.none();
  private Path urlMapper;
  private GitApiMockHttpTransport gitApiMockHttpTransport;

  @Before
  public void setup() throws Exception {
    workdir = Files.createTempDirectory("workdir");

    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console)
        .setOutputRootToTmpDir();
    urlMapper = Files.createTempDirectory("url_mapper");
    options.git = new TestGitOptions(urlMapper, options.general);
    options.gerrit = new GerritOptions(options.general, options.git) {
      @Override
      protected GerritApiTransportImpl newGerritApiTransport(URI uri) {
        return new GerritApiTransportImpl(repo(), uri, gitApiMockHttpTransport);
      }
    };
    options.gitDestination = new GitDestinationOptions(options.general, options.git);
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";
    excludedDestinationPaths = ImmutableList.of();

    repoGitDir = localGerritRepo("localhost:33333/foo/bar").getGitDir();
    url = "https://localhost:33333/foo/bar";
    repo().init();

    skylark = new SkylarkTestExecutor(options);

    gitApiMockHttpTransport =
        new GitApiMockHttpTransport() {
          @Override
          public String getContent(String method, String url, MockLowLevelHttpRequest request) {
            if (method.equals("GET") && url.startsWith("https://localhost:33333/changes/")) {
              return "["
                  + "{"
                  + "  change_id : \""
                  + changeIdFromRequest(url)
                  + "\","
                  + "  status : \"NEW\""
                  + "}]";
            }
            throw new IllegalArgumentException(method + " " + url);
          }
        };
  }

  private String changeIdFromRequest(String url) {
    return url.replaceAll(".*(I[a-z0-9]{40}).*", "$1");
  }

  private GitRepository repo() {
    return GitRepository.newBareRepo(repoGitDir, getGitEnv(),  /*verbose=*/true);
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
        + (lines.length == 0 ? "" : "    " + Joiner.on(",\n    ").join(lines) + ",\n")
        + "    " + (pushToRefsFor == null ? "" : "push_to_refs_for = '" + pushToRefsFor + "',")
        + ")");
  }

  private static String lastCommitChangeIdLine(String ref, GitRepository repo) throws Exception {
    return lastCommitChangeIdLineForRef("refs/for/master", ref, repo);
  }

  private static String lastCommitChangeIdLineForRef(String gitRef, String originRef,
      GitRepository repo) throws RepoException {
    GitLogEntry log = Iterables.getOnlyElement(repo.log(gitRef).withLimit(1).run());
    assertThat(log.getBody()).contains("\n" + DummyOrigin.LABEL_NAME + ": " + originRef + "\n");
    String line = null;
    for (LabelFinder label : ChangeMessage.parseMessage(log.getBody()).getLabels()) {
      if (label.isLabel(GerritDestination.CHANGE_ID_LABEL)) {
        assertThat(label.getValue()).matches("I[0-9a-f]{40}$");
        assertThat(line).isNull(); // Multiple Change-Ids are not allowed.
        line = label.getLine();
      }
    }
    assertWithMessage(
        "Cannot find " + GerritDestination.CHANGE_ID_LABEL + " in:\n" + log.getBody())
        .that(line).isNotNull();

    return line;
  }

  private void process(DummyRevision originRef)
      throws ValidationException, RepoException, IOException {
    ImmutableList<DestinationEffect> result = destination()
        .newWriter(Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths),
            /*dryRun=*/false, /*groupId=*/null, /*oldWriter=*/null)
        .write(
            TransformResults.of(workdir, originRef).withIdentity(originRef.asString()),
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

    Files.write(workdir.resolve("file"), "some content".getBytes());

    options.setForce(true);
    process(new DummyRevision("origin_ref"));

    String firstChangeIdLine = lastCommitChangeIdLine("origin_ref", repo());

    Files.write(workdir.resolve("file2"), "some more content".getBytes());
    git("branch", "master", "refs/for/master");
    options.setForce(false);
    process(new DummyRevision("origin_ref2"));

    assertThat(firstChangeIdLine)
        .isNotEqualTo(lastCommitChangeIdLine("origin_ref2", repo()));
  }

  @Test
  public void specifyChangeId() throws Exception {
    fetch = "master";

    Files.write(workdir.resolve("file"), "some content".getBytes());

    String changeId = "Iaaaaaaaaaabbbbbbbbbbccccccccccdddddddddd";
    options.setForce(true);
    options.gerrit.gerritChangeId = changeId;
    process(new DummyRevision("origin_ref"));
    assertThat(lastCommitChangeIdLine("origin_ref", repo()))
        .isEqualTo(GerritDestination.CHANGE_ID_LABEL + ": " + changeId);

    git("branch", "master", "refs/for/master");

    Files.write(workdir.resolve("file"), "some different content".getBytes());

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
    assertThat(lastCommitChangeIdLineForRef("refs/for/foo", "origin_ref", repo()))
        .isEqualTo(expectedChangeId);
  }

  @Test
  public void testDefaultRefForPushFetchFlag() throws Exception {
    fetch = "bar";
    options.gitDestination.fetch = "foo";
    pushToRefsFor = null;

    String expectedChangeId = runForDefaults();
    assertThat(lastCommitChangeIdLineForRef("refs/for/foo", "origin_ref", repo()))
        .isEqualTo(expectedChangeId);
  }

  @Test
  public void testRefForPushFetchFlag() throws Exception {
    fetch = "bar";
    options.gitDestination.fetch = "foo";
    pushToRefsFor = "baz";

    String expectedChangeId = runForDefaults();
    assertThat(lastCommitChangeIdLineForRef("refs/for/baz", "origin_ref", repo()))
        .isEqualTo(expectedChangeId);
  }

  private String runForDefaults() throws IOException, ValidationException, RepoException {
    fakeOneCommitInDestination();
    git("branch", "foo");
    Files.write(workdir.resolve("file"), "some content".getBytes());

    String changeId = "Iaaaaaaaaaabbbbbbbbbbccccccccccdddddddddd";
    options.gerrit.gerritChangeId = changeId;
    process(new DummyRevision("origin_ref"));
    return GerritDestination.CHANGE_ID_LABEL + ": " + changeId;
  }

  @Test
  public void reuseChangeId() throws Exception {
    fetch = "master";

    Files.write(workdir.resolve("file"), "some content".getBytes());

    options.setForce(true);
    options.gerrit.gerritChangeId = null;

    url = "https://localhost:33333/foo/bar";
    GitRepository repo = localGerritRepo("localhost:33333/foo/bar");
    gitApiMockHttpTransport = NO_CHANGE_FOUND_MOCK;

    process(new DummyRevision("origin_ref"));
    String changeId = lastCommitChangeIdLine("origin_ref", repo);
    assertThat(changeId).matches(GerritDestination.CHANGE_ID_LABEL + ": I[a-z0-9]+");
    LabelFinder labelFinder = new LabelFinder(changeId);

    Files.write(workdir.resolve("file"), "some different content".getBytes());

    gitApiMockHttpTransport =
        new GitApiMockHttpTransport() {
          @Override
          public String getContent(String method, String url, MockLowLevelHttpRequest request) {
            String expected =
                "https://localhost:33333/changes/?q=change:%20"
                    + labelFinder.getValue()
                    + "%20AND%20project:foo/bar";
            if (method.equals("GET") && url.equals(expected)) {
              return "["
                  + "{"
                  + "  change_id : \""
                  + labelFinder.getValue()
                  + "\","
                  + "  status : \"NEW\""
                  + "}]";
            }
            throw new IllegalArgumentException(method + " " + url);
          }
        };
    // Allow to push again in a non-fastforward way.
    repo.simpleCommand("update-ref", "-d", "refs/for/master");
    process(new DummyRevision("origin_ref"));
    assertThat(lastCommitChangeIdLine("origin_ref", repo)).isEqualTo(changeId);
    GitTesting.assertThatCheckout(repo, "refs/for/master")
        .containsFile("file", "some different content")
        .containsNoMoreFiles();
  }

  @Test
  public void testChangeIdPolicyRequire() throws Exception {
    options.gerrit.gerritChangeId = null;
    options.gitDestination.nonFastForwardPush = true;
    String changeId = runChangeIdPolicy("Test message\n\nChange-Id: " + CONSTANT_CHANGE_ID + "\n",
        "change_id_policy = 'REQUIRE'");
    assertThat(changeId).isEqualTo(CONSTANT_CHANGE_ID);
    try {
      runChangeIdPolicy("Test message", "change_id_policy = 'REQUIRE'");
      fail();
    } catch (ValidationException e) {
      assertThat(e).hasMessageThat().contains("label not found in message");
    }
  }

  @Test
  public void testChangeIdPolicyFailIfPresent() throws Exception {
    options.gerrit.gerritChangeId = null;
    options.gitDestination.nonFastForwardPush = true;
    String changeId = runChangeIdPolicy("Test message", "change_id_policy = 'FAIL_IF_PRESENT'");
    assertThat(changeId).isNotNull();
    try {
      runChangeIdPolicy("Test message\n\nChange-Id: " + CONSTANT_CHANGE_ID + "\n",
          "change_id_policy = 'FAIL_IF_PRESENT'");
      fail();
    } catch (ValidationException e) {
      assertThat(e).hasMessageThat().contains("label found in message");
    }
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
    try {
      runChangeIdPolicy("Test message\n\nChange-Id: " + CONSTANT_CHANGE_ID + "\n");
      fail();
    } catch (ValidationException e) {
      assertThat(e).hasMessageThat().contains("label found in message");
    }
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

    Files.write(workdir.resolve("file"), "some content".getBytes());

    options.setForce(true);

    url = "https://localhost:33333/foo/bar";
    GitRepository repo = localGerritRepo("localhost:33333/foo/bar");
    gitApiMockHttpTransport = NO_CHANGE_FOUND_MOCK;

    DummyRevision originRef = new DummyRevision("origin_ref");
    List<DestinationEffect> result = destination(config)
        .newWriter(Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths),
            /*dryRun=*/false, /*groupId=*/null, /*oldWriter=*/null)
        .write(
            TransformResults.of(workdir, originRef)
                .withSummary(summary)
                .withIdentity(originRef.asString()),
            console);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getErrors()).isEmpty();

    return lastCommitChangeIdLine("origin_ref", repo).replace("Change-Id: ", "").trim();
  }

  public GitRepository localGerritRepo(String url) throws RepoException {
    return GitRepository
        .newBareRepo(urlMapper.resolve(url), getGitEnv(), options.general.isVerbose())
        .init();
  }

  @Test
  public void testGerritSubmit() throws Exception {
    options.gerrit.gerritChangeId = null;
    fetch = "master";
    pushToRefsFor = "master";
    Files.write(workdir.resolve("file"), "some content".getBytes());

    options.setForce(true);

    url = "https://localhost:33333/foo/bar";
    GitRepository repo = localGerritRepo("localhost:33333/foo/bar");
    gitApiMockHttpTransport = NO_CHANGE_FOUND_MOCK;

    DummyRevision originRef = new DummyRevision("origin_ref");
    GerritDestination destination = destination("submit = True");
    Glob glob = Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths);
    List<DestinationEffect> result = destination
        .newWriter(glob,/*dryRun=*/false, /*groupId=*/null, /*oldWriter=*/null)
        .write(
            TransformResults.of(workdir, originRef)
                .withSummary("Test message")
                .withIdentity(originRef.asString()),
            console);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getErrors()).isEmpty();

    String changeId = lastCommitChangeIdLineForRef("master", "origin_ref", repo)
        .replace("Change-Id: ", "").trim();
    
    assertThat(changeId).isNotNull();
    assertThat(destination.getType()).isEqualTo("git.destination");
    assertThat(destination.describe(glob).get("fetch")).isEqualTo(ImmutableSet.of("master"));
    assertThat(destination.describe(glob).get("push")).isEqualTo(ImmutableSet.of("master"));
  }

  @Test
  public void changeExists() throws Exception {
    fetch = "master";

    Files.write(workdir.resolve("file"), "some content".getBytes());

    options.setForce(true);
    String expectedChangeId = "I" + Hashing.sha1()
        .newHasher()
        .putString("origin_ref", StandardCharsets.UTF_8)
        .putString(options.gitDestination.committerEmail, StandardCharsets.UTF_8)
        .putInt(0)
        .hash();
    process(new DummyRevision("origin_ref"));
    assertThat(lastCommitChangeIdLine("origin_ref", repo()))
        .isEqualTo(GerritDestination.CHANGE_ID_LABEL + ": " + expectedChangeId);
  }

  @Test
  public void changeAlreadyMergedOnce() throws Exception {
    fetch = "master";
    Files.write(workdir.resolve("file"), "some content".getBytes());
    options.setForce(true);
    String firstChangeId = "I" + Hashing.sha1()
        .newHasher()
        .putString("origin_ref", StandardCharsets.UTF_8)
        .putString(options.gitDestination.committerEmail, StandardCharsets.UTF_8)
        .putInt(0)
        .hash();
    String secondChangeId = "I" + Hashing.sha1()
        .newHasher()
        .putString("origin_ref", StandardCharsets.UTF_8)
        .putString(options.gitDestination.committerEmail, StandardCharsets.UTF_8)
        .putInt(1)
        .hash();

    gitApiMockHttpTransport =
        new GitApiMockHttpTransport() {
          @Override
          public String getContent(String method, String url, MockLowLevelHttpRequest request) {
            if (method.equals("GET") && url.startsWith("https://localhost:33333/changes/")) {
              String change = changeIdFromRequest(url);
              return "["
                  + "{"
                  + "  change_id : \""
                  + change
                  + "\","
                  + "  status : \""
                  + (change.equals(firstChangeId) ? "MERGED" : "NEW")
                  + "\""
                  + "}]";
            }
            throw new IllegalArgumentException(method + " " + url);
          }
        };

    process(new DummyRevision("origin_ref"));
    assertThat(lastCommitChangeIdLine("origin_ref", repo()))
        .isEqualTo(GerritDestination.CHANGE_ID_LABEL + ": " + secondChangeId);
  }

  @Test
  public void changeAlreadyMergedTooOften() throws Exception {
    fetch = "master";
    Files.write(workdir.resolve("file"), "some content".getBytes());
    options.setForce(true);
    String firstChangeId = "I" + Hashing.sha1()
        .newHasher()
        .putString("origin_ref", StandardCharsets.UTF_8)
        .putString(options.gitDestination.committerEmail, StandardCharsets.UTF_8)
        .putInt(0)
        .hash();
    String secondChangeId = "I" + Hashing.sha1()
        .newHasher()
        .putString("origin_ref", Charsets.UTF_8)
        .putString(options.gitDestination.committerEmail, StandardCharsets.UTF_8)
        .putInt(1)
        .hash();
    gitApiMockHttpTransport =
        new GitApiMockHttpTransport() {
          @Override
          public String getContent(String method, String url, MockLowLevelHttpRequest request) {
            if (method.equals("GET") && url.startsWith("https://localhost:33333/changes/")) {
              String change = changeIdFromRequest(url);
              return "["
                  + "{"
                  + "  change_id : \""
                  + change
                  + "\","
                  + "  status : \"MERGED\""
                  + "}]";
            }
            throw new IllegalArgumentException(method + " " + url);
          }
        };

    try {
      process(new DummyRevision("origin_ref"));
      fail("Should have thrown RepoException");
    } catch (RepoException expected) {
      assertThat(expected.getMessage()).contains("Unable to find unmerged change for ");
    }
  }

  @Test
  public void specifyTopic() throws Exception {
    fetch = "master";
    options.setForce(true);
    Files.write(workdir.resolve("file"), "some content".getBytes());
    options.gerrit.gerritTopic = "testTopic";
    process(new DummyRevision("origin_ref"));
    boolean correctMessage =
        console
            .getMessages()
            .stream()
            .anyMatch(message -> message.getText().contains("refs/for/master%topic=testTopic"));
    assertThat(correctMessage).isTrue();
  }

  @Test
  public void writesOriginTimestampToAuthorField() throws Exception {
    fetch = "master";

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    options.setForce(true);

    ZonedDateTime time1 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(355558888),
                                                  ZoneId.of("-08:00"));
    process(new DummyRevision("first_commit").withTimestamp(time1));
    GitTesting.assertAuthorTimestamp(repo(), "refs/for/master", time1);

    git("branch", "master", "refs/for/master");

    Files.write(workdir.resolve("test2.txt"), "some more content".getBytes());
    options.setForce(false);
    ZonedDateTime time2 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(424242420),
                                                  ZoneId.of("-08:00"));
    process(new DummyRevision("first_commit").withTimestamp(
        time2));
    GitTesting.assertAuthorTimestamp(repo(), "refs/for/master",
                                     time2);
  }

  @Test
  public void validationErrorForMissingPullFromRef() throws Exception {
    skylark.evalFails(
        "git.gerrit_destination(\n"
            + "    url = 'file:///foo',\n"
            + ")",
        "parameter 'fetch' has no default value");
  }

  @Test
  public void validationErrorForMissingUrl() throws Exception {
    skylark.evalFails(
        "git.gerrit_destination(\n"
            + "    fetch = 'master',\n"
            + ")",
        "parameter 'url' has no default value");
  }

  @Test
  public void testProcessPushOutput_new() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    GerritWriteHook process =
        new GerritWriteHook(options.gerrit, "http://example.com/foo",
            new Author("foo", "foo@example.com"),
            LogConsole.readWriteConsole(System.in, new PrintStream(out), /*verbose=*/ false),
            ChangeIdPolicy.REPLACE, /*allowEmptyPatchSet=*/true);
    fakeOneCommitInDestination();
    
    ImmutableList<DestinationEffect> result = process.afterPush(
        GERRIT_RESPONSE, new GerritMessageInfo(ImmutableList.of(), /*newReview=*/true),
        repo().resolveReference("HEAD"), Changes.EMPTY.getCurrent());

    assertThat(out.toString())
        .contains("INFO: New Gerrit review created at https://some.url.google.com/1234");
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getErrors()).isEmpty();
    assertThat(result.get(0).getType()).isEqualTo(Type.CREATED);
    assertThat(result.get(0).getDestinationRef().getType()).isEqualTo("commit");
    assertThat(result.get(0).getDestinationRef().getId()).matches("[0-9a-f]{40}");

    assertThat(result.get(1).getSummary())
        .contains("New Gerrit review created at https://some.url.google.com/1234");
    assertThat(result.get(1).getType()).isEqualTo(Type.CREATED);
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
  public void testProcessPushOutput_existing() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    GerritWriteHook process =
        new GerritWriteHook(options.gerrit, "http://example.com/foo",
            new Author("foo", "foo@example.com"),
            LogConsole.readWriteConsole(System.in, new PrintStream(out), /*verbose=*/ false),
            ChangeIdPolicy.REPLACE, /*allowEmptyPatchSet=*/true);
    fakeOneCommitInDestination();

    ImmutableList<DestinationEffect> result = process.afterPush(
        GERRIT_RESPONSE, new GerritMessageInfo(ImmutableList.of(), /*newReview=*/ false),
        repo().resolveReference("HEAD"), Changes.EMPTY.getCurrent());


    assertThat(out.toString())
        .contains("INFO: Updated existing Gerrit review at https://some.url.google.com/1234");

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getErrors()).isEmpty();
    assertThat(result.get(0).getType()).isEqualTo(Type.CREATED);
    assertThat(result.get(0).getDestinationRef().getType()).isEqualTo("commit");
    assertThat(result.get(0).getDestinationRef().getId()).matches("[0-9a-f]{40}");

    assertThat(result.get(1).getSummary())
        .contains("Updated existing Gerrit review at https://some.url.google.com/1234");
    assertThat(result.get(1).getType()).isEqualTo(Type.UPDATED);
    assertThat(result.get(1).getDestinationRef().getId()).isEqualTo("1234");
    assertThat(result.get(1).getDestinationRef().getType()).isEqualTo("gerrit_review");
    assertThat(result.get(1).getDestinationRef().getUrl())
        .isEqualTo("https://some.url.google.com/1234");
  }

  @Test
  public void testPushToNonDefaultRef() throws Exception {
    fetch = "master";
    pushToRefsFor = "testPushToRef";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    options.setForce(true);
    process(new DummyRevision("origin_ref"));

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "refs/for/testPushToRef");
    assertThat(showResult).contains("some content");
  }

  @Test
  public void testPushToNonMasterDefaultRef() throws Exception {
    fetch = "fooze";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    options.setForce(true);
    process(new DummyRevision("origin_ref"));

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "refs/for/fooze");
    assertThat(showResult).contains("some content");
  }

  @Test
  public void canExcludeDestinationPathFromWorkflow() throws Exception {
    fetch = "master";

    Path scratchWorkTree = Files.createTempDirectory("GitDestinationTest-scratchWorkTree");
    Files.write(scratchWorkTree.resolve("excluded.txt"), "some content".getBytes(UTF_8));
    repo().withWorkTree(scratchWorkTree)
        .add().files("excluded.txt").run();
    repo().withWorkTree(scratchWorkTree)
        .simpleCommand("commit", "-m", "message");

    Files.write(workdir.resolve("normal_file.txt"), "some more content".getBytes(UTF_8));
    excludedDestinationPaths = ImmutableList.of("excluded.txt");
    process(new DummyRevision("ref"));
    GitTesting.assertThatCheckout(repo(), "refs/for/master")
        .containsFile("excluded.txt", "some content")
        .containsFile("normal_file.txt", "some more content")
        .containsNoMoreFiles();
  }
}
