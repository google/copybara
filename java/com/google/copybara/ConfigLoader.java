/*
 * Copyright (C) 2017 Google Inc.
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
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.SkylarkParser;
import com.google.copybara.config.SkylarkParser.ConfigWithDependencies;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.StarlarkMode;
import java.io.IOException;

/**
 * Loads the configuration from a given config file.
 */
public class ConfigLoader {

  private final SkylarkParser skylarkParser;
  private final ConfigFile configFile;
  private final ModuleSet moduleSet;

  public ConfigLoader(ModuleSet moduleSet, ConfigFile configFile, StarlarkMode validateStarlark) {
    this.moduleSet = moduleSet;
    this.skylarkParser = new SkylarkParser(this.moduleSet.getStaticModules(), validateStarlark);
    this.configFile = Preconditions.checkNotNull(configFile);
  }

  /**
   * Returns a string representation of the location of this configuration.
   */
  public String location() {
    return configFile.path();
  }

  /**
   * Loads the configuration using this loader.
   * @param console the console to use for reporting progress/errors
   */
  public Config load(Console console) throws ValidationException, IOException {
    return loadForConfigFile(console, configFile);
  }

  /**
   * Loads the configuration using this loader.
   * @param console the console to use for reporting progress/errors
   */
  public ConfigWithDependencies loadWithDependencies(Console console)
      throws ValidationException, IOException {
    console.progressFmt("Loading config and dependencies %s", configFile.getIdentifier());

    try (ProfilerTask ignore = moduleSet.getOptions().get(GeneralOptions.class).profiler()
        .start("loading_config_with_deps")) {
      return skylarkParser.getConfigWithTransitiveImports(configFile, moduleSet, console);
    }
  }

  protected Config loadForConfigFile(Console console, ConfigFile configFile)
      throws IOException, ValidationException {
    console.progressFmt("Loading config %s", configFile.getIdentifier());

    try (ProfilerTask ignore = moduleSet.getOptions().get(GeneralOptions.class).profiler()
        .start("loading_config")) {
      return skylarkParser.loadConfig(configFile, moduleSet, console);
    }
  }

  protected Config doLoadForRevision(Console console, Revision revision)
      throws ValidationException, RepoException {
    throw new UnsupportedOperationException(
        "This origin/configuration doesn't allow loading configs from specific revisions");
  }

  public final Config loadForRevision(Console console, Revision revision)
      throws ValidationException, RepoException {
    try (ProfilerTask ignore = moduleSet.getOptions().get(GeneralOptions.class).profiler()
        .start("loading_config_for_revision")) {
      return doLoadForRevision(console, revision);
    }
  }

  public boolean supportsLoadForRevision() {
    return false;
  }
}
