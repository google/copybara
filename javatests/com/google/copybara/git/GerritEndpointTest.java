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
import static com.google.copybara.git.GitRepository.newBareRepo;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static com.google.copybara.testing.git.GitTestUtil.mockResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.matches;
import static org.mockito.Matchers.startsWith;

import com.google.api.client.json.gson.GsonFactory;
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
import com.google.copybara.testing.git.GitTestUtil.Validator;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.devtools.build.lib.syntax.Runtime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GerritEndpointTest {

  private static final String BASE_URL = "https://user:SECRET@copybara-not-real.com";

  private SkylarkTestExecutor skylark;
  private Path workdir;
  private DummyTrigger dummyTrigger;
  private String url;
  private GitTestUtil gitUtil;

  @Before
  public void setup() throws Exception {
    workdir = Jimfs.newFileSystem().getPath("/");
    TestingConsole console = new TestingConsole();
    OptionsBuilder options = new OptionsBuilder();
    options.setConsole(console)
        .setOutputRootToTmpDir();
    dummyTrigger = new DummyTrigger();
    options.testingOptions.feedbackTrigger = dummyTrigger;
    options.testingOptions.checker = new DummyChecker(ImmutableSet.of("badword"));
    gitUtil = new GitTestUtil(options);
    Path credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, BASE_URL.getBytes(UTF_8));
    GitRepository repo = newBareRepo(Files.createTempDirectory("test_repo"),
        getGitEnv(), /*verbose=*/true)
        .init()
        .withCredentialHelper("store --file=" + credentialsFile);
    gitUtil.mockRemoteGitRepos(new Validator(), repo);

    url = BASE_URL + "/foo/bar";
    skylark = new SkylarkTestExecutor(options);
  }

  private String changeNumberFromRequest(String url) {
    return url.replaceAll(".*changes/([0-9]{1,10}).*", "$1");
  }

  @Test
  public void testParsing() throws Exception {
    GerritEndpoint gerritEndpoint =
        skylark.eval(
            "e",
            "e = git.gerrit_api(url = 'https://test.googlesource.com/example')");
    assertThat(gerritEndpoint.describe())
        .containsExactly("type", "gerrit_api", "url", "https://test.googlesource.com/example");

    skylark.verifyField(
        "git.gerrit_api(url = 'https://test.googlesource.com/example')",
        "url", "https://test.googlesource.com/example");
  }

  @Test
  public void testParsingWithChecker() throws Exception {
    GerritEndpoint gerritEndpoint =
        skylark.eval(
            "e",
            "e = git.gerrit_api(\n"
                + "url = 'https://test.googlesource.com/example', \n"
                + "checker = testing.dummy_checker(),\n"
                + ")\n");
    assertThat(gerritEndpoint.describe())
        .containsExactly("type", "gerrit_api", "url", "https://test.googlesource.com/example");

    skylark.verifyField(
        "git.gerrit_api(url = 'https://test.googlesource.com/example')",
        "url", "https://test.googlesource.com/example");
  }

  @Test
  public void testCheckerIsHonored() throws Exception {
    String config =
        ""
            + "def test_action(ctx):\n"
            + "  ctx.destination.get_change('12_badword_34', include_results = ['LABELS'])\n"
            + "  return ctx.success()\n"
            + "\n"
            + "core.feedback(\n"
            + "    name = 'default',\n"
            + "    origin = testing.dummy_trigger(),\n"
            + "    destination = git.gerrit_api("
            + "        url = 'https://test.googlesource.com/example',\n"
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
    skylark.evalFails("git.gerrit_api(url = '')))", "Invalid empty field 'url'");
    }

  @Test
  public void testOriginRef() throws ValidationException {
    String var =
        "git.gerrit_api(url = 'https://test.googlesource.com/example').new_origin_ref('12345')";
    ImmutableMap<String, Object> expectedFieldValues =
        ImmutableMap.<String, Object>builder()
            .put("ref", "12345")
            .build();
    skylark.verifyFields(var, expectedFieldValues);
  }

  /**
   * A test that uses feedback.
   *
   * <p>Does not verify all the fields, see {@link #testGetChangeExhaustive()} ()} for that.
   */
  @Test
  public void testFeedbackGetChange() throws Exception {
    mockForTest();
    Feedback feedback = notifyChangeToOriginFeedback();
    feedback.run(workdir, ImmutableList.of("12345"));
    assertThat(dummyTrigger.messages).containsAllIn(ImmutableList.of("Change number 12345"));
  }

  @Test
  public void testFeedbackGetChange_malformedJson() throws Exception {
    gitUtil.mockApi(anyString(), anyString(), mockResponse("foo   bar"));
    Feedback feedback = notifyChangeToOriginFeedback();
    try {
      feedback.run(workdir, ImmutableList.of("12345"));
      fail();
    } catch (ValidationException expected) {
      assertThat(expected).hasMessageThat()
          .contains("Error while executing the skylark transformation test_action");
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
    gitUtil.mockApi(
        eq("GET"),
        startsWith(BASE_URL + "/changes/"),
        mockResponse(
            "{\n"
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
                + "  'current_revision': 'foo',\n"
                + "  'revisions': {\n"
                + "    'foo': {\n"
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
                + "}\n"));
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
            .put("current_revision", "foo")
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
            .put("messages[0].id", "e6aa8a323fd948cc9986dd4d8b4c253487bab253")
            .put("messages[0].tag", "autogenerated:gerrit:newPatchSet")
            .put("messages[0].author.account_id", "12345")
            .put("messages[0].author.name", "Glorious Copybara")
            .put("messages[0].author.email", "no-reply@glorious-copybara.com")
            .put("messages[0].date", "2017-12-01 00:00:00.000000000")
            .put("messages[0].message", "Uploaded patch set 1.")
            .put("messages[0].revision_number", 1)
            .put("revisions['foo'].kind", "REWORK")
            .put("revisions['foo'].patchset_number", 1)
            .put("revisions['foo'].created", "2017-12-07 19:11:59.000000000")
            .put("revisions['foo'].uploader.account_id", "12345")
            .put("revisions['foo'].ref", "refs/changes/11/11111/1")
            .put("revisions['foo'].commit.author.name", "Glorious Copybara")
            .put("revisions['foo'].commit.author.email", "no-reply@glorious-copybara.com")
            .put("revisions['foo'].commit.author.date", "2017-12-01 00:00:00.000000000")
            .put("revisions['foo'].commit.committer.name", "Glorious Copybara")
            .put("revisions['foo'].commit.committer.email", "no-reply@glorious-copybara.com")
            .put("revisions['foo'].commit.committer.date", "2017-12-01 00:00:00.000000000")
            .put("revisions['foo'].commit.subject", "JUST A TEST")
            .put("revisions['foo'].commit.message", "JUST A TEST\n\nSecond line of description.\n")
            .build();
    skylark.verifyFields(var, expectedFieldValues);
  }

  @Test
  public void testGetChangePaginationNotSupported() throws IOException {
    gitUtil.mockApi(
        eq("GET"),
        startsWith(BASE_URL + "/changes/"),
        mockResponse("{" + "  id : '12345'," + "  _more_changes : true" + "}"));

    String config =
        String.format(
            "git.gerrit_api(url = '%s').get_change('12345', include_results = ['LABELS'])", url);
    skylark.evalFails(config, "Pagination is not supported yet.");
  }

  @Test
  public void testPostLabel() throws Exception {
    mockForTest();
    String config =
        String.format(
            "git.gerrit_api(url = '%s')."
                + "post_review('12345', 'sha1', git.review_input({'Code-Review': 1}, 'foooo'))",
            url);
    ImmutableMap<String, Object> expectedFieldValues =
        ImmutableMap.of("labels", ImmutableMap.of("Code-Review", 1));
    skylark.verifyFields(config, expectedFieldValues);
  }

  @Test
  public void testListChangesByCommit() throws Exception {
    mockForTest();
    String config =
        String.format(
            "git.gerrit_api(url = '%s')."
                + "list_changes_by_commit('7956f527ec8a23ebba9c3ebbcf88787aa3411425')",
            url);
    ImmutableMap<String, Object> expectedFieldValues =
        ImmutableMap.of(
            "id", "copybara-team%2Fcopybara~master~I85dd4ea583ac218d9480eefb12ff2c83ce0bce61");
    skylark.verifyFields(config + "[0]", expectedFieldValues);
  }

  @Test
  public void testListChangesByCommit_withIncludeResults() throws Exception {
    mockForTest();
    String config =
        String.format(
            "git.gerrit_api(url = '%s')."
                + "list_changes_by_commit('7956f527ec8a23ebba9c3ebbcf88787aa3411425',"
                + " include_results = ['LABELS', 'MESSAGES'])",
            url);
    ImmutableMap<String, Object> expectedFieldValues =
        ImmutableMap.of(
            "id", "copybara-team%2Fcopybara~master~I16e447bb2bb51952021ec3ea50991d923dcbbf58");
    skylark.verifyFields(config + "[0]", expectedFieldValues);
  }

  private Feedback notifyChangeToOriginFeedback() throws IOException, ValidationException {
    return feedback(
        ""
            + "def test_action(ctx):\n"
            + "  c = ctx.destination.get_change(ctx.refs[0], include_results = ['LABELS'])\n"
            + "  if c != None and c.id != None:\n"
            + "    ctx.origin.message('Change number ' + str(c.id))\n"
            + "  return ctx.success()\n"
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
    return (Feedback) skylark.loadConfig(config).getMigration("default");
  }

  private void mockForTest() throws IOException {
    gitUtil.mockApi(
        eq("GET"),
        startsWith(BASE_URL + "/changes/?q="),
        mockResponse(
            "[{\"id\":"
                + " \"copybara-team%2Fcopybara~master~I85dd4ea583ac218d9480eefb12ff2c83ce0bce61\""
                + "}]"));

    gitUtil.mockApi(
        "GET",
        BASE_URL
            + "/changes/?q=commit:7956f527ec8a23ebba9c3ebbcf88787aa3411425"
            + "&o=LABELS&o=MESSAGES",
        mockResponse(
            "[{"
                + "\"id\":"
                + " \"copybara-team%2Fcopybara~master~I16e447bb2bb51952021ec3ea50991d923dcbbf58\""
                + "}]"));

    gitUtil.mockApi(
        eq("GET"),
        matches(BASE_URL + "/changes/[0-9]+.*"),
        mockResponse("" + "{" + "  id : \"12345\"," + "  status : \"NEW\"" + "}"));

    gitUtil.mockApi(
        eq("POST"),
        matches(BASE_URL + "/changes/.*/revisions/.*/review"),
        mockResponse(postLabel()));
  }

  private String postLabel() {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("labels", ImmutableMap.of("Code-Review",  1));
      try {
        return GsonFactory.getDefaultInstance().toPrettyString(result);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
}
