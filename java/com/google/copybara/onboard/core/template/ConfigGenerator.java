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

package com.google.copybara.onboard.core.template;

import com.google.common.collect.ImmutableSet;
import com.google.copybara.onboard.core.CannotProvideException;
import com.google.copybara.onboard.core.Input;
import com.google.copybara.onboard.core.InputProviderResolver;

/**
 * Config generators can generate a config file (as String) given
 * an {@link InputProviderResolver} with context of the {@code Input}s.
 */
public interface ConfigGenerator {

  String generate(InputProviderResolver inputProviders)
      throws CannotProvideException, InterruptedException;

  /**
   * Name of the template.
   */
  String name();

  /** List of {@link Input}s that the generator consumes */
  ImmutableSet<Input<?>> consumes();
}
