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

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.beust.jcommander.Parameters;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigValidator;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.config.Migration;
import com.google.copybara.config.ValidationResult;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Executes the migration for the given config.
 */
@Parameters(separators = "=", commandDescription = "Executes the migration for the given config.")
public class MigrateCmd implements CopybaraCmd {

  private final ConfigValidator configValidator;
  private final Consumer<Migration> migrationRanConsumer;
  private final ConfigLoaderProvider configLoaderProvider;
  private final ModuleSet moduleSet;

  MigrateCmd(ConfigValidator configValidator, Consumer<Migration> migrationRanConsumer,
      ConfigLoaderProvider configLoaderProvider, ModuleSet moduleSet) {
    this.configValidator = Preconditions.checkNotNull(configValidator);
    this.migrationRanConsumer = Preconditions.checkNotNull(migrationRanConsumer);
    this.configLoaderProvider = Preconditions.checkNotNull(configLoaderProvider);
    this.moduleSet = moduleSet;
  }

  @Override
  public ExitCode run(CommandEnv commandEnv)
      throws RepoException, ValidationException, IOException {
    ConfigFileArgs configFileArgs = commandEnv.parseConfigFileArgs(this,
        /*useSourceRef*/true);
    ImmutableList<String> sourceRefs = configFileArgs.getSourceRefs();
    String workflowName = configFileArgs.getWorkflowName();
    updateEnvironment(workflowName);
    run(
        commandEnv.getOptions(),
        configLoaderProvider.newLoader(
            configFileArgs.getConfigPath(),
            sourceRefs.size() == 1 ? Iterables.getOnlyElement(sourceRefs) : null),
        workflowName,
        commandEnv.getWorkdir(),
        sourceRefs);
    return ExitCode.SUCCESS;
  }

  /**
   * Runs the migration specified by {@code migrationName}.
   */
  private void run(Options options, ConfigLoader configLoader, String migrationName,
      Path workdir, ImmutableList<String> sourceRefs)
      throws RepoException, ValidationException, IOException {
    Config config = loadConfig(options, configLoader, migrationName);

    Migration migration = config.getMigration(migrationName);

    if (!options.get(WorkflowOptions.class).isReadConfigFromChange()) {
      this.migrationRanConsumer.accept(migration);
      migration.run(workdir, sourceRefs);
      return;
    }

    checkCondition(configLoader.supportsLoadForRevision(),
        "%s flag is not supported for the origin/config file path",
        WorkflowOptions.READ_CONFIG_FROM_CHANGE);

    // A safeguard, mirror workflows are not supported in the service anyway
    checkCondition(migration instanceof Workflow,
        "Flag --read-config-from-change is not supported for non-workflow migrations: %s",
        migrationName);
    migrationRanConsumer.accept(migration);

    Workflow<? extends Revision, ? extends Revision> workflow =
        (Workflow<? extends Revision, ? extends Revision>) migration;
    new ReadConfigFromChangeWorkflow<>(workflow, options, configLoader, configValidator)
        .run(workdir, sourceRefs);
  }

  private Config loadConfig(Options options, ConfigLoader configLoader, String migrationName)
      throws IOException, ValidationException {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    Console console = generalOptions.console();
    Config config = configLoader.load(console);
    console.progress("Validating configuration");
    ValidationResult result = configValidator.validate(config, migrationName);
    if (!result.hasErrors()) {
      return config;
    }
    result.getErrors().forEach(console::error);
    console.error("Configuration is invalid.");
    throw new ValidationException("Error validating configuration: Configuration is invalid.");
  }

  private void updateEnvironment(String migrationName) {
    for (Object module : moduleSet.getModules().values()) {
      // We mutate the module per file loaded. Not ideal but it is the best we can do.
      if (module instanceof LabelsAwareModule) {
        LabelsAwareModule m = (LabelsAwareModule) module;
        m.setWorkflowName(migrationName);
      }
    }
  }

  @Override
  public String name() {
    return "migrate";
  }
}
