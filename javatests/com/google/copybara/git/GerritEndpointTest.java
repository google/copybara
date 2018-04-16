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
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.Core;
import com.google.copybara.config.Config;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.config.SkylarkParser;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.feedback.Feedback;
import com.google.copybara.git.gerritapi.GerritApiTransport;
import com.google.copybara.git.gerritapi.GerritApiTransportImpl;
import com.google.copybara.testing.DummyTrigger;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.OptionsBuilder.GitApiMockHttpTransport;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TestingModule;
import com.google.copybara.testing.git.GitTestUtil.TestGitOptions;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GerritEndpointTest {

  private static final ImmutableSet<Class<?>> MODULES =
      ImmutableSet.of(Core.class, TestingModule.class, GitModule.class);

  private SkylarkTestExecutor skylarkTestExecutor;
  private SkylarkParser skylarkParser;
  private TestingConsole console;
  private OptionsBuilder options;
  private Path workdir;
  private DummyTrigger dummyTrigger;
  private Path urlMapper;
  private String url;
  private  GitApiMockHttpTransport gitApiMockHttpTransport;
  private Path repoGitDir;

  @Before
  public void setup() throws Exception {
    repoGitDir = Files.createTempDirectory("GerritEndpointTest-repoGitDir");
    workdir = Files.createTempDirectory("workdir");
    Files.createDirectories(workdir);
    console = new TestingConsole();
    options = new OptionsBuilder();
    options.setConsole(console)
        .setOutputRootToTmpDir();
    dummyTrigger = new DummyTrigger();
    options.testingOptions.feedbackTrigger = dummyTrigger;
    urlMapper = Files.createTempDirectory("url_mapper");
    options.git = new TestGitOptions(urlMapper, () -> options.general);
    url = "https://localhost:33333/foo/bar";
    gitApiMockHttpTransport = new TestingGitApiHttpTransport();
    options.gerrit =
        new GerritOptions(() -> options.general, options.git) {
          protected GerritApiTransport getGerritApiTransport(URI uri) {
            return new GerritApiTransportImpl(repo(), uri, gitApiMockHttpTransport);
          }
        };

    skylarkTestExecutor =
        new SkylarkTestExecutor(options, MODULES.toArray(new Class<?>[MODULES.size()]));
    skylarkParser = new SkylarkParser(MODULES);
  }

  private String changeNumberFromRequest(String url) {
    return url.replaceAll(".*changes/([0-9]{1,10}).*", "$1");
  }

  private GitRepository repo() {
    return GitRepository.newBareRepo(repoGitDir, getGitEnv(),  /*verbose=*/true);
  }

  @Test
  public void testParsing() throws Exception {
    GerritEndpoint gerritEndpoint =
        skylarkTestExecutor.eval(
            "e",
            "e = git.gerrit_api(url = 'https://test.googlesource.com/example'))");
    assertThat(gerritEndpoint.describe())
        .containsExactly("type", "gerrit_api", "url", "https://test.googlesource.com/example");
  }

  @Test
  public void testParsingEmptyUrl() {
    skylarkTestExecutor.evalFails("git.gerrit_api(url = '')))", "Invalid empty field 'url'");
  }

  @Test
  public void testGetChange() throws Exception {
    Feedback feedback = notifyChangeToOriginFeedback();
    feedback.run(workdir, /*sourceRef*/ "12345");
    assertThat(dummyTrigger.messages).containsAllIn(ImmutableList.of("Change number 12345"));
  }

  @Test
  public void testGetChange_malformedJson() throws Exception {
    gitApiMockHttpTransport = new TestingGitApiHttpTransport() {
      @Override
      String getChange(String url) {
        return "foo   bar";
      }
    };
    Feedback feedback = notifyChangeToOriginFeedback();
    try {
      feedback.run(workdir, /*sourceRef*/ "12345");
      fail();
    } catch (ValidationException expected) {
      assertThat(expected).hasMessageThat()
          .contains("Error while executing the skylark transformer test_action");
      Throwable cause = expected.getCause();
      assertThat(cause).isInstanceOf(IllegalArgumentException.class);
    }
  }

  private Feedback notifyChangeToOriginFeedback() throws IOException, ValidationException {
    return feedback(
        ""
            + "def test_action(ctx):\n"
            + "  c = ctx.destination.get_change(ctx.ref, include_results = ['LABELS'])\n"
            + "  if c != None and c.id != None:\n"
            + "    ctx.origin.message('Change number ' + str(c.id))\n"
            + "\n");
  }

  private Feedback feedback(String actionFunction) throws IOException, ValidationException {
    String config =
        actionFunction
            + "\n"
            + "core.feedback(\n"
            + "    name = 'default',\n"
            + "    origin = testing.dummy_trigger(),\n"
            + "    destination = git.gerrit_api(url = '" + url + "'),\n"
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

  private class TestingGitApiHttpTransport extends GitApiMockHttpTransport {

    @Override
    protected byte[] getContent(String method, String url, MockLowLevelHttpRequest request) {
      if (method.equals("GET") && url.startsWith("https://localhost:33333/changes/")) {
        return getChange(url).getBytes(UTF_8);
      }
      throw new IllegalArgumentException(method + " " + url);
    }

    String getChange(String url) {
      return ""
          + "{"
          + "  id : \"" + changeNumberFromRequest(url) + "\","
          + "  status : \"NEW\""
          + "}";
    }
  }
}
