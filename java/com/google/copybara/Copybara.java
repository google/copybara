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

package com.google.copybara;

import com.google.common.base.Preconditions;
import com.google.copybara.Info.MigrationReference;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.ConfigValidator;
import com.google.copybara.config.SkylarkParser;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Copybara tool main class.
 *
 * <p>Executes Copybara workflows independently from the environment that they are invoked from
 * (command-line, service).
 */
public class Copybara {

  private final SkylarkParser skylarkParser;
  private final ConfigValidator configValidator;

  public Copybara(SkylarkParser skylarkParser) {
    this.skylarkParser = Preconditions.checkNotNull(skylarkParser);
    this.configValidator = new ConfigValidator();
  }

  public Copybara(SkylarkParser skylarkParser, ConfigValidator configValidator) {
    this.skylarkParser = Preconditions.checkNotNull(skylarkParser);
    this.configValidator = Preconditions.checkNotNull(configValidator);
  }

  public void run(Options options, ConfigFile<?> configContents, String migrationName,
      Path baseWorkdir, @Nullable String sourceRef)
      throws RepoException, ValidationException, IOException {
    Config config = loadConfig(options, configContents, migrationName);
    config.getMigration(migrationName).run(baseWorkdir, sourceRef);
  }

  public Config info(Options options, ConfigFile<?> configContents, String migrationName)
      throws IOException, ValidationException, RepoException {
    Console console = options.get(GeneralOptions.class).console();
    Config config = loadConfig(options, configContents, migrationName);
    Info info = config.getMigration(migrationName).getInfo();
    for (MigrationReference ref : info.migrationReferences()) {
      console.info(
          String.format("'%s': last_migrated %s - last_available %s.",
              ref.getLabel(),
              ref.getLastMigrated() != null ? ref.getLastMigrated().asString() : "None",
              ref.getNextToMigrate() != null ? ref.getNextToMigrate().asString() : "None"));
    }
    return config;
  }

  public boolean validate(Options options, ConfigFile<?> configContent, String migrationName)
      throws RepoException, IOException {
    Console console = options.get(GeneralOptions.class).console();
    ArrayList<String> messages = new ArrayList<>();
    try {
      Config config = skylarkParser.loadConfig(configContent, options);
      messages.addAll(validateConfig(config, migrationName));
    } catch (ValidationException e) {
      // The validate subcommand should not throw Validation exceptions but log a result
      StringBuilder error = new StringBuilder(e.getMessage()).append("\n");
      Throwable cause = e.getCause();
      while (cause != null) {
        error.append("  CAUSED BY: ").append(cause.getMessage()).append("\n");
        cause = cause.getCause();
      }
      messages.add(error.toString());
    }
    if (messages.isEmpty()) {
      console.info(String.format("Configuration '%s' is valid.", configContent.path()));
    } else {
      console.error(String.format("Configuration '%s' is invalid.", configContent.path()));
      messages.forEach(console::error);
    }
    return messages.isEmpty();
  }

  private Config loadConfig(Options options, ConfigFile<?> configContents, String migrationName)
      throws IOException, ValidationException {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    Console console = generalOptions.console();
    Config config = skylarkParser.loadConfig(configContents, options);
    console.progress("Validating configuration");
    List<String> validationMessages = validateConfig(config, migrationName);
    if (!validationMessages.isEmpty()) {
      console.error("Configuration is invalid:");
      for (String validationMessage : validationMessages) {
        console.error(validationMessage);
      }
      throw new ValidationException("Error validating configuration: Configuration is invalid.");
    }
    return config;
  }

  /**
   * Returns a list of validation error messages, if any, for the given configuration.
   */
  private List<String> validateConfig(Config config, String migrationName) {
    return configValidator.validate(config, migrationName);
  }
}
