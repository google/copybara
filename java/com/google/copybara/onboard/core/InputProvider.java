/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.onboard.core;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import java.util.Set;

/**
 * A Data provider provides values of {@link Input} to {@code Populator}s and {@code
 * ConfigTemplate}s
 */
public interface InputProvider {

  /**
   * Resolve the value for an {@link Input} object.
   */
  <T> Optional<T> resolve(Input<T> input, InputProviderResolver db)
      throws InterruptedException, CannotProvideException;

  /**
   * Return the Set of {@link Input} objects that this {@link InputProvider} can provide, with its
   * associated priority. The higher, the more priority.
   */
  ImmutableMap<Input<?>, Integer> provides() throws CannotProvideException;

  int DEFAULT_PRIORITY = 100;
  int COMMAND_LINE_PRIORITY = 1000;

  /**
   * Given a set of Input returns a map of the Input to default priority. This is a helper function
   * to be used by {@code provide} implementations that don't care about priorities.
   */
  default ImmutableMap<Input<?>, Integer> defaultPriority(Set<Input<?>> data) {
    return data.stream().collect(ImmutableMap.toImmutableMap(d -> d, d -> DEFAULT_PRIORITY));
  }

}
