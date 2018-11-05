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

import com.google.common.base.Preconditions;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigValidator;
import com.google.copybara.config.Migration;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.ExitCode;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Reads the last migrated revision in the origin and destination.
 */
public class InfoCmd implements CopybaraCmd {

  private final ConfigValidator configValidator;
  private final Consumer<Migration> migrationRanConsumer;
  private final ConfigLoaderProvider configLoaderProvider;

  InfoCmd(ConfigValidator configValidator, Consumer<Migration> migrationRanConsumer,
      ConfigLoaderProvider configLoaderProvider) {
    this.configValidator = Preconditions.checkNotNull(configValidator);
    this.migrationRanConsumer = Preconditions.checkNotNull(migrationRanConsumer);
    this.configLoaderProvider = Preconditions.checkNotNull(configLoaderProvider);
  }

  @Override
  public ExitCode run(CommandEnv commandEnv)
      throws ValidationException, IOException, RepoException {
    ConfigFileArgs configFileArgs = commandEnv.parseConfigFileArgs(this,  /*useSourceRef*/false);
    Copybara copybara = new Copybara(configValidator, migrationRanConsumer);

    Config config = configLoaderProvider
        .newLoader(configFileArgs.getConfigPath(), configFileArgs.getSourceRef())
        .load(commandEnv.getOptions().get(GeneralOptions.class).console());
    copybara.info(commandEnv.getOptions(), config, configFileArgs.getWorkflowName());
    return ExitCode.SUCCESS;
  }

  @Override
  public String name() {
    return "info";
  }
}
