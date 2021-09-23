/*
 * Copyright (C) 2021 Google Inc.
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
import com.google.copybara.CommandEnv;
import com.google.copybara.CopybaraCmd;
import com.google.copybara.util.ExitCode;

/** CopybaraCmd that assists users in creating a config file. Note that this is still experimental
 * and a work in progress. In the near term, users will be able to generate a simple config by
 * running `copybara onboard` and following onscreen prompts. This functionality will be extended
 * over time. */
@Parameters(separators = "=", commandDescription = "Runs assisted onboarding tool.")
public class OnboardCmd implements CopybaraCmd {

  public OnboardCmd() {}

  @Override
  public ExitCode run(CommandEnv commandEnv) {
    try {
      CommandLineGuide.runForCommandLine(commandEnv);
      return ExitCode.SUCCESS;
    } catch (RuntimeException e) {
      return ExitCode.COMMAND_LINE_ERROR;
    }
  }

  @Override
  public String name() {
    return "onboard";
  }
}
