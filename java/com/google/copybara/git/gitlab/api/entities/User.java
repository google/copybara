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

package com.google.copybara.git.gitlab.api.entities;

import com.google.api.client.util.Key;
import com.google.common.annotations.VisibleForTesting;

/**
 * Represents a GitLab instance user.
 *
 * @see <a href="https://docs.gitlab.com/api/users/">GitLab Users API documentation</a>
 */
public class User implements GitLabApiEntity {
  @Key private int id;

  /** Creates a new instance of {@link User}. */
  public User() {}

  /**
   * Creates a new User object.
   *
   * @param id the ID of the user
   */
  @VisibleForTesting
  public User(int id) {
    this.id = id;
  }

  /**
   * Returns the numeric ID of the GitLab user
   *
   * @return the ID
   */
  public int getId() {
    return id;
  }
}
