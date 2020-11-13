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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.GeneralOptions;
import com.google.copybara.ModuleSet;
import com.google.copybara.ModuleSupplier;
import com.google.copybara.Options;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.config.SkylarkParser;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Message;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.starlark.java.eval.Module;

/**
 * Utility class for running a simple skylark code and getting back a declared variable.
 */
public class SkylarkTestExecutor {

  private static final String DEFAULT_FILE = "copy.bara.sky";
  private final OptionsBuilder options;
  private final ModuleSupplier moduleSupplier;
  private final Map<String, byte[]> extraConfigFiles = new HashMap<>();
  private SkylarkParser skylarkParser;
  private ModuleSupplier moduleSupplierForTest;
  private ImmutableSet<Class<?>> staticTestModules = ImmutableSet.of();

  public SkylarkTestExecutor(OptionsBuilder options) {
    this(options, new ModuleSupplier(options.general.getEnvironment(),
        options.general.getFileSystem(), options.general.console()));
  }

  protected SkylarkTestExecutor(OptionsBuilder options, ModuleSupplier moduleSupplier) {
    this.options = options;
    this.moduleSupplier = moduleSupplier;
    initParser();
  }

  public SkylarkTestExecutor withStaticModules(ImmutableSet<Class<?>> staticTestModules) {
    this.staticTestModules = Preconditions.checkNotNull(staticTestModules);
    // TODO(malcon): Remove this once all the static modules are gone.
    initParser();
    return this;
  }

  private void initParser() {
    moduleSupplierForTest = new ModuleSupplierForTest(options, moduleSupplier);
    skylarkParser = new SkylarkParser(moduleSupplierForTest.create().getStaticModules(),
        options.general.getStarlarkMode());
  }

  public SkylarkTestExecutor addConfigFile(String key, String content) {
    if (extraConfigFiles.put(key, content.getBytes(UTF_8)) != null) {
      throw new IllegalArgumentException("Already have content for: " + key);
    }
    return this;
  }

  public SkylarkTestExecutor addAllConfigFiles(Map<String, String> configFiles) {
    configFiles.forEach(this::addConfigFile);
    return this;
  }

  public Iterable<Class<?>> getModules(){
    ModuleSet moduleSet = createModuleSet();
    return ImmutableSet.<Class<?>>builder()
        .addAll(moduleSet.getModules().values().stream()
            .map(Object::getClass)
            .collect(Collectors.toList()))
        .addAll(moduleSet.getStaticModules())
        .build();
  }

  public <T> T eval(String var, String config) throws ValidationException {
    return evalWithConfigFilePathAndModuleSet(var, config, DEFAULT_FILE, createModuleSet());
  }

  @SuppressWarnings({"TypeParameterUnusedInFormals"})
  public <T> T evalWithConfigFilePath(String var, String config, String configPath)
      throws ValidationException {
    return evalWithConfigFilePathAndModuleSet(var, config, configPath, createModuleSet());
  }

  @SuppressWarnings({"TypeParameterUnusedInFormals"})
  public <T> T evalWithModuleSet(String var, String config, ModuleSet moduleSet)
      throws ValidationException {
    return evalWithConfigFilePathAndModuleSet(var, config, DEFAULT_FILE, moduleSet);
  }

  @SuppressWarnings({"TypeParameterUnusedInFormals"})
  public <T> T evalWithConfigFilePathAndModuleSet(String var, String config, String configPath,
      ModuleSet moduleSet) throws ValidationException {
    try {
      Module module =
          skylarkParser.executeSkylark(
              createConfigFile(configPath, config), moduleSet, options.general.console());
      @SuppressWarnings("unchecked") // the cast below is wildly unsound
          T t = (T) module.getGlobal(var);
      Preconditions.checkNotNull(t, "Config %s evaluates to null '%s' var.", config, var);
      return t;
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(
          String.format("Should not happen: %s.\n %s", e.getMessage(), getLogErrors()), e);
    } catch (ValidationException ve) {
      throw new ValidationException(ve.getMessage() + getLogErrors(), ve);
    }
  }

