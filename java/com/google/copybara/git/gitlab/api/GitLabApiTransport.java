/*
 * Copyright (C) 2022 Google Inc.
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

import java.lang.reflect.Type;

/**
 * Http transport interface for talking to GitLab.
 */
public interface GitLabApiTransport {

  /**
   * Do a HTTP GET call with headers.
   * The return type will be different.
   * Therefore, using generics type here
   */
  <T> T get(String path, Type responseType, ImmutableListMultimap<String, String> headers,
            ImmutableListMultimap<String, String> params)
      throws RepoException, ValidationException;

  /**
   * Do a HTTP POST call
   * The return type will be different.
   * Therefore, using generics type here
   */
  <T> T post(String path, Type responseType, ImmutableListMultimap<String, String> params)
      throws RepoException, ValidationException;

  /**
   * Do a HTTP DELETE call
   */
  void delete(String path) throws RepoException, ValidationException;

  /**
   * Do a HTTP PUT call
   * The return type will be different.
   * Therefore, using generics type here
   */
  <T> T put(String path, Type responseType, ImmutableListMultimap<String, String> params)
      throws RepoException, ValidationException;

}
