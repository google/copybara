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

import com.google.common.collect.ImmutableListMultimap;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.gitlab.api.entities.GitLabApiEntity;
import java.lang.reflect.Type;
import java.util.Optional;

/** An interface for transports that communicate with a GitLab API endpoint. */
public interface GitLabApiTransport {

  /**
   * Perform a GET request on the GitLab API, for the provided path.
   *
   * @param <T> the class the JSON response will be parsed to
   * @param path the path to call, e.g. projects/13422/merge_requests
   * @param responseType the Java type that GSON should parse the response into
   * @param headers the headers to add to the HTTP request
   * @return the returned {@link T}, if a response is returned
   * @throws GitLabApiException if there is an issue performing the request
   * @throws ValidationException if there is an issue with credential issuing or retrieval
   */
  <T> Optional<T> get(String path, Type responseType, ImmutableListMultimap<String, String> headers)
      throws GitLabApiException, ValidationException;

  /**
   * Perform a POST request on the GitLab API, for the provided path.
   *
   * @param <T> the class the JSON response will be parsed to
   * @param path the path to call, e.g. projects/13422/merge_requests
   * @param request the object to send as part of the request
   * @param responseType the Java type that GSON should parse the response into
   * @return the returned {@link T}, if a response is returned
   * @throws GitLabApiException if there is an issue performing the request
   * @throws ValidationException if there is an issue with credential issuing or retrieval
   */
  <T> Optional<T> post(
      String path,
      GitLabApiEntity request,
      Type responseType,
      ImmutableListMultimap<String, String> headers)
      throws RepoException, ValidationException;

  /**
   * Perform a DELETE request on the GitLab API, for the provided path.
   *
   * @param path the path to call, e.g. projects/13422/merge_requests/80
   * @throws RepoException if there is an issue performing the request
   * @throws ValidationException if there is an issue with credential issuing or retrieval
   */
  void delete(String path) throws RepoException, ValidationException;
}
