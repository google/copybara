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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.feedback.Feedback;
import com.google.copybara.git.github.api.GitHubApi;
import com.google.copybara.testing.DummyChecker;
import com.google.copybara.testing.DummyTrigger;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.git.GitApiMockHttpTransport;
import com.google.copybara.testing.git.GitApiMockHttpTransport.RequestRecord;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitHubEndpointTest {

  private static final String PROJECT = "google/example";

  private SkylarkTestExecutor skylark;
  private TestingConsole console;
  private DummyTrigger dummyTrigger;
  private Path workdir;
  private GitApiMockHttpTransport gitApiMockHttpTransport;

  @Before
  public void setup() throws Exception {
    workdir = Jimfs.newFileSystem().getPath("/");
    console = new TestingConsole();
    OptionsBuilder options = new OptionsBuilder();
    options.setConsole(console)
        .setOutputRootToTmpDir();
    dummyTrigger = new DummyTrigger();
    options.testingOptions.feedbackTrigger = dummyTrigger;
    options.testingOptions.checker = new DummyChecker(ImmutableSet.of("badword"));
    gitApiMockHttpTransport =
        new GitApiMockHttpTransport() {
          @Override
          public String getContent(String method, String url, MockLowLevelHttpRequest request) {
            if (url.contains("/status")) {
              return ("{\n"
                  + "    state : 'success',\n"
                  + "    target_url : 'https://github.com/google/example',\n"
                  + "    description : 'Observed foo',\n"
                  + "    context : 'test'\n"
                  + "}");
            }
            if (url.contains("/git/refs/heads")) {
              return ("{\n"
                  + "    ref : 'refs/heads/test',\n"
                  + "    url : 'https://github.com/google/example/git/refs/heads/test',\n"
                  + "    object : { \n"
                  + "       type : 'commit',\n"
                  + "       sha : 'e597746de9c1704e648ddc3ffa0d2096b146d600', \n"
                  + "       url : 'https://github.com/google/example/git/commits/e597746de9c1704e648ddc3ffa0d2096b146d600'\n"
                  + "   } \n"
                  + "}");
            }
            if (url.contains("git/refs?per_page=100")) {
              return ("[{\n"
                  + "    ref : 'refs/heads/test',\n"
                  + "    url : 'https://github.com/google/example/git/refs/heads/test',\n"
                  + "    object : { \n"
                  + "       type : 'commit',\n"
                  + "       sha : 'e597746de9c1704e648ddc3ffa0d2096b146d600', \n"
                  + "       url : 'https://github.com/google/example/git/commits/e597746de9c1704e648ddc3ffa0d2096b146d600'\n"
                  + "   } \n"
                  + "}]");
            }
            throw new RuntimeException("Unexpected url: " + url);
          }
        };

    options.github = new GitHubOptions(options.general, options.git) {
      @Override
      public GitHubApi newGitHubApi(String project) throws RepoException {
        assertThat(project).isEqualTo(PROJECT);
        return super.newGitHubApi(project);
      }

      @Override
      protected HttpTransport newHttpTransport() {
        return gitApiMockHttpTransport;
      }
    };

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
          .contains("Bad word found!. Location: copy.bara.sky:2:3");
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
                + "    if ctx.ref:\n"
                + "      ref = ctx.ref\n"
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
    feedback.run(workdir, ImmutableList.of("e597746de9c1704e648ddc3ffa0d2096b146d600"));
    console.assertThat().timesInLog(2, MessageType.INFO, "Created status");

    List<RequestRecord> requests = gitApiMockHttpTransport.requests;
    assertThat(requests).hasSize(2);
    assertThat(requests.get(0).getRequest()).contains("Observed Foo");
    assertThat(requests.get(1).getRequest()).contains("Observed Bar");
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

  /**
   * A test that uses update_reference.
   *
   */
  @Test
  public void testFeedbackUpdateReference() throws Exception{
    String var =
        "git.github_api(url = 'https://github.com/google/example')"
            + ".update_reference('e597746de9c1704e648ddc3ffa0d2096b146d600', 'test', True)";
    ImmutableMap<String, Object> expectedFieldValues =
        ImmutableMap.<String, Object>builder()
            .put("ref", "refs/heads/test")
            .put("url", "https://github.com/google/example/git/refs/heads/test")
            .put("sha", "e597746de9c1704e648ddc3ffa0d2096b146d600")
            .build();
    skylark.verifyFields(var, expectedFieldValues);
  }

  /**
   * A test that uses get_reference.
   *
   */
  @Test
  public void testGetReference() throws Exception{
    String var =
        "git.github_api(url = 'https://github.com/google/example')"
            + ".get_reference('heads/test')";
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
