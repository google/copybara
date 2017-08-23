/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.copybara.git.github_api;


import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.copybara.git.GitRepository.newBareRepo;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.Json;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.git.GitRepository;
import com.google.copybara.git.github_api.Issue.Label;
import com.google.copybara.profiler.LogProfilerListener;
import com.google.copybara.profiler.Profiler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GithubApiTest {


  private MockHttpTransport httpTransport;
  private GithubApi api;
  private Map<String, byte[]> requestToResponse;

  @Before
  public void setup() throws Exception {

    Path credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, "https://user:SECRET@github.com".getBytes(UTF_8));
    GitRepository repo = newBareRepo(Files.createTempDirectory("test_repo"),
        getGitEnv(), /*verbose=*/true)
        .init()
        .withCredentialHelper("store --file=" + credentialsFile);

    requestToResponse = new HashMap<>();
    httpTransport = new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
        MockLowLevelHttpRequest request = new MockLowLevelHttpRequest();
        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
        response.setContentType(Json.MEDIA_TYPE);
        byte[] content = requestToResponse.get(method + " " + url);
        assertWithMessage("'" + method + " " + url + "'").that(content).isNotNull();
        response.setContent(content);
        request.setResponse(response);
        return request;
      }
    };
    GitHubApiTransportImpl transport = new GitHubApiTransportImpl(repo, httpTransport);
    Profiler profiler = new Profiler(Ticker.systemTicker());
    profiler.init(ImmutableList.of(new LogProfilerListener()));
    api = new GithubApi(transport, profiler);
  }

  @Test
  public void testGetPulls() throws RepoException, ValidationException, IOException {
    requestToResponse.put("GET https://api.github.com/repos/example/project/pulls",
        getResource("pulls_testdata.json"));
    ImmutableList<PullRequest> pullRequests = api.getPullRequests("example/project");

    assertThat(pullRequests).hasSize(2);
    assertThat(pullRequests.get(0).getNumber()).isEqualTo(12345);
    assertThat(pullRequests.get(1).getNumber()).isEqualTo(12346);

    assertThat(pullRequests.get(0).getState()).isEqualTo("open");
    assertThat(pullRequests.get(1).getState()).isEqualTo("closed");

    assertThat(pullRequests.get(0).getTitle()).isEqualTo("[TEST] example pull request one");
    assertThat(pullRequests.get(1).getTitle()).isEqualTo("Another title");

    assertThat(pullRequests.get(0).getBody()).isEqualTo("Example body.\r\n");
    assertThat(pullRequests.get(1).getBody()).isEqualTo("Some text\r\n"
        + "And even more text\r\n");

    assertThat(pullRequests.get(0).getHead().getLabel())
        .isEqualTo("googletestuser:example-branch");
    assertThat(pullRequests.get(0).getHead().getRef()).isEqualTo("example-branch");
    assertThat(pullRequests.get(0).getHead().getSha()).isEqualTo(
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

    assertThat(pullRequests.get(1).getHead().getLabel())
        .isEqualTo("anothergoogletestuser:another-branch");
    assertThat(pullRequests.get(1).getHead().getRef()).isEqualTo("another-branch");
    assertThat(pullRequests.get(1).getHead().getSha()).isEqualTo(
        "dddddddddddddddddddddddddddddddddddddddd");
  }

  @Test
  public void testGetPull() throws RepoException, ValidationException, IOException {
    requestToResponse.put("GET https://api.github.com/repos/example/project/pulls/12345",
        getResource("pulls_12345_testdata.json"));
    PullRequest pullRequest = api.getPullRequest("example/project", 12345);

    assertThat(pullRequest.getNumber()).isEqualTo(12345);
    assertThat(pullRequest.getState()).isEqualTo("open");
    assertThat(pullRequest.getTitle()).isEqualTo("[TEST] example pull request one");
    assertThat(pullRequest.getBody()).isEqualTo("Example body.\r\n");
    assertThat(pullRequest.getHead().getLabel())
        .isEqualTo("googletestuser:example-branch");
    assertThat(pullRequest.getHead().getRef()).isEqualTo("example-branch");
    assertThat(pullRequest.getHead().getSha()).isEqualTo(
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
  }

  @Test
  public void testGetIssue() throws RepoException, ValidationException, IOException {
    requestToResponse.put("GET https://api.github.com/repos/example/project/issues/12345",
        getResource("issues_12345_testdata.json"));
    Issue issue = api.getIssue("example/project", 12345);

    assertThat(issue.getNumber()).isEqualTo(12345);
    assertThat(issue.getState()).isEqualTo("open");
    assertThat(issue.getTitle()).isEqualTo("[TEST] example pull request one");
    assertThat(issue.getBody()).isEqualTo("Example body.\r\n");
    assertThat(Lists.transform(issue.getLabels(), Label::getName))
        .containsExactly("cla: yes");
  }

  private byte[] getResource(String testfile) throws IOException {
    return Files.readAllBytes(
        Paths.get(System.getenv("TEST_SRCDIR"),
            "copybara/javatests/com/google/copybara/git/github_api")
            .resolve(testfile));
  }
}
