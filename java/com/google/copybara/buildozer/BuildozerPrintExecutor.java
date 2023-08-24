/*
 * Copyright (C) 2023 Google LLC
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

package com.google.copybara.buildozer;

import com.google.common.collect.ImmutableList;
import com.google.copybara.buildozer.BuildozerOptions.BuildozerCommand;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import java.nio.file.Path;

/** A class that can run a 'buildozer print' command. */
public class BuildozerPrintExecutor {
  private final BuildozerOptions options;
  private final Console console;

  private BuildozerPrintExecutor(BuildozerOptions options, Console console) {
    this.options = options;
    this.console = console;
  }

  public static BuildozerPrintExecutor create(BuildozerOptions options, Console console) {
    return new BuildozerPrintExecutor(options, console);
  }

  /**
   * Runs a Buildozer print command.
   *
   * @param attr The attribute from the target rule to print.
   * @param target The target to print from.
   * @return A string with the buildozer print output.
   * @throws ValidationException If there is an issue running buildozer print.
   */
  public String run(Path checkoutDir, String attr, String target) throws ValidationException {
    try {
      BuildozerCommand command = new BuildozerCommand(target, String.format("print %s", attr));
      return options.runCaptureOutput(console, checkoutDir, ImmutableList.of(command));
    } catch (TargetNotFoundException e) {
      throw new ValidationException("Buildozer could not find the specified target", e);
    }
  }
}
