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
import static com.google.copybara.testing.git.GitTestUtil.ALWAYS_TRUE;
import static com.google.copybara.testing.git.GitTestUtil.mockGitHubNotFound;
import static com.google.copybara.testing.git.GitTestUtil.mockGitHubUnauthorized;
import static com.google.copybara.testing.git.GitTestUtil.mockResponse;
import static com.google.copybara.testing.git.GitTestUtil.mockResponseAndValidateRequest;
import static com.google.copybara.testing.git.GitTestUtil.mockResponseWithStatus;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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
import com.google.copybara.testing.git.GitTestUtil.MockRequestAssertion;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.starlark.java.eval.Starlark;
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

    gitUtil.mockApi(eq("POST"), contains("/statuses/e59774"),
        mockResponse("{\n"
            + "    state : 'success',\n"
            + "    target_url : 'https://github.com/google/example',\n"
            + "    description : 'Observed foo',\n"
            + "    context : 'test'\n"
            + "}"));

    gitUtil.mockApi(
        anyString(),
        contains("/git/refs/heads/test"),
        mockResponse(
            "{\n"
                + "    ref : 'refs/heads/test',\n"
                + "    url : 'https://github.com/google/example/git/refs/heads/test',\n"
                + "    object : { \n"
                + "       type : 'commit',\n"
                + "       sha : 'e597746de9c1704e648ddc3ffa0d2096b146d600', \n"
                + "       url :"
                + " 'https://github.com/google/example/git/commits/e597746de9c1704e648ddc3ffa0d2096b146d600'\n"
                + "   } \n"
                + "}"));

    gitUtil.mockApi(
        eq("GET"),
        contains("git/refs?per_page=100"),
        mockResponse(
            "[{\n"
                + "    ref : 'refs/heads/test',\n"
                + "    url : 'https://github.com/google/example/git/refs/heads/test',\n"
                + "    object : { \n"
                + "       type : 'commit',\n"
                + "       sha : 'e597746de9c1704e648ddc3ffa0d2096b146d600', \n"
                + "       url :"
                + " 'https://github.com/google/example/git/commits/e597746de9c1704e648ddc3ffa0d2096b146d600'\n"
                + "   } \n"
                + "}]"));

    gitUtil.mockApi(
        eq("GET"),
        contains("commits/e597746de9c1704e648ddc3ffa0d2096b146d610/check-runs"),
        mockResponse(
            "{\n"
                + "  total_count: 1,\n"
                + "  check_runs: [\n"
                + "    {\n"
                + "      id: 4,\n"
                + "      details_url: 'https://example.com',\n"
                + "      status: 'completed',\n"
                + "      conclusion: 'neutral',\n"
                + "      name: 'mighty_readme',\n"
                + "      output: {\n"
                + "        title: 'Mighty Readme report',\n"
                + "        summary: 'test_summary',\n"
                + "        text: 'test_text'\n"
                + "      },\n"
                + "      app: {\n"
                + "        id: 1,\n"
                + "        slug: 'octoapp',\n"
                + "        name: 'Octocat App'\n"
                + "      }\n"
                + "    }\n"
                + "  ]\n"
                + "}"
        ));

    Path credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, "https://user:SECRET@github.com".getBytes(UTF_8));
    options.git.credentialHelperStorePath = credentialsFile.toString();

    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void testParsing() throws Exception {
    skylark.eval(
        "e",
        "e = git.github_api(url = 'https://github.com/google/example')");
  }

  @Test
  public void testParsingWithChecker() throws Exception {
    skylark.eval(
        "e",
        "e = git.github_api(\n"
            + "url = 'https://github.com/google/example', \n"
            + "checker = testing.dummy_checker(),\n"
            + ")\n");
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
    assertThat(feedback.getDestinationDescription().get("url"))
        .containsExactly("https://github.com/google/example");
    ValidationException expected =
        assertThrows(
            ValidationException.class, () -> feedback.run(workdir, ImmutableList.of("12345")));
    assertThat(expected)
        .hasMessageThat()
        .contains("Bad word 'badword' found: field 'path'.");
  }

  @Test
  public void testParsingEmptyUrl() {
    skylark.evalFails("git.github_api(url = '')", "Invalid empty field 'url'");
  }

  @Test
  public void testOriginRef() throws Exception {
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.new_origin_ref('12345')")
        .addAll(checkFieldStarLark("res", "ref", "'12345'"))
        .build());
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
            + "}",
            new MockRequestAssertion(String.format(
                "Requests were expected to cycle through the values of %s", createValues),
                r -> r.contains(createValues.next()))));

    feedback.run(workdir, ImmutableList.of("e597746de9c1704e648ddc3ffa0d2096b146d600"));
    console.assertThat().timesInLog(2, MessageType.INFO, "Created status");

    verify(gitUtil.httpTransport(), times(2)).buildRequest(eq("POST"), contains("/status"));
  }

  @Test
  public void testCreateStatusExhaustive() throws Exception {
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.create_status(sha = 'e597746de9c1704e648ddc3ffa0d2096b146d600',"
            + " state = 'success', context = 'test', description = 'Observed foo')")
        .addAll(checkFieldStarLark("res", "state", "'success'"))
        .addAll(checkFieldStarLark("res", "target_url", "'https://github.com/google/example'"))
        .addAll(checkFieldStarLark("res", "description", "'Observed foo'"))
        .addAll(checkFieldStarLark("res", "context", "'test'"))
        .build());
  }


  @Test
  public void testCreateStatusLimitReached() throws Exception {
    gitUtil.mockApi(eq("POST"), contains("/statuses/c59774"),
       mockResponseWithStatus(
        "{\n"
            + "\"message\" : \"This SHA and context has reached the maximum number of statuses\",\n"
            + "\"documentation_url\" : \"https://developer.github.com/v3\"\n"
            + "}", 422, ALWAYS_TRUE));
    ValidationException expected =
        assertThrows(ValidationException.class, () -> runFeedback(ImmutableList.<String>builder()
        .add("ctx.destination.create_status("
            + "sha = 'c597746de9c1704e648ddc3ffa0d2096b146d600',"
            + " state = 'success', context = 'test', description = 'Observed foo')").build()));
    assertThat(expected).hasMessageThat()
        .contains("This SHA and context has reached the maximum number of statuses");
  }

  @Test
  public void testGetCombinedStatus() throws Exception {
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.get_combined_status(ref = 'master')")
        .addAll(checkFieldStarLark("res", "state", "'failure'"))
        .addAll(checkFieldStarLark("res", "total_count", "2"))
        .addAll(checkFieldStarLark("res", "statuses[0].context", "'some/context'"))
        .addAll(checkFieldStarLark("res", "statuses[0].state", "'failure'"))
        .addAll(checkFieldStarLark("res", "statuses[1].context", "'other/context'"))
        .addAll(checkFieldStarLark("res", "statuses[1].state", "'success'"))
        .build());
  }

  @Test
  public void testGetCheckRuns() throws Exception {
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.get_check_runs(sha='e597746de9c1704e648ddc3ffa0d2096b146d610')[0]")
        .addAll(checkFieldStarLark("res", "detail_url", "'https://example.com'"))
        .addAll(checkFieldStarLark("res", "status", "'completed'"))
        .addAll(checkFieldStarLark("res", "conclusion", "'neutral'"))
        .addAll(checkFieldStarLark("res", "name", "'mighty_readme'"))
        .addAll(checkFieldStarLark("res", "app.id", "1"))
        .addAll(checkFieldStarLark("res", "app.slug", "'octoapp'"))
        .addAll(checkFieldStarLark("res", "app.name", "'Octocat App'"))
        .addAll(checkFieldStarLark("res", "output.title", "'Mighty Readme report'"))
        .addAll(checkFieldStarLark("res", "output.summary", "'test_summary'"))
        .addAll(checkFieldStarLark("res", "output.text", "'test_text'"))
        .build());
  }

  @Test
  public void testGetCombinedStatus_notFound() throws Exception {
    gitUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/repos/google/example/commits/heads/not_found/"
            + "status?per_page=100"),
        mockGitHubNotFound());
    runFeedback(ImmutableList.<String>builder()
        .add("res = {}")
        .add("res['foo'] = ctx.destination.get_combined_status(ref = 'heads/not_found')")
        .addAll(checkFieldStarLark("res", "get('foo')", "None"))
        .build());
  }

  @Test
  public void testGetPullRequestComment() throws Exception {
    gitUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/repos/google/example/pulls/comments/12345"),
        mockResponse(toJson(jsonComment())));
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.get_pull_request_comment(comment_id = '12345')")
        .addAll(checkFieldStarLark("res", "id", "'12345'"))
        .addAll(checkFieldStarLark("res", "path", "'foo/Bar.java'"))
        .addAll(checkFieldStarLark("res", "body", "'This needs to be fixed.'"))
        .addAll(checkFieldStarLark("res", "diff_hunk", "'@@ -36,11 +35,16 @@ foo bar'"))
        .build());
  }

  private static ImmutableMap<String, ? extends Serializable> jsonComment() {
    return ImmutableMap.of(
        "id", 12345,
        "path", "foo/Bar.java",
        "body", "This needs to be fixed.",
        "diff_hunk", "@@ -36,11 +35,16 @@ foo bar");
  }

  @Test
  public void testGetPullRequestComment_notFound() {
    gitUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/repos/google/example/pulls/comments/12345"),
        mockGitHubNotFound());
    ValidationException expected = assertThrows(ValidationException.class, () -> runFeedback(
        ImmutableList.of("ctx.destination.get_pull_request_comment(comment_id = '12345')")));
    assertThat(expected).hasMessageThat().contains("Pull Request Comment not found");
  }

  @Test
  public void testGetPullRequestComment_invalidId() {
    ValidationException expected = assertThrows(ValidationException.class, () -> runFeedback(
        ImmutableList.of("ctx.destination.get_pull_request_comment(comment_id = 'foo')")));
    assertThat(expected).hasMessageThat().contains("Invalid comment id foo");
  }

  @Test
  public void testGetPullRequestComments() throws Exception {
    gitUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/repos/google/example/pulls/12345/comments?per_page=100"),
        mockResponse(toJson(ImmutableList.of(jsonComment(), jsonComment()))));
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.get_pull_request_comments(number = 12345)")
        .addAll(checkFieldStarLark("res[0]", "id", "'12345'"))
        .addAll(checkFieldStarLark("res[0]", "path", "'foo/Bar.java'"))
        .addAll(checkFieldStarLark("res[0]", "body", "'This needs to be fixed.'"))
        .addAll(checkFieldStarLark("res[0]", "diff_hunk", "'@@ -36,11 +35,16 @@ foo bar'"))
        .addAll(checkFieldStarLark("res[1]", "id", "'12345'"))
        .build());
  }

  @Test
  public void testGetPullRequestComments_notFound() {
    gitUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/repos/google/example/pulls/12345/comments?per_page=100"),
        mockGitHubNotFound());
    ValidationException expected = assertThrows(ValidationException.class, () ->
        runFeedback(ImmutableList.of("ctx.destination.get_pull_request_comments(number = 12345)")));
    assertThat(expected).hasMessageThat().contains("Pull Request Comments not found");
  }

  @Test
  public void testGetCommit() throws Exception {
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.get_commit(ref = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')")
        .addAll(checkFieldStarLark("res", "sha", "'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'"))
        .addAll(checkFieldStarLark("res", "commit.author.name", "'theauthor'"))
        .addAll(checkFieldStarLark("res", "commit.author.email", "'author@example.com'"))
        .addAll(checkFieldStarLark("res", "commit.committer.name", "'thecommitter'"))
        .addAll(checkFieldStarLark("res", "commit.committer.email", "'committer@example.com'"))
        .addAll(checkFieldStarLark("res", "commit.message",
            "'This is a message\\n\\nWith body\\n'"))
        .addAll(checkFieldStarLark("res", "author.login", "'github_author'"))
        .addAll(checkFieldStarLark("res", "committer.login", "'github_committer'"))
        .build());
  }

  @Test
  public void testGetCommitNotFound() throws Exception {
    gitUtil.mockApi(eq("GET"), eq("https://api.github.com/repos/google/example/commits/"
            + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
        mockGitHubNotFound());
    runFeedback(ImmutableList.<String>builder()
        .add("res = {}")
        .add("res['foo'] = ctx.destination.get_commit("
            + "ref = 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb')")
        .addAll(checkFieldStarLark("res", "get('foo')", "None"))
        .build());
  }

  @Test
  public void testGetReferenceNotFound() throws Exception {
    gitUtil.mockApi(eq("GET"),
        eq("https://api.github.com/repos/google/example/git/refs/heads/not_found"),
        mockGitHubNotFound());
    runFeedback(ImmutableList.<String>builder()
        .add("res = {}")
        .add("res['foo'] = ctx.destination.get_reference(ref = 'refs/heads/not_found')")
        .addAll(checkFieldStarLark("res", "get('foo')", "None"))
        .build());
  }

  /**
   * A test that uses update_reference.
   *
   */
  @Test
  public void testFeedbackUpdateReference() throws Exception{
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.update_reference('e597746de9c1704e648ddc3ffa0d2096b146d600',"
            + "'refs/heads/test', True)")
        .addAll(checkFieldStarLark("res", "ref", "'refs/heads/test'"))
        .addAll(checkFieldStarLark("res", "url",
            "'https://github.com/google/example/git/refs/heads/test'"))
        .addAll(checkFieldStarLark("res", "sha", "'e597746de9c1704e648ddc3ffa0d2096b146d600'"))
        .build());
  }

  @Test
  public void testFeedbackUpdateReferenceShortRef() throws Exception{
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.update_reference('e597746de9c1704e648ddc3ffa0d2096b146d600',"
            + " 'test', True)")
        .addAll(checkFieldStarLark("res", "ref", "'refs/heads/test'"))
        .addAll(checkFieldStarLark("res", "url",
            "'https://github.com/google/example/git/refs/heads/test'"))
        .addAll(checkFieldStarLark("res", "sha", "'e597746de9c1704e648ddc3ffa0d2096b146d600'"))
        .build());
  }

  @Test
  public void testFeedbackDeleteReference() throws Exception{
    AtomicBoolean called = new AtomicBoolean(false);
    gitUtil.mockApi(eq("DELETE"), contains("/git/refs/heads/test"),
        mockResponseWithStatus("", 202,
            new MockRequestAssertion("Always true with side-effect",
            s -> {
              called.set(true);
              return true;
        })));
    runFeedback(ImmutableList.of("ctx.destination.delete_reference('refs/heads/test')"));
    assertThat(called.get()).isTrue();
  }

  @Test
  public void testFeedbackDeleteReference_masterCheck() {
    AtomicBoolean called = new AtomicBoolean(false);
    gitUtil.mockApi(eq("DELETE"), contains("/git/refs/heads/master"),
        mockResponseWithStatus("", 202,
            new MockRequestAssertion("Always true with side-effect",
            s -> {
              called.set(true);
              return true;
            })));
    ValidationException expected = assertThrows(ValidationException.class, () ->
        runFeedback(ImmutableList.of("ctx.destination.delete_reference('refs/heads/master')")));
    assertThat(expected).hasMessageThat().contains("Copybara doesn't allow to delete master");
    assertThat(called.get()).isFalse();
  }

  /**
   * A test that uses get_reference.
   *
   */
  @Test
  public void testGetReference() throws Exception{
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.get_reference('refs/heads/test')")
        .addAll(checkFieldStarLark("res", "ref", "'refs/heads/test'"))
        .addAll(checkFieldStarLark("res", "url",
            "'https://github.com/google/example/git/refs/heads/test'"))
        .addAll(checkFieldStarLark("res", "sha", "'e597746de9c1704e648ddc3ffa0d2096b146d600'"))
        .build());
  }

  /**
   * A test that uses get_references.
   *
   */
  @Test
  public void testGetReferences() throws Exception{
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.get_references()")
        .addAll(checkFieldStarLark("res[0]", "ref", "'refs/heads/test'"))
        .addAll(checkFieldStarLark("res[0]", "url",
            "'https://github.com/google/example/git/refs/heads/test'"))
        .addAll(checkFieldStarLark("res[0]", "sha", "'e597746de9c1704e648ddc3ffa0d2096b146d600'"))
        .build());
  }

  /**
   * A test that uses get_pull_requests.
   */
  @Test
  public void testPullRequests() throws Exception {
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
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.get_pull_requests(state='OPEN')")
        .addAll(checkFieldStarLark("res[0]", "number", "12345"))
        .addAll(checkFieldStarLark("res[0]", "state", "'OPEN'"))
        .addAll(checkFieldStarLark("res[0]", "head.label", "'someuser:somebranch'"))
        .addAll(checkFieldStarLark("res[0]", "head.sha", "'" + Strings.repeat("a", 40) + "'"))
        .addAll(checkFieldStarLark("res[0]", "head.ref", "'somebranch'"))
        .build());
  }

  @Test
  public void testUpdatePullRequest() throws Exception {
    gitUtil.mockApi(eq("POST"), contains("repos/google/example/pulls/12345"),
        mockResponseAndValidateRequest(toJson(
            ImmutableMap.of(
                "number", 12345,
                "state", "closed",
                "head", ImmutableMap.of(
                    "label", "someuser:somebranch",
                    "sha", Strings.repeat("a", 40),
                    "ref", "somebranch"
                ))), MockRequestAssertion.contains("{\"state\":\"closed\"}")));
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.update_pull_request(12345, state='CLOSED')")
            .addAll(checkFieldStarLark("res", "number", "12345"))
            .addAll(checkFieldStarLark("res", "state", "'CLOSED'"))
            .addAll(checkFieldStarLark("res", "head.label", "'someuser:somebranch'"))
            .addAll(checkFieldStarLark("res", "head.sha", "'" + Strings.repeat("a", 40) + "'"))
            .addAll(checkFieldStarLark("res", "head.ref", "'somebranch'"))
        .build());
  }

  @Test
  public void testGetAuthenticatedUser() throws Exception {
    gitUtil.mockApi(eq("GET"), contains("user"),
        mockResponse(toJson(ImmutableMap.of("login", "tester"))));
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.get_authenticated_user()")
        .addAll(checkFieldStarLark("res", "login", "'tester'"))
        .build());
  }

  @Test
  public void testGetAuthenticatedUser_not_authorized() throws Exception {
    gitUtil.mockApi(eq("GET"), contains("user"), mockGitHubUnauthorized());
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.get_authenticated_user()")
        .add("if res:\n"
            + "    fail('Should return none')")
        .build());
  }

  /**
   * A test that uses get_pull_requests.
   */
  @Test
  public void testPullRequests_badPrefix() throws Exception {
    ValidationException expected = assertThrows(ValidationException.class, () ->
        runFeedback(ImmutableList.of("ctx.destination.get_pull_requests(head_prefix = 'bad@*')")));
    assertThat(expected).hasMessageThat().contains("'bad@*' is not a valid head_prefix");
  }

  @Test
  public void testAddlabel() throws Exception {
    gitUtil.mockApi(
        eq("POST"),
        contains("12345/labels"),
        mockResponse(
        "[\n"
            + "  {\n"
            + "    \"id\": 123456,\n"
            + "    \"node_id\": \"BASE64=\",\n"
            + "    \"url\": \"https://api.github.com/repos/google/example/labels/run_kokoro\",\n"
            + "    \"name\": \"run_kokoro\",\n"
            + "    \"description\": \"Run me!\",\n"
            + "    \"color\": \"ffffff\",\n"
            + "    \"default\": true\n"
            + "  }"
            + "]"
        ));
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.add_label(number = 12345, labels = ['run_kokoro'])")
        .build());
    verify(gitUtil.httpTransport())
        .buildRequest(eq("POST"), contains("google/example/issues/12345/labels"));
  }

  @Test
  public void testPostComment() throws Exception {
    gitUtil.mockApi(eq("POST"), contains("12345/comments"), mockResponse("{}"));
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.post_issue_comment(number = 12345,"
            + " comment = 'This is a comment')")
        .build());
    verify(gitUtil.httpTransport())
        .buildRequest(eq("POST"), contains("google/example/issues/12345/comments"));
  }

  @Test
  public void testCreateIssue() throws Exception {
    gitUtil.mockApi(eq("POST"), contains("/issues"),    mockResponse(toJson(
        ImmutableMap.of(
            "number", 123456,
            "title", "This is an issue"
        ))));
    runFeedback(ImmutableList.<String>builder()
        .add("res = ctx.destination.create_issue("
            + "title='This is an issue', body='body', assignees=['foo'])")
        .addAll(checkFieldStarLark("res", "number", "123456"))
        .addAll(checkFieldStarLark("res", "title", "'This is an issue'"))
        .build());
    verify(gitUtil.httpTransport())
        .buildRequest(eq("POST"), contains("google/example/issues"));
  }

  @Test
  public void testListComments() throws Exception {
    gitUtil.mockApi(
        eq("GET"),
        contains("/comments"),
        mockResponse(toJson(ImmutableList.of(ImmutableMap.of("id", 1, "body", "Me too")))));
    runFeedback(
        ImmutableList.<String>builder()
            .add("res = ctx.destination.list_issue_comments(number = 12345)[0]")
            .addAll(checkFieldStarLark("res", "id", "1"))
            .addAll(checkFieldStarLark("res", "body", "'Me too'"))
            .build());
    verify(gitUtil.httpTransport()).buildRequest(eq("GET"), contains("issues/12345/comments"));
  }

  private String toJson(Object obj) throws IOException {
    return GsonFactory.getDefaultInstance().toPrettyString(obj);
  }

  // var, field, and value are all Starlark expressions.
  private static ImmutableList<String> checkFieldStarLark(String var, String field, String value) {
    return ImmutableList.of(
        String.format("if %s.%s != %s:", var, field, value),
        String.format(
            "  fail('unexpected value for '+%1$s+'.'+%2$s+' (expected '+%3$s+'): ' + %4$s.%5$s)",
            Starlark.repr(var), // string literal
            Starlark.repr(field), // string literal
            Starlark.repr(value), // string literal
            var, // expression
            field)); // expression
  }

  private void runFeedback(ImmutableList<String> funBody) throws Exception {
    Feedback test = feedback("def test_action(ctx):\n"
        + funBody.stream().map(s -> "  " + s).collect(Collectors.joining("\n"))
        + "\n  return ctx.success()\n");
    test.run(workdir, ImmutableList.of("e597746de9c1704e648ddc3ffa0d2096b146d600"));
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
