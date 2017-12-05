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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;

/**
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#label-info
 */
public class LabelInfo {
  @Key boolean optional;
  @Key AccountInfo approved;
  @Key AccountInfo rejected;
  @Key AccountInfo recommended;
  @Key AccountInfo disliked;
  @Key boolean blocking;
  @Key int value;
  @Key("default_value") int defaultValue;
  @Key Map<String, String> values;
  @Key List<ApprovalInfo> all;

  public boolean isOptional() {
    return optional;
  }

  public AccountInfo getApproved() {
    return approved;
  }

  public AccountInfo getRejected() {
    return rejected;
  }

  public AccountInfo getRecommended() {
    return recommended;
  }

  public AccountInfo getDisliked() {
    return disliked;
  }

  public boolean isBlocking() {
    return blocking;
  }

  public int getValue() {
    return value;
  }

  public int getDefaultValue() {
    return defaultValue;
  }

  public ImmutableMap<String, String> getValues() {
    return ImmutableMap.copyOf(values);
  }

  public ImmutableList<ApprovalInfo> getAll() {
    return all != null ? ImmutableList.copyOf(all) : ImmutableList.of();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("optional", optional)
        .add("approved", approved)
        .add("rejected", rejected)
        .add("recommended", recommended)
        .add("disliked", disliked)
        .add("blocking", blocking)
        .add("value", value)
        .add("defaultValue", defaultValue)
        .add("values", values)
        .add("all", all)
        .toString();
  }
}
