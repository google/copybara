/*
 * Copyright (C) 2023 Google Inc.
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
package com.google.copybara.regenerate;

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.collect.ImmutableList;
import com.google.copybara.CommandEnv;
import com.google.copybara.ConfigFileArgs;
import com.google.copybara.ConfigLoaderProvider;
import com.google.copybara.CopybaraCmd;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Workflow;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.config.Migration;
import com.google.copybara.config.SkylarkParser.ConfigWithDependencies;
import com.google.copybara.exception.CommandLineException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.revision.Revision;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * RegenerateCmd is used to re-create the patch representing the destination-only changes after
 * manual edits are made to a destination change.
 */
public class RegenerateCmd implements CopybaraCmd {
  private final ConfigLoaderProvider configLoaderProvider;

  public RegenerateCmd(ConfigLoaderProvider configLoaderProvider) {
    this.configLoaderProvider = configLoaderProvider;
  }

  @Override
  public ExitCode run(CommandEnv commandEnv)
      throws ValidationException, IOException, RepoException {
    ConfigFileArgs configFileArgs = commandEnv.parseConfigFileArgs(this, /*useSourceRef*/ true);
    ImmutableList<String> sourceRefs = configFileArgs.getSourceRefs();
    if (sourceRefs.size() > 1) {
      throw new CommandLineException(
          String.format(
              "Workflow does not support multiple source_ref arguments yet: %s",
              ImmutableList.copyOf(sourceRefs)));
    }
    @Nullable String sourceRef = sourceRefs.size() == 1 ? sourceRefs.get(0) : null;

    GeneralOptions options = commandEnv.getOptions().get(GeneralOptions.class);
    WorkflowOptions workflowOptions = commandEnv.getOptions().get(WorkflowOptions.class);
    RegenerateOptions regenerateOptions = commandEnv.getOptions().get(RegenerateOptions.class);
    Console console = options.console();

    ConfigWithDependencies config =
        configLoaderProvider
            .newLoader(configFileArgs.getConfigPath(), configFileArgs.getSourceRef())
            .loadWithDependencies(console);

    String workflowName = configFileArgs.getWorkflowName();
    console.infoFmt("running regenerate for workflow %s", workflowName);

    Migration migration = config.getConfig().getMigration(workflowName);
    checkCondition(
        migration instanceof Workflow,
        "regenerate patch files is only supported for workflow migrations");

    Workflow<? extends Revision, ? extends Revision> workflow =
        (Workflow<? extends Revision, ? extends Revision>) migration;

    Regenerate<? extends Revision, ? extends Revision> regenerate =
        Regenerate.newRegenerate(
            workflow,
            commandEnv.getWorkdir(),
            options,
            workflowOptions,
            regenerateOptions,
            sourceRef);
    regenerate.regenerate();

    return ExitCode.SUCCESS;
  }

  @Override
  public String name() {
    return "regenerate";
  }
}
