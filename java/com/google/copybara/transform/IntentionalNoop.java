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
 * A transformation which does nothing. This is a no-op which is not considered an error (unlike a
 * core.move which has no matching files for the 'before' path), so it does not generate a warning.
 */
public enum IntentionalNoop implements Transformation {
  INSTANCE;

  @Override
  public void transform(TransformWork work, Console console) {}

  @Override
  public Transformation reverse() {
    return this;
  }

  @Override
  public String describe() {
    return "no-op";
  }
}
