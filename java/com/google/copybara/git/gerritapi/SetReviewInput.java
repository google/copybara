
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
import java.util.Collections;
import java.util.Map;

/**
 * See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-input.
 */
public class SetReviewInput {

  @VisibleForTesting
  @Key
  Map<String, Short> labels;

  @SuppressWarnings("unused")
  public SetReviewInput() {
    this.labels = Collections.emptyMap();
  }

  public SetReviewInput(Map<String, Short> labels) {
    this.labels = labels;
  }

  public static SetReviewInput create(Map<String, Short> labels) {
    return new SetReviewInput(labels);
  }
}
