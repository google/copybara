/*
 * Copyright (C) 2024 Google LLC
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

import com.google.common.collect.ImmutableList;
import com.google.copybara.CommandEnv;
import com.google.copybara.CopybaraCmd;
import com.google.copybara.GeneralOptions;
import com.google.copybara.ModuleSet;
import com.google.copybara.onboard.core.CannotProvideException;
import com.google.copybara.onboard.core.InputProvider;
import com.google.copybara.onboard.core.InputProviderResolver;
import com.google.copybara.onboard.core.InputProviderResolverImpl;
import com.google.copybara.util.console.Console;

/** An interface for onboarding specific commands. */
public interface OnboardingCmd extends CopybaraCmd {
  ModuleSet getModuleSet();

  ImmutableList<InputProvider> getInputProviders(CommandEnv commandEnv)
      throws CannotProvideException;

  default InputProviderResolver createInputProviderResolver(CommandEnv commandEnv)
      throws CannotProvideException {
    Console console = commandEnv.getOptions().get(GeneralOptions.class).console();

    return InputProviderResolverImpl.create(
        getInputProviders(commandEnv),
        new StarlarkConverter(getModuleSet(), console),
        commandEnv.getOptions().get(GeneratorOptions.class).askMode,
        console);
  }
}
