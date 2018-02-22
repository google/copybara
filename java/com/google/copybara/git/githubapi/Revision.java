/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.copybara.git.githubapi;

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;

/**
 * Represents a revision: information about the origin of a pull request like the ref (branch) or
 * specific SHA-1.
 */
public class Revision {

  @Key
  private String label;
  @Key
  private String ref;
  @Key
  private String sha;

  public String getLabel() {
    return label;
  }

  public String getRef() {
    return ref;
  }

  public String getSha() {
    return sha;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("label", label)
        .add("ref", ref)
        .add("sha", sha)
        .toString();
  }
}
