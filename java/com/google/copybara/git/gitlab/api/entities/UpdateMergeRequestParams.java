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
import com.google.api.client.util.Value;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Params used when updating a merge request using the GitLab API.
 *
 * @param projectId the ID of the GitLab project
 * @param mergeRequestIid the ID of the merge request to update
 * @param title the title for the merge request. Will be used if this value is not null
 * @param description the description for the merge request. Will be used if this value is not null
 * @param assigneeIds the IDs of the users to assign the merge request to. Provide an empty value to
 *     unassign all assignees
 * @param stateEvent the state to update the merge request too
 */
public record UpdateMergeRequestParams(
    @Key("id") int projectId,
    @Key("merge_request_iid") int mergeRequestIid,
    @Key @Nullable String title,
    @Key @Nullable String description,
    @Key("assignee_ids") List<Integer> assigneeIds,
    @Key("state_event") @Nullable StateEvent stateEvent)
    implements GitLabApiEntity {

  /**
   * Represents the states that we can update a merge request to.
   */
  public enum StateEvent {
    @Value("close")
    CLOSE,
    @Value("reopen")
    REOPEN
  }
}
