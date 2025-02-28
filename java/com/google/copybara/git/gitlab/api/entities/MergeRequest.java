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
 * Represents a GitLab Merge Request.
 *
 * @see <a
 *     href="https://docs.gitlab.com/api/merge_requests/#response">https://docs.gitlab.com/api/merge_requests/#response</a>
 */
public class MergeRequest implements GitLabApiEntity {
  @Key private int id;
  @Key private int iid;

  @Key("source_branch")
  private String sourceBranch;

  /**
   * Returns the ID of the merge request. When querying for an MR, use {@link #getIid()} instead.
   *
   * @return the ID
   */
  public int getId() {
    return id;
  }

  /**
   * Returns the internal ID (iid) of the merge request. When querying for an MR, this is the ID
   * that GitLab expects.
   *
   * @return the internal ID
   */
  public int getIid() {
    return iid;
  }

  /**
   * Returns the name of the source branch of the merge request.
   *
   * @return the source branch
   */
  public String getSourceBranch() {
    return sourceBranch;
  }
}
