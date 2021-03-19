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

import com.beust.jcommander.Parameters;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.copybara.Info.MigrationReference;
import com.google.copybara.config.Config;
import com.google.copybara.config.Migration;
import com.google.copybara.config.SkylarkParser.ConfigWithDependencies;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.monitor.EventMonitor.InfoFailedEvent;
import com.google.copybara.monitor.EventMonitor.InfoFinishedEvent;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.TablePrinter;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

/**
 * Reads the last migrated revision in the origin and destination.
 */
@Parameters(separators = "=",
    commandDescription = "Reads the last migrated revision in the origin and destination.")
public class InfoCmd implements CopybaraCmd {

  private static final int REVISION_MAX_LENGTH = 15;
  private static final int DESCRIPTION_MAX_LENGTH = 80;
  private static final int AUTHOR_MAX_LENGTH = 40;
  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final ConfigLoaderProvider configLoaderProvider;
  private final ContextProvider contextProvider;

  public InfoCmd(ConfigLoaderProvider configLoaderProvider, ContextProvider contextProvider) {
    this.configLoaderProvider = Preconditions.checkNotNull(configLoaderProvider);
    this.contextProvider = Preconditions.checkNotNull(contextProvider);
  }

  @Override
  public ExitCode run(CommandEnv commandEnv)
      throws ValidationException, IOException, RepoException {
    ConfigFileArgs configFileArgs = commandEnv.parseConfigFileArgs(this,  /*useSourceRef*/false);
    Console console = commandEnv.getOptions().get(GeneralOptions.class).console();
    ConfigWithDependencies config = configLoaderProvider
        .newLoader(configFileArgs.getConfigPath(), configFileArgs.getSourceRef())
        .loadWithDependencies(console);
    if (commandEnv.getOptions().get(GeneralOptions.class).infoListOnly) {
      listMigrations(commandEnv, config.getConfig());
      return ExitCode.SUCCESS;
    }
    if (configFileArgs.hasWorkflowName()) {
      ImmutableMap<String, String> context = contextProvider.getContext(
          config, configFileArgs, configLoaderProvider, commandEnv.getOptions(), console);
      infoWithFailureHandling(
          commandEnv.getOptions(), config.getConfig(), configFileArgs.getWorkflowName(), context);
    } else {
      showAllMigrations(commandEnv, config.getConfig());
    }
    return ExitCode.SUCCESS;
  }

  private static void listMigrations(CommandEnv commandEnv, Config config) {
    Console console = commandEnv.getOptions().get(GeneralOptions.class).console();
    console.infoFmt("MIGRATIONS: %s",
        Joiner.on(',').join(ImmutableSortedSet.copyOf(config.getMigrations().keySet())));
  }

  private static void showAllMigrations(CommandEnv commandEnv, Config config) {
    TablePrinter table = new TablePrinter("Name", "Origin", "Destination", "Mode", "Description");
    for (Migration m :
        config.getMigrations().values().stream()
            .sorted(Comparator.comparing(Migration::getName))
            .collect(ImmutableList.toImmutableList())) {
      table.addRow(
          m.getName(),
          prettyOriginDestination(m.getOriginDescription()),
          prettyOriginDestination(m.getDestinationDescription()),
          m.getModeString(),
          Strings.nullToEmpty(m.getDescription()));
    }
    Console console = commandEnv.getOptions().get(GeneralOptions.class).console();
    for (String line : table.build()) {
      console.info(line);
    }
    console.info("To get information about the state of any migration run:\n\n"
        + "    copybara info " + config.getLocation() + " [workflow_name]"
        + "\n");
  }

  private static String prettyOriginDestination(ImmutableSetMultimap<String, String> desc) {
    return Iterables.getOnlyElement(desc.get("type"))
        + (desc.containsKey("url") ? " (" + Iterables.getOnlyElement(desc.get("url")) + ")" : "");
  }

  /** Retrieves the {@link Info} of the {@code migrationName} and prints it to the console. */
  private static void infoWithFailureHandling (
      Options options, Config config, String migrationName, ImmutableMap<String, String> context)
      throws ValidationException, RepoException {
    try {
      info(options, config, migrationName, context);
    } catch (ValidationException | RepoException e) {
      options.get(GeneralOptions.class).eventMonitors()
          .dispatchEvent(d -> d.onInfoFailed(new InfoFailedEvent(e.getMessage(), context)));
      throw e;
    }
  }

  /** Retrieves the {@link Info} of the {@code migrationName} and prints it to the console. */
  private static void info(
      Options options, Config config, String migrationName, ImmutableMap<String, String> context)
      throws ValidationException, RepoException {
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
      if (outputSize > 100) {
        console.infoFmt(
            "Use %s to limit the output of the command.", GeneralOptions.OUTPUT_LIMIT_FLAG);
      }
    }
    options.get(GeneralOptions.class).eventMonitors()
        .dispatchEvent(e -> e.onInfoFinished(new InfoFinishedEvent(info, context)));
  }

  /** Returns the {@link Info} of the {@code migrationName}. */
  private static Info<? extends Revision> getInfo(String migrationName, Config config)
      throws ValidationException, RepoException {
    return config.getMigration(migrationName).getInfo();
  }

  @Override
  public String name() {
    return "info";
  }
}
