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

/**
 * Represents a GitLab Commit.
 *
 * @see <a
 *     href="https://docs.gitlab.com/api/commits/#response">https://docs.gitlab.com/api/commits/#response</a>
 */
public class Commit implements GitLabApiEntity {
  @Key private int id;

  /**
   * Returns the ID of the commit, e.g. the sha.
   *
   * @return the ID of the commit
   */
  public int getId() {
    return id;
  }
}
