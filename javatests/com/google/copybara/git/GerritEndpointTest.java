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
import com.google.devtools.build.lib.syntax.Runtime;
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

  /**
   * A test that uses feedback.
   *
   * <p>Does not verify all the fields, see {@link #testGetChangeExhaustive()} ()} for that.
   */
  @Test
  public void testFeedbackGetChange() throws Exception {
    Feedback feedback = notifyChangeToOriginFeedback();
    feedback.run(workdir, /*sourceRef*/ "12345");
    assertThat(dummyTrigger.messages).containsAllIn(ImmutableList.of("Change number 12345"));
  }

  @Test
  public void testFeedbackGetChange_malformedJson() throws Exception {
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
    assertThat(dummyTrigger.messages).isEmpty();
  }

  /**
   * An exhaustive test that evaluates each field of the change object.
   */
  @Test
  public void testGetChangeExhaustive() throws Exception {
    gitApiMockHttpTransport =
        new TestingGitApiHttpTransport() {
          @Override
          String getChange(String url) {
            return "{\n"
                + "  'id': 'copybara-project~Ie39b6e2c0c6e5ef8839013360bba38238c6ecfcd',\n"
                + "  'project': 'copybara-project',\n"
                + "  'branch': 'master',\n"
                + "  'topic': 'test_topic',\n"
                + "  'hashtags': [],\n"
                + "  'change_id': 'Ie39b6e2c0c6e5ef8839013360bba38238c6ecfcd',\n"
                + "  'subject': 'JUST A TEST',\n"
                + "  'status': 'NEW',\n"
                + "  'created': '2017-12-01 17:33:30.000000000',\n"
                + "  'updated': '2017-12-02 17:33:30.000000000',\n"
                + "  'submitted': '2017-12-03 17:33:30.000000000',\n"
                + "  'submit_type': 'MERGE_IF_NECESSARY',\n"
                + "  'mergeable': true,\n"
                + "  'insertions': 2,\n"
                + "  'deletions': 10,\n"
                + "  'unresolved_comment_count': 0,\n"
                + "  'has_review_started': true,\n"
                + "  '_number': 1082,\n"
                + "  'owner': {\n"
                + "    '_account_id': 12345,\n"
                + "    'name': 'Glorious Copybara',\n"
                + "    'email': 'no-reply@glorious-copybara.com',\n"
                + "    'secondary_emails': ['foo@bar.com'],\n"
                + "    'username': 'glorious.copybara'\n"
                + "  },\n"
                + "  'labels': {\n"
                + "    'Code-Review': {\n"
                + "      'all': [\n"
                + "        {\n"
                + "          'value': 2,\n"
                + "          'date': '2017-01-01 12:00:00.000000000',\n"
                + "          'permitted_voting_range': {\n"
                + "            'min': 2,\n"
                + "            'max': 2\n"
                + "          },\n"
                + "          '_account_id': 123456\n"
                + "        },\n"
                + "        {\n"
                + "          'value': 0,\n"
                + "          '_account_id': 123456\n"
                + "        },\n"
                + "        {\n"
                + "          'value': 0,\n"
                + "          '_account_id': 123456\n"
                + "        }\n"
                + "      ],\n"
                + "      'values': {\n"
                + "        '-2': 'Do not submit',\n"
                + "        '-1': 'I would prefer that you didn\\u0027t submit this',\n"
                + "        ' 0': 'No score',\n"
                + "        '+1': 'Looks good to me, but someone else must approve',\n"
                + "        '+2': 'Looks good to me, approved'\n"
                + "      },\n"
                + "      'default_value': 0\n"
                + "    }\n"
                + "},\n"
                + "  'current_revision': 'f33bd8687ae27c25254a21012b3c9b4a546db779',\n"
                + "  'revisions': {\n"
                + "    'f33bd8687ae27c25254a21012b3c9b4a546db779': {\n"
                + "      'kind': 'REWORK',\n"
                + "      '_number': 1,\n"
                + "      'created': '2017-12-07 19:11:59.000000000',\n"
                + "      'uploader': {\n"
                + "        '_account_id': 12345\n"
                + "      },\n"
                + "      'ref': 'refs/changes/11/11111/1',\n"
                + "      'fetch': {\n"
                + "        'https': {\n"
                + "          'url': 'https://foo.bar/copybara/test',\n"
                + "          'ref': 'refs/changes/11/11111/1'\n"
                + "        }\n"
                + "      },\n"
                + "      'commit': {\n"
                + "        'parents': [\n"
                + "          {\n"
                + "            'commit': 'e6b7772add9d2137fd5f879192bd249dfc4d0a00',\n"
                + "            'subject': 'Parent commit description.'\n"
                + "          }\n"
                + "        ],\n"
                + "        'author': {\n"
                + "          'name': 'Glorious Copybara',\n"
                + "          'email': 'no-reply@glorious-copybara.com',\n"
                + "          'date': '2017-12-01 00:00:00.000000000',\n"
                + "          'tz': -480\n"
                + "        },\n"
                + "        'committer': {\n"
                + "          'name': 'Glorious Copybara',\n"
                + "          'email': 'no-reply@glorious-copybara.com',\n"
                + "          'date': '2017-12-01 00:00:00.000000000',\n"
                + "          'tz': -480\n"
                + "        },\n"
                + "        'subject': 'JUST A TEST',\n"
                + "        'message': 'JUST A TEST\\n\\nSecond line of description.\n'\n"
                + "      }\n"
                + "    }\n"
                + "  },\n"
                + "  'messages': [\n"
                + "      {\n"
                + "        'id': 'e6aa8a323fd948cc9986dd4d8b4c253487bab253',\n"
                + "        'tag': 'autogenerated:gerrit:newPatchSet',\n"
                + "        'author': {\n"
                + "          '_account_id': 12345,\n"
                + "          'name': 'Glorious Copybara',\n"
                + "          'email': 'no-reply@glorious-copybara.com'\n"
                + "        },\n"
                + "        'real_author': {\n"
                + "          '_account_id': 12345,\n"
                + "          'name': 'Glorious Copybara',\n"
                + "          'email': 'no-reply@glorious-copybara.com'\n"
                + "        },\n"
                + "        'date': '2017-12-01 00:00:00.000000000',\n"
                + "        'message': 'Uploaded patch set 1.',\n"
                + "        '_revision_number': 1\n"
                + "      }\n"
                + "  ]\n"
                + "}\n";
          }
        };
    String var =
        String.format(
            "git.gerrit_api(url = '%s').get_change('12345', include_results = ['LABELS'])", url);

    ImmutableMap<String, Object> expectedFieldValues =
        ImmutableMap.<String, Object>builder()
            .put("id", "copybara-project~Ie39b6e2c0c6e5ef8839013360bba38238c6ecfcd")
            .put("project", "copybara-project")
            .put("branch", "master")
            .put("topic", "test_topic")
            .put("change_id", "Ie39b6e2c0c6e5ef8839013360bba38238c6ecfcd")
            .put("subject", "JUST A TEST")
            .put("status", "NEW")
            .put("created", "2017-12-01 17:33:30.000000000")
            .put("updated", "2017-12-02 17:33:30.000000000")
            .put("submitted", "2017-12-03 17:33:30.000000000")
            .put("current_revision", "f33bd8687ae27c25254a21012b3c9b4a546db779")
            .put("owner.account_id", "12345")
            .put("owner.name", "Glorious Copybara")
            .put("owner.email", "no-reply@glorious-copybara.com")
            .put("owner.secondary_emails[0]", "foo@bar.com")
            .put("owner.username", "glorious.copybara")
            .put("labels['Code-Review'].approved", Runtime.NONE)
            .put("labels['Code-Review'].recommended", Runtime.NONE)
            .put("labels['Code-Review'].disliked", Runtime.NONE)
            .put("labels['Code-Review'].blocking", false)
            .put("labels['Code-Review'].value", 0)
            .put("labels['Code-Review'].default_value", 0)
            .put("labels['Code-Review'].values['-2']", "Do not submit")
            .put("labels['Code-Review'].values['-1']", "I would prefer that you didn't submit this")
            .put("labels['Code-Review'].values[' 0']", "No score")
            .put(
                "labels['Code-Review'].values['+1']",
                "Looks good to me, but someone else must approve")
            .put("labels['Code-Review'].values['+2']", "Looks good to me, approved")
            .put("labels['Code-Review'].all[0].value", 2)
            .put("labels['Code-Review'].all[0].date", "2017-01-01 12:00:00.000000000")
            .put("labels['Code-Review'].all[0].account_id", "123456")
            .put("labels['Code-Review'].all[1].value", 0)
            .put("labels['Code-Review'].all[1].account_id", "123456")
            .put("labels['Code-Review'].all[2].value", 0)
            .put("labels['Code-Review'].all[2].account_id", "123456")
            // TODO(danielromero): Verify messages
            // TODO(danielromero): Verify revisions
            .build();
    skylarkTestExecutor.verifyFields(var, expectedFieldValues);
  }

  @Test
  public void testGetChangePaginationNotSupported() {
    gitApiMockHttpTransport = new TestingGitApiHttpTransport() {
      @Override
      String getChange(String url) {
        return ""
            + "{"
            + "  id : '12345',"
            + "  _more_changes : true"
            + "}";
      }
    };
    String config =
        String.format(
            "git.gerrit_api(url = '%s').get_change('12345', include_results = ['LABELS'])", url);
    skylarkTestExecutor.evalFails(config, "Pagination is not supported yet.");
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
