// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

/**
 * Exceptions that happen for some environmental reason.
 */
public class EnvironmentException extends Exception {

  public EnvironmentException(String message) {
    super(message);
  }

  public EnvironmentException(String message, Throwable cause) {
    super(message, cause);
  }
}
