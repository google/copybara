// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.copybara.transform.ValidationException;

/**
 * An exception that indicates that a transformation, as defined, does not do anything to the
 * repository. This usually indicates a problem with the transformation configuration.
 */
public class VoidOperationException extends ValidationException {
  public VoidOperationException(String message) {
    super(message);
  }
}
