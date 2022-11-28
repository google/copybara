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

import com.google.api.client.util.Key;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import java.util.Map;

/** GraphQL implementation for GitHub client */
public class GitHubGraphQLApi {

  /** GraphQL request body */
  public static class GraphQLRequest {
    @Key("query")
    private String query;

    @Key("variables")
    private Map<String, Object> variables;

    public GraphQLRequest(String query, Map<String, Object> variables) {
      this.query = query;
      this.variables = variables;
    }

    public GraphQLRequest() {}

    public String getQuery() {
      return query;
    }

    public Map<String, Object> getVariables() {
      return variables;
    }
  }

  private final GitHubApiTransport transport;
  private final Profiler profiler;

  
  public GitHubGraphQLApi(GitHubApiTransport transport, Profiler profiler) {
    this.transport = Preconditions.checkNotNull(transport);
    this.profiler = Preconditions.checkNotNull(profiler);
  }

  /** Sets GraphQL first parameters for the getCommitHistory call. */
  public static class GetCommitHistoryParams {
    private int commits;
    private int pullRequests;
    private int reviews;

    public GetCommitHistoryParams(int commits, int pullRequests, int reviews) {}

    public int getCommits() {
      return commits;
    }

    public int getPullRequests() {
      return pullRequests;
    }

    public int getReviews() {
      return reviews;
    }
  }

  public CommitHistoryResponse getCommitHistory(
      String org, String repo, String branch, GetCommitHistoryParams params)
      throws RepoException, ValidationException {
    // TODO(linjordan): this could look better with a query builder api or load from .graphql file.
    String getCommitHistoryQuery =
                            "query ($repoName: String!, $repoOwner:String!, $branch: String!,"
                              + "$numberOfCommits: Int, $numberOfPRs: Int, "
                              + "$numberOfReviews: Int) {\n"
                              + "repository(name: $repoName, owner: $repoOwner) {\n"
                              + "ref(qualifiedName: $branch) {\n"
                              +    "target {\n"
                              +      "... on Commit {\n"
                              +        "id\n"
                              +        "history(first: $numberOfCommits) {\n"
                              +          "nodes {\n"
                              +            "id\n"
                              +            "oid\n"
                              +            "associatedPullRequests(first: $numberOfPRs) {\n"
                              +              "edges {\n"
                              +                "node {\n"
                              +                  "title\n"
                              +                  "mergedBy {\n"
                              +                    "login\n"
                              +                  "}\n"
                              +                  "author {\n"
                              +                    "login\n"
                              +                  "}\n"
                              +                  "reviewDecision\n"
                              +                  "latestOpinionatedReviews(first: $numberOfReviews)"
                              +                  "{\n"
                              +                    "edges {\n"
                              +                      "node {\n"
                              +                        "author {\n"
                              +                          "login\n"
                              +                        "}\n"
                              +                        "state\n"
                              +                      "}\n"
                              +                    "}\n"
                              +                  "}\n"
                              +                "}\n"
                              +             "}\n"
                              +            "}\n"
                              +          "}\n"
                              +        "}\n"
                              +      "}\n"
                              +    "}\n"
                              +  "}\n"
                              + "}\n"
                          + "}\n";
    ImmutableMap<String, Object> variables =
        ImmutableMap.of(
            "repoOwner",
            org,
            "repoName",
            repo,
            "branch",
            branch,
            "numberOfCommits",
            params.getCommits(),
            "numberOfPRs",
            params.getPullRequests(),
            "numberOfReviews",
            params.getReviews());
    try (ProfilerTask ignore = profiler.start("github_api_get_commit_history")) {
      return transport.post(
          new GraphQLRequest(getCommitHistoryQuery, variables), CommitHistoryResponse.class);
    }
  }
}
