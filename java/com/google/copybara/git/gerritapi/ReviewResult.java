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

package com.google.copybara.git.gerritapi;

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkValue;

/** https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-result */
@SuppressWarnings("unused")
@StarlarkBuiltin(
    name = "gerritapi.ReviewResult",
    doc = "Gerrit review result.")
public class ReviewResult implements StarlarkValue {
  // These private fields are assigned by reflection magic during JSON decoding.
  @Key private Map<String, Integer> labels;
  @Key private boolean ready;

  // constructor for tests
  public ReviewResult(Map<String, Integer> labels, boolean ready) {
    this.labels = labels;
    this.ready = ready;
  }

  // constructor for JSON decoder reflection magic
  public ReviewResult() {}

  @StarlarkMethod(
      name = "labels",
      doc = "Map of labels to values after the review was posted.",
      structField = true)
  public Map<String, StarlarkInt> getLabelsForStarlark() {
    // Convert Integer (Gson-friendly) to StarlarkInt.
    ImmutableMap.Builder<String, StarlarkInt> m = ImmutableMap.builder();
    for (Map.Entry<String, Integer> e : getLabels().entrySet()) {
      m.put(e.getKey(), StarlarkInt.of(e.getValue()));
    }
    return m.build(); // becomes a Starlark dict
  }

  public Map<String, Integer> getLabels() {
    return labels == null ? ImmutableMap.of() : ImmutableMap.copyOf(labels);
  }

  @StarlarkMethod(
    name = "ready",
    doc =
        "If true, the change was moved from WIP to ready for review as a result of this action."
            + " Not set if false.",
      structField = true
  )
  public boolean isReady() {
    return ready;
  }

  @Override
  public void repr(Printer printer) {
    printer.append(toString());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("labels", labels)
        .add("ready", ready)
        .toString();
  }
}
