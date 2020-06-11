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

import com.google.errorprone.annotations.FormatMethod;
import java.io.IOException;
import java.util.function.Predicate;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/** Write user messages to the console */
public interface Console extends AutoCloseable {

  /**
   * Print the Copybara welcome message.
   */
  void startupMessage(String version);

  /**
   * Print an error in the console
   */
  void error(String message);

  /**
   * Print a format string as error on the console
   */
  @FormatMethod
  default void errorFmt(String format, Object... args) {
    error(String.format(format, args));
  }

  /**
   * Print a warning in the console
   */
  void warn(String message);

  /**
   * Print a format string as warn on the console
   */
  @FormatMethod
  default void warnFmt(String format, Object... args) {
    warn(String.format(format, args));
  }


  /**
   * Returns true if verbose
   */
  boolean isVerbose();

  /**
   * Print an informational message in the console, if verbose logging is enabled
   */
  default void verbose(String message) {
    if (isVerbose()) {
      info(message);
    }
  }

  /**
   * Print a format string as info on the console, if verbose logging is enabled
   */
  @FormatMethod
  default void verboseFmt(String format, Object... args) {
    verbose(String.format(format, args));
  }

  /**
   * Print an informational message in the console
   *
   * <p> Warning: Do not abuse the usage of this method. We don't
   * want to spam our users. When in doubt, use verbose.
   */
  void info(String message);

  /**
   * Print a format string as info on the console
   */
  @FormatMethod
  default void infoFmt(String format, Object... args) {
    info(String.format(format, args));
  }

  /**
   * Print a progress message in the console
   */
  void progress(String progress);

  /**
   * Print a format string as progress on the console
   */
  @FormatMethod
  default void progressFmt(String format, Object... args) {
    progress(String.format(format, args));
  }

  /**
   * Returns true if this Console's input registers Y/y after showing the prompt message.
   */
  @CheckReturnValue
  boolean promptConfirmation(String message);

  /**
   * Like promptConfirmation, but takes a format String as argument.
   */
  @CheckReturnValue
  @FormatMethod
  default boolean promptConfirmationFmt(String format, Object... args) throws IOException {
    return promptConfirmation(String.format(format, args));
  }

  default String ask(String msg, @Nullable String defaultAnswer, Predicate<String> validator)
      throws IOException {
    throw new IllegalStateException("Interactive prompt not allowed in " + this.getClass());
  }

  /**
   * Given a message and a console that support colors, return a string that prints the message in
   * the {@code ansiColor}.
   *
   * <p>Note that not all consoles support colors. so messages should be readable without colors.
   */
  String colorize(AnsiColor ansiColor, String message);

  interface PromptPrinter {
    /**
     * Prints a prompt message.
     */
    void print(String message);
  }

  /** Close this console, freeing resources. */
  @Override
  default void close() {}
}
