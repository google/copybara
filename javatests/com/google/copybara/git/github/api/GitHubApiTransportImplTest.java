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
package com.google.copybara.git.github.api;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.git.GitRepository.newBareRepo;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableList;
import com.google.copybara.exception.RepoException;
import com.google.copybara.git.GitRepository;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitHubApiTransportImplTest {

  private static final int STATUS_CODE = 400;
  private static final String ERROR_MESSAGE = "errorMessage";
  private MockHttpTransport httpTransport;

  private GitHubApiTransport transport;
  private GitRepository repo;

  @Before
  public void setup() throws Exception {
    Path credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, "https://user:SECRET@github.com".getBytes(UTF_8));
    repo =
        newBareRepo(Files.createTempDirectory("test_repo"), getGitEnv(), /*verbose=*/ true,
            DEFAULT_TIMEOUT, /*noVerify=*/ false)
            .init()
            .withCredentialHelper("store --file=" + credentialsFile);
  }

  @Test
  public void testGetThrowsIOException() throws Exception {
    runTestThrowsIOException(() -> transport.get("foo/bar", String.class));
  }

  @Test
  public void testGetThrowsHttpResponseException() throws Exception {
    runTestThrowsHttpResponseException(() -> transport.get("foo/bar", String.class));
  }

  @Test
  public void testPostThrowsIOException() throws Exception {
    runTestThrowsIOException(() -> transport.post("foo/bar", String.class, Status.class));
  }

  @Test
  public void testPostThrowsHttpResponseException() throws Exception {
    runTestThrowsHttpResponseException(() -> transport.post("foo/bar", String.class, Status.class));
  }

  @Test
  public void testPasswordHeaderSet() throws Exception {
    Map<String, List<String>> headers = new HashMap<>();
    httpTransport = new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) {
        return new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            headers.putAll(this.getHeaders());
            MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
            response.setContent("foo");
            return response;
          }
        };
      }
    };
    transport = new GitHubApiTransportImpl(repo, httpTransport, "store", new TestingConsole());
    transport.get("foo/bar", String.class);
    assertThat(headers).containsEntry("authorization", ImmutableList.of("Basic dXNlcjpTRUNSRVQ="));
  }

  private void runTestThrowsHttpResponseException(Callable<?> c) throws Exception {
    HttpResponseException ex =
        new HttpResponseException.Builder(STATUS_CODE, ERROR_MESSAGE, new HttpHeaders()).build();
    httpTransport = createMockHttpTransport(ex);
    transport = new GitHubApiTransportImpl(repo, httpTransport, "store", new TestingConsole());
    try {
      c.call();
      fail();
    } catch (GitHubApiException e) {
      assertThat(e.getHttpCode()).isEqualTo(STATUS_CODE);
      assertThat(e.getError()).isNull();
    }
  }

  private void runTestThrowsIOException(Callable<?> c) throws Exception {
    IOException ioException = new IOException();
    httpTransport = createMockHttpTransport(ioException);
    transport = new GitHubApiTransportImpl(repo, httpTransport, "store", new TestingConsole());
    try {
      c.call();
      fail();
    } catch (RepoException e) {
      assertThat(e.getCause()).isEqualTo(ioException);
    }
  }

  private MockHttpTransport createMockHttpTransport(IOException ioException) {
    return new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) {
        return new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            throw ioException;
          }
        };
      }
    };
  }
}
