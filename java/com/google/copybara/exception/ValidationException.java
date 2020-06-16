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

package com.google.copybara.exception;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/**
 * Indicates that the configuration is wrong or some error attributable to the user happened. For
 * example wrong flag usage, errors in fields or errors that we discover during execution.
 */
public class ValidationException extends Exception {

  public ValidationException(String message) {
    super(message);
  }

  public ValidationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Check a condition and throw {@link ValidationException} if false
   *
   * @throws ValidationException if {@code condition} is false
   */
  @FormatMethod
  public static void checkCondition(boolean condition, @FormatString String format, Object... args)
      throws ValidationException {
    if (!condition) {
      // Don't try to format if there is no args. This allows strings like '%Fooooo'¡
      if (args.length == 0) {
        throw new ValidationException(format);
      }
      throw new ValidationException(String.format(format, args));
    }
  }

  /**
   * Check a condition and throw {@link ValidationException} if false
   *
   * @throws ValidationException if {@code condition} is false
   */
  public static void checkCondition(boolean condition, String msg) throws ValidationException {
    checkCondition(condition, "%s", msg);
  }

  /** Throw a {@link ValidationException} that can be retried */
  public static ValidationException retriableException(String message) {
    return new ValidationException(message);
  }
}
