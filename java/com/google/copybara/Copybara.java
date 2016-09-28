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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.SkylarkParser;
import com.google.copybara.folder.FolderDestinationOptions;
import com.google.copybara.folder.FolderModule;
import com.google.copybara.folder.FolderOriginOptions;
import com.google.copybara.git.GerritOptions;
import com.google.copybara.git.GitDestinationOptions;
import com.google.copybara.git.GitMirrorOptions;
import com.google.copybara.git.GitModule;
import com.google.copybara.git.GitOptions;
import com.google.copybara.transform.metadata.MetadataModule;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Copybara tool main class.
 *
 * <p>Executes Copybara workflows independently from the environment that they are invoked from
 * (command-line, service).
 */
public class Copybara {

  protected static final ImmutableSet<Class<?>> BASIC_MODULES = ImmutableSet.of(
      FolderModule.class,
      GitModule.class,
      MetadataModule.class);

  private final SkylarkParser skylarkParser;
  private final String homeDir;

  public Copybara(SkylarkParser skylarkParser, String homeDir) {
    this.skylarkParser = Preconditions.checkNotNull(skylarkParser);
    this.homeDir = Preconditions.checkNotNull(homeDir);
  }

  protected List<Option> getAllOptions() {
    return ImmutableList.of(
        new FolderDestinationOptions(),
        new FolderOriginOptions(),
        new GitOptions(homeDir),
        new GitDestinationOptions(),
        new GitMirrorOptions(),
        new GerritOptions(),
        new WorkflowOptions());
  }

  public void run(Options options, ConfigFile configContents, String workflowName,
      Path baseWorkdir, @Nullable String sourceRef)
      throws RepoException, ValidationException, IOException {
    Config config = getConfig(options, configContents, workflowName);
    config.getActiveMigration().run(baseWorkdir, sourceRef);
  }

  public Migration.Info info(Options options, ConfigFile configContents, String workflowName)
      throws IOException, ValidationException, RepoException {
    Config config = getConfig(options, configContents, workflowName);
    return config.getActiveMigration().getInfo();
  }

  public void validate(Options options, ConfigFile configContent, String workflowName)
      throws RepoException, ValidationException, IOException {
    options.get(WorkflowOptions.class).setWorkflowName(workflowName);
    Config config = skylarkParser.loadConfig(configContent, options);
    validateConfig(options, config);
  }

  private Config getConfig(Options options, ConfigFile configContents, String workflowName)
      throws IOException, ValidationException {
    options.get(WorkflowOptions.class).setWorkflowName(workflowName);
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    Config config = skylarkParser.loadConfig(configContents, options);
    Console console = generalOptions.console();
    console.progress("Validating configuration");
    validateConfig(options, config);
    return config;
  }

  private void validateConfig(Options options, Config config) throws ValidationException {
    Console console = options.get(GeneralOptions.class).console();
    List<String> validationMessages = validateConfig(config);
    if (!validationMessages.isEmpty()) {
      console.error("Configuration is invalid:");
      for (String validationMessage : validationMessages) {
        console.error(validationMessage);
      }
      throw new ValidationException(
          "Error validating configuration: Configuration is invalid.");
    }
  }

  /**
   * Returns a list of validation error messages, if any, for the given configuration.
   */
  protected List<String> validateConfig(Config config) {
    // TODO(copybara-team): Move here SkylarkParser validations once Config has all the workflows.
    // checkCondition(!workflows.isEmpty(), ...)
    return ImmutableList.of();
  }
}
