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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.gitlab.api.entities.Commit;
import com.google.copybara.git.gitlab.api.entities.CreateMergeRequestParams;
import com.google.copybara.git.gitlab.api.entities.GitLabApiEntity;
import com.google.copybara.git.gitlab.api.entities.GitLabApiParams;
import com.google.copybara.git.gitlab.api.entities.ListProjectMergeRequestParams;
import com.google.copybara.git.gitlab.api.entities.ListUsersParams;
import com.google.copybara.git.gitlab.api.entities.MergeRequest;
import com.google.copybara.git.gitlab.api.entities.PaginatedPageList;
import com.google.copybara.git.gitlab.api.entities.Project;
import com.google.copybara.git.gitlab.api.entities.SetExternalStatusCheckParams;
import com.google.copybara.git.gitlab.api.entities.SetExternalStatusCheckResponse;
import com.google.copybara.git.gitlab.api.entities.UpdateMergeRequestParams;
import com.google.copybara.git.gitlab.api.entities.User;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Optional;

/** An API used for interacting with the GitLab REST API. */
public class GitLabApi {
  private final GitLabApiTransport transport;

  public GitLabApi(GitLabApiTransport transport) {
    this.transport = transport;
  }

  /**
   * Returns GitLab Project information for a given URL-encoded path. The path is defined as the
   * group and the project separated by a '/', e.g. google/copybara.
   *
   * @param urlEncodedPath the URL-encoded path of the project
   * @return an Optional containing the project response object, if any
   * @throws ValidationException if there is an issue fetching the provided credentials
   * @throws GitLabApiException if there is an failure while querying data from the API
   * @see <a
   *     href="https://docs.gitlab.com/api/projects/#get-a-single-project">https://docs.gitlab.com/api/projects/#get-a-single-project</a>
   */
  public Optional<Project> getProject(String urlEncodedPath)
      throws ValidationException, GitLabApiException {
    return transport.get(
        "projects/" + urlEncodedPath,
        TypeToken.get(Project.class).getType(),
        ImmutableListMultimap.of());
  }

  /**
   * Returns information about a Merge Request for a GitLab project.
   *
   * @param projectId The numeric project ID
   * @param mergeRequestId The numeric Merge Request ID
   * @return an Optional containing the Merge Request object, if any
   * @throws ValidationException if there is an issue fetching the provided credentials
   * @throws GitLabApiException if there is an failure while querying data from the API
   * @see <a
   *     href="https://docs.gitlab.com/api/merge_requests/#get-single-mr">https://docs.gitlab.com/api/merge_requests/#get-single-mr</a>
   */
  public Optional<MergeRequest> getMergeRequest(int projectId, int mergeRequestId)
      throws ValidationException, GitLabApiException {
    return transport.get(
        "/projects/" + projectId + "/merge_requests/" + mergeRequestId,
        TypeToken.get(MergeRequest.class).getType(),
        ImmutableListMultimap.of());
  }

  /**
   * Returns a list of Merge Requests for the given Project ID.
   *
   * @param projectId the project id
   * @param params the params to attach to this request
   * @return the list of Merge Requests
   * @throws ValidationException if there is an issue fetching the provided credentials
   * @throws GitLabApiException if there is an failure while querying data from the API
   */
  public ImmutableList<MergeRequest> getProjectMergeRequests(
      int projectId, ListProjectMergeRequestParams params)
      throws ValidationException, GitLabApiException {
    return paginatedGet(
        String.format("/projects/%d/merge_requests", projectId),
        TypeToken.get(MergeRequest.class).getType(),
        ImmutableListMultimap.of(),
        50,
        params);
  }

  /**
   * Performs a GET request on the GitLab API, for the provided path, and handles the pagination of
   * responses.
   *
   * <p>This method will perform various GET requests on the API endpoint, following the next link
   * header until a complete response set is obtained, and return the full response set.
   *
   * @param <T> the subclass of {@link GitLabApiEntity} that the inner JSON objects in the pages
   *     will be parsed to
   * @param path the path to call, e.g. projects/13422/merge_requests
   * @param responseType the type of the inner objects of the list
   * @param headers the headers to add to the HTTP request
   * @param perPageAmt the number of objects to list per page returned
   * @param urlQueryParams the params to use in the request
   * @return a list containing the entire response set
   */
  protected <T extends GitLabApiEntity> ImmutableList<T> paginatedGet(
      String path,
      Type responseType,
      ImmutableListMultimap<String, String> headers,
      int perPageAmt,
      GitLabApiParams urlQueryParams)
      throws ValidationException, GitLabApiException {
    path += extractQueryString(path).isPresent() ? "&" : "?";
    path += urlQueryParams.getQueryString();
    return paginatedGet(path, responseType, headers, perPageAmt);
  }

