// Copyright 2016 Google Inc. All Rights Reserved.
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
}
