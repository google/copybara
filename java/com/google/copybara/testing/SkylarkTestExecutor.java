package com.google.copybara.testing;

import com.google.common.collect.ImmutableSet;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.config.skylark.SkylarkParser;
import com.google.devtools.build.lib.syntax.Environment;
import java.io.IOException;

/**
 * Utility class for running a simple skylark code and getting back a declared variable.
 */
public final class SkylarkTestExecutor {

  private final OptionsBuilder options;
  private final SkylarkParser skylarkParser;

  public SkylarkTestExecutor(OptionsBuilder options, Class<?>... modules) {
    skylarkParser = new SkylarkParser(ImmutableSet.copyOf(modules));
    this.options = options;
  }

  @SuppressWarnings("unchecked")
  public <T> T eval(String var, String config) throws ConfigValidationException {
    try {
      Environment env = skylarkParser.executeSkylark(config, options.build());

      return (T) env.getGlobals().get(var);
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Should not happen: " + e.getMessage(), e);
    }
  }
}
