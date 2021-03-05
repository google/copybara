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

package com.google.copybara.util.console;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.util.console.Console.PromptPrinter;
import java.io.InputStream;
import java.util.Scanner;

/** Reads the input from a {@link Console}. */
class ConsolePrompt {

  private static final ImmutableSet<String> YES = ImmutableSet.of("y", "yes");
  private static final ImmutableSet<String> NO = ImmutableSet.of("n", "no");

  private final InputStream input;
  private final PromptPrinter promptPrinter;

  ConsolePrompt(InputStream input, PromptPrinter promptPrinter) {
    this.input = Preconditions.checkNotNull(input);
    this.promptPrinter = Preconditions.checkNotNull(promptPrinter);
  }

  boolean promptConfirmation(String message) {
    Scanner scanner = new Scanner(input, UTF_8.name());
    promptPrinter.print(message);
    while (scanner.hasNextLine()) {
      String answer = scanner.nextLine().trim().toLowerCase();
      if (YES.contains(answer)) {
        return true;
      }
      if (NO.contains(answer)) {
        return false;
      }
      promptPrinter.print(message);
    }
    // EOF while readling from the input (user cancelled)
    return false;
  }
}
