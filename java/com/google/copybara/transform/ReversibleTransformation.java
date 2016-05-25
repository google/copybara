// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.copybara.EnvironmentException;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;

/**
 * Interface implemented by all source code transformations that can be reversed.
 *
 * TODO(matvore): Remove this in favor of adding a reversible() method on Transformation.Yaml? Maybe
 * not all transforms are known to be reversible based on type.
 */
public interface ReversibleTransformation extends Transformation {
  interface Yaml extends Transformation.Yaml {
    ReversibleTransformation withOptions(Options options)
        throws ConfigValidationException, EnvironmentException;
  }

  /**
   * Returns a transformation which runs this transformation in reverse.
   *
   * @throws ValidationException if this transformation cannot be reversed.
   */
  ReversibleTransformation reverse() throws ValidationException;
}
