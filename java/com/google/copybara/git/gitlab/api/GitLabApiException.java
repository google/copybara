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

import com.google.copybara.exception.RepoException;
import java.util.Optional;

/** Exception that contains the error message and other information from GitLab. */
public class GitLabApiException extends RepoException {
  private final Optional<Integer> responseCode;

  public GitLabApiException(String message, int responseCode, Throwable cause) {
    super(message, cause);
    this.responseCode = Optional.of(responseCode);
  }

  public GitLabApiException(String message, int responseCode) {
    super(message);
    this.responseCode = Optional.of(responseCode);
  }

  public GitLabApiException(String message, Throwable cause) {
    super(message, cause);
    this.responseCode = Optional.empty();
  }

  public Optional<Integer> getResponseCode() {
    return responseCode;
  }
}
