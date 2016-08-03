package com.google.copybara.testing;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.config.skylark.SkylarkParser;
import com.google.devtools.build.lib.syntax.Environment;


import java.io.IOException;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Utility class for running a simple skylark code and getting back a declared variable.
 */
public final class SkylarkTestExecutor {

  private final OptionsBuilder options;
  @Nullable
  private final Map<String, String> environment;
  private final SkylarkParser skylarkParser;

  public SkylarkTestExecutor(OptionsBuilder options, Class<?>... modules) {
    skylarkParser = new SkylarkParser(ImmutableSet.copyOf(modules));
    this.options = options;
    this.environment = null;
  }

  public SkylarkTestExecutor(
      OptionsBuilder options, @Nullable Map<String, String> environment, Class<?>... modules) {
    skylarkParser = new SkylarkParser(ImmutableSet.copyOf(modules));
    this.options = options;
    this.environment = environment;
  }

  @SuppressWarnings("unchecked")
  public <T> T eval(String var, String config) throws ConfigValidationException {
    try {
      Environment env = skylarkParser.executeSkylark(config, options.build(), environment);
      T t = (T) env.getGlobals().get(var);
      Preconditions.checkNotNull(t, "Config %s evaluates to null '%s' var.", config, var);
      return t;
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Should not happen: " + e.getMessage(), e);
    }
  }
}
