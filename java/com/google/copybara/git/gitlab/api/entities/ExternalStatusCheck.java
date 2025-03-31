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
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * Represents a GitLab External Status Check.
 *
 * @see <a
 *     href="https://docs.gitlab.com/api/status_checks">https://docs.gitlab.com/api/status_checks</a>
 */
public final class ExternalStatusCheck implements GitLabApiEntity {

  @Key("id")
  private int statusCheckId;

  @Key private String name;

  @Key("project_id")
  private int projectId;

  @Key("external_url")
  private String externalUrl;

  @Key("protected_branches")
  private List<String> protectedBranches;

  @Key private boolean hmac;

  public ExternalStatusCheck(
      int statusCheckId,
      String name,
      int projectId,
      String externalUrl,
      List<String> protectedBranches,
      boolean hmac) {
    this.statusCheckId = statusCheckId;
    this.name = name;
    this.projectId = projectId;
    this.externalUrl = externalUrl;
    this.protectedBranches = protectedBranches;
    this.hmac = hmac;
  }

  public ExternalStatusCheck() {}

  public int getStatusCheckId() {
    return statusCheckId;
  }

  public String getName() {
    return name;
  }

  public int getProjectId() {
    return projectId;
  }

  public String getExternalUrl() {
    return externalUrl;
  }

  public ImmutableList<String> getProtectedBranches() {
    return ImmutableList.copyOf(protectedBranches);
  }

  public boolean getHmac() {
    return hmac;
  }
}
