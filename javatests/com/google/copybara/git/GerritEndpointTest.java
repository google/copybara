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
import static com.google.copybara.testing.git.GitTestUtil.mockResponseAndValidateRequest;
import static com.google.copybara.testing.git.GitTestUtil.mockResponseWithStatus;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.ArgumentMatchers.startsWith;

import com.google.api.client.json.gson.GsonFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.ActionMigration;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.gerritapi.GerritApiException;
import com.google.copybara.testing.DummyChecker;
import com.google.copybara.testing.DummyTrigger;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.testing.git.GitTestUtil.MockRequestAssertion;
import com.google.copybara.testing.git.GitTestUtil.Validator;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
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
    options.setConsole(console).setOutputRootToTmpDir();
    dummyTrigger = new DummyTrigger();
    options.testingOptions.feedbackTrigger = dummyTrigger;
    options.testingOptions.checker = new DummyChecker(ImmutableSet.of("badword"));
    gitUtil = new GitTestUtil(options);
    Path credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, BASE_URL.getBytes(UTF_8));
    GitRepository repo =
        newBareRepo(
                Files.createTempDirectory("test_repo"),
                getGitEnv(),
                /* verbose= */ true,
                DEFAULT_TIMEOUT,
                /* noVerify= */ false)
            .init()
            .withCredentialHelper("store --file=" + credentialsFile);
    gitUtil.mockRemoteGitRepos(new Validator(), repo);

    url = BASE_URL + "/foo/bar";
    options.general.starlarkMode = "STRICT";
    skylark = new SkylarkTestExecutor(options);
  }

  private String changeNumberFromRequest(String url) {
    return url.replaceAll(".*changes/([0-9]{1,10}).*", "$1");
  }

  @Test
  public void testParsing() throws Exception {
    skylark.eval("e", "e = git.gerrit_api(url = 'https://test.googlesource.com/example')");
  }

  @Test
  public void testParsingWithChecker() throws Exception {
    skylark.eval(
        "e",
        """
        e = git.gerrit_api(
        url = 'https://test.googlesource.com/example',\s
        checker = testing.dummy_checker(),
        )
        """);
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
    ActionMigration actionMigration =
        (ActionMigration) skylark.loadConfig(config).getMigration("default");
    ValidationException expected =
        assertThrows(
            ValidationException.class,
            () -> actionMigration.run(workdir, ImmutableList.of("12345")));
    assertThat(expected).hasMessageThat().contains("Bad word 'badword' found: field 'path'.");
  }

  @Test
  public void testActions() throws Exception {
    gitUtil.mockApi(
        eq("GET"),
        matches(BASE_URL + "/changes/12345/revisions/sha1/actions"),
        mockResponse(
            ""
                + ")]}'\n"
                + "{\n"
                + "    \"submit\": {\n"
                + "         \"method\": \"POST\", \n"
                + "         \"label\": \"Submit\", \n"
                + "         \"title\": \"Submit patch set 1 into master\", \n"
                + "         \"enabled\": true\n"
                + "      },\n"
                + "    \"cherrypick\": {\n"
                + "          \"method\": \"POST\", \n"
                + "          \"label\": \"Cherry Pick\", \n"
                + "          \"title\": \"Cherry pick change to a different branch\",\n"
                + "          \"enabled\": false\n"
                + "     }\n"
                + " }"));

    runFeedback(
        ImmutableList.<String>builder()
            .add("res = ctx.destination.get_actions('12345', 'sha1').get(\"submit\")")
            .addAll(checkFieldStarLark("res", "label", "'Submit'"))
            .addAll(checkFieldStarLark("res", "enabled", "True"))
            .build());
  }

  @Test
  public void testActions_gerritApiException_accessIssue() throws Exception {
    var unused =
        gitUtil.mockApi(
            eq("GET"),
            matches(BASE_URL + "/changes/12345/revisions/sha1/actions"),
            invocation -> {
              throw new GerritApiException(400, "", "", "");
            });

    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                runFeedback(
                    ImmutableList.<String>builder()
                        .add("res = ctx.destination.get_actions('12345', 'sha1').get(\"submit\")")
                        .addAll(checkFieldStarLark("res", "label", "'Submit'"))
                        .addAll(checkFieldStarLark("res", "enabled", "True"))
                        .build()));
    assertThat(e).hasMessageThat().contains("Request error calling get_actions.");
    assertThat(e.getCause()).hasMessageThat().contains("Gerrit returned a request error");
  }

  @Test
  public void testParsingEmptyUrl() {
    skylark.evalFails("git.gerrit_api(url = '')", "Invalid empty field 'url'");
  }

  @Test
  public void testOriginRef() throws Exception {
    runFeedback(
        ImmutableList.<String>builder()
            .add("res = ctx.destination.new_origin_ref('12345')")
            .addAll(checkFieldStarLark("res", "ref", "'12345'"))
            .build());
  }

  /**
   * A test that uses feedback.
   *
   * <p>Does not verify all the fields, see {@link #testGetChangeExhaustive()} ()} for that.
   */
  @Test
  public void testFeedbackGetChange() throws Exception {
    mockForTest();
    ActionMigration actionMigration = notifyChangeToOriginFeedback();
    actionMigration.run(workdir, ImmutableList.of("12345"));
    assertThat(dummyTrigger.messages)
        .containsAtLeastElementsIn(ImmutableList.of("Change number 12345"));
  }

  @Test
  public void testFeedbackGetChange_malformedJson() throws Exception {
    gitUtil.mockApi(anyString(), anyString(), mockResponse("foo   bar"));
    ActionMigration actionMigration = notifyChangeToOriginFeedback();
    RepoException expected =
        assertThrows(
            RepoException.class,
            () -> actionMigration.run(workdir, ImmutableList.of("12345")));
    assertThat(expected)
        .hasMessageThat()
        .contains("Error while executing the skylark transformation test_action");
    Throwable cause = expected.getCause();
    assertThat(cause).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(dummyTrigger.messages).isEmpty();
  }

  /** An exhaustive test that evaluates each field of the change object. */
  @Test
  public void testGetChangeExhaustive() throws Exception {
    gitUtil.mockApi(
        eq("GET"),
        startsWith(BASE_URL + "/changes/"),
        mockResponse(
            """
            {
              "id": "copybara-project~Ie39b6e2c0c6e5ef8839013360bba38238c6ecfcd",
              "project": "copybara-project",
              "branch": "master",
              "topic": "test_topic",
              "hashtags": [],
              "change_id": "Ie39b6e2c0c6e5ef8839013360bba38238c6ecfcd",
              "subject": "JUST A TEST",
              "status": "NEW",
              "created": "2017-12-01 17:33:30.000000000",
              "updated": "2017-12-02 17:33:30.000000000",
              "submitted": "2017-12-03 17:33:30.000000000",
              "submit_type": "MERGE_IF_NECESSARY",
              "submittable": true,
              "insertions": 2,
              "deletions": 10,
              "unresolved_comment_count": 0,
              "has_review_started": true,
              "_number": 1082,
              "owner": {
                "_account_id": 12345,
                "name": "Glorious Copybara",
                "email": "no-reply@glorious-copybara.com",
                "secondary_emails": ["foo@bar.com"],
                "username": "glorious.copybara"
              },
              "labels": {
                "Code-Review": {
                  "all": [
                    {
                      "value": 2,
                      "date": "2017-01-01 12:00:00.000000000",
                      "permitted_voting_range": {
                        "min": 2,
                        "max": 2
                      },
                      "_account_id": 123456
                    },
                    {
                      "value": 0,
                      "_account_id": 123456
                    },
                    {
                      "value": 0,
                      "_account_id": 123456
                    }
                  ],
                  "values": {
                    "-2": "Do not submit",
                    "-1": "I would prefer that you didn\\u0027t submit this",
                    " 0": "No score",
                    "+1": "Looks good to me, but someone else must approve",
                    "+2": "Looks good to me, approved"
                  },
                  "default_value": 0
                }
            },
              "current_revision": "foo",
              "revisions": {
                "foo": {
                  "kind": "REWORK",
                  "_number": 1,
                  "created": "2017-12-07 19:11:59.000000000",
                  "uploader": {
                    "_account_id": 12345
                  },
                  "ref": "refs/changes/11/11111/1",
                  "fetch": {
                    "https": {
                      "url": "https://foo.bar/copybara/test",
                      "ref": "refs/changes/11/11111/1"
                    }
                  },
                  "commit": {
                    "parents": [
                      {
                        "commit": "e6b7772add9d2137fd5f879192bd249dfc4d0a00",
                        "subject": "Parent commit description."
                      }
                    ],
                    "author": {
                      "name": "Glorious Copybara",
                      "email": "no-reply@glorious-copybara.com",
                      "date": "2017-12-01 00:00:00.000000000",
                      "tz": -480
                    },
                    "committer": {
                      "name": "Glorious Copybara",
                      "email": "no-reply@glorious-copybara.com",
                      "date": "2017-12-01 00:00:00.000000000",
                      "tz": -480
                    },
                    "subject": "JUST A TEST",
                    "message": "JUST A TEST\\n\\nSecond line of description.
            "
                  }
                }
              },
              "messages": [
                  {
                    "id": "e6aa8a323fd948cc9986dd4d8b4c253487bab253",
                    "tag": "autogenerated:gerrit:newPatchSet",
                    "author": {
                      "_account_id": 12345,
                      "name": "Glorious Copybara",
                      "email": "no-reply@glorious-copybara.com"
                    },
                    "real_author": {
                      "_account_id": 12345,
                      "name": "Glorious Copybara",
                      "email": "no-reply@glorious-copybara.com"
                    },
                    "date": "2017-12-01 00:00:00.000000000",
                    "message": "Uploaded patch set 1.",
                    "_revision_number": 1
                  }
              ]
            }
            """));
    runFeedback(
        ImmutableList.<String>builder()
            .add("res = ctx.destination.get_change('12345', include_results = ['LABELS'])")
            .addAll(
                checkFieldStarLark(
                    "res", "id", "'copybara-project~Ie39b6e2c0c6e5ef8839013360bba38238c6ecfcd'"))
            .addAll(checkFieldStarLark("res", "project", "'copybara-project'"))
            .addAll(checkFieldStarLark("res", "branch", "'master'"))
            .addAll(checkFieldStarLark("res", "topic", "'test_topic'"))
            .addAll(
                checkFieldStarLark(
                    "res", "change_id", "'Ie39b6e2c0c6e5ef8839013360bba38238c6ecfcd'"))
            .addAll(checkFieldStarLark("res", "subject", "'JUST A TEST'"))
            .addAll(checkFieldStarLark("res", "status", "'NEW'"))
            .addAll(checkFieldStarLark("res", "created", "'2017-12-01 17:33:30.000000000'"))
            .addAll(checkFieldStarLark("res", "updated", "'2017-12-02 17:33:30.000000000'"))
            .addAll(checkFieldStarLark("res", "submitted", "'2017-12-03 17:33:30.000000000'"))
            .addAll(checkFieldStarLark("res", "submittable", "True"))
            .addAll(checkFieldStarLark("res", "current_revision", "'foo'"))
            .addAll(checkFieldStarLark("res", "owner.account_id", "'12345'"))
            .addAll(checkFieldStarLark("res", "owner.name", "'Glorious Copybara'"))
            .addAll(checkFieldStarLark("res", "owner.email", "'no-reply@glorious-copybara.com'"))
            .addAll(checkFieldStarLark("res", "owner.secondary_emails[0]", "'foo@bar.com'"))
            .addAll(checkFieldStarLark("res", "owner.username", "'glorious.copybara'"))
            .addAll(checkFieldStarLark("res", "labels['Code-Review'].approved", "None"))
            .addAll(checkFieldStarLark("res", "labels['Code-Review'].recommended", "None"))
            .addAll(checkFieldStarLark("res", "labels['Code-Review'].disliked", "None"))
            .addAll(checkFieldStarLark("res", "labels['Code-Review'].blocking", "False"))
            .addAll(checkFieldStarLark("res", "labels['Code-Review'].value", "0"))
            .addAll(checkFieldStarLark("res", "labels['Code-Review'].default_value", "0"))
            .addAll(
                checkFieldStarLark("res", "labels['Code-Review'].values['-2']", "'Do not submit'"))
            .addAll(
                checkFieldStarLark(
                    "res",
                    "labels['Code-Review'].values['-1']",
                    "'I would prefer that you didn\\'t submit this'"))
            .addAll(checkFieldStarLark("res", "labels['Code-Review'].values[' 0']", "'No score'"))
            .addAll(
                checkFieldStarLark(
                    "res",
                    "labels['Code-Review'].values['+1']",
                    "'Looks good to me, but someone else must approve'"))
            .addAll(
                checkFieldStarLark(
                    "res", "labels['Code-Review'].values['+2']", "'Looks good to me, approved'"))
            .addAll(checkFieldStarLark("res", "labels['Code-Review'].all[0].value", "2"))
            .addAll(
                checkFieldStarLark(
                    "res", "labels['Code-Review'].all[0].date", "'2017-01-01 12:00:00.000000000'"))
            .addAll(
                checkFieldStarLark("res", "labels['Code-Review'].all[0].account_id", "'123456'"))
            .addAll(checkFieldStarLark("res", "labels['Code-Review'].all[1].value", "0"))
            .addAll(
                checkFieldStarLark("res", "labels['Code-Review'].all[1].account_id", "'123456'"))
            .addAll(checkFieldStarLark("res", "labels['Code-Review'].all[2].value", "0"))
            .addAll(
                checkFieldStarLark("res", "labels['Code-Review'].all[2].account_id", "'123456'"))
            .addAll(
                checkFieldStarLark(
                    "res", "messages[0].id", "'e6aa8a323fd948cc9986dd4d8b4c253487bab253'"))
            .addAll(
                checkFieldStarLark("res", "messages[0].tag", "'autogenerated:gerrit:newPatchSet'"))
            .addAll(checkFieldStarLark("res", "messages[0].author.account_id", "'12345'"))
            .addAll(checkFieldStarLark("res", "messages[0].author.name", "'Glorious Copybara'"))
            .addAll(
                checkFieldStarLark(
                    "res", "messages[0].author.email", "'no-reply@glorious-copybara.com'"))
            .addAll(
                checkFieldStarLark("res", "messages[0].date", "'2017-12-01 00:00:00.000000000'"))
            .addAll(checkFieldStarLark("res", "messages[0].message", "'Uploaded patch set 1.'"))
            .addAll(checkFieldStarLark("res", "messages[0].revision_number", "1"))
            .addAll(checkFieldStarLark("res", "revisions['foo'].kind", "'REWORK'"))
            .addAll(checkFieldStarLark("res", "revisions['foo'].patchset_number", "1"))
            .addAll(
                checkFieldStarLark(
                    "res", "revisions['foo'].created", "'2017-12-07 19:11:59.000000000'"))
            .addAll(checkFieldStarLark("res", "revisions['foo'].uploader.account_id", "'12345'"))
            .addAll(checkFieldStarLark("res", "revisions['foo'].ref", "'refs/changes/11/11111/1'"))
            .addAll(
                checkFieldStarLark(
                    "res", "revisions['foo'].commit.author.name", "'Glorious Copybara'"))
            .addAll(
                checkFieldStarLark(
                    "res",
                    "revisions['foo'].commit.author.email",
                    "'no-reply@glorious-copybara.com'"))
            .addAll(
                checkFieldStarLark(
                    "res",
                    "revisions['foo'].commit.author.date",
                    "'2017-12-01 00:00:00.000000000'"))
            .addAll(
                checkFieldStarLark(
                    "res", "revisions['foo'].commit.committer.name", "'Glorious Copybara'"))
            .addAll(
                checkFieldStarLark(
                    "res",
                    "revisions['foo'].commit.committer.email",
                    "'no-reply@glorious-copybara.com'"))
            .addAll(
                checkFieldStarLark(
                    "res",
                    "revisions['foo'].commit.committer.date",
                    "'2017-12-01 00:00:00.000000000'"))
            .addAll(checkFieldStarLark("res", "revisions['foo'].commit.subject", "'JUST A TEST'"))
            .addAll(
                checkFieldStarLark(
                    "res",
                    "revisions['foo'].commit.message",
                    "'JUST A TEST\\n\\nSecond line of description.\\n'"))
            .addAll(checkFieldStarLark("res", "labels['Code-Review'].value", "0"))
            .build());
  }

  @Test
  public void testGetChangePaginationNotSupported() throws IOException {
    gitUtil.mockApi(
        eq("GET"),
        startsWith(BASE_URL + "/changes/"),
        mockResponse("{" + "  \"id\" : \"12345\"," + "  \"_more_changes\" : true" + "}"));
    ValidationException expected =
        assertThrows(
            ValidationException.class,
            () ->
                runFeedback(
                    ImmutableList.of(
                        "ctx.destination.get_change('12345', include_results = ['LABELS'])")));
    assertThat(expected).hasMessageThat().contains("Pagination is not supported yet.");
  }

  @Test
  public void testGetChange_gerritApiException_permissionIssue() throws IOException {
    var unused =
        gitUtil.mockApi(
            eq("GET"),
            startsWith(BASE_URL + "/changes/"),
            invocation -> {
              throw new GerritApiException(400, "test", "test", "test");
            });

    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                runFeedback(
                    ImmutableList.of(
                        "ctx.destination.get_change('12345', include_results = ['LABELS'])")));
    assertThat(e)
        .hasMessageThat()
        .contains("Request error calling get_change. Gerrit returned a request error");
    assertThat(e.getCause()).hasCauseThat().isInstanceOf(GerritApiException.class);
  }

  @Test
  public void testPostLabel() throws Exception {
    mockForTest();
    runFeedback(
        ImmutableList.<String>builder()
            .add(
                "res = ctx.destination.post_review('12345', 'sha1',"
                    + " git.review_input({'Code-Review': 1}, 'foooo'))")
            .addAll(checkFieldStarLark("res", "labels", "{'Code-Review': 1}"))
            .build());
  }

  @Test
  public void testPostTag() throws Exception {
    gitUtil.mockApi(
        eq("POST"),
        matches(BASE_URL + "/changes/.*/revisions/.*/review"),
        mockResponse(postLabel()));
    gitUtil.mockApi(
        eq("POST"),
        contains("/changes/12345/revisions/sha1/review"),
        mockResponseAndValidateRequest(
            postLabel(), MockRequestAssertion.contains("\"tag\":\"tag:me\"")));
    runFeedback(
        ImmutableList.<String>builder()
            .add(
                "res = ctx.destination.post_review('12345', 'sha1',"
                    + " git.review_input({'Code-Review': 1}, tag='tag:me'))")
            .build());
  }

  @Test
  public void testPostNotify() throws Exception {
    gitUtil.mockApi(
        eq("POST"),
        matches(BASE_URL + "/changes/.*/revisions/.*/review"),
        mockResponse(postLabel()));
    gitUtil.mockApi(
        eq("POST"),
        contains("/changes/12345/revisions/sha1/review"),
        mockResponseAndValidateRequest(
            postLabel(), MockRequestAssertion.contains("\"notify\":\"NONE\"")));
    runFeedback(
        ImmutableList.<String>builder()
            .add(
                "res = ctx.destination.post_review('12345', 'sha1',"
                    + " git.review_input({'Code-Review': 1}, notify='NONE'))")
            .build());
  }

  @Test
  public void deleteVote() throws Exception {
    AtomicBoolean called = new AtomicBoolean(false);
    gitUtil.mockApi(
        eq("POST"),
        startsWith(BASE_URL + "/changes/12345/reviewers/me/votes/Code-Review/delete"),
        mockResponseWithStatus(
            "",
            204,
            new MockRequestAssertion(
                "Always true with side-effect",
                s -> {
                  called.set(true);
                  return true;
                })));

    runFeedback(
        ImmutableList.<String>builder()
            .add("ctx.destination" + ".delete_vote('12345', 'me', 'Code-Review')")
            .build());

    assertThat(called.get()).isTrue();
  }

  @Test
  public void deleteVote_gerritApiException_permissionIssue() throws Exception {
    var unused =
        gitUtil.mockApi(
            eq("POST"),
            startsWith(BASE_URL + "/changes/12345/reviewers/me/votes/Code-Review/delete"),
            invocation -> {
              throw new GerritApiException(400, "", "", "");
            });

    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                runFeedback(
                    ImmutableList.<String>builder()
                        .add("ctx.destination" + ".delete_vote('12345', 'me', 'Code-Review')")
                        .build()));

    assertThat(e)
        .hasMessageThat()
        .contains("Request error calling delete_vote. Gerrit returned a request error");
    assertThat(e.getCause()).hasCauseThat().isInstanceOf(GerritApiException.class);
  }

  @Test
  public void submitChange() throws Exception {
    AtomicBoolean called = new AtomicBoolean(false);
    gitUtil.mockApi(
        eq("POST"),
        startsWith(BASE_URL + "/changes/12345/submit"),
        mockResponseWithStatus(
            "{\"id\" : \"12345\"}",
            200,
            new MockRequestAssertion(
                "Always true with side-effect",
                s -> {
                  called.set(true);
                  return true;
                })));

    runFeedbackWithSubmit(
        ImmutableList.<String>builder().add("ctx.destination" + ".submit_change('12345')").build());

    assertThat(called.get()).isTrue();
  }

  @Test
  public void submitChange_gerritApiException_permissionIssue() throws Exception {
    var unused =
        gitUtil.mockApi(
            eq("POST"),
            startsWith(BASE_URL + "/changes/12345/submit"),
            invocation -> {
              throw new GerritApiException(400, "", "", "");
            });

    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                runFeedbackWithSubmit(
                    ImmutableList.<String>builder()
                        .add("ctx.destination" + ".submit_change('12345')")
                        .build()));

    assertThat(e)
        .hasMessageThat()
        .contains("Request error calling submit_change. Gerrit returned a request error");
    assertThat(e.getCause()).hasCauseThat().isInstanceOf(GerritApiException.class);
  }

    @Test
  public void submitChange_disallowed() throws Exception {
    AtomicBoolean called = new AtomicBoolean(false);
    gitUtil.mockApi(
        eq("POST"),
        startsWith(BASE_URL + "/changes/12345/submit"),
        mockResponseWithStatus(
            "{\"id\" : \"12345\"}",
            200,
            new MockRequestAssertion(
                "Always true with side-effect",
                s -> {
                  called.set(true);
                  return true;
                })));

    ValidationException ve = assertThrows(ValidationException.class, () -> runFeedback(
        ImmutableList.<String>builder().add("ctx.destination" + ".submit_change('12345')")
            .build()));
    assertThat(ve).hasMessageThat()
        .contains("Gerrit submit_change is only allowed if it is is enabled on the endpoint");
    assertThat(called.get()).isFalse();
  }

  @Test
  public void abandonChange() throws Exception {
    AtomicBoolean called = new AtomicBoolean(false);
    gitUtil.mockApi(
        eq("POST"),
        startsWith(BASE_URL + "/changes/12345/abandon"),
        mockResponseWithStatus(
            "{\"id\" : \"12345\"}",
            200,
            new MockRequestAssertion(
                "Always true with side-effect",
                s -> {
                  called.set(true);
                  return true;
                })));

    runFeedback(
        ImmutableList.<String>builder()
            .add("ctx.destination" + ".abandon_change('12345')")
            .build());

    assertThat(called.get()).isTrue();
  }

  @Test
  public void abandonChange_gerritApiException_permissionIssue() throws Exception {
    var unused =
        gitUtil.mockApi(
            eq("POST"),
            startsWith(BASE_URL + "/changes/12345/abandon"),
            invocation -> {
              throw new GerritApiException(400, "", "", "");
            });

    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                runFeedback(
                    ImmutableList.<String>builder()
                        .add("ctx.destination" + ".abandon_change('12345')")
                        .build()));

    assertThat(e)
        .hasMessageThat()
        .contains("Request error calling abandon_change. Gerrit returned a request error");
    assertThat(e.getCause()).hasCauseThat().isInstanceOf(GerritApiException.class);
  }

  @Test
  public void testPostLabel_errorCreatesVe() throws Exception {
    mockForTest();
    gitUtil.mockApi(
        eq("POST"),
        matches(BASE_URL + "/changes/.*/revisions/.*/review"),
        mockResponseWithStatus("\n\nApplying label \"Verified\": -1 is restricted.", 403));
    ValidationException expected =
        assertThrows(
            ValidationException.class,
            () ->
                runFeedback(
                    ImmutableList.of(
                        "ctx.destination.post_review("
                            + "'12345', 'sha1', git.review_input({'Code-Review': 1}, 'foooo'))")));
    assertThat(expected)
        .hasMessageThat()
        .contains("Request error calling post_review. Gerrit returned a request error");
    assertThat(expected.getCause()).hasCauseThat().isInstanceOf(GerritApiException.class);
  }

  @Test
  public void testListChangesByCommit() throws Exception {
    mockForTest();
    runFeedback(
        ImmutableList.<String>builder()
            .add(
                "res = ctx.destination"
                    + ".list_changes('commit:7956f527ec8a23ebba9c3ebbcf88787aa3411425')")
            .addAll(
                checkFieldStarLark(
                    "res[0]",
                    "id",
                    "'copybara-team%2Fcopybara~master~I85dd4ea583ac218d9480eefb12ff2c83ce0bce61'"))
            .build());
  }

  @Test
  public void testListChangesByCommit_withIncludeResults() throws Exception {
    mockForTest();
    runFeedback(
        ImmutableList.<String>builder()
            .add(
                "res = ctx.destination"
                    + ".list_changes('commit:7956f527ec8a23ebba9c3ebbcf88787aa3411425',"
                    + " include_results = ['LABELS', 'MESSAGES'])")
            .addAll(
                checkFieldStarLark(
                    "res[0]",
                    "id",
                    "'copybara-team%2Fcopybara~master~I16e447bb2bb51952021ec3ea50991d923dcbbf58'"))
            .build());
  }

  @Test
  public void testListChangesByQueryString() throws Exception {
    gitUtil.mockApi(
        "GET",
        BASE_URL + "/changes/?q=topic:test/1234%2Bstatus:open",
        mockResponse(
            "[{" + "\"id\":" + " \"34567\"" + "}," + "{" + "\"id\":" + " \"4567\"" + "}]"));
    runFeedback(
        ImmutableList.<String>builder()
            .add("res = ctx.destination" + ".list_changes('topic:test/1234+status:open')")
            .addAll(checkFieldStarLark("res[0]", "id", "'34567'"))
            .addAll(checkFieldStarLark("res[1]", "id", "'4567'"))
            .build());
  }

  @Test
  public void testListChangesByQueryString_withIncludeResults() throws Exception {
    gitUtil.mockApi(
        "GET",
        BASE_URL + "/changes/?q=topic:test/1234%2Bstatus:open" + "&o=LABELS&o=MESSAGES",
        mockResponse("[{" + "\"id\":" + " \"4567\"" + "}]"));
    runFeedback(
        ImmutableList.<String>builder()
            .add(
                "res = ctx.destination"
                    + ".list_changes('topic:test/1234+status:open',"
                    + " include_results = ['LABELS', 'MESSAGES'])")
            .addAll(checkFieldStarLark("res[0]", "id", "'4567'"))
            .build());
  }

  private ActionMigration notifyChangeToOriginFeedback() throws IOException, ValidationException {
    return feedback(
        ""
            + "def test_action(ctx):\n"
            + "  c = ctx.destination.get_change(ctx.refs[0], include_results = ['LABELS'])\n"
            + "  if c != None and c.id != None:\n"
            + "    ctx.origin.message('Change number ' + str(c.id))\n"
            + "  return ctx.success()\n"
            + "\n", false);
  }

  private ActionMigration feedback(String actionFunction, boolean allowSubmit)
      throws IOException, ValidationException {
    String config =
        actionFunction
            + "\n"
            + "core.feedback(\n"
            + "    name = 'default',\n"
            + "    origin = testing.dummy_trigger(),\n"
            + "    destination = git.gerrit_api("
            + "                      url = '" + url + "', "
            + "                      allow_submit = " + (allowSubmit ? "True" : "False")
            + "    ),\n"
            + "    actions = [test_action,],\n"
            + ")\n"
            + "\n";
    System.err.println(config);
    return (ActionMigration) skylark.loadConfig(config).getMigration("default");
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
        mockResponse("" + "{" + "  \"id\" : \"12345\"," + "  \"status\" : \"NEW\"" + "}"));

    gitUtil.mockApi(
        eq("POST"),
        matches(BASE_URL + "/changes/.*/revisions/.*/review"),
        mockResponse(postLabel()));
  }

  private static ImmutableList<String> checkFieldStarLark(String var, String field, String value) {
    return ImmutableList.of(
        String.format("if %s.%s != %s:", var, field, value),
        String.format(
            "  fail('unexpected value for %1$s.%3$s: ' + str(%1$s.%2$s))",
            var, field, field.replace("'", "\\'")));
  }

  private void runFeedback(ImmutableList<String> funBody) throws Exception {
    ActionMigration test =
        feedback(
            "def test_action(ctx):\n"
                + funBody.stream().map(s -> "  " + s).collect(Collectors.joining("\n"))
                + "\n  return ctx.success()\n", false);
    test.run(workdir, ImmutableList.of("e597746de9c1704e648ddc3ffa0d2096b146d600"));
  }

  private void runFeedbackWithSubmit(ImmutableList<String> funBody) throws Exception {
    ActionMigration test =
        feedback(
            "def test_action(ctx):\n"
                + funBody.stream().map(s -> "  " + s).collect(Collectors.joining("\n"))
                + "\n  return ctx.success()\n", true);
    test.run(workdir, ImmutableList.of("e597746de9c1704e648ddc3ffa0d2096b146d600"));
  }

  private String postLabel() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("labels", ImmutableMap.of("Code-Review", 1));
    try {
      return GsonFactory.getDefaultInstance().toPrettyString(result);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
