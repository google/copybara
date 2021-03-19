package com.google.copybara;

/*
 * Copyright (C) 2019 Google Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.copybara.config.Config;
import com.google.copybara.config.SkylarkParser.ConfigWithDependencies;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import java.io.IOException;

/** A class providing additional context for CMD*/
public interface ContextProvider {
  /** get context for CMD */
  ImmutableMap<String, String> getContext(Config config, ConfigFileArgs configFileArgs,
      ConfigLoaderProvider configLoaderProvider, Console console)
      throws ValidationException, IOException;

  /** get context for CMD */
  default ImmutableMap<String, String> getContext(
      ConfigWithDependencies config, ConfigFileArgs configFileArgs,
      ConfigLoaderProvider configLoaderProvider, Options options, Console console)
      throws ValidationException, IOException {
    return getContext(config.getConfig(), configFileArgs, configLoaderProvider, console);
  };
}
