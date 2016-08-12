// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;

/**
 * Contains information related to an on-going process of repository transformation.
 */
public final class TransformWork {

  private final Path checkoutDir;
  private final String summary;

  public TransformWork(Path checkoutDir, String summary) {
    this.checkoutDir = Preconditions.checkNotNull(checkoutDir);
    this.summary = Preconditions.checkNotNull(summary);
  }

  /**
   * The path containing the repository state to transform. Transformation should be done in-place.
   */
  public Path getCheckoutDir() {
    return checkoutDir;
  }

  /**
   * A description of the migrated changes to include in the destination's change description. The
   * destination may add more boilerplate text or metadata.
   */
  public String getSummary() {
    return summary;
  }
}
