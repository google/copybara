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

package com.google.copybara.git.github.api;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.util.Key;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * Request type for adding a label to an issue
 * https://developer.github.com/v3/issues/labels/#add-labels-to-an-issue
 */
public class AddLabels {
  @Key List<String> labels;

  public AddLabels(List<String> labels) {
    this.labels = checkNotNull(ImmutableList.copyOf(labels));
  }

  public AddLabels() { }

  public ImmutableList<String> getLabels() {
    return ImmutableList.copyOf(labels);
  }
}
