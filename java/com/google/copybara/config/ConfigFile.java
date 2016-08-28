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

package com.google.copybara.config;

import java.io.IOException;

/**
 * An object representing a configuration file and that it can be used to resolve
 * other config files relative to this one.
 */
public interface ConfigFile {

  /**
   * Resolve {@code label} relative to the current config file.
   *
   * @throws CannotResolveLabel if the label cannot be resolved to a content
   */
  ConfigFile resolve(String label) throws CannotResolveLabel;

  /**
   * Resolved, non-relative name of the config file.
   */
  String path();

  /**
   * Get the contents of the file.
   *
   * <p>Implementations of this interface should prefer to not eagerly load the content of this
   * method is call in order to allow the callers to check its own cache if they already have
   * {@link #path()} path.
   */
  byte[] content() throws IOException;
}
