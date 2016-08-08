package com.google.copybara.config.skylark;

import com.google.copybara.config.ConfigValidationException;

/**
 * An exception thrown when a dependency expressed as a label in a content cannot be resolved.
 */
public class CannotResolveLabel extends ConfigValidationException {

  public CannotResolveLabel(String message) {
    super(message);
  }
}
