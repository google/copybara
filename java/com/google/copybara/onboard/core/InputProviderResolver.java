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
import com.google.copybara.onboard.core.template.ConfigGenerator;
import java.util.Optional;

/**
 * This interface represents an object that can be used for resolving {@link Input} objects and
 * can be use by {@code InputProvider}s to resolve {@link Input}s recursively.
 */
public interface InputProviderResolver {

  /**
   * Given an {@link Input}, resolve to the corresponding value if possible
   *
   * @throws InterruptedException if user cancels the request (e.g. Ctrl break on the console)
   * @throws CannotProvideException if there is a failure during the resolution
   */
  <T> Optional<T> resolve(Input<T> input) throws InterruptedException, CannotProvideException;

  /**
   * Resolve an input that might not have a value but that it is optional.
   */
  default <T> Optional<T> resolveOptional(Input<T> input) throws InterruptedException {
    try {
      return resolve(input);
    } catch (CannotProvideException e) {
      return Optional.empty();
    }
  }

  /**
   * Config generators registered in the system
   */
  default ImmutableMap<String, ConfigGenerator> getGenerators() {
    return ImmutableMap.of();
  }
}
