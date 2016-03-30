// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Interface implemented by all source code transformations.
 */
public interface Transformation {
  interface Yaml {
    Transformation build();
  }
}
