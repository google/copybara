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

import com.google.copybara.Options;
import net.starlark.java.annot.StarlarkBuiltin;

/**
 * A {@link StarlarkBuiltin} that implements this interface will be initialized with the options.
 *
 * <p>This method will be invoked just after registering the namespace objects in Skylark.
 */
public interface OptionsAwareModule {

  /**
   * Set the options for the current Copybara run.
   */
  void setOptions(Options options);
}
