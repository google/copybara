// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface implemented by all source code transformations.
 */
public interface Transformation {
  interface Yaml {
    Transformation withOptions(Options options) throws ConfigValidationException;
  }

  /**
   * Transforms the files inside {@code workdir}
   * TODO(malcon,matvore): Think if we want to remove workdir from this interface
   *
   * @throws IOException if an error occur during the access to the files
   */
  void transform(Path workdir) throws IOException;

  /**
   * Return a high level description of what the transform is doing. Note that this should not be
   * {@link #toString()} method but something more user friendly.
   */
  String describe();
}
