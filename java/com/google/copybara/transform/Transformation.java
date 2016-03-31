// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.common.collect.ImmutableMap;
import com.google.copybara.Options;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Interface implemented by all source code transformations.
 */
public interface Transformation {
  interface Yaml {
    Transformation withOptions(Options options);
  }

  /**
   * Transforms the files inside {@code workdir}
   *
   * @throws IOException if an error occur during the access to the files
   */
  void transform(Path workdir) throws IOException;
}
