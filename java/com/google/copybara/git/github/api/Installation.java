/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.git.github.api;

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;

/**
 * Correspond to JSON schema response for individual installations detailed in
 * https://docs.github.com/en/rest/orgs/orgs#list-app-installations-for-an-organization
 *
 * Not all property keys are included here. Add them as needed.
 */
public class Installation {
  @Key("app_slug")
  private String appSlug;

  @Key("app_id")
  private int appId;

  @Key("target_type")
  private String targetType;

  @Key("repository_selection")
  private String repositorySelection;

  public String getAppSlug() {
    return appSlug;
  }

  public int getAppId() {
    return appId;
  }

  public String getTargetType() {
    return targetType;
  }

  public String getRepositorySelection() {
    return repositorySelection;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("app_slug", appSlug)
        .add("app_id", appId)
        .add("target_type", targetType)
        .add("repository_selection", repositorySelection)
        .toString();
  }
}
