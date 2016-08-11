// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

import com.google.copybara.transform.Transformation;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.EvalException;

/**
 * Exception thrown when a {@link Transformation} is not reversible but the configuration asked for
 * the reverse.
 */
public class NonReversibleValidationException extends EvalException {

  public NonReversibleValidationException(Location location, String message) {
    super(location, message);
  }
}
