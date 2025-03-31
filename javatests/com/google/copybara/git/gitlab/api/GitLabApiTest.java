/*
 * Copyright (C) 2025 Google LLC
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

package com.google.copybara.git.gitlab.api;

import static com.google.api.client.http.HttpStatusCodes.STATUS_CODE_NO_CONTENT;
import static com.google.api.client.http.HttpStatusCodes.STATUS_CODE_SERVER_ERROR;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.util.Key;
import com.google.common.base.Splitter;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.copybara.credentials.ConstantCredentialIssuer;
import com.google.copybara.credentials.CredentialIssuer;
import com.google.copybara.git.gitlab.api.entities.Commit;
import com.google.copybara.git.gitlab.api.entities.CreateMergeRequestParams;
import com.google.copybara.git.gitlab.api.entities.GitLabApiEntity;
import com.google.copybara.git.gitlab.api.entities.GitLabApiParams;
import com.google.copybara.git.gitlab.api.entities.GitLabApiParams.Param;
import com.google.copybara.git.gitlab.api.entities.ListProjectMergeRequestParams;
import com.google.copybara.git.gitlab.api.entities.ListUsersParams;
import com.google.copybara.git.gitlab.api.entities.MergeRequest;
import com.google.copybara.git.gitlab.api.entities.SetExternalStatusCheckParams;
import com.google.copybara.git.gitlab.api.entities.SetExternalStatusCheckResponse;
import com.google.copybara.git.gitlab.api.entities.Project;
import com.google.copybara.git.gitlab.api.entities.UpdateMergeRequestParams;
import com.google.copybara.git.gitlab.api.entities.User;
import com.google.copybara.http.auth.AuthInterceptor;
import com.google.copybara.http.auth.BearerInterceptor;
import com.google.copybara.json.GsonParserUtil;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitLabApiTest {
  public static final GsonFactory GSON_FACTORY = new GsonFactory();
  private static final ImmutableList<ImmutableList<TestResponse>> SAMPLE_PAGES =
      ImmutableList.of(
          ImmutableList.of(new TestResponse(1), new TestResponse(2)),
          ImmutableList.of(new TestResponse(3), new TestResponse(4)),
          ImmutableList.of(new TestResponse(5), new TestResponse(6)));
  private final TestingConsole console = new TestingConsole();

  @Test
  public void testPaginatedGet_singlePage() throws Exception {
    MockHttpTransport httpTransport =
        new PaginatedMockHttpTransport<>(
            "https://gitlab.copybara.io/api/v4",
            "/projects/12345/test_requests",
            ImmutableList.of(SAMPLE_PAGES.getFirst()));
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));

    ImmutableList<TestResponse> fullResponse =
        underTest.paginatedGet(
            "/projects/12345/test_requests",
            TestResponse.class,
            ImmutableListMultimap.of(),
            Integer.MAX_VALUE);

    assertThat(fullResponse).containsExactlyElementsIn(SAMPLE_PAGES.getFirst());
  }

  @Test
  public void paginatedGet_gitLabApiParams_addedToUrlCorrectly() throws Exception {
    PaginatedMockHttpTransport<TestResponse> httpTransport =
        new PaginatedMockHttpTransport<>(
            "https://gitlab.copybara.io/api/v4",
            "/projects/12345/test_requests",
            ImmutableList.of(SAMPLE_PAGES.getFirst()));
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));
    GitLabApiParams gitLabApiParams = () -> ImmutableList.of(new Param("param", "value"));

    ImmutableList<TestResponse> fullResponse =
        underTest.paginatedGet(
            "/projects/12345/test_requests",
            TestResponse.class,
            ImmutableListMultimap.of(),
            Integer.MAX_VALUE,
            gitLabApiParams);

    assertThat(fullResponse).containsExactlyElementsIn(SAMPLE_PAGES.getFirst());
    assertThat(httpTransport.getCapturedUrls())
        .containsExactly(
            "https://gitlab.copybara.io/api/v4/projects/12345/test_requests?"
                + gitLabApiParams.getQueryString()
                + "&per_page="
                + Integer.MAX_VALUE);
  }

  @Test
  public void paginatedGet_gitLabApiParams_existingQueryStringHandledCorrectly() throws Exception {
    PaginatedMockHttpTransport<TestResponse> httpTransport =
        new PaginatedMockHttpTransport<>(
            "https://gitlab.copybara.io/api/v4",
            "/projects/12345/test_requests",
            ImmutableList.of(SAMPLE_PAGES.getFirst()));
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));
    GitLabApiParams gitLabApiParams = () -> ImmutableList.of(new Param("param", "value"));

    ImmutableList<TestResponse> fullResponse =
        underTest.paginatedGet(
            "/projects/12345/test_requests?foo=bar&baz=bat",
            TestResponse.class,
            ImmutableListMultimap.of(),
            Integer.MAX_VALUE,
            gitLabApiParams);

    assertThat(fullResponse).containsExactlyElementsIn(SAMPLE_PAGES.getFirst());
    assertThat(httpTransport.getCapturedUrls())
        .containsExactly(
            "https://gitlab.copybara.io/api/v4/projects/12345/test_requests?foo=bar&baz=bat&"
                + gitLabApiParams.getQueryString()
                + "&per_page="
                + Integer.MAX_VALUE);
  }

  @Test
  public void testPaginatedGet_multiplePages() throws Exception {
    MockHttpTransport httpTransport =
        new PaginatedMockHttpTransport<>(
            "https://gitlab.copybara.io/api/v4", "/projects/12345/test_requests", SAMPLE_PAGES);
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));

    ImmutableList<TestResponse> fullResponse =
        underTest.paginatedGet(
            "/projects/12345/test_requests",
            TestResponse.class,
            ImmutableListMultimap.of(),
            Integer.MAX_VALUE);

    assertThat(fullResponse)
        .containsExactlyElementsIn(
            SAMPLE_PAGES.stream().flatMap(List::stream).collect(toImmutableList()));
  }

  @Test
  public void testPaginatedGet_exceptionIfNextUrlDoesNotMatch() {
    MockHttpTransport httpTransport =
        new PaginatedMockHttpTransport<>(
            "https://bad-gitlab-instance.io/api/v4", "/projects/12345/test_requests", SAMPLE_PAGES);
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));

    VerifyException e =
        assertThrows(
            VerifyException.class,
            () ->
                underTest.paginatedGet(
                    "/projects/12345/test_requests",
                    TestResponse.class,
                    ImmutableListMultimap.of(),
                    2));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "https://bad-gitlab-instance.io/api/v4/projects/12345/test_requests?per_page=2&page=2"
                + " doesn't start with https://gitlab.copybara.io/api/v4");
  }

  @Test
  public void testPaginatedGet_paramHandling() throws Exception {
    PaginatedMockHttpTransport<TestResponse> httpTransport =
        new PaginatedMockHttpTransport<>(
            "https://gitlab.copybara.io/api/v4", "/projects/12345/test_requests", SAMPLE_PAGES);
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));
    // Add some extra params in the requested path, to make sure they're preserved.
    String path = "/projects/12345/test_requests?capy=bara&foo=bar";

    ImmutableList<TestResponse> unused =
        underTest.paginatedGet(path, TestResponse.class, ImmutableListMultimap.of(), 10);

    assertThat(httpTransport.getCapturedUrls())
        .containsExactly(
            "https://gitlab.copybara.io/api/v4/projects/12345/test_requests?capy=bara&foo=bar&per_page=10",
            "https://gitlab.copybara.io/api/v4/projects/12345/test_requests?capy=bara&foo=bar&per_page=10&page=2",
            "https://gitlab.copybara.io/api/v4/projects/12345/test_requests?capy=bara&foo=bar&per_page=10&page=3");
  }

  @Test
  public void paginatedGet_handleUrlQueryParamsCorrectly() throws Exception {
    String path = "/projects/12345/test_requests";
    PaginatedMockHttpTransport<TestResponse> httpTransport =
        new PaginatedMockHttpTransport<>(
            "https://gitlab.copybara.io/api/v4",
            path,
            ImmutableList.of(ImmutableList.of(new TestResponse(1))));
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));
    GitLabApiParams gitLabApiParams =
        () -> ImmutableList.of(new Param("param1", "value1"), new Param("param2", "value2"));

    ImmutableList<TestResponse> unused =
        underTest.paginatedGet(
            path, TestResponse.class, ImmutableListMultimap.of(), 10, gitLabApiParams);

    assertThat(httpTransport.getCapturedUrls())
        .containsExactly(
            "https://gitlab.copybara.io/api/v4/projects/12345/test_requests?param1=value1&param2=value2&per_page=10");
  }

  @Test
  public void testGetMergeRequest() throws Exception {
    String json =
"""
{
  "id": 12345
}
""";
    MockHttpTransportWithCapture httpTransport = new MockHttpTransportWithCapture(json);
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));

    Optional<MergeRequest> response = underTest.getMergeRequest(12345, 456123);

    assertThat(response).isPresent();
    assertThat(response.get().getId()).isEqualTo(12345);
    assertThat(httpTransport.getCapturedUrls())
        .containsExactly("https://gitlab.copybara.io/api/v4/projects/12345/merge_requests/456123");
  }

  @Test
  public void testGetMergeRequest_badHttpResponse() throws Exception {
    GitLabApi underTest = setupApiWithMockedStatusCodeResponse(STATUS_CODE_SERVER_ERROR);

    GitLabApiException e =
        assertThrows(GitLabApiException.class, () -> underTest.getMergeRequest(12345, 456123));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Error calling GET on"
                + " https://gitlab.copybara.io/api/v4/projects/12345/merge_requests/456123");
  }

  private GitLabApi setupApiWithMockedStatusCodeResponse(int statusCode) {
    MockHttpTransport httpTransport =
        new MockHttpTransport.Builder()
            .setLowLevelHttpResponse(new MockLowLevelHttpResponse().setStatusCode(statusCode))
            .build();
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));
    return underTest;
  }

  @Test
  public void testGetMergeRequest_emptyResponse() throws Exception {
    GitLabApi underTest = setupApiWithMockedStatusCodeResponse(STATUS_CODE_NO_CONTENT);

    Optional<MergeRequest> mergeRequest = underTest.getMergeRequest(12345, 456123);

    assertThat(mergeRequest).isEmpty();
  }

  @Test
  public void getProjectMergeRequests_apiError_throwsException() throws Exception {
    MockHttpTransport httpTransport =
        new MockHttpTransport.Builder()
            .setLowLevelHttpResponse(
                new MockLowLevelHttpResponse().setStatusCode(STATUS_CODE_SERVER_ERROR))
            .build();
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));

    GitLabApiException e =
        assertThrows(
            GitLabApiException.class,
            () ->
                underTest.getProjectMergeRequests(
                    12345, ListProjectMergeRequestParams.getDefaultInstance()));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Error calling GET on"
                + " https://gitlab.copybara.io/api/v4/projects/12345/merge_requests?per_page=50");
  }

  @Test
  public void getProjectMergeRequests_noParams_singlePageResponseWorksSuccessfully()
      throws Exception {
    ArrayList<MergeRequest> mergeRequests =
        GsonParserUtil.parseString(
            "[{\"id\": 1}, {\"id\": 2}]",
            TypeToken.getParameterized(ArrayList.class, MergeRequest.class).getType(),
            false);
    PaginatedMockHttpTransport<MergeRequest> httpTransport =
        new PaginatedMockHttpTransport<>(
            "https://gitlab.copybara.io/api/v4",
            "/projects/12345/merge_requests",
            ImmutableList.of(ImmutableList.copyOf(mergeRequests)));
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));

    ImmutableList<MergeRequest> fullResponse =
        underTest.getProjectMergeRequests(
            12345, ListProjectMergeRequestParams.getDefaultInstance());

    assertThat(fullResponse.stream().map(MergeRequest::getId)).containsExactly(1, 2).inOrder();
    assertThat(httpTransport.getCapturedUrls())
        .containsExactly(
            "https://gitlab.copybara.io/api/v4/projects/12345/merge_requests?per_page=50");
  }

  @Test
  public void getProjectMergeRequests_withParams_singlePageResponseWorksSuccessfully()
      throws Exception {
    ArrayList<MergeRequest> mergeRequests =
        GsonParserUtil.parseString(
            "[{\"id\": 1}, {\"id\": 2}]",
            TypeToken.getParameterized(ArrayList.class, MergeRequest.class).getType(),
            false);
    PaginatedMockHttpTransport<MergeRequest> httpTransport =
        new PaginatedMockHttpTransport<>(
            "https://gitlab.copybara.io/api/v4",
            "/projects/12345/merge_requests",
            ImmutableList.of(ImmutableList.copyOf(mergeRequests)));
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));

    ImmutableList<MergeRequest> fullResponse =
        underTest.getProjectMergeRequests(
            12345, new ListProjectMergeRequestParams(Optional.of("my_branch")));

    assertThat(fullResponse.stream().map(MergeRequest::getId)).containsExactly(1, 2).inOrder();
    assertThat(httpTransport.getCapturedUrls())
        .containsExactly(
            "https://gitlab.copybara.io/api/v4/projects/12345/merge_requests?source_branch=my_branch&per_page=50");
  }

  @Test
  public void getProjectMergeRequests_noParams_multiPageResponseWorksSuccessfully()
      throws Exception {
    ArrayList<MergeRequest> page1 =
        GsonParserUtil.parseString(
            "[{\"id\": 1}, {\"id\": 2}]",
            TypeToken.getParameterized(ArrayList.class, MergeRequest.class).getType(),
            false);
    ArrayList<MergeRequest> page2 =
        GsonParserUtil.parseString(
            "[{\"id\": 3}, {\"id\": 4}]",
            TypeToken.getParameterized(ArrayList.class, MergeRequest.class).getType(),
            false);
    PaginatedMockHttpTransport<MergeRequest> httpTransport =
        new PaginatedMockHttpTransport<>(
            "https://gitlab.copybara.io/api/v4",
            "/projects/12345/merge_requests",
            ImmutableList.of(ImmutableList.copyOf(page1), ImmutableList.copyOf(page2)));
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));

    ImmutableList<MergeRequest> fullResponse =
        underTest.getProjectMergeRequests(
            12345, ListProjectMergeRequestParams.getDefaultInstance());

    assertThat(fullResponse.stream().map(MergeRequest::getId))
        .containsExactly(1, 2, 3, 4)
        .inOrder();
    assertThat(httpTransport.getCapturedUrls())
        .containsExactly(
            "https://gitlab.copybara.io/api/v4/projects/12345/merge_requests?per_page=50",
            "https://gitlab.copybara.io/api/v4/projects/12345/merge_requests?per_page=50&page=2");
  }

  @Test
  public void getProjectMergeRequests_withParams_multiPageResponseWorksSuccessfully()
      throws Exception {
    ArrayList<MergeRequest> page1 =
        GsonParserUtil.parseString(
            "[{\"id\": 1}, {\"id\": 2}]",
            TypeToken.getParameterized(ArrayList.class, MergeRequest.class).getType(),
            false);
    ArrayList<MergeRequest> page2 =
        GsonParserUtil.parseString(
            "[{\"id\": 3}, {\"id\": 4}]",
            TypeToken.getParameterized(ArrayList.class, MergeRequest.class).getType(),
            false);
    PaginatedMockHttpTransport<MergeRequest> httpTransport =
        new PaginatedMockHttpTransport<>(
            "https://gitlab.copybara.io/api/v4",
            "/projects/12345/merge_requests",
            ImmutableList.of(ImmutableList.copyOf(page1), ImmutableList.copyOf(page2)));
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));

    ImmutableList<MergeRequest> fullResponse =
        underTest.getProjectMergeRequests(
            12345, new ListProjectMergeRequestParams(Optional.of("my_branch")));

    assertThat(fullResponse.stream().map(MergeRequest::getId))
        .containsExactly(1, 2, 3, 4)
        .inOrder();
    assertThat(httpTransport.getCapturedUrls())
        .containsExactly(
            "https://gitlab.copybara.io/api/v4/projects/12345/merge_requests?source_branch=my_branch&per_page=50",
            "https://gitlab.copybara.io/api/v4/projects/12345/merge_requests?source_branch=my_branch&per_page=50&page=2");
  }

  @Test
  public void testGetProject() throws Exception {
    String json =
"""
{
  "id": 12345
}
""";
    String urlEncodedPath = URLEncoder.encode("capybara/project", UTF_8);
    MockHttpTransportWithCapture httpTransport = new MockHttpTransportWithCapture(json);
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));

    Optional<Project> response = underTest.getProject(urlEncodedPath);

    assertThat(response).isPresent();
    assertThat(response.get().getId()).isEqualTo(12345);
    assertThat(httpTransport.getCapturedUrls())
        .containsExactly("https://gitlab.copybara.io/api/v4/projects/" + urlEncodedPath);
  }

  @Test
  public void testGetProject_badHttpResponse() {
    String urlEncodedPath = URLEncoder.encode("capybara/project", UTF_8);
    GitLabApi underTest = setupApiWithMockedStatusCodeResponse(STATUS_CODE_SERVER_ERROR);

    GitLabApiException e =
        assertThrows(GitLabApiException.class, () -> underTest.getProject(urlEncodedPath));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Error calling GET on https://gitlab.copybara.io/api/v4/projects/" + urlEncodedPath);
    assertThat(e).hasCauseThat().isInstanceOf(HttpResponseException.class);
  }

  @Test
  public void testGetProject_emptyResponse() throws Exception {
    String urlEncodedPath = URLEncoder.encode("capybara/project", UTF_8);
    GitLabApi underTest = setupApiWithMockedStatusCodeResponse(STATUS_CODE_NO_CONTENT);

    Optional<Project> project = underTest.getProject(urlEncodedPath);

    assertThat(project).isEmpty();
  }

  @Test
  public void testGetCommit() throws Exception {
    String json =
"""
{
  "id": 12345
}
""";
    MockHttpTransportWithCapture httpTransport = new MockHttpTransportWithCapture(json);
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));

    Optional<Commit> response = underTest.getCommit(12345, "refs/heads/cl_12345");

    assertThat(response).isPresent();
    assertThat(response.get().getId()).isEqualTo(12345);
  }

  @Test
  public void testGetCommit_badHttpResponse() {
    GitLabApi underTest = setupApiWithMockedStatusCodeResponse(STATUS_CODE_SERVER_ERROR);

    GitLabApiException e =
        assertThrows(
            GitLabApiException.class, () -> underTest.getCommit(12345, "refs/heads/cl_12345"));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Error calling GET on"
                + " https://gitlab.copybara.io/api/v4/projects/12345/repository/commits/refs/heads/cl_12345");
    assertThat(e).hasCauseThat().isInstanceOf(HttpResponseException.class);
  }

  @Test
  public void testGetCommit_emptyResponse() throws Exception {
    GitLabApi underTest = setupApiWithMockedStatusCodeResponse(STATUS_CODE_NO_CONTENT);

    Optional<Commit> project = underTest.getCommit(12345, "refs/heads/cl_12345");

    assertThat(project).isEmpty();
  }

  @Test
  public void getListUsers() throws Exception {
    String json =
"""
[{"id": 12345}, {"id": 6789}]
""";
    MockHttpTransportWithCapture httpTransport = new MockHttpTransportWithCapture(json);
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));

    ImmutableList<User> response = underTest.getListUsers(new ListUsersParams("capybara"));

    assertThat(response.stream().map(User::getId)).containsExactly(12345, 6789).inOrder();
    assertThat(httpTransport.getCapturedUrls())
        .containsExactly("https://gitlab.copybara.io/api/v4/users?username=capybara&per_page=50");
  }

  @Test
  public void getListUsers_emptyResponse() throws Exception {
    GitLabApi underTest = setupApiWithMockedStatusCodeResponse(STATUS_CODE_NO_CONTENT);

    ImmutableList<User> response = underTest.getListUsers(new ListUsersParams("capybara"));

    assertThat(response).isEmpty();
  }

  @Test
  public void getListUsers_badHttpResponse() {
    GitLabApi underTest = setupApiWithMockedStatusCodeResponse(STATUS_CODE_SERVER_ERROR);

    GitLabApiException e =
        assertThrows(
            GitLabApiException.class,
            () -> underTest.getListUsers(new ListUsersParams("capybara")));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Error calling GET on"
                + " https://gitlab.copybara.io/api/v4/users?username=capybara&per_page=50");
    assertThat(e).hasCauseThat().isInstanceOf(HttpResponseException.class);
  }

  @Test
  public void setExternalStatusCheck() throws Exception {
    String json =
"""
{"id": 12345, "merge_request": {"iid": 99999}, "external_status_check": {"id": 12345}}
""";
    MockHttpTransportWithCapture httpTransport = new MockHttpTransportWithCapture(json);
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));

    SetExternalStatusCheckParams params =
        new SetExternalStatusCheckParams(12345, 99999, "shaValue", 12345, "passed");
    Optional<SetExternalStatusCheckResponse> response = underTest.setExternalStatusCheck(params);

    assertThat(response).isPresent();
    assertThat(response.get().getMergeRequest().getIid()).isEqualTo(99999);
    assertThat(response.get().getExternalStatusCheck().getStatusCheckId()).isEqualTo(12345);
  }

  @Test
  public void setExternalStatusCheck_emptyResponse() throws Exception {
    GitLabApi underTest = setupApiWithMockedStatusCodeResponse(STATUS_CODE_NO_CONTENT);

    SetExternalStatusCheckParams params =
        new SetExternalStatusCheckParams(12345, 99999, "shaValue", 12345, "passed");
    Optional<SetExternalStatusCheckResponse> response = underTest.setExternalStatusCheck(params);

    assertThat(response).isEmpty();
  }

  @Test
  public void setExternalStatusCheck_badHttpResponse() {
    GitLabApi underTest = setupApiWithMockedStatusCodeResponse(STATUS_CODE_SERVER_ERROR);

    SetExternalStatusCheckParams params =
        new SetExternalStatusCheckParams(12345, 99999, "shaValue", 12345, "passed");

    GitLabApiException e =
        assertThrows(GitLabApiException.class, () -> underTest.setExternalStatusCheck(params));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Error calling POST on"
                + " https://gitlab.copybara.io/api/v4/projects/12345/merge_requests/99999/status_check_responses");
    assertThat(e).hasCauseThat().isInstanceOf(HttpResponseException.class);
  }

  @Test
  public void createMergeRequest() throws Exception {
    String json =
"""
{"iid": 1234}
""";
    MockHttpTransportWithCapture httpTransport = new MockHttpTransportWithCapture(json);
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));

    CreateMergeRequestParams params =
        new CreateMergeRequestParams(
            12345,
            "capys_source_branch",
            "capys_target_branch",
            "capys_title",
            "capys_description",
            ImmutableList.of(1, 2, 3, 4, 5));
    Optional<MergeRequest> response = underTest.createMergeRequest(params);

    assertThat(response).isPresent();
    assertThat(response.get().getIid()).isEqualTo(1234);
  }

  @Test
  public void createMergeRequest_emptyResponse() throws Exception {
    GitLabApi underTest = setupApiWithMockedStatusCodeResponse(STATUS_CODE_NO_CONTENT);

    CreateMergeRequestParams params =
        new CreateMergeRequestParams(
            12345,
            "capys_source_branch",
            "capys_target_branch",
            "capys_title",
            "capys_description",
            ImmutableList.of(1, 2, 3, 4, 5));
    Optional<MergeRequest> response = underTest.createMergeRequest(params);

    assertThat(response).isEmpty();
  }

  @Test
  public void createMergeRequest_badHttpResponse() {
    GitLabApi underTest = setupApiWithMockedStatusCodeResponse(STATUS_CODE_SERVER_ERROR);

    CreateMergeRequestParams params =
        new CreateMergeRequestParams(
            12345,
            "capys_source_branch",
            "capys_target_branch",
            "capys_title",
            "capys_description",
            ImmutableList.of(1, 2, 3, 4, 5));
    GitLabApiException e =
        assertThrows(GitLabApiException.class, () -> underTest.createMergeRequest(params));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Error calling POST on"
                + " https://gitlab.copybara.io/api/v4/projects/12345/merge_requests");
    assertThat(e).hasCauseThat().isInstanceOf(HttpResponseException.class);
  }

  @Test
  public void updateMergeRequest() throws Exception {
    String json =
"""
{"iid": 1234}
""";
    MockHttpTransportWithCapture httpTransport = new MockHttpTransportWithCapture(json);
    GitLabApi underTest =
        new GitLabApi(
            getApiTransport(
                httpTransport,
                "https://gitlab.copybara.io/capybara/project",
                getBearerInterceptor()));

    UpdateMergeRequestParams params =
        new UpdateMergeRequestParams(
            12345, 99999, "capys_title", "capys_description", ImmutableList.of(1, 2, 3, 4, 5),
            null);
    Optional<MergeRequest> response = underTest.updateMergeRequest(params);

    assertThat(response).isPresent();
    assertThat(response.get().getIid()).isEqualTo(1234);
  }

  @Test
  public void updateMergeRequest_emptyResponse() throws Exception {
    GitLabApi underTest = setupApiWithMockedStatusCodeResponse(STATUS_CODE_NO_CONTENT);

    UpdateMergeRequestParams params =
        new UpdateMergeRequestParams(
            12345, 99999, "capys_title", "capys_description", ImmutableList.of(1, 2, 3, 4, 5),
            null);
    Optional<MergeRequest> response = underTest.updateMergeRequest(params);

    assertThat(response).isEmpty();
  }

  @Test
  public void updateMergeRequest_badHttpResponse() {
    GitLabApi underTest = setupApiWithMockedStatusCodeResponse(STATUS_CODE_SERVER_ERROR);

    UpdateMergeRequestParams params =
        new UpdateMergeRequestParams(
            12345, 99999, "capys_title", "capys_description", ImmutableList.of(1, 2, 3, 4, 5),
            null);
    GitLabApiException e =
        assertThrows(GitLabApiException.class, () -> underTest.updateMergeRequest(params));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Error calling PUT on"
                + " https://gitlab.copybara.io/api/v4/projects/12345/merge_requests/99999");
    assertThat(e).hasCauseThat().isInstanceOf(HttpResponseException.class);
  }

  private static BearerInterceptor getBearerInterceptor() {
    CredentialIssuer credentialIssuer =
        ConstantCredentialIssuer.createConstantSecret("test", "example-access-token");
    return new BearerInterceptor(credentialIssuer);
  }

  private GitLabApiTransport getApiTransport(
      HttpTransport httpTransport, String url, AuthInterceptor authInterceptor) {
    return new GitLabApiTransportImpl(url, httpTransport, console, authInterceptor);
  }

  private static class MockHttpTransportWithCapture extends MockHttpTransport {
    private final ImmutableList.Builder<String> capturedUrls = ImmutableList.builder();
    private final String response;

    public MockHttpTransportWithCapture(String response) {
      super();
      this.response = response;
    }

    /**
     * Returns the URLs requested from this transport.
     *
     * @return a list of URLs.
     */
    public ImmutableList<String> getCapturedUrls() {
      return capturedUrls.build();
    }

    @Override
    public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
      capturedUrls.add(url);
      return new MockLowLevelHttpRequest()
          .setResponse(new MockLowLevelHttpResponse().setContent(response));
    }
  }

  private static class PaginatedMockHttpTransport<T extends GitLabApiEntity>
      extends MockHttpTransport {
    private final String apiUrl;
    private final String path;
    private final ImmutableList<ImmutableList<T>> pages;
    private final ImmutableList.Builder<String> capturedUrls = ImmutableList.builder();

    public PaginatedMockHttpTransport(
        String apiUrl, String path, ImmutableList<ImmutableList<T>> pages) {
      this.apiUrl = apiUrl;
      this.path = path;
      this.pages = pages;
    }

    /**
     * Returns the URLs requested from this transport.
     *
     * @return a list of URLs.
     */
    public ImmutableList<String> getCapturedUrls() {
      return capturedUrls.build();
    }

    @Override
    public LowLevelHttpRequest buildRequest(String method, String url) {
      capturedUrls.add(url);

      return new MockLowLevelHttpRequest() {
        @Override
        public LowLevelHttpResponse execute() throws IOException {
          MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
          LinkedHashMap<String, String> urlParamsMap = getUrlParamsMap(url);
          int page = Integer.parseInt(urlParamsMap.getOrDefault("page", "1"));
          // GitLab's page parameter starts at 1, not 0.
          int index = page - 1;

          if (index < pages.size()) {
            response.setContent(GSON_FACTORY.toString(pages.get(index)));

            if (index != pages.size() - 1) {
              // Add a link header if we're not at the last index of the page list.
              response.setHeaderNames(ImmutableList.of("link"));
              urlParamsMap.put("page", String.valueOf(++page));
              String nextUrl = apiUrl + path + constructUrlParam(urlParamsMap);
              response.setHeaderValues(ImmutableList.of("<" + nextUrl + ">; rel=\"next\""));
            }
          }

          return response;
        }
      };
    }

    private static LinkedHashMap<String, String> getUrlParamsMap(String url) {
      return Splitter.on(',')
          .trimResults()
          .splitToStream(URI.create(url).getQuery())
          .flatMap(query -> Splitter.on('&').splitToStream(query))
          .map(s -> Splitter.on('=').splitToList(s))
          .collect(
              Collectors.toMap(List::getFirst, List::getLast, (o, n) -> n, LinkedHashMap::new));
    }

    private static String constructUrlParam(LinkedHashMap<String, String> params) {
      StringBuilder sb = new StringBuilder();
      for (Entry<String, String> param : params.sequencedEntrySet()) {
        if (sb.isEmpty()) {
          sb.append('?');
        } else {
          sb.append('&');
        }
        sb.append(param.getKey()).append("=").append(param.getValue());
      }
      return sb.toString();
    }
  }

  /**
   * A response class used for GitLab API related tests. This is only intended for testing purposes,
   * and public visibility is required for GSON support.
   */
  public static class TestResponse implements GitLabApiEntity {
    @Key public int id;

    @SuppressWarnings("unused")
    public TestResponse() {}

    public TestResponse(int id) {
      this.id = id;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof TestResponse)) {
        return false;
      }

      return this.id == ((TestResponse) obj).id;
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }
  }
}
