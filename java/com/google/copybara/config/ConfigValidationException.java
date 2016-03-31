// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

/**
 * Indicates that a configuration is not valid or some error occurred during configuration
 * validation or parsing.
 */
public class ConfigValidationException extends RuntimeException {
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
  public static <T> T checkNotMissing(T value, String fieldName) {
    if (value == null) {
      throw new ConfigValidationException(String.format("missing required field '%s'", fieldName));
    }
    return value;
  }
}
