// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.testing;

import com.google.copybara.Options;
import com.google.copybara.transform.Transformation;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A transformation for testing which doesn't do anything to the workdir and simply records whether
 * {@link #transform(Path)} is invoked.
 */
public class RecordsInvocationTransformation implements Transformation, Transformation.Yaml {

  public int timesInvoked;

  @Override
  public void transform(Path workdir) throws IOException {
    timesInvoked++;
  }

  @Override
  public Transformation withOptions(Options options) {
    return this;
  }
}
