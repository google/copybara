// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.common.base.Preconditions;
import com.google.copybara.EnvironmentException;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.ProgressPrefixConsole;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A transformation which runs some other transformation in reverse.
 */
public final class Reverse implements Transformation {

  private final Transformation original;

  Reverse(Transformation original) {
    this.original = Preconditions.checkNotNull(original);
  }

  @Override
  public void transform(Path workdir, Console console) throws IOException, ValidationException {
    original.reverse().transform(workdir, new ProgressPrefixConsole("Reverse ", console));
  }

  @Override
  public String describe() {
    return "reverse " + original.describe();
  }

  @Override
  public Transformation reverse() {
    return original;
  }

  @DocElement(yamlName = "!Reverse", description = "Run a particular transformation in reverse. Note that not all transformations support being reversed.", elementKind = Transformation.class)
  public final static class Yaml implements Transformation.Yaml {

    private Transformation.Yaml original;

    @DocField(description = "The transformation to be reversed.")
    public void setOriginal(Transformation.Yaml original) throws ConfigValidationException {
      original.checkReversible();
      this.original = original;
    }

    @Override
    public Transformation withOptions(Options options)
        throws ConfigValidationException, EnvironmentException {
      ConfigValidationException.checkNotMissing(original, "original");
      Transformation original = this.original.withOptions(options);
      return new Reverse(original);
    }

    @Override
    public void checkReversible() throws ConfigValidationException {
      // original is already validated in the setter
    }
  }
}
