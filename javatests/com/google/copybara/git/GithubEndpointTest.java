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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.Core;
import com.google.copybara.config.Config;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.config.SkylarkParser;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.feedback.Feedback;
import com.google.copybara.git.github.api.GithubApi;
import com.google.copybara.testing.DummyEndpoint;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.OptionsBuilder.GitApiMockHttpTransport;
import com.google.copybara.testing.OptionsBuilder.GitApiMockHttpTransport.RequestRecord;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TestingModule;
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
public class GithubEndpointTest {

  private static final ImmutableSet<Class<?>> MODULES =
      ImmutableSet.of(Core.class, TestingModule.class, GitModule.class);
  private static final String PROJECT = "google/example";

  private SkylarkTestExecutor skylarkTestExecutor;
  private SkylarkParser skylarkParser;
  private TestingConsole console;
  private OptionsBuilder options;
  private DummyEndpoint dummyEndpoint;
  private Path workdir;
  private GitApiMockHttpTransport gitApiMockHttpTransport;

  @Before
  public void setup() throws Exception {
    workdir = Files.createTempDirectory("workdir");
    Files.createDirectories(workdir);
    console = new TestingConsole();
    options = new OptionsBuilder();
    options.setConsole(console)
        .setOutputRootToTmpDir();
    dummyEndpoint = new DummyEndpoint();
    options.testingOptions.feedbackEndpoint = dummyEndpoint;
    options.github = new GithubOptions(() -> options.general, options.git) {
      @Override
      public GithubApi getApi(String project) throws RepoException {
        assertThat(project).isEqualTo(PROJECT);
        return super.getApi(project);
      }

      @Override
      protected HttpTransport getHttpTransport() {
        return gitApiMockHttpTransport;
      }
    };

    Path credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, "https://user:SECRET@github.com".getBytes(UTF_8));
    options.git.credentialHelperStorePath = credentialsFile.toString();

    skylarkTestExecutor =
        new SkylarkTestExecutor(options, MODULES.toArray(new Class<?>[MODULES.size()]));
    skylarkParser = new SkylarkParser(MODULES);
  }

  @Test
  public void testParsing() throws Exception {
    GitHubEndPoint gitHubEndPoint =
        skylarkTestExecutor.eval(
            "e",
            "e = git.github_api(url = 'https://github.com/google/example'))");
    assertThat(gitHubEndPoint.describe())
        .containsExactly("type", "github_api", "url", "https://github.com/google/example");
  }

  @Test
  public void testParsingEmptyUrl() {
    try {
      skylarkTestExecutor.eval("e", "e = git.github_api(url = '')))");
      fail();
    } catch (ValidationException ignored) {
    }
  }

  @Test
  public void testFeedbackCallsGithubEndpoint() throws Exception{
    gitApiMockHttpTransport = new GitApiMockHttpTransport() {
      @Override
      protected byte[] getContent(String method, String url, MockLowLevelHttpRequest request) {
        if (url.contains("/status")) {
          return ("{\n"
              + "    state : 'success',\n"
              + "    context : 'the_context'\n"
              + "}"
          ).getBytes(UTF_8);
        }
        throw new RuntimeException("Unexpected url: " + url);
      }
    };

    dummyEndpoint.addAll("Foo", "Bar");
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
                + "\n");
    feedback.run(workdir, /*sourceRef*/ "e597746de9c1704e648ddc3ffa0d2096b146d600");
    console.assertThat().timesInLog(2, MessageType.INFO, "Created status");

    List<RequestRecord> requests = gitApiMockHttpTransport.requests;
    assertThat(requests).hasSize(2);
    assertThat(requests.get(0).getRequest()).contains("Observed Foo");
    assertThat(requests.get(1).getRequest()).contains("Observed Bar");
  }

  private Feedback feedback(String actionFunction) throws IOException, ValidationException {
    String config =
        actionFunction
            + "\n"
            + "core.feedback(\n"
            + "    name = 'default',\n"
            + "    origin = testing.feedback_endpoint(),\n"
            + "    destination = git.github_api(\n"
            + "      url = 'https://github.com/google/example',\n"
            + "    ),\n"
            + "    actions = [test_action,],\n"
            + ")\n"
            + "\n";
    System.err.println(config);
    return (Feedback) loadConfig(config).getMigration("default");
  }

  private Config loadConfig(String content) throws IOException, ValidationException {
    return skylarkParser.loadConfig(
        new MapConfigFile(
            ImmutableMap.of("copy.bara.sky", content.getBytes(UTF_8)), "copy.bara.sky"),
        options.build(),
        options.general.console());
  }
}
