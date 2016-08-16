// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.ValidationException;
import com.google.copybara.util.console.Console;
import java.io.IOException;

/**
 * A transformation which delegates to some arbitrary transformation and reverses to some arbitrary
 * transformation.
 */
public final class ExplicitReversal implements Transformation {

  private final Transformation forward;
  private final Transformation reverse;

  public ExplicitReversal(Transformation forward, Transformation reverse) {
    this.forward = Preconditions.checkNotNull(forward);
    this.reverse = Preconditions.checkNotNull(reverse);
  }

  @Override
  public void transform(TransformWork work, Console console)
      throws IOException, ValidationException {
    forward.transform(work, console);
  }

  @Override
  public Transformation reverse() {
    return new ExplicitReversal(reverse, forward);
  }

  @Override
  public String describe() {
    return forward.describe();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("forward", forward)
        .add("reverse", reverse)
        .toString();
  }
}
