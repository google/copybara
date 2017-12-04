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

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.copybara.git.GerritOptions;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.git.GitTestUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

  @Before
  public void setUp() throws Exception {
    OptionsBuilder options = new OptionsBuilder()
        .setWorkdirToRealTempDir()
        .setEnvironment(GitTestUtil.getGitEnv())
        .setOutputRootToTmpDir();
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
        response.setContent(("REQUEST: " + requestString));
        return request;
      }
    };

    GerritOptions gerritOptions = new GerritOptions(
        () -> options.general, options.git) {
      @Override
      protected HttpTransport getHttpTransport() {
        return httpTransport;
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
        + ")]}'\n"
        + "[\n"
        + "  {\n"
        + "    \"id\": \"copybara-team%2Fcopybara~master~" + CHANGE_ID + "\",\n"
        + "    \"project\": \"copybara-team/copybara\",\n"
        + "    \"branch\": \"master\",\n"
        + "    \"hashtags\": [],\n"
        + "    \"change_id\": \"" + CHANGE_ID + "\",\n"
        + "    \"subject\": \"JUST A TEST\",\n"
        + "    \"status\": \"NEW\",\n"
        + "    \"created\": \"2017-12-01 17:33:30.000000000\",\n"
        + "    \"updated\": \"2017-12-01 17:37:59.000000000\",\n"
        + "    \"submit_type\": \"REBASE_IF_NECESSARY\",\n"
        + "    \"mergeable\": true,\n"
        + "    \"insertions\": 1,\n"
        + "    \"deletions\": 1,\n"
        + "    \"unresolved_comment_count\": 0,\n"
        + "    \"has_review_started\": true,\n"
        + "    \"_number\": 1234567,\n"
        + "    \"owner\": {\n"
        + "      \"_account_id\": 12345\n"
        + "    }\n"
        + "  }\n"
        + "]");

    List<ChangeInfo> changes = gerritApi.getChanges(new ChangesQuery("status:open"));
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).getId()).contains(CHANGE_ID);
    assertThat(changes.get(0).getStatus()).isEqualTo(ChangeStatus.NEW);
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
