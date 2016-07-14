// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.util.console;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.util.console.Console.PromptPrinter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

/**
 * Reads the input from a {@link Console}.
 */
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
    Scanner scanner = new Scanner(input);
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
    // TODO(danielromero): This should really be IOException
    throw new RuntimeException("EOF while reading from the input");
  }
}
