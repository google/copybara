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

  public ValidationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Checks that a field has been supplied. A field is considered supplied if it is not
   * {@code null}.
   *
   * @param value the value of the field
   * @param fieldName the name of the field to use in the exception message
   * @throws ValidationException if {@code value} is {@code null}
   */
  public static <T> T checkNotMissing(T value, String fieldName) throws ValidationException {
    if (value == null) {
      throw new ValidationException(String.format("missing required field '%s'", fieldName));
    }
    return value;
  }

  public static void checkCondition(boolean condition, String msg)
      throws ValidationException {
    if (!condition) {
      throw new ValidationException(msg);
    }
  }
}
