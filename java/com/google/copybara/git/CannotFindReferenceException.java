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

package com.google.copybara.git;

import com.google.copybara.RepoException;

/**
 * Indicates that a Git reference could not be found when performing a {@code git} operation.
 */
public class CannotFindReferenceException extends RepoException {
  public CannotFindReferenceException(String message) {
    super(message);
  }

  public CannotFindReferenceException(String message, Throwable cause) {
    super(message, cause);
  }
}
