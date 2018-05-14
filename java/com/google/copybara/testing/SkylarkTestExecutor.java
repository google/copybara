/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara.testing;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.Core;
import com.google.copybara.GeneralOptions;
import com.google.copybara.GlobModule;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.config.SkylarkParser;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.devtools.build.lib.syntax.Environment;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Utility class for running a simple skylark code and getting back a declared variable.
 */
public final class SkylarkTestExecutor {

  private static final String DEFAULT_FILE = "copy.bara.sky";
  private final OptionsBuilder options;
  private final SkylarkParser skylarkParser;
  private final Map<String, byte[]> extraConfigFiles = new HashMap<>();

  public SkylarkTestExecutor(OptionsBuilder options, Class<?>... modules) {
    skylarkParser =
        new SkylarkParser(
            ImmutableSet.<Class<?>>builder()
                .add(GlobModule.class)
                .add(Core.class)
                .add(Authoring.Module.class)
                .add(modules)
                .build());
    this.options = options;
  }

  public SkylarkTestExecutor addExtraConfigFile(String key, String content) {
    if (extraConfigFiles.put(key, content.getBytes(UTF_8)) != null) {
      throw new IllegalArgumentException("Already have content for: " + key);
    }
    return this;
  }

  @VisibleForTesting
  public Iterable<Class<?>> getModules(){
    return skylarkParser.getModules();
  }

  @SuppressWarnings({"TypeParameterUnusedInFormals", "unchecked"})
  public <T> T eval(String var, String config) throws ValidationException {
    try {
      Environment env =
          skylarkParser.executeSkylark(
              createConfigFile(DEFAULT_FILE, config), options.build(), options.general.console());
      T t = (T) env.getGlobals().get(var);
      Preconditions.checkNotNull(t, "Config %s evaluates to null '%s' var.", config, var);
      return t;
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(
          String.format("Should not happen: %s.\n %s", e.getMessage(), getLogErrors()), e);
    } catch (ValidationException ve) {
      throw new ValidationException(ve, ve.getMessage() + getLogErrors());
    }
  }

  String getLogErrors() {
    return "\nLogged errors:\n" +
        getConsole().getMessages().stream()
        .filter(m -> m.getType().equals(MessageType.ERROR))
        .map(m -> m.getText()).reduce((a,b) -> a + "\n" + b)
        .orElse("No log errors");
  }

  /**
   * Evaluates the given {@code config} and invokes the {@code fieldName}, verifying that the
   * returned value is equal to the expected one.
   */
  public void verifyField(String var, String fieldName, Object expectedValue)
      throws ValidationException {
    Object result = eval("e", String.format("e = %s.%s", var, fieldName));
    if (!result.equals(expectedValue)) {
      throw new RuntimeException(
          String.format("Invalid field %s. Got: %s. Want: %s", fieldName, result, expectedValue));
    }
  }

  /**
   * Evaluates the given {@code config}. invoking each of the fields and verifying that the
   * returned value is equal to the expected one.
   */
  public void verifyFields(String var, ImmutableMap<String, Object> fieldNameToExpectedValue)
      throws ValidationException {
    for (Entry<String, Object> entry : fieldNameToExpectedValue.entrySet()) {
      verifyField(var, entry.getKey(), entry.getValue());
    }
  }

  public Config loadConfig(String filename, String configContent)
      throws IOException, ValidationException {
    try {
      return skylarkParser.loadConfig(
          createConfigFile(filename, configContent),
          options.build(), options.general.console());
    } catch (ValidationException ve) {
      throw new ValidationException(ve, ve.getMessage() + getLogErrors());
    }
  }

  public Config loadConfig(String configContent) throws IOException, ValidationException {
    try {
      return skylarkParser.loadConfig(
          createConfigFile(DEFAULT_FILE, configContent),
          options.build(), options.general.console());
    } catch (ValidationException ve) {
      throw new ValidationException(ve, ve.getMessage() + getLogErrors());
    }
  }

  public Map<String, ConfigFile<String>> getConfigMap(String configContent)
      throws IOException, ValidationException {
    return getConfigMap(createConfigFile(DEFAULT_FILE, configContent));
  }

  public <T> Map<String, ConfigFile<T>> getConfigMap(ConfigFile<T> config)
      throws IOException, ValidationException {
    return skylarkParser.getConfigWithTransitiveImports(
            config, options.build(), options.general.console())
        .files;
  }

  private ConfigFile<String> createConfigFile(String filename, String configContent) {
    return new MapConfigFile(
        new ImmutableMap.Builder<String, byte[]>()
            .putAll(extraConfigFiles)
            .put(filename, configContent.getBytes(UTF_8))
            .build(),
        filename);
  }

  public void evalFails(String config, String expectedMsg) {
    try {
      eval("r", "r = " + config);
      throw new RuntimeException("Eval should fail: " + config);
    } catch (ValidationException e) {
      getConsole().assertThat().onceInLog(MessageType.ERROR, "(.|\n)*" + expectedMsg + "(.|\n)*");
    }
  }

  public void evalProgramFails(String config, String expectedMsg) {
    try {
      eval("not_used", config);
      throw new RuntimeException("Eval should fail: " + config);
    } catch (ValidationException e) {
      getConsole().assertThat().onceInLog(MessageType.ERROR, "(.|\n)*" + expectedMsg + "(.|\n)*");
    }
  }

  private TestingConsole getConsole() {
    return (TestingConsole) options.build().get(GeneralOptions.class).console();
  }
}
