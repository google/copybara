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
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/** Represents a GitHub App detail. https://developer.github.com/v3/apps/#response */
@StarlarkBuiltin(
    name = "github_app_obj",
    doc = "Detail about a GitHub App.")
public class GitHubApp implements StarlarkValue {

  @Key private int id;

  @Key private String slug;

  @Key private String name;

  @StarlarkMethod(name = "id", doc = "The GitHub App's Id", structField = true)
  public int getId() {
    return id;
  }

  @StarlarkMethod(
      name = "slug",
      doc = "The url-friendly name of the GitHub App.",
      structField = true
  )
  public String getSlug() {
    return slug;
  }

  @StarlarkMethod(
      name = "name",
      doc = "The GitHub App's name",
      structField = true,
      allowReturnNones = true)
  @Nullable
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
