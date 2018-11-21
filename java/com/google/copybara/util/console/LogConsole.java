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

import com.google.common.base.Preconditions;
import java.io.InputStream;
import java.io.PrintStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import javax.annotation.Nullable;

/**
 * A simple console logger that prefixes the output with the time
 */
public class LogConsole implements Console {

  private static final DateTimeFormatter DATE_PREFIX_FMT =
      DateTimeFormatter.ofPattern("MMdd HH:mm:ss.SSS");
  
  @Nullable
  private final InputStream input;
  private final PrintStream output;
  private final boolean verbose;

  /**
   * Creates a new instance of {@link LogConsole} with write capabilities, only.
   */
  public static LogConsole writeOnlyConsole(PrintStream output, boolean verbose) {
    return new LogConsole(/*input*/ null, Preconditions.checkNotNull(output), verbose);
  }

  /**
   * Creates a new instance of {@link LogConsole} with read and write capabilities.
   */
  public static LogConsole readWriteConsole(
      InputStream input, PrintStream output, boolean verbose) {
    return new LogConsole(
        Preconditions.checkNotNull(input), Preconditions.checkNotNull(output), verbose);
  }

  private LogConsole(InputStream input, PrintStream output, boolean verbose) {
    this.input = input;
    this.output = Preconditions.checkNotNull(output);
    this.verbose = verbose;
  }

  @Override
  public void startupMessage(String version) {
    output.println("Copybara source mover (Version: " + version + ")");
  }

  @Override
  public boolean isVerbose() {
    return verbose;
  }

  @Override
  public void error(String message) {
    printMessage("ERROR", message);
  }

  @Override
  public void warn(String message) {
    printMessage("WARN", message);
  }

  @Override
  public void info(String message) {
    printMessage("INFO", message);
  }

  @Override
  public void progress(final String task) {
    printMessage("TASK", task);
  }

  @Override
  public boolean promptConfirmation(String message) {
    Preconditions.checkState(input != null,
        "LogConsole cannot read user input if system console is not present.");
    return new ConsolePrompt(input,
        msg -> output.printf("%s WARN: %s [y/n] ", nowToString(), msg))
        .promptConfirmation(message);
  }

  @Override
  public String colorize(AnsiColor ansiColor, String message) {
    return message;
  }

  private void printMessage(final String messageKind, String message) {
    output.printf("%s %s: %s%n", nowToString(), messageKind, message);
  }

  private String nowToString() {
    return ZonedDateTime.now(ZoneId.systemDefault()).format(DATE_PREFIX_FMT);
  }
}
