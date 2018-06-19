
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
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import java.util.Collections;
import java.util.Map;

/**
 * See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-input.
 */
@SkylarkModule(
    name = "SetReviewInput",
    doc =
        "Input for posting a review to Gerrit. See "
            + "https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-input",
    category = SkylarkModuleCategory.BUILTIN
)
public class SetReviewInput {

  @VisibleForTesting
  @Key
  Map<String, Integer> labels;

  @SuppressWarnings("unused")
  public SetReviewInput() {
    this.labels = Collections.emptyMap();
  }

  public SetReviewInput(Map<String, Integer> labels) {
    this.labels = labels;
  }

  public static SetReviewInput create(Map<String, Integer> labels) {
    return new SetReviewInput(labels);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("labels", labels).toString();
  }
}
