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
import java.util.List;
import javax.annotation.Nullable;

/**
 * Params used when creating a merge request using the GitLab API.
 *
 * @param projectId the ID of the GitLab project
 * @param sourceBranch the source branch
 * @param targetBranch the target branch
 * @param title the title for the merge request
 * @param description the description of the merge request
 * @param assigneeIds the IDs of the users to assign the merge request to
 * @see <a href="https://docs.gitlab.com/api/merge_requests/#create-mr">GitLab API Create MR
 *     docs</a>
 */
public record CreateMergeRequestParams(
    @Key("id") int projectId,
    @Key("source_branch") @Nullable String sourceBranch,
    @Key("target_branch") @Nullable String targetBranch,
    @Key @Nullable String title,
    @Key @Nullable String description,
    @Key("assignee_ids") List<Integer> assigneeIds)
    implements GitLabApiEntity {}
