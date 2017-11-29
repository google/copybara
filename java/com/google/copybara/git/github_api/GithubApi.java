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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import java.util.List;

/**
 * A mini API for getting and updating GitHub projects through the GitHub REST API.
 */
public class GithubApi {

  private final GitHubApiTransport transport;
  private final Profiler profiler;

  public static final int REFS_PER_PAGE = 100;

  public GithubApi(GitHubApiTransport transport, Profiler profiler) {
    this.transport = Preconditions.checkNotNull(transport);
    this.profiler = Preconditions.checkNotNull(profiler);
  }

  /**
   * Get all the pull requests for a project
   * @param projectId a project in the form of "google/copybara"
   */
  public ImmutableList<PullRequest> getPullRequests(String projectId)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("github_api_list_pulls")) {
      List<PullRequest> result =
          transport.get(String.format("repos/%s/pulls", projectId),
              new TypeToken<List<PullRequest>>() {
              }.getType());

      return ImmutableList.copyOf(result);
    }
  }

  /**
   * Get a specific pull request for a project
   * @param projectId a project in the form of "google/copybara"
   * @param number the issue number
   */
  public PullRequest getPullRequest(String projectId, long number)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("github_api_get_pull")) {
      return transport.get(
          String.format("repos/%s/pulls/%d", projectId, number), PullRequest.class);
    }
  }

  /**
   * Get reviews for a pull request
   *
   * @param projectId a project in the form of "google/copybara"
   * @param number the pull request number
   */
  public List<Review> getReviews(String projectId, long number)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("github_api_get_reviews")) {
      return transport.get(
          String.format("repos/%s/pulls/%d/reviews", projectId, number),
          new TypeToken<List<Review>>() {}.getType());
    }
  }

  /**
   * Create a pull request
   */
  public PullRequest createPullRequest(String projectId, CreatePullRequest request)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("github_api_create_pull")) {
      return transport.post(
          String.format("repos/%s/pulls", projectId), request, PullRequest.class);
    }
  }

  /**
   * Get a specific issue for a project.
   *
   * <p>Use this method to get the Pull Request labels.
   * @param projectId a project in the form of "google/copybara"
   * @param number the issue number
   */
  public Issue getIssue(String projectId, long number)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("github_api_get_issue")) {
      return transport.get(String.format("repos/%s/issues/%d", projectId, number), Issue.class);
    }
  }

  /**
   * Get all the refs for a repo (git ls-remote)
   * @param projectId a project in the form of "google/copybara"
   */
  public ImmutableList<Ref> getLsRemote(String projectId)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("github_api_list_refs")) {
      List<Ref> result =
          transport.get(String.format("repos/%s/git/refs?per_page=%d", projectId, REFS_PER_PAGE),
              new TypeToken<List<Ref>>() {
              }.getType());

      return ImmutableList.copyOf(result);
    }
  }
}
