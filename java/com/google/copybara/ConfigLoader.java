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
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import java.io.IOException;

/**
 * Loads the configuration from a given config file.
 */
public class ConfigLoader {

  private final SkylarkParser skylarkParser;
  private final ConfigFile<?> configFile;
  private final ModuleSet moduleSet;

  public ConfigLoader(ModuleSet moduleSet, ConfigFile<?> configFile) {
    this.moduleSet = moduleSet;
    this.skylarkParser = new SkylarkParser(this.moduleSet.getStaticModules());
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

  protected Config loadForConfigFile(Console console, ConfigFile<?> configFile)
      throws IOException, ValidationException {
    return skylarkParser.loadConfig(configFile, moduleSet, console);
  }

  public Config loadForRevision(Console console, Revision revision)
      throws ValidationException, RepoException{
    throw new RuntimeException("This origin/configuration doesn't allow loading configs from"
        + " specific revisions");
  }

  public boolean supportsLoadForRevision() {
    return false;
  }
}
