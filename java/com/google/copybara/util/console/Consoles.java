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

import com.google.common.base.Splitter;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Utility methods for working with {@link Console}s.
 */
public class Consoles {

  private Consoles() {}

  /**
   * Logs text as separate lines using {@link Console#info(String)}. If {@code text} is an empty
   * string, does nothing.
   */
  public static void logLines(Console console, String prefix, String text) {
    consoleLogLines(prefix, text, console::info);
  }

  /**
   * Logs text as separate lines using {@link Console#error(String)} (String)}. If {@code text} is
   * an empty string, does nothing.
   */
  public static void errorLogLines(Console console, String prefix, String text) {
    consoleLogLines(prefix, text, console::error);
  }

  /**
   * Logs text as separate lines using {@link Console#verbose(String)} (String)} if verbose
   * is enabled.
   */
  public static void verboseLogLines(Console console, String prefix, String text) {
    consoleLogLines(prefix, text, console::verbose);
  }

  private static void consoleLogLines(String prefix, String text,
      Consumer<String> logLevel) {
    Iterator<String> lines = Splitter.on('\n').split(text).iterator();
    while (lines.hasNext()) {
      String line = lines.next();
      if (line.isEmpty() && !lines.hasNext()) {
        break;
      }
      logLevel.accept(prefix + line);
    }
  }
}
