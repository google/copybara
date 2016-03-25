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
}
