/*
 * Copyright (C) 2018 Google Inc.
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
import static com.google.copybara.testing.git.GitTestUtil.mockGitHubNotFound;
import static com.google.copybara.testing.git.GitTestUtil.mockResponse;
import static com.google.copybara.testing.git.GitTestUtil.mockResponseAndValidateRequest;
import static com.google.copybara.testing.git.GitTestUtil.mockResponseWithStatus;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.api.client.json.gson.GsonFactory;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.feedback.Feedback;
import com.google.copybara.testing.DummyChecker;
import com.google.copybara.testing.DummyTrigger;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.devtools.build.lib.syntax.Runtime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitHubEndpointTest {

  private SkylarkTestExecutor skylark;
  private TestingConsole console;
  private DummyTrigger dummyTrigger;
  private Path workdir;
  private GitTestUtil gitUtil;

  @Before
  public void setup() throws Exception {
    workdir = Jimfs.newFileSystem().getPath("/");
    console = new TestingConsole();
    OptionsBuilder options = new OptionsBuilder();
    options.setConsole(console)
        .setOutputRootToTmpDir();
    gitUtil = new GitTestUtil(options);
    gitUtil.mockRemoteGitRepos();

    dummyTrigger = new DummyTrigger();
    options.testingOptions.feedbackTrigger = dummyTrigger;
    options.testingOptions.checker = new DummyChecker(ImmutableSet.of("badword"));

    gitUtil.mockApi(eq("GET"), contains("master/status"),
        mockResponse("{\n"
            + "    state : 'failure',\n"
            + "    total_count : 2,\n"
            + "    sha : 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',\n"
            + "    statuses : [\n"
            + "       { state : 'failure', context: 'some/context'},\n"
            + "       { state : 'success', context: 'other/context'}\n"
            + "    ]\n"
            + "}"));

    gitUtil.mockApi(eq("GET"), contains("/commits/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
        mockResponse("{\n"
            + "    sha : 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',\n"
            + "    commit : {\n"
            + "       author: { name : 'theauthor', email: 'author@example.com'},\n"
            + "       committer: { name : 'thecommitter', email: 'committer@example.com'},\n"
            + "       message: \"This is a message\\n\\nWith body\\n\"\n"
            + "    },\n"
            + "    committer : { login : 'github_committer'},\n"
            + "    author : { login : 'github_author'}\n"
            + "}"));

    gitUtil.mockApi(eq("POST"), contains("/status"),
        mockResponse("{\n"
            + "    state : 'success',\n"
            + "    target_url : 'https://github.com/google/example',\n"
            + "    description : 'Observed foo',\n"
            + "    context : 'test'\n"
            + "}"));

    gitUtil.mockApi(anyString(), contains("/git/refs/heads/test"),
        mockResponse("{\n"
            + "    ref : 'refs/heads/test',\n"
            + "    url : 'https://github.com/google/example/git/refs/heads/test',\n"
            + "    object : { \n"
            + "       type : 'commit',\n"
            + "       sha : 'e597746de9c1704e648ddc3ffa0d2096b146d600', \n"
            + "       url : 'https://github.com/google/example/git/commits/e597746de9c1704e648ddc3ffa0d2096b146d600'\n"
            + "   } \n"
            + "}"));

    gitUtil.mockApi(eq("GET"), contains("git/refs?per_page=100"),
        mockResponse("[{\n"
            + "    ref : 'refs/heads/test',\n"
            + "    url : 'https://github.com/google/example/git/refs/heads/test',\n"
            + "    object : { \n"
            + "       type : 'commit',\n"
            + "       sha : 'e597746de9c1704e648ddc3ffa0d2096b146d600', \n"
            + "       url : 'https://github.com/google/example/git/commits/e597746de9c1704e648ddc3ffa0d2096b146d600'\n"
            + "   } \n"
            + "}]"));

    Path credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, "https://user:SECRET@github.com".getBytes(UTF_8));
    options.git.credentialHelperStorePath = credentialsFile.toString();

    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void testParsing() throws Exception {
    GitHubEndPoint gitHubEndPoint =
        skylark.eval(
            "e",
            "e = git.github_api(url = 'https://github.com/google/example')");
    assertThat(gitHubEndPoint.describe())
        .containsExactly("type", "github_api", "url", "https://github.com/google/example");

    skylark.verifyField(
        "git.github_api(url = 'https://github.com/google/example')",
        "url", "https://github.com/google/example");
  }

  @Test
  public void testParsingWithChecker() throws Exception {
    GitHubEndPoint gitHubEndpoint =
        skylark.eval(
            "e",
            "e = git.github_api(\n"
                + "url = 'https://github.com/google/example', \n"
                + "checker = testing.dummy_checker(),\n"
                + ")\n");
    assertThat(gitHubEndpoint.describe())
        .containsExactly("type", "github_api", "url", "https://github.com/google/example");

    skylark.verifyField(
        "git.github_api(url = 'https://github.com/google/example')",
        "url", "https://github.com/google/example");
  }

  @Test
  public void testCheckerIsHonored() throws Exception {
    String config =
        ""
            + "def test_action(ctx):\n"
            + "  ctx.destination.update_reference(\n"
            + "      'e597746de9c1704e648ddc3ffa0d2096b146d600', 'foo_badword_bar', True)\n"
            + "  return ctx.success()\n"
            + "\n"
            + "core.feedback(\n"
            + "    name = 'default',\n"
            + "    origin = testing.dummy_trigger(),\n"
            + "    destination = git.github_api("
            + "        url = 'https://github.com/google/example',\n"
            + "        checker = testing.dummy_checker(),\n"
            + "    ),\n"
            + "    actions = [test_action,],\n"
            + ")\n"
            + "\n";
    Feedback feedback = (Feedback) skylark.loadConfig(config).getMigration("default");
    try {
      feedback.run(workdir, ImmutableList.of("12345"));
      fail();
    } catch (ValidationException expected) {
      assertThat(expected).hasMessageThat()
          .contains("Bad word 'badword' found: field 'path'. Location: copy.bara.sky:2:3");
    }
  }

  @Test
  public void testParsingEmptyUrl() {
    skylark.evalFails("git.github_api(url = '')", "Invalid empty field 'url'");
  }

  @Test
  public void testOriginRef() throws ValidationException {
    String var =
        "git.github_api(url = 'https://github.com/google/example').new_origin_ref('12345')";
    ImmutableMap<String, Object> expectedFieldValues =
        ImmutableMap.<String, Object>builder()
            .put("ref", "12345")
            .build();
    skylark.verifyFields(var, expectedFieldValues);
  }

  /**
   * A test that uses feedback.
   *
   * <p>Does not verify all the fields, see {@link #testCreateStatusExhaustive()} for that.
   */
  @Test
  public void testFeedbackCreateStatus() throws Exception{
    dummyTrigger.addAll("Foo", "Bar");
    Feedback feedback =
        feedback(
            ""
                + "def test_action(ctx):\n"
                + "    ref = 'None'\n"
                + "    if len(ctx.refs) > 0:\n"
                + "      ref = ctx.refs[0]\n"
                + "    \n"
                + "    for m in ctx.origin.get_messages:\n"
                + "      status = ctx.destination.create_status(\n"
                + "        sha = ref,\n"
                + "        state = 'success',\n"
                + "        context = 'test',\n"
                + "        description = 'Observed ' + m,\n"
                + "      )\n"
                + "      ctx.console.info('Created status')\n"
                + "    return ctx.success()\n"
                + "\n");
    Iterator<String> createValues = ImmutableList.of("Observed Foo", "Observed Bar").iterator();
    gitUtil.mockApi(eq("POST"), contains("/status"),
        mockResponseAndValidateRequest("{\n"
            + "    state : 'success',\n"
            + "    target_url : 'https://github.com/google/example',\n"
            + "    description : 'Observed foo',\n"
            + "    context : 'test'\n"
            + "}", content -> content.contains(createValues.next())));

    feedback.run(workdir, ImmutableList.of("e597746de9c1704e648ddc3ffa0d2096b146d600"));
    console.assertThat().timesInLog(2, MessageType.INFO, "Created status");

    verify(gitUtil.httpTransport(), times(2)).buildRequest(eq("POST"), contains("/status"));
  }

  @Test
  public void testCreateStatusExhaustive() throws Exception {
    String var =
        ""
            + "git.github_api(url = 'https://github.com/google/example')"
            + "  .create_status("
            + "    sha = 'e597746de9c1704e648ddc3ffa0d2096b146d600', "
            + "    state = 'success', "
            + "    context = 'test', "
            + "    description = 'Observed foo'"
            + "  )";
    ImmutableMap<String, Object> expectedFieldValues =
        ImmutableMap.<String, Object>builder()
            .put("state", "success")
            .put("target_url", "https://github.com/google/example")
            .put("description", "Observed foo")
            .put("context", "test")
            .build();
    skylark.verifyFields(var, expectedFieldValues);
  }

  @Test
  public void testGetCombinedStatus() throws Exception {
    String var = ""
            + "git.github_api(url = 'https://github.com/google/example')"
            + "  .get_combined_status(ref = 'master')";
    ImmutableMap<String, Object> expectedFieldValues =
        ImmutableMap.<String, Object>builder()
            .put("state", "failure")
            .put("total_count", 2)
            .put("statuses[0].context", "some/context")
            .put("statuses[0].state", "failure")
            .put("statuses[1].context", "other/context")
            .put("statuses[1].state", "success")
            .build();
    skylark.verifyFields(var, expectedFieldValues);
  }

  @Test
  public void testGetCommit() throws Exception {
    String var = ""
        + "git.github_api(url = 'https://github.com/google/example')"
        + "  .get_commit(ref = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')";
    ImmutableMap<String, Object> expectedFieldValues =
        ImmutableMap.<String, Object>builder()
            .put("sha", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            .put("commit.author.name", "theauthor")
            .put("commit.author.email", "author@example.com")
            .put("commit.committer.name", "thecommitter")
            .put("commit.committer.email", "committer@example.com")
            .put("commit.message", "This is a message\n\nWith body\n")
            .put("author.login", "github_author")
            .put("committer.login", "github_committer")
            .build();
    skylark.verifyFields(var, expectedFieldValues);
  }

  @Test
  public void testGetCommitNotFound() throws Exception {
    String var = ""
        + "git.github_api(url = 'https://github.com/google/example')"
        + "  .get_commit(ref = 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb')";
    gitUtil.mockApi(eq("GET"), eq("https://api.github.com/repos/google/example/commits/"
            + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
        mockGitHubNotFound());

    skylark.verifyObject(var, Runtime.NONE);
  }

  @Test
  public void testGetReferenceNotFound() throws Exception {
    String var = ""
        + "git.github_api(url = 'https://github.com/google/example')"
        + "  .get_reference(ref = 'refs/heads/not_found')";
    gitUtil.mockApi(eq("GET"),
        eq("https://api.github.com/repos/google/example/git/refs/heads/not_found"),
        mockGitHubNotFound());

    skylark.verifyObject(var, Runtime.NONE);
  }

  @Test
  public void test() throws Exception {
    String var = ""
        + "git.github_api(url = 'https://github.com/google/example')"
        + "  .get_combined_status(ref = 'heads/not_found')";
    gitUtil.mockApi(eq("GET"),
        eq("https://api.github.com/repos/google/example/commits/heads/not_found/status"),
        mockGitHubNotFound());

    skylark.verifyObject(var, Runtime.NONE);
  }

  /**
   * A test that uses update_reference.
   *
   */
  @Test
  public void testFeedbackUpdateReference() throws Exception{
    String var =
        "git.github_api(url = 'https://github.com/google/example')"
            + ".update_reference('e597746de9c1704e648ddc3ffa0d2096b146d600', "
            + "'refs/heads/test', True)";
    ImmutableMap<String, Object> expectedFieldValues =
        ImmutableMap.<String, Object>builder()
            .put("ref", "refs/heads/test")
            .put("url", "https://github.com/google/example/git/refs/heads/test")
            .put("sha", "e597746de9c1704e648ddc3ffa0d2096b146d600")
            .build();
    skylark.verifyFields(var, expectedFieldValues);
  }

  @Test
  public void testFeedbackUpdateReferenceShortRef() throws Exception{
    String var =
        "git.github_api(url = 'https://github.com/google/example')"
            + ".update_reference('e597746de9c1704e648ddc3ffa0d2096b146d600', "
            + "'test', True)";
    ImmutableMap<String, Object> expectedFieldValues =
        ImmutableMap.<String, Object>builder()
            .put("ref", "refs/heads/test")
            .put("url", "https://github.com/google/example/git/refs/heads/test")
            .put("sha", "e597746de9c1704e648ddc3ffa0d2096b146d600")
            .build();
    skylark.verifyFields(var, expectedFieldValues);
  }

  @Test
  public void testFeedbackDeleteReference() throws Exception{
    AtomicBoolean called = new AtomicBoolean(false);
    gitUtil.mockApi(eq("DELETE"), contains("/git/refs/heads/test"),
        mockResponseWithStatus("", 202, s -> {
          called.set(true);
          return true;
        }));

    skylark.eval("not_used", "not_used = git.github_api(url = 'https://github.com/google/example')"
        + ".delete_reference('refs/heads/test')");

    assertThat(called.get()).isTrue();
  }

  @Test
  public void testFeedbackDeleteReference_MasterCheck() {
    AtomicBoolean called = new AtomicBoolean(false);
    gitUtil.mockApi(eq("DELETE"), contains("/git/refs/heads/master"),
        mockResponseWithStatus("", 202, s -> {
          called.set(true);
          return true;
        }));

    skylark.evalFails("git.github_api(url = 'https://github.com/google/example')"
        + ".delete_reference('refs/heads/master')",
        "Copybara doesn't allow to delete master branch for security reasons");

    assertThat(called.get()).isFalse();
  }

  /**
   * A test that uses get_reference.
   *
   */
  @Test
  public void testGetReference() throws Exception{
    String var =
        "git.github_api(url = 'https://github.com/google/example')"
            + ".get_reference('refs/heads/test')";
    ImmutableMap<String, Object> expectedFieldValues =
        ImmutableMap.<String, Object>builder()
            .put("ref", "refs/heads/test")
            .put("url", "https://github.com/google/example/git/refs/heads/test")
            .put("sha", "e597746de9c1704e648ddc3ffa0d2096b146d600")
            .build();
    skylark.verifyFields(var, expectedFieldValues);
  }

  /**
   * A test that uses get_references.
   *
   */
  @Test
  public void testGetReferences() throws Exception{
    String var =
        "git.github_api(url = 'https://github.com/google/example')"
            + ".get_references()";
    ImmutableMap<String, Object> expectedFieldValues =
        ImmutableMap.<String, Object>builder()
            .put("ref", "refs/heads/test")
            .put("url", "https://github.com/google/example/git/refs/heads/test")
            .put("sha", "e597746de9c1704e648ddc3ffa0d2096b146d600")
            .build();
    skylark.verifyFields(var + "[0]", expectedFieldValues);
  }

  /**
   * A test that uses get_pull_requests.
   */
  @Test
  public void testPullRequests() throws Exception {
    String var =
        "git.github_api(url = 'https://github.com/google/example')"
            + ".get_pull_requests(state='OPEN')";
    gitUtil.mockApi(anyString(), contains(
        "repos/google/example/pulls?per_page=100&state=open&sort=created&direction=asc"),
        mockResponse(toJson(
            ImmutableList.of(
                ImmutableMap.of(
                    "number", 12345,
                    "state", "open",
                    "head", ImmutableMap.of(
                        "label", "someuser:somebranch",
                        "sha", Strings.repeat("a", 40),
                        "ref", "somebranch"
                    ))))));

    skylark.verifyFields(var + "[0]", ImmutableMap.<String, Object>builder()
        .put("number", 12345)
        .put("state", "OPEN")
        .put("head.label", "someuser:somebranch")
        .put("head.sha", Strings.repeat("a", 40))
        .put("head.ref", "somebranch")
        .build());
  }

  @Test
  public void testUpdatePullRequest() throws Exception {
    String var =
        "git.github_api(url = 'https://github.com/google/example')"
            + ".update_pull_request(12345, state='CLOSED')";
    gitUtil.mockApi(eq("POST"), contains("repos/google/example/pulls/12345"),
        mockResponseAndValidateRequest(toJson(
            ImmutableMap.of(
                "number", 12345,
                "state", "closed",
                "head", ImmutableMap.of(
                    "label", "someuser:somebranch",
                    "sha", Strings.repeat("a", 40),
                    "ref", "somebranch"
                ))), s -> s.contains("{\"state\":\"closed\"}")));

    skylark.verifyFields(var, ImmutableMap.<String, Object>builder()
        .put("number", 12345)
        .put("state", "CLOSED")
        .put("head.label", "someuser:somebranch")
        .put("head.sha", Strings.repeat("a", 40))
        .put("head.ref", "somebranch")
        .build());
  }

  @Test
  public void testGetAuthenticatedUser() throws Exception {
    String var =
        "git.github_api(url = 'https://github.com/google/example')"
            + ".get_authenticated_user()";
    gitUtil.mockApi(eq("GET"), contains("user"),
        mockResponse(toJson(ImmutableMap.of("login", "tester"))));
    skylark.verifyFields(var, ImmutableMap.<String, Object>builder()
        .put("login", "tester")
        .build());
  }

  /**
   * A test that uses get_pull_requests.
   */
  @Test
  public void testPullRequests_badPrefix() throws Exception {
    skylark.evalFails("git.github_api(url = 'https://github.com/google/example')"
        + ".get_pull_requests(head_prefix = 'bad@*')",
        "'bad@\\*' is not a valid head_prefix");
  }

  private String toJson(Object obj) throws IOException {
    return GsonFactory.getDefaultInstance().toPrettyString(obj);
  }

  private Feedback feedback(String actionFunction) throws IOException, ValidationException {
    String config =
        actionFunction
            + "\n"
            + "core.feedback(\n"
            + "    name = 'default',\n"
            + "    origin = testing.dummy_trigger(),\n"
            + "    destination = git.github_api(\n"
            + "      url = 'https://github.com/google/example',\n"
            + "    ),\n"
            + "    actions = [test_action,],\n"
            + ")\n"
            + "\n";
    System.err.println(config);
    return (Feedback) skylark.loadConfig(config).getMigration("default");
  }
}
