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

import com.google.common.collect.ImmutableMap;
import java.util.function.Supplier;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.StarlarkThread;

/**
 * A {@link StarlarkBuiltin} that implements this interface will be given information about the
 * config files and resources loaded by the configuration.
 */
public interface LabelsAwareModule {

  /**
   * Called before invoking any methods on a module in order to give the module access to the
   * current config file. This may be called multiple times, in which case only the most recent
   * {@link ConfigFile} should be used.
   *
   * TODO(copybara-team): Figure out how this works with concurrent loading.
   */
  default void setConfigFile(ConfigFile mainConfigFile, ConfigFile currentConfigFile) {

  }

  /**
   * Called before invoking any methonds on a module to give the module access to the current
   * workflow name. This may be called multiple times, in which case only the most recent
   * should be used.
   */
  default void setWorkflowName(String workflowName) {
  }

  /**
   * A Supplier that returns all the files loaded by the configuration loading. The supplier
   * shouldn't be evaluated before loading finishes.
   */
  default void setAllConfigResources(Supplier<ImmutableMap<String, ConfigFile>> configs) {

  }

  /**
   * Set handler for print statements executed by Starlark code run during a migration (for example
   * dynamic transformations, migration hooks or feedback mechanism).
   */
  default void setPrintHandler(StarlarkThread.PrintHandler printHandler) {}
}
