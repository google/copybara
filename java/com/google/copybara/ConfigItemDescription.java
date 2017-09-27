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

import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.util.Glob;
import javax.annotation.Nullable;

/**
 * Interface for self-description. The information returned should be sufficient to create a new
 * instance with identical migration behavior (but potentially different side effects). This is
 * intended for discovering changes in a config.
 */
public interface ConfigItemDescription {

  /** Returns a key-value ist of the options the endpoint was instantiated with. */
  default ImmutableSetMultimap<String, String> describe(@Nullable Glob originFiles) {
    return ImmutableSetMultimap.of("type", getClass().getName());
  }
}
