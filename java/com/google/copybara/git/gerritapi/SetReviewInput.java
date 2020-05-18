
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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.devtools.build.lib.syntax.Printer;
import com.google.devtools.build.lib.syntax.StarlarkValue;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkDocumentationCategory;

/** See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-input. */
@StarlarkBuiltin(
    name = "SetReviewInput",
    doc =
        "Input for posting a review to Gerrit. See "
            + "https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-input",
    category = StarlarkDocumentationCategory.BUILTIN)
public class SetReviewInput implements StarlarkValue {

  @Key String message;
  @VisibleForTesting
  @Key Map<String, Integer> labels;

  @SuppressWarnings("unused")
  public SetReviewInput() {
    this.labels = Collections.emptyMap();
  }

  public SetReviewInput(String message, Map<String, Integer> labels) {
    this.message = message;
    this.labels = labels;
  }

  public static SetReviewInput create(String message, Map<String, Integer> labels) {
    return new SetReviewInput(message, labels);
  }

  public String getMessage() {
    return message;
  }

  public Map<String, Integer> getLabels() {
    return labels;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("message", message)
        .add("labels", labels)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SetReviewInput)) {
      return false;
    }
    SetReviewInput setReviewInput = (SetReviewInput) o;
    return Objects.equals(message, setReviewInput.message)
        && Objects.equals(labels, setReviewInput.labels);
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, labels);
  }

  @Override
  public void repr(Printer printer) {
    printer.append(toString());
  }
}
