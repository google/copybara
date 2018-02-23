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

package com.google.copybara;

/**
 * Indicates that the configuration is wrong or some error attributable to the user happened. For
 * example wrong flag usage, errors in fields or errors that we discover during execution.
 */
public class ValidationException extends Exception {

  public ValidationException(String message) {
    super(message);
  }

  public ValidationException(String message, Object... args) {
    super(String.format(message, args));
  }

  public ValidationException(Throwable cause, String message) {
    super(message, cause);
  }

  public ValidationException(Throwable cause, String message, Object... args) {
    this(cause, String.format(message, args));
  }

  public static void checkCondition(boolean condition, String format, Object... args)
      throws ValidationException {
    if (!condition) {
      throw new ValidationException(format, args);
    }
  }
}
