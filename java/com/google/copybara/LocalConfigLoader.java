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

import static com.google.copybara.MainArguments.COPYBARA_SKYLARK_CONFIG_FILENAME;

import com.google.common.base.Preconditions;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.ConfigLoader;
import com.google.copybara.config.PathBasedConfigFile;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * A {@link ConfigLoader} for the local filesystem.
 *
 * <p>If the root path is not provided, tries to find it with heuristics.
 */
public class LocalConfigLoader extends ConfigLoader<Path> {

  private final GeneralOptions generalOptions;
  private final Path configPath;

  public LocalConfigLoader(
      ModuleSupplier<Path> moduleSupplier, GeneralOptions generalOptions, Path configPath) {
    super(moduleSupplier);
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.configPath = Preconditions.checkNotNull(configPath);
  }

  @Override
  public String location() {
    return configPath.toString();
  }

  @Override
  protected ConfigFile<Path> getConfigFile() throws ValidationException {
    String fileName = configPath.getFileName().toString();
    ValidationException.checkCondition(
        fileName.contentEquals(COPYBARA_SKYLARK_CONFIG_FILENAME),
        String.format("Copybara config file filename should be '%s' but it is '%s'.",
            COPYBARA_SKYLARK_CONFIG_FILENAME, configPath.getFileName()));

    // Treat the top level element specially since it is passed thru the command line.
    if (!Files.exists(configPath)) {
      throw new CommandLineException("Configuration file not found: " + configPath);
    }
    Path root = generalOptions.getConfigRoot() != null
        ? generalOptions.getConfigRoot()
        : findConfigRootHeuristic(configPath.toAbsolutePath());
    return new PathBasedConfigFile(configPath.toAbsolutePath(), root).withContentLogging();
  }

  /**
   * Find the root path for resolving configuration file paths and resources. This method
   * assumes that the .git containing directory is the root path.
   *
   * <p>This could be extended to other kind of source control systems.
   */
  @Nullable
  protected Path findConfigRootHeuristic(Path configPath) {
    Path parent = configPath.getParent();
    while (parent != null) {
      if (Files.isDirectory(parent.resolve(".git"))) {
        return parent;
      }
      parent = parent.getParent();
    }
    return null;
  }
}
