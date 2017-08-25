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

package com.google.copybara.git.github_api;

import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import java.lang.reflect.Type;
/**
 * TODO(copybara-team): Document
 */
public interface GitHubApiTransport {

  /**
   * Do a http GET call
   */
  <T> T get(String path, Type responseType) throws RepoException, ValidationException;
  /**
   * Do a http POST call
   */
  <T> T post(String path, Object request, Type responseType)
      throws RepoException, ValidationException;
}
