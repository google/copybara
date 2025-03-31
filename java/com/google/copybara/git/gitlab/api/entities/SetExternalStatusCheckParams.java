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
 * Params used when calling the "set external status check" API endpoint
 *
 * @param projectId the project id
 * @param mergeRequestIid the merge request id
 * @param sha the commit sha
 * @param externalStatusCheckId the external status check id
 * @param status the status of the external status check, must be one of "pending", "passed", or
 *     "failed"
 * @see <a
 *     href="https://docs.gitlab.com/api/status_checks/#set-status-of-an-external-status-check">https://docs.gitlab.com/api/status_checks/#set-status-of-an-external-status-check</a>
 */
public record SetExternalStatusCheckParams(
    @Key("id") int projectId,
    @Key("merge_request_iid") int mergeRequestIid,
    @Key String sha,
    @Key("external_status_check_id") int externalStatusCheckId,
    @Key String status)
    implements GitLabApiEntity {}
