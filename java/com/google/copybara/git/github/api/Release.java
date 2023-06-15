/*
 * Copyright (C) 2023 Google Inc.
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
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/**
 * A Release object *
 * https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28#create-a-release
 */
@StarlarkBuiltin(
    name = "github_release_obj",
    doc = "GitHub API value type for a release. See "
        + "https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28#create-a-release"
)
public class Release implements StarlarkValue {
  @Key("id") private int id;
  @Key("tarball_url") private String tarball;
  @Key("zipball_url") private String zip;


  @StarlarkMethod(
      name = "id",
      doc = "Release id",
      structField = true)
  public int getId() {
    return id;
  }

  @StarlarkMethod(
      name = "tarball",
      doc = "Tarball Url",
      structField = true)
  public String getTarball() {
    return tarball;
  }

  @StarlarkMethod(
      name = "zip",
      doc = "Zip Url",
      structField = true)
  public String getZip() {
    return zip;
  }
}
