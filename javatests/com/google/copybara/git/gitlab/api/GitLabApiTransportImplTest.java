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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.util.Key;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.credentials.ConstantCredentialIssuer;
import com.google.copybara.credentials.Credential;
import com.google.copybara.credentials.CredentialIssuer;
import com.google.copybara.credentials.CredentialIssuingException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.gitlab.api.entities.GitLabApiEntity;
import com.google.copybara.http.auth.AuthInterceptor;
import com.google.copybara.http.auth.BearerInterceptor;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitLabApiTransportImplTest {

  public static final GsonFactory GSON_FACTORY = new GsonFactory();
  private TestingConsole console;

  @Before
  public void setup() throws Exception {
    console = new TestingConsole();
  }

  @Test
  public void testSimpleGetRequest() throws Exception {
    MockHttpTransport.Builder httpTransport = new MockHttpTransport.Builder();
    httpTransport.setLowLevelHttpResponse(
        new MockLowLevelHttpResponse().setContent(getExampleTestResponseJson()));
    BearerInterceptor bearerInterceptor = getBearerInterceptor();
    GitLabApiTransport underTest =
        getApiTransport(
            httpTransport.build(),
            "https://gitlab.copybara.io/capybara/project",
            bearerInterceptor);

    Optional<TestResponse> testResponse =
        underTest.get(
            "/projects/12345/test_request", TestResponse.class, ImmutableListMultimap.of());

    assertThat(testResponse).isPresent();
    assertThat(testResponse.get().getId()).isEqualTo(12345);
    assertThat(testResponse.get().getDescription()).isEqualTo("capybara");
  }

  @Test
  public void testSimpleGetRequest_parsingFailure_throwsException() throws Exception {
    MockHttpTransport.Builder httpTransport = new MockHttpTransport.Builder();
    httpTransport.setLowLevelHttpResponse(new MockLowLevelHttpResponse().setContent("%foo{)'"));
    BearerInterceptor bearerInterceptor = getBearerInterceptor();
    GitLabApiTransport underTest =
        getApiTransport(
            httpTransport.build(),
            "https://gitlab.copybara.io/capybara/project",
            bearerInterceptor);

    GitLabApiException e =
        assertThrows(
            GitLabApiException.class,
            () ->
                underTest.get(
                    "/projects/12345/test_request",
                    TestResponse.class,
                    ImmutableListMultimap.of()));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Error calling GET on https://gitlab.copybara.io/api/v4/projects/12345/test_request."
                + " Failed to parse response.");
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testSimpleGetRequest_withHttpPort() throws Exception {
    MockHttpTransport.Builder httpTransport = new MockHttpTransport.Builder();
    httpTransport.setLowLevelHttpResponse(
        new MockLowLevelHttpResponse().setContent(getExampleTestResponseJson()));
    BearerInterceptor bearerInterceptor = getBearerInterceptor();
    GitLabApiTransport underTest =
        getApiTransport(
            httpTransport.build(),
            "https://gitlab.copybara.io:8080/capybara/project",
            bearerInterceptor);

    Optional<TestResponse> unused =
        underTest.get(
            "/projects/12345/test_request", TestResponse.class, ImmutableListMultimap.of());

    console
        .assertThat()
        .onceInLog(
            MessageType.VERBOSE,
            "Sending GET request to"
                + " https://gitlab.copybara.io:8080/api/v4/projects/12345/test_request");
  }

  @Test
  public void testErrorGetHttpResponse_throwsException() {
    MockHttpTransport.Builder httpTransport = new MockHttpTransport.Builder();
    httpTransport.setLowLevelHttpResponse(
        new MockLowLevelHttpResponse().setStatusCode(HttpStatusCodes.STATUS_CODE_NOT_FOUND));
    BearerInterceptor bearerInterceptor = getBearerInterceptor();
    GitLabApiTransport underTest =
        getApiTransport(
            httpTransport.build(),
            "https://gitlab.copybara.io/capybara/project",
            bearerInterceptor);

    GitLabApiException e =
        assertThrows(
            GitLabApiException.class,
            () ->
                underTest.get(
                    "/projects/12345/test_request",
                    TestResponse.class,
                    ImmutableListMultimap.of()));

    assertThat(e).hasCauseThat().isInstanceOf(HttpResponseException.class);
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Error calling GET on https://gitlab.copybara.io/api/v4/projects/12345/test_request");
    assertThat(e.getResponseCode()).hasValue(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
  }

  @Test
  public void testSimplePostRequest() throws Exception {
    MockHttpTransport.Builder httpTransport = new MockHttpTransport.Builder();
    httpTransport.setLowLevelHttpResponse(
        new MockLowLevelHttpResponse().setContent(getExampleTestResponseJson()));
    BearerInterceptor bearerInterceptor = getBearerInterceptor();
    GitLabApiTransport underTest =
        getApiTransport(
            httpTransport.build(),
            "https://gitlab.copybara.io/capybara/project",
            bearerInterceptor);
    TestRequest testRequest = new TestRequest(54321, "baracopy");

    Optional<TestResponse> testResponse =
        underTest.post(
            "/projects/12345/test_request",
            testRequest,
            TestResponse.class,
            ImmutableListMultimap.of());

    assertThat(testResponse).isPresent();
    assertThat(testResponse.get().getId()).isEqualTo(12345);
    assertThat(testResponse.get().getDescription()).isEqualTo("capybara");
  }

  @Test
  public void testSimplePostRequest_httpPort() throws Exception {
    MockHttpTransport.Builder httpTransport = new MockHttpTransport.Builder();
    httpTransport.setLowLevelHttpResponse(
        new MockLowLevelHttpResponse().setContent(getExampleTestResponseJson()));
    BearerInterceptor bearerInterceptor = getBearerInterceptor();
    GitLabApiTransport underTest =
        getApiTransport(
            httpTransport.build(),
            "https://gitlab.copybara.io:8080/capybara/project",
            bearerInterceptor);
    TestRequest testRequest = new TestRequest(54321, "baracopy");

    Optional<TestResponse> unused =
        underTest.post(
            "/projects/12345/test_request",
            testRequest,
            TestResponse.class,
            ImmutableListMultimap.of());

    console
        .assertThat()
        .onceInLog(
            MessageType.VERBOSE,
            "Sending POST request to"
                + " https://gitlab.copybara.io:8080/api/v4/projects/12345/test_request");
  }

  @Test
  public void testErrorPostRequest_throwsException() {
    MockHttpTransport.Builder httpTransport = new MockHttpTransport.Builder();
    httpTransport.setLowLevelHttpResponse(
        new MockLowLevelHttpResponse().setStatusCode(HttpStatusCodes.STATUS_CODE_NOT_FOUND));
    BearerInterceptor bearerInterceptor = getBearerInterceptor();
    GitLabApiTransport underTest =
        getApiTransport(
            httpTransport.build(),
            "https://gitlab.copybara.io/capybara/project",
            bearerInterceptor);
    TestRequest testRequest = new TestRequest(54321, "baracopy");

    GitLabApiException e =
        assertThrows(
            GitLabApiException.class,
            () ->
                underTest.post(
                    "/projects/12345/test_request",
                    testRequest,
                    TestResponse.class,
                    ImmutableListMultimap.of()));

    assertThat(e).hasCauseThat().isInstanceOf(HttpResponseException.class);
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Error calling POST on https://gitlab.copybara.io/api/v4/projects/12345/test_request");
    assertThat(e.getResponseCode()).hasValue(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
  }

  @Test
  public void testSimpleDeleteRequest() throws Exception {
    MockHttpTransport.Builder httpTransport = new MockHttpTransport.Builder();
    httpTransport.setLowLevelHttpResponse(
        new MockLowLevelHttpResponse().setContent(getExampleTestResponseJson()));
    BearerInterceptor bearerInterceptor = getBearerInterceptor();
    GitLabApiTransport underTest =
        getApiTransport(
            httpTransport.build(),
            "https://gitlab.copybara.io/capybara/project",
            bearerInterceptor);

    // Should not throw
    underTest.delete("/projects/12345/test_request");
  }

  @Test
  public void testSetPasswordHeaders() throws Exception {
    Map<String, List<String>> headers = new HashMap<>();
    MockHttpTransport httpTransport = getMockHttpTransportWithHeadersMap(headers);
    BearerInterceptor bearerInterceptor = getBearerInterceptor();
    GitLabApiTransport underTest =
        getApiTransport(
            httpTransport, "https://gitlab.copybara.io/capybara/project", bearerInterceptor);

    Optional<TestResponse> unused =
        underTest.get(
            "/projects/12345/test_request", TestResponse.class, ImmutableListMultimap.of());

    assertThat(headers)
        .containsEntry("authorization", ImmutableList.of("Bearer example-access-token"));
  }

  private static BearerInterceptor getBearerInterceptor() {
    CredentialIssuer credentialIssuer =
        ConstantCredentialIssuer.createConstantSecret("test", "example-access-token");
    return new BearerInterceptor(credentialIssuer);
  }

  @Test
  public void testSetPasswordHeaders_credentialsIssueException() throws Exception {
    HashMap<String, List<String>> headers = new HashMap<>();
    MockHttpTransport httpTransport = getMockHttpTransportWithHeadersMap(headers);
    BearerInterceptor bearerInterceptor =
        new BearerInterceptor(
            new CredentialIssuer() {
              @Override
              public Credential issue() throws CredentialIssuingException {
                throw new CredentialIssuingException("oh noes, i failed");
              }

              @Override
              public ImmutableSetMultimap<String, String> describe() {
                return null;
              }
            });
    GitLabApiTransport underTest =
        getApiTransport(
            httpTransport, "https://gitlab.copybara.io/capybara/project", bearerInterceptor);
    TestRequest testRequest = new TestRequest(54321, "baracopy");

    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                underTest.post(
                    "/projects/12345/test_request",
                    testRequest,
                    TestResponse.class,
                    ImmutableListMultimap.of()));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "There was an issue obtaining credentials for"
                + " https://gitlab.copybara.io/api/v4/projects/12345/test_request: oh noes, i"
                + " failed");
  }

  /**
   * Creates a {@link MockHttpTransport} that writes the received HTTP headers to the given map, for
   * test verification.
   *
   * @param mapForHeaders The map to write headers to.
   * @return
   */
  private static MockHttpTransport getMockHttpTransportWithHeadersMap(
      final Map<String, List<String>> mapForHeaders) {
    return new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) {
        return new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() {
            mapForHeaders.putAll(this.getHeaders());
            MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
            response.setContent(getExampleTestResponseJson());
            return response;
          }
        };
      }
    };
  }

  private GitLabApiTransport getApiTransport(
      HttpTransport httpTransport, String url, AuthInterceptor authInterceptor) {
    return new GitLabApiTransportImpl(url, httpTransport, console, authInterceptor);
  }

  private static String getExampleTestResponseJson() {
    try {
      return GSON_FACTORY.toString(new TestResponse(12345, "capybara"));
    } catch (IOException e) {
      throw new RuntimeException("This should not fail!", e);
    }
  }

  @VisibleForTesting
  public static class TestRequest implements GitLabApiEntity {
    @SuppressWarnings("unused")
    @Key
    private final int id;

    @SuppressWarnings("unused")
    @Key
    private final String description;

    public TestRequest(int id, String description) {
      this.id = id;
      this.description = description;
    }
  }

  @VisibleForTesting
  public static class TestResponse implements GitLabApiEntity {
    @Key private int id;
    @Key private String description;

    @SuppressWarnings("unused")
    public TestResponse() {}

    public TestResponse(int id, String description) {
      this.id = id;
      this.description = description;
    }

    public int getId() {
      return id;
    }

    public String getDescription() {
      return description;
    }
  }
}
