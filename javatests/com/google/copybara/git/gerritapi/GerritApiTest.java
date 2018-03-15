/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.copybara.git.gerritapi;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.copybara.git.GitRepository.newBareRepo;
import static com.google.copybara.git.gerritapi.ChangeStatus.ABANDONED;
import static com.google.copybara.git.gerritapi.ChangeStatus.NEW;
import static com.google.copybara.git.gerritapi.IncludeResult.CURRENT_COMMIT;
import static com.google.copybara.git.gerritapi.IncludeResult.CURRENT_REVISION;
import static com.google.copybara.git.gerritapi.IncludeResult.DETAILED_LABELS;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.copybara.exception.RepoException;
import com.google.copybara.git.GerritOptions;
import com.google.copybara.git.GitRepository;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.git.GitTestUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GerritApiTest {

  private static final String CHANGE_ID = "Ie39b6e2c0c6e5ef8839013360bba38238c6ecfcd";

  protected Map<Predicate<String>, byte[]> requestToResponse = Maps.newHashMap();

  protected GerritApi gerritApi;
  private MockHttpTransport httpTransport;
  private Path credentialsFile;

  @Before
  public void setUp() throws Exception {
    OptionsBuilder options = new OptionsBuilder()
        .setWorkdirToRealTempDir()
        .setEnvironment(GitTestUtil.getGitEnv())
        .setOutputRootToTmpDir();

    credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, "https://user:SECRET@copybara-not-real.com".getBytes(UTF_8));
    GitRepository repo = newBareRepo(Files.createTempDirectory("test_repo"),
                                     getGitEnv(), /*verbose=*/true)
        .init()
        .withCredentialHelper("store --file=" + credentialsFile);


    httpTransport = new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
        String requestString = method + " " + url;
        MockLowLevelHttpRequest request = new MockLowLevelHttpRequest();
        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
        request.setResponse(response);
        for (Entry<Predicate<String>, byte[]> entry : requestToResponse.entrySet()) {
          if (entry.getKey().test(requestString)) {
            byte[] content = entry.getValue();
            assertWithMessage("'" + method + " " + url + "'").that(content).isNotNull();
            response.setContent(content);
            return request;
          }
        }
        response.setStatusCode(404);
        response.setContent(("NO URL MATCHED! (Returning 404) REQUEST: " + requestString));
        return request;
      }
    };

    GerritOptions gerritOptions = new GerritOptions(
        () -> options.general, options.git) {
      @Override
      protected HttpTransport getHttpTransport() {
        return httpTransport;
      }

      @Override
      protected GitRepository getCredentialsRepo() throws RepoException {
        return repo;
      }
    };
    gerritApi = gerritOptions.newGerritApi(getHost() + "/foo/bar/baz");
  }

  protected String getHost() {
    return "https://copybara-not-real.com";
  }

  @Test
  public void testChanges() throws Exception {
    mockResponse(new CheckRequest("GET", "/changes/\\?q=status(:|%3A)open"), ""
        + ")]}'\n" + "[\n" + mockChangeInfo(NEW) + "]");

    List<ChangeInfo> changes = gerritApi.getChanges(new ChangesQuery("status:open"));
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).getId()).contains(CHANGE_ID);
    assertThat(changes.get(0).getStatus()).isEqualTo(NEW);
    assertThat(changes.get(0).getNumber()).isEqualTo(1082);
  }

  @Test
  public void testChangesNoChanges() throws Exception {
    mockResponse(new CheckRequest("GET", "/changes/\\?q=status(:|%3A)open"), ""
        + ")]}'\n"
        + "[]");

    List<ChangeInfo> changes = gerritApi.getChanges(new ChangesQuery("status:open"));
    assertThat(changes).isEmpty();
  }

  @Test
  public void testChanges404NotFound() throws Exception {
    mockResponse(s -> false, "");
    try {
      gerritApi.getChanges(new ChangesQuery("status:open"));
      Assert.fail();
    } catch (GerritApiException e) {
      assertThat(e.getExitCode()).isEqualTo(404);
    }
  }

  @Test
  public void testGetChange() throws Exception {
    mockResponse(new CheckRequest("GET", "/changes/" + CHANGE_ID + "\\?o="), ""
        + ")]}'\n" + mockChangeInfo(NEW));
    ChangeInfo change = gerritApi.getChange(CHANGE_ID,
        new GetChangeInput(ImmutableSet.of(CURRENT_REVISION, CURRENT_COMMIT, DETAILED_LABELS)));
    assertThat(change.getChangeId()).isEqualTo(CHANGE_ID);
    assertThat(change.getNumber()).isEqualTo(1082);
    assertThat(change.getUpdated().format(DateTimeFormatter.ISO_DATE_TIME))
        .isEqualTo("2017-12-01T17:33:30Z");
    RevisionInfo revisionInfo = change.getAllRevisions().get(change.getCurrentRevision());
    assertThat(revisionInfo.getCommit().getMessage()).contains("JUST A TEST");
    assertThat(revisionInfo.getCommit().getMessage()).contains("Second line of description");
    assertThat(revisionInfo.getFetch()).containsKey("https");
    FetchInfo fetchInfo = revisionInfo.getFetch().get("https");
    assertThat(fetchInfo.getUrl()).isEqualTo("https://foo.bar/copybara/test");
    assertThat(fetchInfo.getRef()).isEqualTo("refs/changes/11/11111/1");

    List<ChangeMessageInfo> messages = change.getMessages();
    assertThat(messages).hasSize(1);
    ChangeMessageInfo message = Iterables.getOnlyElement(messages);
    assertThat(message.getMessage()).isEqualTo("Uploaded patch set 1.");

    ImmutableMap<String, LabelInfo> labels = change.getLabels();
    assertThat(labels).hasSize(1);
    LabelInfo labelInfo = Iterables.getOnlyElement(labels.values());
    assertThat(labelInfo.getAll().asList().get(0).getDate().format(DateTimeFormatter.ISO_DATE_TIME))
        .isEqualTo("2017-01-01T12:00:00Z");
  }

  @Test
  public void testGetChange404NotFound() throws Exception {
    mockResponse(s -> false, "");
    try {
      gerritApi.getChange(CHANGE_ID, new GetChangeInput());
      Assert.fail();
    } catch (GerritApiException e) {
      assertThat(e.getExitCode()).isEqualTo(404);
    }
  }


  @Test
  public void testAbandonRestore() throws Exception {
    mockResponse(new CheckRequest("POST", ".*/abandon.*"), ""
        + ")]}'\n" + mockChangeInfo(ChangeStatus.ABANDONED));
    mockResponse(new CheckRequest("POST", ".*/restore.*"), ""
        + ")]}'\n" + mockChangeInfo(NEW));

    ChangeInfo change = gerritApi.abandonChange(CHANGE_ID, AbandonInput.createWithoutComment());
    assertThat(change.getId()).contains(CHANGE_ID);
    assertThat(change.getStatus()).isEqualTo(ABANDONED);

    change = gerritApi.restoreChange(CHANGE_ID, RestoreInput.createWithoutComment());
    assertThat(change.getId()).contains(CHANGE_ID);
    assertThat(change.getStatus()).isEqualTo(NEW);
  }

  @Test
  public void testListProjects() throws Exception {
    mockResponse(new CheckRequest("GET", "/projects/"), ""
        + ")]}'\n"
        + "{\n"
        + "    \"external/bison\": {\n"
        + "      \"id\": \"external%2Fbison\",\n"
        + "      \"description\": \"GNU parser generator\"\n"
        + "    },\n"
        + "    \"external/gcc\": {\n"
        + "      \"id\": \"external%2Fgcc\"\n"
        + "    },\n"
        + "    \"external/openssl\": {\n"
        + "      \"id\": \"external%2Fopenssl\",\n"
        + "      \"description\": \"encryption\\\\ncrypto routines\"\n"
        + "    }\n"
        + "  }");

    Map<String, ProjectInfo> projects = gerritApi.listProjects(
        new ListProjectsInput().withLimit(3).withRegex("external.*"));

    assertThat(projects).hasSize(3);
    assertThat(projects.get("external/bison").getId()).isEqualTo("external%2Fbison");
    assertThat(projects.get("external/bison").getDescription()).isEqualTo("GNU parser generator");
    assertThat(projects.get("external/gcc").getId()).isEqualTo("external%2Fgcc");
    assertThat(projects.get("external/openssl").getId()).isEqualTo("external%2Fopenssl");
  }

  private static String mockChangeInfo(ChangeStatus status) {
        return "{\n"
        + "  \"id\": \"copybara-project~" + CHANGE_ID + "\",\n"
        + "  \"project\": \"copybara-project\",\n"
        + "  \"branch\": \"master\",\n"
        + "  \"hashtags\": [],\n"
        + "  \"change_id\": \"" + CHANGE_ID + "\",\n"
        + "  \"subject\": \"JUST A TEST\",\n"
        + "    \"status\": \"" + status + "\",\n"
        + "  \"created\": \"2017-12-01 17:33:30.000000000\",\n"
        + "  \"updated\": \"2017-12-01 17:33:30.000000000\",\n"
        + "  \"submit_type\": \"MERGE_IF_NECESSARY\",\n"
        + "  \"mergeable\": true,\n"
        + "  \"insertions\": 2,\n"
        + "  \"deletions\": 10,\n"
        + "  \"unresolved_comment_count\": 0,\n"
        + "  \"has_review_started\": true,\n"
        + "  \"_number\": 1082,\n"
        + "  \"owner\": {\n"
        + "    \"_account_id\": 12345\n"
        + "  },\n"
        + "  \"labels\": {\n"
        + "    \"Code-Review\": {\n"
        + "      \"all\": [\n"
        + "        {\n"
        + "          \"value\": 2,\n"
        + "          \"date\": \"2017-01-01 12:00:00.000000000\",\n"
        + "          \"permitted_voting_range\": {\n"
        + "            \"min\": 2,\n"
        + "            \"max\": 2\n"
        + "          },\n"
        + "          \"_account_id\": 123456\n"
        + "        },\n"
        + "        {\n"
        + "          \"value\": 0,\n"
        + "          \"_account_id\": 123456\n"
        + "        },\n"
        + "        {\n"
        + "          \"value\": 0,\n"
        + "          \"_account_id\": 123456\n"
        + "        }\n"
        + "      ],\n"
        + "      \"values\": {\n"
        + "        \"-2\": \"Do not submit\",\n"
        + "        \"-1\": \"I would prefer that you didn\\u0027t submit this\",\n"
        + "        \" 0\": \"No score\",\n"
        + "        \"+1\": \"Looks good to me, but someone else must approve\",\n"
        + "        \"+2\": \"Looks good to me, approved\"\n"
        + "      },\n"
        + "      \"default_value\": 0\n"
        + "    }\n"
        + "},\n"
        + "  \"current_revision\": \"f33bd8687ae27c25254a21012b3c9b4a546db779\",\n"
        + "  \"revisions\": {\n"
        + "    \"f33bd8687ae27c25254a21012b3c9b4a546db779\": {\n"
        + "      \"kind\": \"REWORK\",\n"
        + "      \"_number\": 1,\n"
        + "      \"created\": \"2017-12-07 19:11:59.000000000\",\n"
        + "      \"uploader\": {\n"
        + "        \"_account_id\": 12345\n"
        + "      },\n"
        + "      \"ref\": \"refs/changes/11/11111/1\",\n"
        + "      \"fetch\": {\n"
        + "        \"https\": {\n"
        + "          \"url\": \"https://foo.bar/copybara/test\",\n"
        + "          \"ref\": \"refs/changes/11/11111/1\"\n"
        + "        }\n"
        + "      },\n"
        + "      \"commit\": {\n"
        + "        \"parents\": [\n"
        + "          {\n"
        + "            \"commit\": \"e6b7772add9d2137fd5f879192bd249dfc4d0a00\",\n"
        + "            \"subject\": \"Parent commit description.\"\n"
        + "          }\n"
        + "        ],\n"
        + "        \"author\": {\n"
        + "          \"name\": \"Glorious Copybara\",\n"
        + "          \"email\": \"no-reply@glorious-copybara.com\",\n"
        + "          \"date\": \"2017-12-01 00:00:00.000000000\",\n"
        + "          \"tz\": -480\n"
        + "        },\n"
        + "        \"committer\": {\n"
        + "          \"name\": \"Glorious Copybara\",\n"
        + "          \"email\": \"no-reply@glorious-copybara.com\",\n"
        + "          \"date\": \"2017-12-01 00:00:00.000000000\",\n"
        + "          \"tz\": -480\n"
        + "        },\n"
        + "        \"subject\": \"JUST A TEST\",\n"
        + "        \"message\": \"JUST A TEST\\n\\nSecond line of description.\n\"\n"
        + "      }\n"
        + "    }\n"
        + "  },\n"
        + "  \"messages\": [\n"
        + "      {\n"
        + "        \"id\": \"e6aa8a323fd948cc9986dd4d8b4c253487bab253\",\n"
        + "        \"tag\": \"autogenerated:gerrit:newPatchSet\",\n"
        + "        \"author\": {\n"
        + "          \"_account_id\": 12345,\n"
        + "          \"name\": \"Glorious Copybara\",\n"
        + "          \"email\": \"no-reply@glorious-copybara.com\"\n"
        + "        },\n"
        + "        \"real_author\": {\n"
        + "          \"_account_id\": 12345,\n"
        + "          \"name\": \"Glorious Copybara\",\n"
        + "          \"email\": \"no-reply@glorious-copybara.com\"\n"
        + "        },\n"
        + "        \"date\": \"2017-12-01 00:00:00.000000000\",\n"
        + "        \"message\": \"Uploaded patch set 1.\",\n"
        + "        \"_revision_number\": 1\n"
        + "      }\n"
        + "  ]\n"
        + "}\n";
  }

  public void mockResponse(Predicate<String> filter, String response) throws Exception {
    requestToResponse.put(filter, response.getBytes(StandardCharsets.UTF_8));
  }

  private class CheckRequest implements Predicate<String> {

    private final String method;
    private final String path;

    CheckRequest(String method, String path) {
      this.method = Preconditions.checkNotNull(method);
      this.path = Preconditions.checkNotNull(path);
    }

    @Override
    public boolean test(String s) {
      return s.matches(
          "(\r|\n|.)*" + method + " " + GerritApiTest.this.getHost() + path + "(\r|\n|.)*");
    }
  }
}
