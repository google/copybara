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

import com.google.common.annotations.VisibleForTesting;
import com.google.copybara.AutoPatchfileConfiguration;
import com.google.copybara.CommandEnv;
import com.google.copybara.ConfigFileArgs;
import com.google.copybara.ConfigLoaderProvider;
import com.google.copybara.CopybaraCmd;
import com.google.copybara.Destination.PatchRegenerator;
import com.google.copybara.Destination.Writer;
import com.google.copybara.DestinationReader;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Workflow;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.WriterContext;
import com.google.copybara.config.Migration;
import com.google.copybara.config.SkylarkParser.ConfigWithDependencies;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.revision.Revision;
import com.google.copybara.util.AutoPatchUtil;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.Glob;
import com.google.copybara.util.InsideGitDirException;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
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
    ConfigFileArgs configFileArgs = commandEnv.parseConfigFileArgs(this, /*useSourceRef*/ false);

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

    return regenerate(
        workflow, commandEnv.getWorkdir(), options, workflowOptions, regenerateOptions);
  }

  @VisibleForTesting
  public ExitCode regenerate(
      Workflow<? extends Revision, ? extends Revision> workflow,
      Path workdir,
      GeneralOptions generalOptions,
      WorkflowOptions workflowOptions,
      RegenerateOptions regenerateOptions)
      throws ValidationException, RepoException, IOException {
    Console console = generalOptions.console();

    @Nullable
    AutoPatchfileConfiguration autoPatchfileConfiguration =
        workflow.getAutoPatchfileConfiguration();
    checkCondition(
        autoPatchfileConfiguration != null,
        "regenerate patch files requires the workflow %s to have an autopatch file configuration"
            + " set",
        workflow.getName());
    checkCondition(
        !autoPatchfileConfiguration.stripFileNamesAndLineNumbers(),
        "regenerate patch files requires the file names and line numbers to be present");

    Writer<? extends Revision> destinationWriter =
        workflow
            .getDestination()
            .newWriter(
                new WriterContext(
                    workflow.getName(),
                    workflowOptions.workflowIdentityUser,
                    generalOptions.dryRunMode,
                    workflow.getOrigin().resolve(null),
                    workflow.getDestinationFiles().roots()));
    PatchRegenerator patchRegenerator =
        destinationWriter
            .getPatchRegenerator(generalOptions.console())
            .orElseThrow(
                () ->
                    new ValidationException(
                        "this destination does not support regenerating patch files"));

    Path previousPath = workdir.resolve("previous");
    Path nextPath = workdir.resolve("next");
    Path autopatchPath = workdir.resolve("autopatches");
    Files.createDirectories(previousPath);
    Files.createDirectories(nextPath);
    Files.createDirectories(autopatchPath);

    Optional<String> optRegenBaseline = regenerateOptions.getRegenBaseline();
    if (optRegenBaseline.isEmpty()) {
      optRegenBaseline = patchRegenerator.inferRegenBaseline();
    }
    String regenBaseline =
        optRegenBaseline.orElseThrow(
            () ->
                new ValidationException(
                    "Regen baseline was neither supplied nor able to be inferred. Supply with"
                        + " --regen-baseline parameter"));

    Optional<String> optRegenTarget = regenerateOptions.getRegenTarget();
    if (optRegenTarget.isEmpty()) {
      optRegenTarget = patchRegenerator.inferRegenTarget();
    }
    String regenTarget =
        optRegenTarget.orElseThrow(
            () ->
                new ValidationException(
                    "Regen target was neither supplied nor able to be inferred. Supply with"
                        + " --regen-target parameter"));

    // copy the baseline to one directory
    DestinationReader previousDestinationReader =
        destinationWriter.getDestinationReader(console, regenBaseline, workdir);
    previousDestinationReader.copyDestinationFilesToDirectory(
        workflow.getDestinationFiles(), previousPath);

    // copy the target to another directory
    DestinationReader nextDestinationReader =
        destinationWriter.getDestinationReader(console, regenTarget, workdir);
    nextDestinationReader.copyDestinationFilesToDirectory(workflow.getDestinationFiles(), nextPath);

    // copy existing autopatch files to a third directory
    AutoPatchfileConfiguration autopatchConfig = workflow.getAutoPatchfileConfiguration();
    Glob autopatchGlob =
        AutoPatchUtil.getAutopatchGlob(
            autopatchConfig.directoryPrefix(), autopatchConfig.directory());
    previousDestinationReader.copyDestinationFilesToDirectory(autopatchGlob, autopatchPath);

    // reverse autopatch files on the target directory here to get a pristine import
    AutoPatchUtil.reversePatchFiles(previousPath, autopatchPath, autopatchConfig.suffix());

    // generate new autopatch files in the target directory
    try {
      AutoPatchUtil.generatePatchFiles(
          previousPath,
          nextPath,
          Path.of(autopatchConfig.directoryPrefix()),
          autopatchConfig.directory(),
          workflow.isVerbose(),
          workflow.getGeneralOptions().getEnvironment(),
          autopatchConfig.header(),
          autopatchConfig.suffix(),
          nextPath,
          autopatchConfig.stripFileNamesAndLineNumbers(),
          autopatchConfig.glob());
    } catch (InsideGitDirException e) {
      throw new ValidationException(
          String.format(
              "Could not automatically generate patch files because temporary directory %s is"
                  + " inside git repository %s. Error received is %s",
              e.getPath(), e.getGitDirPath(), e.getMessage()),
          e);
    }

    // push the new set of files
    patchRegenerator.updateChange(
        workflow.getName(), nextPath, workflow.getDestinationFiles(), regenTarget);

    return ExitCode.SUCCESS;
  }

  @Override
  public String name() {
    return "regenerate";
  }
}
