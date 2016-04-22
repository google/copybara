// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.common.collect.ImmutableMap;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

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
}