  /**
   * Returns information about a commit for a GitLab project.
   *
   * @param projectId The ID or URL-encoded path of the project
   * @param refName The commit hash or name of a repository branch or tag
   * @return an Optional containing the Merge Request object, if any
   * @throws ValidationException if there is an issue fetching the provided credentials
   * @throws GitLabApiException if there is an failure while querying data from the API
   * @see <a
   *     href="https://docs.gitlab.com/ee/api/commits/#get-a-single-commit">https://docs.gitlab.com/ee/api/commits/#get-a-single-commit</a>
   */
  public Optional<Commit> getCommit(int projectId, String refName)
      throws ValidationException, GitLabApiException {
    return transport.get(
        "/projects/" + projectId + "/repository/commits/" + refName,
        TypeToken.get(Commit.class).getType(),
        ImmutableListMultimap.of());
  }

  /**
   * Returns a list of users that match the given criteria from the GitLab instance.
   *
   * @param params the parameters to use in the request
   * @return a list of users
   * @throws ValidationException if there is an issue fetching the provided credentials
   * @throws GitLabApiException if there is an failure while querying data from the API
   * @see <a href="https://docs.gitlab.com/api/users/#list-users">GitLab API List Users docs</a>
   */
  public ImmutableList<User> getListUsers(ListUsersParams params)
      throws ValidationException, GitLabApiException {
    return paginatedGet(
        "users", TypeToken.get(User.class).getType(), ImmutableListMultimap.of(), 50, params);
  }

  /**
   * Performs a GET request on the GitLab API, for the provided path, and handles the pagination of
   * responses.
   *
   * <p>This method will perform various GET requests on the API endpoint, following the next link
   * header until a complete response set is obtained, and return the full response set.
   *
   * @param path the path to call, e.g. projects/13422/merge_requests
   * @param responseType the type of the inner objects of the list
   * @param headers the headers to add to the HTTP request
   * @param perPageAmt the number of objects to list per page returned
   * @return a list containing the entire response set
   * @param <T> the class the inner JSON objects in the pages will be parsed to
   */
  protected <T extends GitLabApiEntity> ImmutableList<T> paginatedGet(
      String path, Type responseType, ImmutableListMultimap<String, String> headers, int perPageAmt)
      throws ValidationException, GitLabApiException {
    ImmutableList.Builder<T> response = ImmutableList.builder();
    Optional<PaginatedPageList<T>> page =
        transport.get(
            getPathWithPerPageParam(path, perPageAmt),
            TypeToken.getParameterized(PaginatedPageList.class, responseType).getType(),
            headers);

    while (page.isPresent()) {
      response.addAll(page.get());
      Optional<String> nextUrl = page.get().getNextUrl();
      if (nextUrl.isPresent()) {
        page =
            transport.get(
                nextUrl.get(),
                TypeToken.getParameterized(PaginatedPageList.class, responseType).getType(),
                headers);
      } else {
        page = Optional.empty();
      }
    }

    return response.build();
  }

  /**
   * Creates a merge request via the GitLab API.
   *
   * @param params the parameters to use in the request
   * @return the created merge request info, if returned by the API
   * @throws ValidationException if there is an issue fetching the provided credentials
   * @throws GitLabApiException if there is an failure while querying this data to the API
   */
  public Optional<MergeRequest> createMergeRequest(CreateMergeRequestParams params)
      throws ValidationException, RepoException {
    return transport.post(
        String.format("/projects/%d/merge_requests", params.projectId()),
        params,
        TypeToken.get(MergeRequest.class).getType(),
        ImmutableListMultimap.of());
  }

  /**
   * Updates a merge request via the GitLab API.
   *
   * @param params the parameters to use in the request
   * @return the updated merge request info, if returned by the API
   * @throws ValidationException if there is an issue fetching the provided credentials
   * @throws GitLabApiException if there is an failure while querying this data to the API
   */
  public Optional<MergeRequest> updateMergeRequest(UpdateMergeRequestParams params)
      throws ValidationException, RepoException {
    return transport.put(
        String.format(
            "/projects/%d/merge_requests/%d", params.projectId(), params.mergeRequestIid()),
        params,
        TypeToken.get(MergeRequest.class).getType(),
        ImmutableListMultimap.of());
  }

  private static String getPathWithPerPageParam(String path, int itemsPerPage) {
    // TODO: b/397732032 - Use a library instead of manually constructing the query params.
    StringBuilder queryBuilder = new StringBuilder(path);
    Optional<String> existingQuery = extractQueryString(path);
    existingQuery.ifPresentOrElse(
        unused -> queryBuilder.append('&'), () -> queryBuilder.append('?'));
    queryBuilder.append("per_page=").append(itemsPerPage);
    return queryBuilder.toString();
  }

  public static Optional<String> extractQueryString(String path) {
    int lastQuestionMarkIndex = path.lastIndexOf('?');
    if (lastQuestionMarkIndex == -1) {
      return Optional.empty();
    }
    return Optional.of(path.substring(lastQuestionMarkIndex + 1));
  }

  public Optional<SetExternalStatusCheckResponse> setExternalStatusCheck(
      SetExternalStatusCheckParams params) throws ValidationException, RepoException {
    return transport.post(
        String.format(
            "projects/%d/merge_requests/%d/status_check_responses",
            params.projectId(), params.mergeRequestIid()),
        params,
        TypeToken.get(SetExternalStatusCheckResponse.class).getType(),
        ImmutableListMultimap.of());
  }
}
