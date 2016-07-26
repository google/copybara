// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.copybara.Option;
import com.google.copybara.util.console.Console;

/**
 * Options relating transformations in general.
 */
@Parameters(separators = "=")
public final class TransformOptions implements Option {
  @Parameter(names = "--transform-noop-is-warning")
  public boolean noop_is_warning = false;

  /**
   * Reports that some transform is a no-op. This will either throw an exception or report the
   * incident to the console, depending on the options.
   */
  public void reportNoop(Console console, String message) throws VoidTransformationException {
    if (noop_is_warning) {
      console.warn(message);
    } else {
      throw new VoidTransformationException(message);
    }
  }
}
