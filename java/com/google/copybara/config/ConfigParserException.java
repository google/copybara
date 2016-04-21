package com.google.copybara.config;

/**
 * An exception that happens when we fail to load the configuration due to a user error.
 * TODO(malcon): Merge this exception with the unchecked ConfigValidationException.
 */
public class ConfigParserException extends Exception {

  public ConfigParserException(String message) {
    super(message);
  }
}
