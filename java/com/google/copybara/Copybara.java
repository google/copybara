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

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.copybara.Info.MigrationReference;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigValidator;
import com.google.copybara.config.Migration;
import com.google.copybara.config.ValidationResult;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.monitor.EventMonitor.InfoFinishedEvent;
import com.google.copybara.util.TablePrinter;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Copybara tool main class.
 *
 * <p>Executes Copybara subcommands independently from the environment that they are invoked from
 * (command-line, service).
 */
public class Copybara {

  private static final int REVISION_MAX_LENGTH = 15;
  private static final int DESCRIPTION_MAX_LENGTH = 80;
  private static final int AUTHOR_MAX_LENGTH = 40;
  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final ConfigValidator configValidator;
  private final Consumer<Migration> migrationRanConsumer;

  public Copybara(
      ConfigValidator configValidator,
      Consumer<Migration> migrationRanConsumer) {
    this.configValidator = Preconditions.checkNotNull(configValidator);
    this.migrationRanConsumer = Preconditions.checkNotNull(migrationRanConsumer);
  }

  /**
   * Runs the migration specified by {@code migrationName}.
   */
  public void run(Options options, ConfigLoader configLoader, String migrationName,
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
    @SuppressWarnings("unchecked")
    Workflow<? extends Revision, ? extends Revision> workflow =
        (Workflow<? extends Revision, ? extends Revision>) migration;
    new ReadConfigFromChangeWorkflow<>(workflow, options, configLoader, configValidator)
        .run(workdir, sourceRefs);
  }

  /** Retrieves the {@link Info} of the {@code migrationName} and prints it to the console. */
  public void info(Options options, Config config, String migrationName)
      throws ValidationException, RepoException {
    @SuppressWarnings("unchecked")
    Info<? extends Revision> info = getInfo(migrationName, config);
    Console console = options.get(GeneralOptions.class).console();
    int outputSize = 0;
    for (MigrationReference<? extends Revision> migrationRef : info.migrationReferences()) {
      console.info(String.format(
          "'%s': last_migrated %s - last_available %s.",
          migrationRef.getLabel(),
          migrationRef.getLastMigrated() != null
              ? migrationRef.getLastMigrated().asString() : "None",
          migrationRef.getLastAvailableToMigrate() != null
              ? migrationRef.getLastAvailableToMigrate().asString() : "None"));

      ImmutableList<? extends Change<? extends Revision>> availableToMigrate =
          migrationRef.getAvailableToMigrate();
      int outputLimit = options.get(GeneralOptions.class).getOutputLimit();
      if (!availableToMigrate.isEmpty()) {
        console.infoFmt(
            "Available changes %s:",
            availableToMigrate.size() <= outputLimit
                ? String.format("(%d)", availableToMigrate.size())
                : String.format(
                    "(showing only first %d out of %d)", outputLimit, availableToMigrate.size()));
        TablePrinter table = new TablePrinter("Date", "Revision", "Description", "Author");
        for (Change<? extends Revision> change :
            Iterables.limit(availableToMigrate, outputLimit)) {
          outputSize++;
          table.addRow(
              change.getDateTime().format(DATE_FORMATTER),
              Ascii.truncate(change.getRevision().asString(), REVISION_MAX_LENGTH, ""),
              Ascii.truncate(change.firstLineMessage(), DESCRIPTION_MAX_LENGTH, "..."),
              Ascii.truncate(change.getAuthor().toString(), AUTHOR_MAX_LENGTH, "..."));
        }
        for (String line : table.build()) {
          console.info(line);
        }
      }
      // TODO(danielromero): Check flag usage on 2018-06 and decide if we keep it
      if (outputSize > 100) {
        console.infoFmt(
            "Use %s to limit the output of the command.", GeneralOptions.OUTPUT_LIMIT_FLAG);
      }
    }
    options.get(GeneralOptions.class).eventMonitor().onInfoFinished(new InfoFinishedEvent(info));
  }

  /** Returns the {@link Info} of the {@code migrationName}. */
  public Info<? extends Revision> getInfo(String migrationName, Config config)
      throws ValidationException, RepoException {
    return config.getMigration(migrationName).getInfo();
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
  public ValidationResult validate(Options options, ConfigLoader configLoader, String migrationName)
      throws IOException {
    Console console = options.get(GeneralOptions.class).console();
    ValidationResult.Builder resultBuilder = new ValidationResult.Builder();
    try {
      Config config = configLoader.load(console);
      resultBuilder.append(validateConfig(config, migrationName));
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

  protected Config loadConfig(Options options, ConfigLoader configLoader, String migrationName)
      throws IOException, ValidationException {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    Console console = generalOptions.console();
    Config config = configLoader.load(console);
    console.progress("Validating configuration");
    ValidationResult result = validateConfig(config, migrationName);
    if (!result.hasErrors()) {
      return config;
    }
    result.getErrors().forEach(console::error);
    console.error("Configuration is invalid.");
    throw new ValidationException("Error validating configuration: Configuration is invalid.");
  }

  /**
   * Returns a list of validation error messages, if any, for the given configuration.
   */
  private ValidationResult validateConfig(Config config, String migrationName) {
    return configValidator.validate(config, migrationName);
  }
}
