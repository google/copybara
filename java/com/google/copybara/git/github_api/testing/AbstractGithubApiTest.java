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

package com.google.copybara.git.github_api.testing;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.client.json.gson.GsonFactory;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.copybara.git.github_api.CreatePullRequest;
import com.google.copybara.git.github_api.GitHubApiTransport;
import com.google.copybara.git.github_api.GithubApi;
import com.google.copybara.git.github_api.Issue;
import com.google.copybara.git.github_api.Issue.Label;
import com.google.copybara.git.github_api.PullRequest;
import com.google.copybara.git.github_api.Ref;
import com.google.copybara.git.github_api.Review;
import com.google.copybara.profiler.LogProfilerListener;
import com.google.copybara.profiler.Profiler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Test;

/**
 * Base test to run the same tests on various implementations of the GitHubApiTransport
 */
public abstract class AbstractGithubApiTest {

  protected GithubApi api;

  @Before
  public void setUpFamework() throws Exception {
    Profiler profiler = new Profiler(Ticker.systemTicker());
    profiler.init(ImmutableList.of(new LogProfilerListener()));
    api = new GithubApi(getTransport(), profiler);
  }

  public abstract GitHubApiTransport getTransport() throws Exception;

  public abstract void trainMockGet(String apiPath, byte[] response) throws Exception;
  public abstract void trainMockPost(String apiPath, Predicate<String> validator, byte[] response)
      throws Exception;

  @Test
  public void testGetPulls() throws Exception {
    trainMockGet("/repos/example/project/pulls", getResource("pulls_testdata.json"));
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
  public void testGetPull() throws Exception {
    trainMockGet(
        "/repos/example/project/pulls/12345", getResource("pulls_12345_testdata.json"));
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
  public void testGetPullReviews() throws Exception {
    trainMockGet(
        "/repos/octocat/Hello-World/pulls/12/reviews",
        getResource("pulls_12345_reviews_testdata.json"));
    List<Review> reviews = api.getReviews("octocat/Hello-World", 12);

    assertThat(reviews).hasSize(1);
    assertThat(reviews.get(0).getBody()).isEqualTo("Here is the body for the review.");
    assertThat(reviews.get(0).getId()).isEqualTo(80L);
    assertThat(reviews.get(0).getUser().getId()).isEqualTo(1L);
    assertThat(reviews.get(0).getUser().getLogin()).isEqualTo("octocat");
    assertThat(reviews.get(0).getState()).isEqualTo("APPROVED");
    assertThat(reviews.get(0).isApproved()).isTrue();
    assertThat(reviews.get(0).getCommitId())
        .isEqualTo("ecdd80bb57125d7ba9641ffaa4d7d2c19d3f3091");
  }

  @Test
  public void testGetLsRemote() throws Exception {
    trainMockGet(
        "/repos/copybara-test/copybara/git/refs", getResource("lsremote_testdata.json"));
    ImmutableList<Ref> refs = api.getLsRemote("copybara-test/copybara");

    assertThat(refs).hasSize(3);
    assertThat(refs.get(0).toString()).contains("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    assertThat(refs.get(0).toString()).contains("refs/heads/master");
    assertThat(refs.get(0).getRef()).isEqualTo("refs/heads/master");
    assertThat(refs.get(0).getUrl())
        .isEqualTo("https://api.github.com/repos/copybara-test/copybara/git/refs/heads/master");
    assertThat(refs.get(0).getSha()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    assertThat(refs.get(1).getRef()).isEqualTo("refs/pull/1/head");
    assertThat(refs.get(1).getSha()).isEqualTo("1234567890123456789012345678901234567890");
    assertThat(refs.get(2).getRef()).isEqualTo("refs/pull/1/merge");
    assertThat(refs.get(2).getSha()).isEqualTo("abcdefabcdefabcdefabcdefabcdefabcdefabcd");
  }

  @Test
  public void testCreatePullRequest() throws Exception {
    trainMockPost(
        "/repos/example/project/pulls", createValidator(TestCreatePullRequest.class,
            (cpr) ->
                cpr.getTitle().equals("title")
                && cpr.getBase().equals("aabbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                && cpr.getBody().equals("[TEST] example pull request one")
                && cpr.getHead().equals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")),
        getResource("pulls_12345_testdata.json"));
    // The test does not actually use the data in the CreatePullRequest
    PullRequest pullRequest = api.createPullRequest("example/project",
        new CreatePullRequest("title",
            "[TEST] example pull request one",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "aabbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));

    assertThat(pullRequest.getNumber()).isEqualTo(12345);
    assertThat(pullRequest.getState()).isEqualTo("open");
    assertThat(pullRequest.getTitle()).isEqualTo("[TEST] example pull request one");
  }

  @Test
  public void testGetIssue() throws Exception {
    trainMockGet(
        "/repos/example/project/issues/12345", getResource("issues_12345_testdata.json"));
    Issue issue = api.getIssue("example/project", 12345);

    assertThat(issue.getNumber()).isEqualTo(12345);
    assertThat(issue.getState()).isEqualTo("open");
    assertThat(issue.getTitle()).isEqualTo("[TEST] example pull request one");
    assertThat(issue.getBody()).isEqualTo("Example body.\r\n");
    assertThat(Lists.transform(issue.getLabels(), Label::getName))
        .containsExactly("cla: yes");
  }

  protected byte[] getResource(String testfile) throws IOException {
    return Files.readAllBytes(
        Paths.get(System.getenv("TEST_SRCDIR"),
            "copybara/java/com/google/copybara/git/github_api/"
                + "testing")
            .resolve(testfile));
  }

  protected <T> Predicate<String> createValidator(Class<T> clazz, Predicate<T> predicate) {
    return new Predicate<String>() {
      @Override
      public boolean test(String s) {
        try {
          T requestObject = GsonFactory.getDefaultInstance().createJsonParser(s).parse(clazz);
          return predicate.test(requestObject);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  // We don't want CreatePullRequest to be instantiable, this subclass sidesteps the issue.
  public static class TestCreatePullRequest extends CreatePullRequest {
    public TestCreatePullRequest() {
      super("invalid", "invalid", "invalid", "invalid");
    }
  }
}
