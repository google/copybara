/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.onboard;

import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.CommandEnv;
import com.google.copybara.GeneralOptions;
import com.google.copybara.ModuleSet;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitOptions;
import com.google.copybara.onboard.core.CannotProvideException;
import com.google.copybara.onboard.core.ConstantProvider;
import com.google.copybara.onboard.core.InputProvider;
import com.google.copybara.onboard.core.InputProviderResolver;
import com.google.copybara.onboard.core.MapBasedInputProvider;
import com.google.copybara.onboard.core.template.ConfigGenerator;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.console.Console;
import java.io.IOException;

/**
 * A command that generates a config file based on user and inferred inputs.
 *
 * <p>TODO(malcon, joshgoldman): Rename to 'GenerateCmd' once we remove old version
 */
@Parameters(
    separators = "=",
    commandDescription = "Generates a config file by asking/inferring field information")
public class GeneratorCmd implements OnboardingCmd {

  public static final int PERCENTAGE_SIMILAR = 30;

  private final ModuleSet moduleSet;
  private final GeneratorCmdImpl generatorCmd = new GeneratorCmdImpl();

  public GeneratorCmd(ModuleSet moduleSet) {
    this.moduleSet = moduleSet;
  }

  @Override
  public ExitCode run(CommandEnv commandEnv)
      throws ValidationException, IOException, RepoException {
    Console console = commandEnv.getOptions().get(GeneralOptions.class).console();

    ImmutableList<ConfigGenerator> generators = getGeneratorCmdImpl().generators();
    Inputs.maybeSetTemplates(generators);
    for (ConfigGenerator generator : generators) {
      // force the generator to initialize its Inputs so that they are declared in the registry
      var unused = generator.consumes();
    }

    try {
      InputProviderResolver resolver = createInputProviderResolver(commandEnv);

      return getGeneratorCmdImpl().executeCommand(commandEnv, resolver);
    } catch (CannotProvideException e) {
      console.error("Cannot resolve input field: " + e.getMessage());
      return ExitCode.COMMAND_LINE_ERROR;
    }
  }

  public GeneratorCmdImpl getGeneratorCmdImpl() {
    return generatorCmd;
  }

  @Override
  public ModuleSet getModuleSet() {
    return moduleSet;
  }

  @Override
  public ImmutableList<InputProvider> getInputProviders(CommandEnv commandEnv)
      throws CannotProvideException {
    GeneratorOptions genOpts = commandEnv.getOptions().get(GeneratorOptions.class);
    Console console = commandEnv.getOptions().get(GeneralOptions.class).console();

    return ImmutableList.of(
        new ConstantProvider<>(
            Inputs.GENERATOR_FOLDER, commandEnv.getOptions().get(GeneralOptions.class).getCwd()),
        new ConfigHeuristicsInputProvider(
            commandEnv.getOptions().get(GitOptions.class),
            commandEnv.getOptions().get(GeneralOptions.class),
            commandEnv.getOptions().get(GeneratorOptions.class),
            ImmutableSet.of(),
            PERCENTAGE_SIMILAR,
            console,
            (db) -> db.resolve(Inputs.GENERATOR_FOLDER)),
        new MapBasedInputProvider(genOpts.inputs, InputProvider.COMMAND_LINE_PRIORITY));
  }

  @Override
  public String name() {
    // TODO(malcon, joshgoldman): Rename to 'generate' once we remove old version
    return "generator";
  }
}
