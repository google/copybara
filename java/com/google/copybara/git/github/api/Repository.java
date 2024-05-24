/*
 * Copyright (C) 2024 Google LLC
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
import net.starlark.java.annot.StarlarkMethod;

/**
 * This class is only used to represent a GitHub Repository object returned by the GitHub REST API,
 * see https://docs.github.com/en/rest/repos/repos?apiVersion=2022-11-28#get-a-repository
 */
public class Repository {
  @Key("default_branch")
  private String defaultBranch;

  @StarlarkMethod(name = "id", doc = "Release id", structField = true)
  public String getDefaultBranch() {
    return defaultBranch;
  }
}
