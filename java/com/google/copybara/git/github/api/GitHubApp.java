/*
 * Copyright (C) 2019 Google Inc.
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
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.StarlarkValue;

/** Represents a GitHub App detail. https://developer.github.com/v3/apps/#response */
@SkylarkModule(
    name = "github_app_obj",
    category = SkylarkModuleCategory.BUILTIN,
    doc = "Detail about a GitHub App.")
public class GitHubApp implements StarlarkValue {

  @Key private int id;

  @Key private String slug;

  @Key private String name;

  @SkylarkCallable(
      name = "id",
      doc = "The GitHub App's Id",
      structField = true,
      allowReturnNones = true
  )
  public int getId() {
    return id;
  }

  @SkylarkCallable(
      name = "slug",
      doc = "The url-friendly name of the GitHub App.",
      structField = true
  )
  public String getSlug() {
    return slug;
  }
  
  @SkylarkCallable(
      name = "name",
      doc = "The GitHub App's name",
      structField = true,
      allowReturnNones = true
  )
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", id)
        .add("slug", slug)
        .add("name", name)
        .toString();
  }

}
