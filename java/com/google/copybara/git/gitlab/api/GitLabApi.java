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
import com.google.copybara.git.gitlab.api.entities.PaginatedPageList;
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
