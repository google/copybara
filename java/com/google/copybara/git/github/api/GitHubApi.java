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

package com.google.copybara.git.github.api;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.github.api.GitHubApiException.ResponseCode;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import java.lang.reflect.Type;
import java.util.List;

/**
 * A mini API for getting and updating GitHub projects through the GitHub REST API.
 */
public class GitHubApi {

  private final GitHubApiTransport transport;
  private final Profiler profiler;

  public static final int MAX_PER_PAGE = 100;
  private static final int MAX_PAGES = 5;

  public GitHubApi(GitHubApiTransport transport, Profiler profiler) {
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
    } catch (GitHubApiException e) {
      throw treatGitHubException(e, "Project");
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
    } catch (GitHubApiException e) {
      throw treatGitHubException(e, "Pull Request");
    }
  }

  /**
   * Get reviews for a pull request
   *
   * @param projectId a project in the form of "google/copybara"
   * @param number the pull request number
   */
  public ImmutableList<Review> getReviews(String projectId, long number)
      throws RepoException, ValidationException {
    return paginatedGet(String.format("repos/%s/pulls/%d/reviews?per_page=%d",
        projectId, number, MAX_PER_PAGE),
        "github_api_get_reviews",
        new TypeToken<PaginatedList<Review>>() {}.getType());
  }

  private <T> ImmutableList<T> paginatedGet(String path, String profilerName, Type type)
      throws RepoException, ValidationException {
    ImmutableList.Builder<T> builder = ImmutableList.builder();
    int pages = 0;
    while (path != null && pages < MAX_PAGES) {
      try (ProfilerTask ignore = profiler.start(String.format("%s_page_%d", profilerName, pages))) {
        PaginatedList<T> page = transport.get(path, type);
        builder.addAll(page);
        path = page.getNextUrl();
        pages++;
      }
    }
    return builder.build();
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
    } catch (GitHubApiException e) {
      throw treatGitHubException(e, "Issue");
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
          transport.get(String.format("repos/%s/git/refs?per_page=%d", projectId, MAX_PER_PAGE),
              new TypeToken<List<Ref>>() {
              }.getType());

      return ImmutableList.copyOf(result);
    }
  }

  public Status createStatus(String projectId, String sha1, CreateStatusRequest request)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("github_api_create_status")) {
      Status result = transport.post(
          String.format("repos/%s/statuses/%s", projectId, sha1), request, Status.class);
      if (result.getContext() == null || result.getState() == null) {
        throw new RepoException(
            String.format(
                "Something went wrong at the GitHub API transport level."
                    + " Context: %s state: %s",
                result.getContext(), result.getState()));
      }
      return result;
    }
  }

  public Ref updateReference(String projectId, String branchName, UpdateReferenceRequest request)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("github_api_update_reference")) {
      Ref result = transport.post(
          String.format("repos/%s/git/refs/heads/%s", projectId, branchName), request, Ref.class);
      if (result.getRef() == null || result.getSha() == null || result.getUrl() == null) {
        throw new RepoException(
            String.format(
                "Something went wrong at the GitHub API transport level."
                    + " ref: %s sha: %s, url: %s",
                result.getRef(), result.getSha(), result.getUrl()));
      }
      return result;
    }
  }

  public Ref getReference(String projectId, String branchName)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("github_api_get_reference")) {
      Ref result = transport.get(
          String.format("repos/%s/git/refs/%s", projectId, branchName), Ref.class);
      return result;
    }
  }

  public ImmutableList<Ref> getReferences(String projectId)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("github_api_get_references")) {
      return paginatedGet(String.format("repos/%s/git/refs?per_page=%d",
          projectId, MAX_PER_PAGE),
          "github_api_get_references",
          new TypeToken<PaginatedList<Ref>>() {}.getType());
    }
  }

  public CombinedStatus getCombinedStatus(String projectId, String sha1)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("github_api_get_combined_status")) {
      return transport.get(String.format("repos/%s/statuses/%s", projectId, sha1),
          CombinedStatus.class);
    }
  }

  private RepoException treatGitHubException(GitHubApiException e, final String entity)
      throws ValidationException, GitHubApiException {
    if (e.getResponseCode() == ResponseCode.NOT_FOUND) {
      throw new ValidationException(e, "%s not found: %s", entity, e.getRawError());
    }
    throw e;
  }
}
