/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.copybara.git.gerritapi;

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;
import java.util.Map;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.StarlarkValue;

/** See https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#project-info */
public class ProjectInfo implements StarlarkValue {
  @Key String id;
  @Key String name;
  @Key String parent;
  @Key String description;
  @Key String state;
  @Key Map<String, String> branches;

  public enum State {
    ACTIVE, READ_ONLY, HIDDEN
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getParent() {
    return parent;
  }

  public String getDescription() {
    return description;
  }

  public State getState() {
    return state == null ? null : State.valueOf(state);
  }

  public Map<String, String> getBranches() {
    return branches;
  }

  @Override
  public void repr(Printer printer) {
    printer.append(toString());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", id)
        .add("name", name)
        .add("parent", parent)
        .add("description", description)
        .add("state", state)
        .add("branches", branches)
        .toString();
  }
}
