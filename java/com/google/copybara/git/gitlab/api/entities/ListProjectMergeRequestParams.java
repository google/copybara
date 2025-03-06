/*
 * Copyright (C) 2025 Google LLC.
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

import com.google.common.collect.ImmutableList;
import java.util.Optional;

/**
 * Params used when requesting a list of project merge requests from GitLab.
 *
 * @param sourceBranch the source branch to filter the merge requests by
 * @see <a href="https://docs.gitlab.com/api/merge_requests/#list-project-merge-requests">GitLab API
 *     documentation</a>
 */
public record ListProjectMergeRequestParams(Optional<String> sourceBranch)
    implements GitLabApiParams {
  /**
   * Creates a {@link ListProjectMergeRequestParams} instance with no params set.
   *
   * @return the new instance
   */
  public static ListProjectMergeRequestParams getDefaultInstance() {
    return new ListProjectMergeRequestParams(Optional.empty());
  }

  @Override
  public ImmutableList<Param> params() {
    ImmutableList.Builder<Param> params = ImmutableList.builder();
    sourceBranch.ifPresent(sb -> params.add(new Param("source_branch", sb)));
    return params.build();
  }
}
