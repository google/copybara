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

package com.google.copybara.git;

import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import javax.annotation.Nullable;

/** Defines behavior to perform before checking out a Git repository. */
public interface GitRepositoryHook {
  /** Data class for a Git repository. */
  record GitRepositoryData(@Nullable String id, String url) {}

  /**
   * Procedures to be performed before checking out a Git repository.
   *
   * @throws ValidationException if checkout prework fails due to user error
   * @throws RepoException if the checkout prework fails due to a system error
   */
  public void beforeCheckout() throws ValidationException, RepoException;

  /**
   * Returns the Git repository data used for hook validation during a checkout.
   *
   * @return the Git repository data
   */
  public GitRepositoryData getGitRepositoryData();
}
