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
import com.google.copybara.git.gitlab.api.entities.GitLabApiEntity;
import com.google.copybara.git.gitlab.api.entities.MergeRequest;
import com.google.copybara.git.gitlab.api.entities.Project;
import com.google.copybara.http.auth.AuthInterceptor;
import com.google.copybara.http.auth.BearerInterceptor;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
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
