package com.google.copybara.testing;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.GeneralOptions;
import com.google.copybara.ConfigValidationException;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.SkylarkParser;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.util.console.testing.TestingConsole.MessageType;
import com.google.devtools.build.lib.syntax.Environment;
import java.io.IOException;
import java.util.HashMap;
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
  private final Map<String, byte[]> extraConfigFiles = new HashMap<>();

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

  public SkylarkTestExecutor addExtraConfigFile(String key, String content) {
    if (extraConfigFiles.put(key, content.getBytes(UTF_8)) != null) {
      throw new IllegalArgumentException("Already have content for: " + key);
    }
    return this;
  }

  @SuppressWarnings("unchecked")
  public <T> T eval(String var, String config) throws ConfigValidationException {
    try {
      ConfigFile configFile = new MapConfigFile(
          new ImmutableMap.Builder<String, byte[]>()
              .putAll(extraConfigFiles)
              .put("copy.bara.sky", config.getBytes())
              .build(),
          "copy.bara.sky");
      Environment env = skylarkParser.executeSkylark(configFile, options.build(), environment);
      T t = (T) env.getGlobals().get(var);
      Preconditions.checkNotNull(t, "Config %s evaluates to null '%s' var.", config, var);
      return t;
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Should not happen: " + e.getMessage(), e);
    }
  }

  public void evalFails(String config, String expectedMsg) {
    try {
      eval("r", "r = " + config);
      throw new RuntimeException("Eval should fail: " + config);
    } catch (ConfigValidationException e) {
      getConsole().assertThat().onceInLog(MessageType.ERROR, "(.|\n)*" + expectedMsg + "(.|\n)*");
    }
  }

  private TestingConsole getConsole() {
    return (TestingConsole) options.build().get(GeneralOptions.class).console();
  }
}
