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

import com.google.copybara.util.FileUtil;
import java.io.IOException;

/**
 * An object representing a configuration file and that it can be used to resolve
 * other config files relative to this one.
 */
public abstract class ConfigFile<T> {

  private final String path;

  /**
   * Construct a config file using the passed path.
   */
  public ConfigFile(String path) {
    this.path = path;
  }

  /**
   * Resolve {@code label} relative to the current config file.
   *
   * @throws CannotResolveLabel if the label cannot be resolved to a content
   */
  public final ConfigFile<T> resolve(String label) throws CannotResolveLabel {
    boolean isAbsolute = label.startsWith("//");
    // Remove '//' for absolute paths
    checkNormalized(isAbsolute ? label.substring(2) : label);
    return createConfigFile(label, isAbsolute
        ? relativeToRoot(label.substring(2))
        : relativeToCurrentPath(label));
  }

  /**
   * Resolved, non-relative name of the config file.
   */
  public final String path() {
    return path;
  }

  /**
   * Get the contents of the file.
   *
   * <p>Implementations of this interface should prefer to not eagerly load the content of this
   * method is call in order to allow the callers to check its own cache if they already have
   * {@link #path()} path.
   */
  public abstract byte[] content() throws IOException;

  /**
   * Resolves a label relative to the root config file, if present.
   *
   * @throws CannotResolveLabel if the root doesn't exist or the file cannot be resolved
   */
  protected abstract T relativeToRoot(String label) throws CannotResolveLabel;

  /**
   * Resolves a label relative to the current config file.
   *
   * @throws CannotResolveLabel if the root doesn't exist or the file cannot be resolved
   */
  protected abstract T relativeToCurrentPath(String label) throws CannotResolveLabel;

  /**
   * Perform additional validations and construct a ConfigFile object
   */
  protected abstract ConfigFile<T> createConfigFile(String label, T resolved)
      throws CannotResolveLabel;

  private void checkNormalized(String label) throws CannotResolveLabel {
    try {
      FileUtil.checkNormalizedRelative(label);
    } catch (Exception e) {
      throw new CannotResolveLabel(String.format("Invalid label '%s': %s", label, e.getMessage()));
    }
  }
}
