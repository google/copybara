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

package com.google.copybara.git.github.api;

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/**
 * Represents a revision: information about the origin of a pull request like the ref (branch) or
 * specific SHA-1.
 */
@StarlarkBuiltin(
    name = "github_api_revision_obj",
    doc = "Information about a GitHub revision (Used in Pull Request and other entities)")
public class Revision implements StarlarkValue {

  @Key private String label;
  @Key private String ref;
  @Key private String sha;

  @StarlarkMethod(name = "label", doc = "Label for the revision", structField = true)
  public String getLabel() {
    return label;
  }

  @StarlarkMethod(name = "ref", doc = "Reference", structField = true)
  public String getRef() {
    return ref;
  }

  @StarlarkMethod(name = "sha", doc = "SHA of the reference", structField = true)
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
