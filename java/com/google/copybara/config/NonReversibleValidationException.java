// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

import com.google.copybara.doc.annotations.DocElementUtil;
import com.google.copybara.transform.Transformation;

/**
 * Exception thrown when a {@link Transformation} is not reversible but the configuration asked for
 * the reverse.
 */
public class NonReversibleValidationException extends ConfigValidationException {

  public NonReversibleValidationException(Transformation.Yaml element, String message) {
    super(String.format("'%s' transformation is not automatically reversible: %s",
        DocElementUtil.getYamlName(element), message));
  }

  public NonReversibleValidationException(Transformation.Yaml element) {
    super(String.format("'%s' transformation is not automatically reversible",
        DocElementUtil.getYamlName(element)));
  }
}
