/*
 * Copyright (C) 2018 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * A set of modules and options for evaluating a Skylark config file.
 */
public class ModuleSet {

  private final Options options;
  // TODO(malcon): Remove this once all modules are @StarlarkMethod
  private final ImmutableSet<Class<?>> staticModules;
  private final ImmutableMap<String, Object> modules;

  ModuleSet(Options options,
      ImmutableSet<Class<?>> staticModules,
      ImmutableMap<String, Object> modules) {
    this.options = Preconditions.checkNotNull(options);
    this.staticModules = Preconditions.checkNotNull(staticModules);
    this.modules = Preconditions.checkNotNull(modules);
  }

  /**
   * Copybara options
   */
  public Options getOptions() {
    return options;
  }

  /**
   * Static modules. Will be deleted.
   * TODO(malcon): Delete
   */
  public ImmutableSet<Class<?>> getStaticModules() {
    return staticModules;
  }

  /**
   * Non-static Copybara modules.
   */
  public ImmutableMap<String, Object> getModules() {
    return modules;
  }

  
}
