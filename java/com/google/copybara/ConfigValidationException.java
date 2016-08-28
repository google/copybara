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
 * Indicates that the data in the configuration is not valid or some error occurred during
 * configuration validation or parsing. For instance, this exception is thrown if a field is missing
 * or is not formatted correctly.
 */
public class ConfigValidationException extends ValidationException {

  public ConfigValidationException(String message) {
    super(message);
  }

  public ConfigValidationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Checks that a field has been supplied. A field is considered supplied if it is not
   * {@code null}.
   *
   * @param value the value of the field
   * @param fieldName the name of the field to use in the exception message
   * @throws ConfigValidationException if {@code value} is {@code null}
   */
  public static <T> T checkNotMissing(T value, String fieldName) throws ConfigValidationException {
    if (value == null) {
      throw new ConfigValidationException(String.format("missing required field '%s'", fieldName));
    }
    return value;
  }

  public static void checkCondition(boolean condition, String msg)
      throws ConfigValidationException {
    if (!condition) {
      throw new ConfigValidationException(msg);
    }
  }
}
