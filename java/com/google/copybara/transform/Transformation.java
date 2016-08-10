// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.copybara.config.NonReversibleValidationException;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface implemented by all source code transformations.
 */
@SkylarkModule(
    name = "transformation",
    doc = "A transformation to the workdir",
    category = SkylarkModuleCategory.TOP_LEVEL_TYPE)
public interface Transformation {

  /**
   * Transforms the files inside {@code workdir}
   *
   * @throws IOException if an error occur during the access to the files
   * @throws ValidationException if an error attributable to the user happened
   */
  void transform(Path workdir, Console console) throws IOException, ValidationException;

  /**
   * Returns a transformation which runs this transformation in reverse.
   *
   * @throws NonReversibleValidationException if the transform is not reversible
   */
  Transformation reverse() throws NonReversibleValidationException;

  /**
   * Return a high level description of what the transform is doing. Note that this should not be
   * {@link #toString()} method but something more user friendly.
   */
  String describe();
}
