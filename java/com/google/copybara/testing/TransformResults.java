// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.testing;

import com.google.copybara.RepoException;
import com.google.copybara.TransformResult;
import com.google.copybara.ConfigValidationException;
import com.google.copybara.util.Glob;
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
      Path path, DummyReference originRef, Glob destinationFiles)
      throws ConfigValidationException, RepoException {
    return new TransformResult(
        path, originRef, originRef.getOriginalAuthor().resolve(), "test summary\n",
        destinationFiles);
  }

  /**
   * Creates an instance with reasonable defaults for testing and no excluded destination paths.
   */
  public static TransformResult of(Path path, DummyReference originRef)
      throws ConfigValidationException, RepoException {
    return of(path, originRef, Glob.ALL_FILES);
  }
}
