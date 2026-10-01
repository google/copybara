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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.Migration;
import com.google.copybara.config.SkylarkParser;
import com.google.copybara.config.SkylarkParser.ConfigWithDependencies;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.revision.Revision;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.StarlarkMode;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

/**
 * Loads the configuration from a given config file.
 */
public class ConfigLoader {

  private final SkylarkParser skylarkParser;
  protected final ConfigFile configFile;
  private final ModuleSet moduleSet;

  public ConfigLoader(ModuleSet moduleSet, ConfigFile configFile, StarlarkMode validateStarlark) {
    this(moduleSet, configFile, new SkylarkParser(moduleSet.getStaticModules(), validateStarlark));
  }

  @VisibleForTesting
  public ConfigLoader(ModuleSet moduleSet, ConfigFile configFile, SkylarkParser skylarkParser) {
    this.moduleSet = Preconditions.checkNotNull(moduleSet);
    this.configFile = Preconditions.checkNotNull(configFile);
    this.skylarkParser = Preconditions.checkNotNull(skylarkParser);
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
      ConfigWithDependencies result =
          skylarkParser.getConfigWithTransitiveImports(configFile, moduleSet, console);
      validateConsistencyFileWorkflows(result.getConfig());
      return result;
    }
  }

  protected Config loadForConfigFile(Console console, ConfigFile configFile)
      throws IOException, ValidationException {
    console.progressFmt("Loading config %s", configFile.getIdentifier());

    try (ProfilerTask ignore = moduleSet.getOptions().get(GeneralOptions.class).profiler()
        .start("loading_config")) {
      Config config = skylarkParser.loadConfig(configFile, moduleSet, console);
      validateConsistencyFileWorkflows(config);
      return config;
    }
  }

  private static void validateConsistencyFileWorkflows(Config config) throws ValidationException {
    ImmutableSortedMap<String, Migration> migrations =
        ImmutableSortedMap.copyOf(config.getMigrations());
    ImmutableListMultimap.Builder<String, Workflow<?, ?>> workflowsByConsistencyPath =
        ImmutableListMultimap.builder();
    for (Migration migration : migrations.values()) {
      if (!(migration instanceof Workflow<?, ?> workflow)) {
        continue;
      }
      ConsistencyFileConfiguration consistencyConfig = workflow.getConsistencyFileConfig();
      if (consistencyConfig == null) {
        continue;
      }
      workflowsByConsistencyPath.put(consistencyConfig.path(), workflow);
      if (consistencyConfig.workflow() != null) {
        Migration authoritativeMigration = migrations.get(consistencyConfig.workflow());
        ValidationException.checkCondition(
            authoritativeMigration instanceof Workflow<?, ?> authoritativeWorkflow
                && consistencyConfig.path().equals(authoritativeWorkflow.getConsistencyFilePath()),
            "consistency_file workflow_name '%s' in workflow '%s' must refer to a workflow defined"
                + " in the same config that uses consistency file '%s'",
            consistencyConfig.workflow(),
            workflow.getName(),
            consistencyConfig.path());
      }
    }
    for (Collection<Workflow<?, ?>> group : workflowsByConsistencyPath.build().asMap().values()) {
      if (group.stream().allMatch(w -> w.getConsistencyFileConfig().workflow() == null)) {
        continue;
      }
      Workflow<?, ?> firstWorkflow = group.iterator().next();
      String expectedWorkflowName =
          firstWorkflow.getConsistencyFileConfig().workflowOrDefault(firstWorkflow.getName());
      for (Workflow<?, ?> workflow : group) {
        String actualWorkflowName =
            workflow.getConsistencyFileConfig().workflowOrDefault(workflow.getName());
        ValidationException.checkCondition(
            Objects.equals(actualWorkflowName, expectedWorkflowName),
            "Using different workflows with a single consistency file requires using a single,"
                + " authoritative workflow name (using the 'workflow_name' argument in the"
                + " 'consistency_file' parameter). The two conflicting workflows are '%s' and"
                + " '%s'.",
            firstWorkflow.getName(),
            workflow.getName());
      }
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
