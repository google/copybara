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
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.CommandEnv;
import com.google.copybara.CopybaraCmd;
import com.google.copybara.GeneralOptions;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.onboard.core.CannotProvideException;
import com.google.copybara.onboard.core.ConstantProvider;
import com.google.copybara.onboard.core.InputProvider;
import com.google.copybara.onboard.core.InputProviderResolver;
import com.google.copybara.onboard.core.InputProviderResolverImpl;
import com.google.copybara.onboard.core.MapBasedInputProvider;
import com.google.copybara.onboard.core.template.ConfigGenerator;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * A command that generates a config file based on user and inferred inputs.
 *
 * TODO(malcon, joshgoldman): Rename to 'GenerateCmd' once we remove old version
 */
@Parameters(separators = "=",
    commandDescription = "Generates a config file by asking/inferring field information")
public class GeneratorCmd implements CopybaraCmd {

  @Override
  public ExitCode run(CommandEnv commandEnv)
      throws ValidationException, IOException, RepoException {
    GeneratorOptions genOpts = commandEnv.getOptions().get(GeneratorOptions.class);
    Console console = commandEnv.getOptions().get(GeneralOptions.class).console();


    try {
      InputProviderResolver resolver =
          InputProviderResolverImpl.create(
              inputProviders(genOpts, commandEnv, console), generators(), genOpts.askMode, console);
      Optional<Path> path = resolver.resolve(Inputs.GENERATOR_FOLDER);
      if (path.isEmpty()) {
        console.error("Cannot infer a path to place the generated config");
        return ExitCode.COMMAND_LINE_ERROR;
      }
      Optional<ConfigGenerator> template = resolver.resolve(Inputs.TEMPLATE);
      if (template.isEmpty()) {
        console.error("Cannot infer a template for generating a config. Use --template flag.");
        return ExitCode.COMMAND_LINE_ERROR;
      }
      String config = template.get().generate(resolver);
      Path configDestination = path.get().resolve("copy.bara.sky");
      Files.write(configDestination, config.getBytes(StandardCharsets.UTF_8));
      console.infoFmt("%s created", configDestination);

    } catch (InterruptedException e) {
      console.error("Interrupted: " + e.getMessage());
      return ExitCode.INTERRUPTED;
    } catch (CannotProvideException e) {
      console.error("Cannot resolve input field: " + e.getMessage());
      return ExitCode.COMMAND_LINE_ERROR;
    }
    return ExitCode.SUCCESS;
  }

  protected ImmutableList<InputProvider> inputProviders(
      GeneratorOptions genOpts, CommandEnv commandEnv, Console console)
      throws CannotProvideException {

    ImmutableMap<String, String> inputs = genOpts.inputs;
    // Special case --template to make it easier for users.
    if (genOpts.template != null) {
      inputs = ImmutableMap.<String, String>builder().putAll(inputs).put(
          Inputs.TEMPLATE.name(),
          genOpts.template
      ).build();
    }
    return ImmutableList.of(
        new ConstantProvider<>(Inputs.GENERATOR_FOLDER,
            Paths.get(StandardSystemProperty.USER_DIR.value())),
        new MapBasedInputProvider(inputs, InputProvider.COMMAND_LINE_PRIORITY)
    );
  }

  protected ImmutableList<ConfigGenerator> generators() {
    return ImmutableList.of(
        new GitToGitGenerator()
    );
  }

  @Override
  public String name() {
    // TODO(malcon, joshgoldman): Rename to 'generate' once we remove old version
    return "generator";
  }
}
