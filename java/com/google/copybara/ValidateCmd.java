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

import static com.google.common.base.Preconditions.checkNotNull;

import com.beust.jcommander.Parameters;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigValidator;
import com.google.copybara.config.Migration;
import com.google.copybara.config.ValidationResult;
import com.google.copybara.config.ValidationResult.ValidationMessage;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Validates that the configuration is correct.
 */
@Parameters(separators = "=", commandDescription = "Validates that the configuration is correct.")
public class ValidateCmd implements CopybaraCmd {

  private final ConfigValidator configValidator;
  private final ConfigLoaderProvider configLoaderProvider;

  ValidateCmd(ConfigValidator configValidator, Consumer<Migration> migrationRanConsumer,
      ConfigLoaderProvider configLoaderProvider) {
    this.configValidator = checkNotNull(configValidator);
    this.configLoaderProvider = checkNotNull(configLoaderProvider);
  }

  @Override
  public ExitCode run(CommandEnv commandEnv)
      throws ValidationException, IOException, RepoException {
    ConfigFileArgs configFileArgs = commandEnv.parseConfigFileArgs(this, /*useSourceRef*/false);
    ConfigLoader configLoader =
        configLoaderProvider.newLoader(
            configFileArgs.getConfigPath(), configFileArgs.getSourceRef());
    ValidationResult result =
        validate(
            commandEnv.getOptions(),
            configLoader,
            configFileArgs.getWorkflowName());

    Console console = commandEnv.getOptions().get(GeneralOptions.class).console();
    for (ValidationMessage message : result.getAllMessages()) {
      switch (message.getLevel()) {
        case WARNING:
          console.warn(message.getMessage());
          break;
        case ERROR:
          console.error(message.getMessage());
          break;
      }
    }
    if (result.hasErrors()) {
      console.errorFmt("Configuration '%s' is invalid.", configLoader.location());
      return ExitCode.CONFIGURATION_ERROR;
    }
    console.infoFmt("Configuration '%s' is valid.", configLoader.location());
    return ExitCode.SUCCESS;
  }

  /**
   * Validates that the configuration is correct and that there is a valid migration specified by
   * {@code migrationName}.
   *
   * <p>Note that, besides validating the specific migration, all the configuration will be
   * validated syntactically.
   *
   * Returns true iff this configuration is valid.
   */
  private ValidationResult validate(Options options, ConfigLoader configLoader,
      String migrationName)
      throws IOException {
    Console console = options.get(GeneralOptions.class).console();
    ValidationResult.Builder resultBuilder = new ValidationResult.Builder();
    try {
      Config config = configLoader.load(console);
      resultBuilder.append(configValidator.validate(config, migrationName));
    } catch (ValidationException e) {
      // The validate subcommand should not throw Validation exceptions but log a result
      StringBuilder error = new StringBuilder(e.getMessage()).append("\n");
      Throwable cause = e.getCause();
      while (cause != null) {
        error.append("  CAUSED BY: ").append(cause.getMessage()).append("\n");
        cause = cause.getCause();
      }
      resultBuilder.error(error.toString());
    }
    return resultBuilder.build();
  }

  @Override
  public String name() {
    return "validate";
  }
}
