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
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.gitlab.api.entities.GitLabApiEntity;
import com.google.copybara.git.gitlab.api.entities.MergeRequest;
import com.google.copybara.git.gitlab.api.entities.PaginatedPageList;
import com.google.copybara.git.gitlab.api.entities.Project;
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
}
