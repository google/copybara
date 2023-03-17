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

package com.google.copybara.git.github.api;

import com.google.common.collect.ImmutableListMultimap;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.lang.reflect.Type;

/**
 * Http transport interface for talking to GitHub.
 */
public interface GitHubApiTransport {

  /** Do a HTTP GET call with headers.
   * The return type will be different.
   * Therefore, using generics type here
   */
  @FormatMethod
  default <T> T get(Type responseType, ImmutableListMultimap<String, String> headers,
      @FormatString String pathTemplate, Object... fmtArgs)
      throws RepoException, ValidationException {
    return get(String.format(pathTemplate, fmtArgs), responseType, headers,
        "GET " + pathTemplate);
  }

  /** Do a HTTP GET call with headers.
   * The return type will be different.
   * Therefore, using generics type here
   */
  <T> T get(String path, Type responseType, ImmutableListMultimap<String, String> headers,
      String requestDescription)
      throws RepoException, ValidationException;

  /** Do a HTTP GET call
   * The return type will be different.
   * Therefore, using generics type here
   */
  @FormatMethod
  default <T> T get(Type responseType, @FormatString String pathTemplate, Object... fmtArgs)
      throws RepoException, ValidationException {
    return get(responseType, ImmutableListMultimap.of(), pathTemplate, fmtArgs);
  }

  /** Do a HTTP POST call
   * The return type will be different.
   * Therefore, using generics type here
   */
  @FormatMethod
  default <T> T post(Object request, Type responseType,
      @FormatString String pathTemplate, Object... fmtArgs)
      throws RepoException, ValidationException {
    return post(String.format(pathTemplate, fmtArgs), request, responseType,
        "POST " + pathTemplate);
  }

  /** Do a HTTP POST call
   * The return type will be different.
   * Therefore, using generics type here
   */
  <T> T post(String path, Object request, Type responseType, String requestType)
      throws RepoException, ValidationException;

  /**
   * Do a HTTP POST call without a path. Mostly for graphql endpoints. The return type will be
   * different. Therefore, using generics type here
   */
  default <T> T post(Object request, Class<T> clazz) throws RepoException, ValidationException {
    return post("", request, clazz, "POST GraphQL");
  }

  /** Do a HTTP DELETE call */
  void delete(String path, String requestType) throws RepoException, ValidationException;

  /** Do a HTTP DELETE call */
  @FormatMethod
  default void delete(@FormatString String pathTemplate, Object... fmtArgs)
      throws RepoException, ValidationException {
    delete(String.format(pathTemplate, fmtArgs), "DELETE " + pathTemplate);
  }
}
