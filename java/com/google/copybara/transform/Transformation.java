// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.copybara.EnvironmentException;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.util.console.Console;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface implemented by all source code transformations.
 */
public interface Transformation {
  interface Yaml {
    Transformation withOptions(Options options)
        throws ConfigValidationException, EnvironmentException;
  }

  /**
   * Transforms the files inside {@code workdir}
   *
   * @throws IOException if an error occur during the access to the files
   * @throws ValidationException if an error attributable to the user happened
   */
  void transform(Path workdir, Console console) throws IOException, ValidationException;

  /**
   * Return a high level description of what the transform is doing. Note that this should not be
   * {@link #toString()} method but something more user friendly.
   */
  String describe();
}