  private String getLogErrors() {
    return "\nLogged errors:\n"
        + getConsole().getMessages().stream()
        .filter(m -> m.getType().equals(MessageType.ERROR))
        .map(Message::getText).reduce((a, b) -> a + "\n" + b)
        .orElse("No log errors");
  }

  /**
   * Evaluates the given {@code config} and invokes the {@code fieldName}, verifying that the
   * returned value is equal to the expected one.
   */
  public void verifyField(String var, String fieldName, Object expectedValue)
      throws ValidationException {
    Object result = eval("e", String.format(
        // Support lists
        fieldName.startsWith("[") ? "e = %s%s" : "e = %s.%s", var, fieldName));
    if (!result.equals(expectedValue)) {
      throw new RuntimeException(
          String.format("Invalid field %s. Got: %s. Want: %s", fieldName, result, expectedValue));
    }
  }

  public void verifyObject(String var, Object expectedValue)
      throws ValidationException {
    // Empty fieldName == the object itself
    // Support lists
    Object result = eval("e", String.format("e = %s", var));
    if (!result.equals(expectedValue)) {
      throw new RuntimeException(
          String.format("Unexpected value Got: %s. Want: %s", result, expectedValue));
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

  public Config loadConfig(String configContent) throws IOException, ValidationException {
    return loadConfig(DEFAULT_FILE, configContent);
  }

  public Config loadConfig(String filename, String configContent)
      throws IOException, ValidationException {
    return loadConfig(createConfigFile(filename, configContent));
  }

  public Config loadConfig(ConfigFile configFile)
      throws IOException, ValidationException {
    try {
      return skylarkParser.loadConfig(configFile, createModuleSet(), options.general.console());
    } catch (ValidationException ve) {
      throw new ValidationException(ve.getMessage() + getLogErrors(), ve);
    }
  }

  /**
   * Normally this is not what you want to use
   */
  public SkylarkParser getSkylarkParser() {
    return skylarkParser;
  }

  /**
   * In general creating multiple ModuleSets is a bad idea (Since options are created again), but
   * for test is fine since we share the same OptionsBuilder.
   */
  public ModuleSet createModuleSet() {
    return moduleSupplierForTest.create();
  }

  public Map<String, ConfigFile> getConfigMap(String configContent)
      throws IOException, ValidationException {
    return getConfigMap(createConfigFile(DEFAULT_FILE, configContent));
  }

  public Map<String, ConfigFile> getConfigMap(ConfigFile config)
      throws IOException, ValidationException {
    return skylarkParser.getConfigWithTransitiveImports(
            config, createModuleSet(), options.general.console())
        .getFiles();
  }

  public ConfigFile createConfigFile(String filename, String configContent) {
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

  private class ModuleSupplierForTest extends ModuleSupplier {

    private final OptionsBuilder options;
    private final ModuleSupplier moduleSupplier;

    ModuleSupplierForTest(OptionsBuilder options, ModuleSupplier moduleSupplier) {
      super(options.general.getEnvironment(), options.general.getFileSystem(),
          options.general.console());
      this.options = options;
      this.moduleSupplier = Preconditions.checkNotNull(moduleSupplier);
    }

    @Override
    protected ImmutableSet<Class<?>> getStaticModules() {
       return ImmutableSet.<Class<?>>builder()
           .addAll(moduleSupplier.create().getStaticModules())
           .addAll(staticTestModules)
           .build();
    }

    @Override
    public ImmutableSet<Object> getModules(Options options) {
      return ImmutableSet.builder()
          .addAll(moduleSupplier.getModules(options))
          .add(new TestingModule(options))
          .build();
    }

    @Override
    protected Options newOptions() {
      return options.build();
    }
  }

}
