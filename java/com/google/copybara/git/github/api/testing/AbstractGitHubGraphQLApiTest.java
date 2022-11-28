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

package com.google.copybara.git.github.api.testing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.git.GitTestUtil.createValidator;
import static com.google.copybara.testing.git.GitTestUtil.getResource;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.copybara.git.github.api.CommitHistoryResponse;
import com.google.copybara.git.github.api.CommitHistoryResponse.AssociatedPullRequestNode;
import com.google.copybara.git.github.api.CommitHistoryResponse.HistoryNode;
import com.google.copybara.git.github.api.GitHubApiTransport;
import com.google.copybara.git.github.api.GitHubGraphQLApi;
import com.google.copybara.git.github.api.GitHubGraphQLApi.GraphQLRequest;
import com.google.copybara.profiler.LogProfilerListener;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.testing.git.GitTestUtil.JsonValidator;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;

/**
 * Base test to run the same tests on various implementations of the GitHubApiTransport for
 * GitHubGraphQLApi
 */
public abstract class AbstractGitHubGraphQLApiTest {

  protected Profiler profiler;
  protected GitHubGraphQLApi api;

  @Before
  public void setUpFramework() throws Exception {
    MockitoJUnit.rule();
    profiler = new Profiler(Ticker.systemTicker());
    profiler.init(ImmutableList.of(new LogProfilerListener()));
    api = new GitHubGraphQLApi(getTransport(), profiler);
  }

  public abstract GitHubApiTransport getTransport() throws Exception;

  public abstract void trainMockPost(Predicate<String> validator, byte[] response);

  @Test
  public void testGetCommitHistory() throws Exception {
    JsonValidator<GraphQLRequest> validator =
        createValidator(GraphQLRequest.class, (r) -> r.getQuery().contains("query"));
    trainMockPost(validator, getResource("commit_history_testdata.json"));
    CommitHistoryResponse response =
        api.getCommitHistory(
            "org_name", "repo_name", "main", new GitHubGraphQLApi.GetCommitHistoryParams(5, 5, 5));
    // commit_history_testdata.json specific assertions
    HistoryNode commitNode =
        Iterables.getOnlyElement(
            response.getData().getRepository().getRef().getTarget().getHistoryNodes().getNodes());
    assertThat(
            commitNode.getAssociatedPullRequests().getEdges().stream()
                .allMatch(
                    review -> {
                      AssociatedPullRequestNode prNode = review.getNode();
                      String prAuthor = prNode.getAuthor().getLogin();
                      String prStatus = prNode.getReviewDecision();
                      String reviewer =
                          Iterables.getOnlyElement(prNode.getLatestOpinionatedReviews().getEdges())
                              .getNode()
                              .getAuthor()
                              .getLogin();
                      String reviewerDecision =
                          Iterables.getOnlyElement(prNode.getLatestOpinionatedReviews().getEdges())
                              .getNode()
                              .getState();
                      return prAuthor.equals("copybara_author")
                          && prStatus.equals("APPROVED")
                          && reviewerDecision.equals("APPROVED")
                          && reviewer.equals("copybara_reviewer");
                    }))
        .isTrue();
  }
}
