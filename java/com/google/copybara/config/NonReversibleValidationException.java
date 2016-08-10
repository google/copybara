// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

import com.google.copybara.transform.Transformation;

/**
 * Exception thrown when a {@link Transformation} is not reversible but the configuration asked for
 * the reverse.
 */
public class NonReversibleValidationException extends ConfigValidationException {

  public NonReversibleValidationException(String message) {
    super(message);
  }
}
