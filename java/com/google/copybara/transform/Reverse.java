// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.common.base.Preconditions;
import com.google.copybara.EnvironmentException;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A transformation which runs some other transformation in reverse.
 */
public final class Reverse implements ReversibleTransformation {

  private final ReversibleTransformation original;

  private Reverse(ReversibleTransformation original) {
    this.original = Preconditions.checkNotNull(original);
  }

  @Override
  public void transform(Path workdir) throws IOException, ValidationException {
    original.reverse().transform(workdir);
  }

  @Override
  public String describe() {
    return "Reverse " + original.describe();
  }

  @Override
  public ReversibleTransformation reverse() {
    return original;
  }

  @DocElement(yamlName = "!Reverse", description = "Run a particular transformation in reverse. Note that not all transformations support being reversed.", elementKind = ReversibleTransformation.class)
  public final static class Yaml implements ReversibleTransformation.Yaml {

    private ReversibleTransformation.Yaml original;

    @DocField(description = "The transformation to be reversed.")
    public void setOriginal(Transformation.Yaml original) throws ConfigValidationException {
      // Throw an exception rather than specify the required type in the signature because the
      // snakeyaml-generated error is not clear.
      if (!(original instanceof ReversibleTransformation.Yaml)) {
        throw new ConfigValidationException(
            "'original' transformation is not automatically reversible.");
      }
      this.original = (ReversibleTransformation.Yaml) original;
    }

    @Override
    public ReversibleTransformation withOptions(Options options)
        throws ConfigValidationException, EnvironmentException {
      ConfigValidationException.checkNotMissing(original, "original");
      return new Reverse(original.withOptions(options));
    }
  }
}
