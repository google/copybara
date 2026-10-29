/*
 * Copyright (C) 2022 Google LLC
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

package com.google.copybara.starlark;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.exception.CannotResolveLabel;
import com.google.copybara.util.console.Console;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.syntax.Location;
import net.starlark.java.syntax.SyntaxError;

/** Utilities for dealing with Starlark language. */
public final class StarlarkUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Checks a condition or throw {@link EvalException}. */
  @FormatMethod
  public static void check(boolean condition, @FormatString String format, Object... args)
      throws EvalException {
    if (!condition) {
      throw Starlark.errorf(format, args);
    }
  }

  /**
   * Prints the 3 lines around each error in {@code configFile} to {@code console} as an error,
   * highlighting the line where {@code throwable} occurred if matching Starlark locations are
   * found.
   */
  public static void printConfigOnError(
      Console console, ConfigFile configFile, Throwable throwable) {
    ImmutableList<SyntaxError> errors =
        extractSyntaxErrors(throwable).stream()
            .filter(e -> configFile.path().equals(e.location().file()))
            .collect(toImmutableList());
    if (errors.isEmpty()) {
      return;
    }
    String content;
    try {
      content = configFile.readContent();
    } catch (IOException | CannotResolveLabel e) {
      logger.atWarning().withCause(e).log(
          "Failed to read config content for '%s' while printing error context", configFile.path());
      return;
    }
    Set<Integer> printedLines = new HashSet<>();
    for (SyntaxError error : errors) {
      Location location = error.location();
      if (printedLines.add(location.line())) {
        console.printSyntaxError(content, error.message(), location.line(), location.column(), 3);
      }
    }
  }

  private static ImmutableList<SyntaxError> extractSyntaxErrors(Throwable throwable) {
    for (Throwable cause : Throwables.getCausalChain(throwable)) {
      if (cause instanceof EvalException evalException) {
        Location location = evalException.getInnermostLocation();
        if (location != null) {
          return ImmutableList.of(
              new SyntaxError(location, Strings.nullToEmpty(evalException.getMessage())));
        }
      } else if (cause instanceof SyntaxError.Exception syntaxException) {
        return syntaxException.errors();
      }
    }
    return ImmutableList.of();
  }

  private StarlarkUtil() {}
}
