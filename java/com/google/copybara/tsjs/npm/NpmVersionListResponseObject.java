/*
 * Copyright (C) 2024 Google LLC.
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

package com.google.copybara.tsjs.npm;

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;
import java.util.Map;
import java.util.Set;

/**
 * A data class that can be used to convert the JSON response from the Npm registry
 * (https://registry.npmjs.com). For more details on the registry, see the official documentation on
 * Github: https://github.com/npm/registry/blob/master/docs/REGISTRY-API.md
 */
public class NpmVersionListResponseObject {

  @Key("dist-tags")
  private Map<String, String> distTags;

  @Key("versions")
  private Map<String, NpmVersionInfo> versions;

  public NpmVersionListResponseObject() {}

  public NpmVersionInfo getLatestVersion() {
    String versionId = distTags.get("latest");
    return versions.get(versionId);
  }

  public NpmVersionInfo getVersionInfo(String versionId) {
    return versions.get(versionId);
  }

  public Set<String> getAllVersions() {
    return versions.keySet();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("dist-tags", distTags)
        .add("versions", this.versions)
        .toString();
  }
}
