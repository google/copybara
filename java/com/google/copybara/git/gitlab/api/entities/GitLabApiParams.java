/*
 * Copyright (C) 2025 Google LLC.
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

package com.google.copybara.git.gitlab.api.entities;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import java.net.URLEncoder;

/**
 * An interface for classes that contain a set of parameters to pass into a GitLab GET request as an
 * HTTP query string.
 *
 * @see <a href="https://docs.gitlab.com/api/rest/#request-payload">GitLab docs</a>
 */
public interface GitLabApiParams {
  /**
   * Represents a param to be used for a GitLab GET HTTP request query string.
   *
   * @param key the key of the query param
   * @param value the value of the query param
   */
  record Param(String key, Object value) {

    /**
     * Encodes the key string into a URL-encoded format, and returns the string.
     *
     * @return the encoded key
     */
    public String encodedKey() {
      return URLEncoder.encode(key, UTF_8);
    }

    /**
     * Encodes the value string into a URL-encoded format, and returns the string.
     *
     * @return the encoded value
     */
    public String encodedValue() {
      return URLEncoder.encode(value.toString(), UTF_8);
    }
  }

  /**
   * Returns the url params associated with this object.
   *
   * @return a list of params
   */
  ImmutableList<Param> params();

  /**
   * Constructs a URL-encoded query string of params defined within this object.
   *
   * <p>The string used for the value is derived from the value's {@code toString} method.
   *
   * @return the constructed query string
   */
  default String getQueryString() {
    return params().stream()
        .map(param -> param.encodedKey() + "=" + param.encodedValue())
        .collect(joining("&"));
  }
}
