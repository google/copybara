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
 * Represents a GitLab project.
 *
 * <p>Documentation: <a
 * href="https://docs.gitlab.com/api/projects/#get-a-single-project">https://docs.gitlab.com/api/projects/#get-a-single-project</a>
 */
public class Project implements GitLabApiEntity {
  @Key private int id;

  /**
   * Returns the numeric ID of the project.
   *
   * @return the ID
   */
  public int getId() {
    return id;
  }
}
