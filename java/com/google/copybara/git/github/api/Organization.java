package com.google.copybara.git.github.api;

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

import com.google.api.client.util.Data;
import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;
import javax.annotation.Nullable;

/**
 * Corresponds to JSON schema response for getting a GitHub organization detailed in
 * https://docs.github.com/en/rest/orgs/orgs#get-an-organization
 *
 * <p>Not all property keys are included here. Add them as needed.
 */
public class Organization {
  @Key("name")
  private String name;

  @Key("two_factor_requirement_enabled")
  private Boolean twoFactorRequirementEnabled;

  public Organization() {}

  @Nullable
  public Boolean getTwoFactorRequirementEnabled() {
    // Explicit null values in JSON data are not automatically converted to Java null; see
    // https://googleapis.github.io/google-http-java-client/json.html
    return Data.isNull(twoFactorRequirementEnabled) ? null : twoFactorRequirementEnabled;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("two_factor_requirement_enabled", getTwoFactorRequirementEnabled())
        .toString();
  }
}
