// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.testing;

import com.google.copybara.RepoException;
import com.google.copybara.TransformResult;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.util.PathMatcherBuilder;

import java.nio.file.FileSystems;
import java.nio.file.Path;

/**
 * Utility methods related to {@link TransformResult}.
 */
public class TransformResults {
  private TransformResults() {}

  /**
   * Creates an instance with reasonable defaults for testing.
   */
  public static TransformResult of(
      Path path, DummyReference originRef, Iterable<String> excludedDestinationPaths)
      throws ConfigValidationException, RepoException {
    return new TransformResult(
        path, originRef, originRef.getAuthor(), "test summary\n",
        PathMatcherBuilder.create(FileSystems.getDefault(), excludedDestinationPaths));
  }
}
