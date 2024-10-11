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

package com.google.copybara;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.util.Glob;
import javax.annotation.Nullable;

/**
 * Interface for self-description. The information returned should be sufficient to create a new
 * instance with identical migration behavior (but potentially different side effects). This is
 * intended for discovering changes in a config.
 */
public interface ConfigItemDescription {

  default String getType() {
    return getClass().getName();
  }

  /** Returns a key-value ist of the options the endpoint was instantiated with. */
  default ImmutableSetMultimap<String, String> describe(@Nullable Glob originFiles) {
    ImmutableSetMultimap.Builder<String, String> builder =
        new ImmutableSetMultimap.Builder<String, String>()
        .put("type", getType());
    return builder.build();
  }

  /** Returns a key-value list describing the credentials the endpoint was instantiated with. */
  default ImmutableList<ImmutableSetMultimap<String, String>> describeCredentials() {
    return ImmutableList.of();
  }

  /** Returns a key-value list describing the credentials the endpoint was instantiated with. */
  default ImmutableList<ImmutableSetMultimap<String, String>> describeCredentials(String endpoint) {
    ImmutableList<ImmutableSetMultimap<String, String>> creds = describeCredentials();
    if (creds.isEmpty()) {
      return creds;
    }
    ImmutableList.Builder <ImmutableSetMultimap<String, String>> builder = ImmutableList.builder();
    for (ImmutableSetMultimap<String, String> cred : creds) {
      builder.add(ImmutableSetMultimap.<String, String>builder().putAll(cred)
          .put("endpoint", endpoint).build());
    }
    return builder.build();
  }
}
