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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.copybara.CommandEnv;
import com.google.copybara.GeneralOptions;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.util.Set;
import java.util.function.Predicate;

/** Class to navigate users through text adventure to populate Copybara config file */
final class CommandLineGuide {

  private CommandLineGuide() {}

  public static void runForCommandLine(CommandEnv commandEnv) {
    Console console = commandEnv.getOptions().get(GeneralOptions.class).console();
    console.info("Welcome to Copybara's Assisted Onboarding Tool!\n");
    com.google.copybara.onboard.ConfigBuilder configBuilder =
        new com.google.copybara.onboard.ConfigBuilder(
            new com.google.copybara.onboard.GitToGitTemplate());
    Set<com.google.copybara.onboard.RequiredField> requiredFields =
        configBuilder.getRequiredFields();
    for (com.google.copybara.onboard.RequiredField field : requiredFields) {
      String response =
          tryAskConsole(
              console,
              String.format(
                  "What should be the value for field %s? The field description is:\n\"%s\"\n",
                  field.name(), field.helpText()),
              "INVALID",
              field.predicate(),
              "Invalid response");
      switch (field.location()) {
        case NAMED:
          configBuilder.setNamedStringParameter(field.name(), response);
          break;
        case KEYWORD:
          configBuilder.addStringKeywordParameter(field.name(), response);
          break;
      }
    }
    if (configBuilder.isValid()) {
      console.info(
          String.format(
              "Config generation successful! Please paste this config text into a new file named"
                  + " copy.bara.sky:\n\n"
                  + "%s",
              configBuilder.build()));
    }
  }

  private static String tryAskConsole(
      Console console,
      String msg,
      String defaultAnswer,
      Predicate<String> predicate,
      String errorMessage) {
    checkNotNull(console);
    checkNotNull(msg);
    checkNotNull(defaultAnswer);
    checkNotNull(predicate);
    checkNotNull(errorMessage);
    try {
      return console.ask(msg, defaultAnswer, predicate);
    } catch (IOException e) {
      console.error(String.format("%s\n%s", errorMessage, e.getMessage()));
      return null;
    }
  }
}
