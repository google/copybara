// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

/**
 * An exception that indicates that a transformation, as defined, does not do anything to the
 * repository. This usually indicates a problem with the transformation configuration.
 */
public class VoidTransformationException extends ValidationException {
  public VoidTransformationException(String message) {
    super(message);
  }
}
