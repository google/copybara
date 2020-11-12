/*
 * Copyright (C) 2020 Google Inc.
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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.StarlarkValue;

/** https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#action-info */

@StarlarkBuiltin(
    name = "gerritapi.getActionInfo",
    doc = "Gerrit actions information.")
public class ActionInfo implements StarlarkValue {

  @Key
  private String method;
  @Key private String label;
  @Key private String title;
  @Key private boolean enabled;

  // Required by GSON
  public ActionInfo() {}

  @VisibleForTesting
  public ActionInfo(String method, String label, String title, boolean enabled) {
    this.method = method;
    this.label = label;
    this.title = title;
    this.enabled = enabled;
  }

  public String getMethod() {
    return method;
  }

  @StarlarkMethod(
      name = "label",
      doc = "Short title to display to a user describing the action",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getLabel() {
    return label;
  }

  public String getTitle() {
    return title;
  }

  @StarlarkMethod(
      name = "enabled",
      doc =
          "If true the action is permitted at this time and the caller is likely "
              + "allowed to execute it.",
      structField = true)
  public boolean getEnabled() {
    return enabled;
  }

  @Override
  public void repr(Printer printer) {
    printer.append(toString());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("method", method)
        .add("label", label)
        .add("title", title)
        .add("enabled", enabled)
        .toString();
  }

}
