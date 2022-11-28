/*
 * Copyright (C) 2023 Google Inc.
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

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.copybara.git.GitRepository.newBareRepo;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.copybara.git.GitRepository;
import com.google.copybara.git.github.api.testing.AbstractGitHubGraphQLApiTest;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitHubGraphQLTest extends AbstractGitHubGraphQLApiTest {

  private MockHttpTransport httpTransport;

  private Map<String, MockLowLevelHttpResponse> requestToResponse;
  private Map<String, Predicate<String>> requestValidators;
  private Path credentialsFile;

  @Before
  public void setUp() {
    requestToResponse.clear();
    requestValidators.clear();
  }

  @Override
  public GitHubApiTransport getTransport() throws Exception {
    credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, "https://user:SECRET@github.com".getBytes(UTF_8));
    GitRepository repo =
        newBareRepo(
                Files.createTempDirectory("test_repo"),
                getGitEnv(),
                /* verbose= */ true,
                DEFAULT_TIMEOUT,
                /* noVerify= */ false)
            .init()
            .withCredentialHelper("store --file=" + credentialsFile);

    requestToResponse = new HashMap<>();
    requestValidators = new HashMap<>();
    httpTransport =
        new MockHttpTransport() {
          @Override
          public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
            String requestString = method + " " + url;
            MockLowLevelHttpRequest request =
                new MockLowLevelHttpRequest() {
                  @Override
                  public LowLevelHttpResponse execute() throws IOException {
                    System.err.println(getContentAsString());

                    Predicate<String> validator = requestValidators.get(method + " " + url);
                    if (validator != null) {
                      assertWithMessage("Request content did not match expected values.")
                          .that(validator.test(getContentAsString()))
                          .isTrue();
                    }
                    return super.execute();
                  }
                };
            MockLowLevelHttpResponse response = requestToResponse.get(requestString);
            if (response == null) {
              response = new MockLowLevelHttpResponse();
              response.setContent(
                  String.format(
                      "{ 'message' : 'This is not the repo you are looking for! %s %s',"
                          + " 'documentation_url' : 'http://github.com/graphql'}",
                      method, url));
              response.setStatusCode(404);
            }
            request.setResponse(response);
            return request;
          }
        };
    return new GitHubApiTransportImpl(
        repo,
        httpTransport,
        APIType.GRAPHQL,
        "some_storage_file",
        new TestingConsole());
  }

  @Override
  public void trainMockPost(Predicate<String> requestValidator, byte[] response) {
    String path = "POST https://api.github.com/graphql";
    requestToResponse.put(path, new MockLowLevelHttpResponse().setContent(response));
    requestValidators.put(path, requestValidator);
  }
}