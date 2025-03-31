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
 * Represents the response from setting an external status check on a GitLab project.
 *
 * @see <a
 *     href="https://docs.gitlab.com/api/status_checks">https://docs.gitlab.com/api/status_checks</a>
 */
public final class SetExternalStatusCheckResponse implements GitLabApiEntity {

  @Key("id")
  int setExternalStatusCheckResponseId;

  @Key("merge_request")
  MergeRequest mergeRequest;

  @Key("external_status_check")
  ExternalStatusCheck externalStatusCheck;

  /** Creates a new instance of {@link SetExternalStatusCheckResponse}. */
  public SetExternalStatusCheckResponse() {}

  public SetExternalStatusCheckResponse(
      int setExternalStatusCheckResponseId,
      MergeRequest mergeRequest,
      ExternalStatusCheck externalStatusCheck) {
    this.setExternalStatusCheckResponseId = setExternalStatusCheckResponseId;
    this.mergeRequest = mergeRequest;
    this.externalStatusCheck = externalStatusCheck;
  }

  public int getSetExternalStatusCheckResponseId() {
    return setExternalStatusCheckResponseId;
  }

  public MergeRequest getMergeRequest() {
    return mergeRequest;
  }

  public ExternalStatusCheck getExternalStatusCheck() {
    return externalStatusCheck;
  }
}
