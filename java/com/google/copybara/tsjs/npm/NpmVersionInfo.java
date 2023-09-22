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

/**
 * A data class that can be used to convert the JSON response from the NPM registry
 * (https://registry.npmjs.com). For more details on the registry, see the official documentation on
 * Github: https://github.com/npm/registry/blob/master/docs/REGISTRY-API.md
 */
public class NpmVersionInfo {

  @Key("version")
  private String version;

  @Key("dist")
  private NpmDistInfo dist;

  public NpmVersionInfo() {}

  // NOTE: this response also contains a direct reference to the downloadable tarball.
  // Currently, this URL is synthesized in starlark code, but it assumes that the way NPM hosts
  // these packages  won't change.

  public String getVersion() {
    return version;
  }

  public String getTarball() {
    return this.dist.getTarball();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("version", version).add("dist", dist).toString();
  }
}
