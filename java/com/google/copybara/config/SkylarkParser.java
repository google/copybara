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

package com.google.copybara.config;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.ModuleSet;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.StarlarkMode;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.EvalUtils;
import com.google.devtools.build.lib.syntax.FileOptions;
import com.google.devtools.build.lib.syntax.LoadStatement;
import com.google.devtools.build.lib.syntax.Module;
import com.google.devtools.build.lib.syntax.Mutability;
import com.google.devtools.build.lib.syntax.ParserInput;
import com.google.devtools.build.lib.syntax.Resolver;
import com.google.devtools.build.lib.syntax.Starlark;
import com.google.devtools.build.lib.syntax.StarlarkFile;
import com.google.devtools.build.lib.syntax.StarlarkSemantics;
import com.google.devtools.build.lib.syntax.StarlarkThread;
import com.google.devtools.build.lib.syntax.Statement;
import com.google.devtools.build.lib.syntax.SyntaxError;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Supplier;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkInterfaceUtils;

/**
 * Loads Copybara configs out of Skylark files.
 */
public class SkylarkParser {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String BARA_SKY = ".bara.sky";
  // For now all the modules are namespaces. We don't use variables except for 'core'.
  private final Iterable<Class<?>> modules;
  private final StarlarkMode validation;

  public SkylarkParser(Set<Class<?>> staticModules, StarlarkMode validation) {
    this.modules = ImmutableSet.<Class<?>>builder()
        .add(GlobalMigrations.class)
        .addAll(staticModules).build();
    this.validation = validation;
  }

  public Config loadConfig(ConfigFile config, ModuleSet moduleSet, Console console)
      throws IOException, ValidationException {
    return getConfigWithTransitiveImports(config, moduleSet, console).config;
  }

  private Config loadConfigInternal(ConfigFile content, ModuleSet moduleSet,
      Supplier<ImmutableMap<String, ConfigFile>> configFilesSupplier, Console console)
      throws IOException, ValidationException {
    GlobalMigrations globalMigrations;
    Module module;
    try {
      module = new Evaluator(moduleSet, content, configFilesSupplier, console).eval(content);
      globalMigrations = GlobalMigrations.getGlobalMigrations(module);
    } catch (InterruptedException e) {
      // This should not happen since we shouldn't have anything interruptable during loading.
      throw new RuntimeException("Internal error", e);
    }
    return new Config(
        globalMigrations.getMigrations(), content.path(), module.getTransitiveBindings());
  }

  @VisibleForTesting
  public Module executeSkylark(ConfigFile content, ModuleSet moduleSet, Console console)
      throws IOException, ValidationException, InterruptedException {
    CapturingConfigFile capturingConfigFile = new CapturingConfigFile(content);
    ConfigFilesSupplier configFilesSupplier = new ConfigFilesSupplier();

    Module module = new Evaluator(moduleSet, content, configFilesSupplier, console).eval(content);
    configFilesSupplier.setConfigFiles(capturingConfigFile.getAllLoadedFiles());
    return module;
  }

  /**
   * Collect all ConfigFiles retrieved by the parser while loading {code config}.
   *
   * @param config Root file of the configuration.
   * @param console the console to use for printing error/information
   * @return A map linking paths to the captured ConfigFiles and the parsed Config
   * @throws IOException If files cannot be read
   * @throws ValidationException If config is invalid, references an invalid file or contains
   *     dependency cycles.
   */
  public ConfigWithDependencies getConfigWithTransitiveImports(
      ConfigFile config, ModuleSet moduleSet, Console console)
      throws IOException, ValidationException {
    CapturingConfigFile capturingConfigFile = new CapturingConfigFile(config);
    ConfigFilesSupplier configFilesSupplier = new ConfigFilesSupplier();

    Config parsedConfig = loadConfigInternal(capturingConfigFile, moduleSet, configFilesSupplier,
        console);

    ImmutableMap<String, ConfigFile> allLoadedFiles = capturingConfigFile.getAllLoadedFiles();

    configFilesSupplier.setConfigFiles(allLoadedFiles);

    return new ConfigWithDependencies(allLoadedFiles, parsedConfig);
  }

  private static class ConfigFilesSupplier
      implements Supplier<ImmutableMap<String, ConfigFile>> {

    private ImmutableMap<String, ConfigFile> configFiles = null;

    void setConfigFiles(ImmutableMap<String, ConfigFile> configFiles) {
      Preconditions.checkState(this.configFiles == null, "Already set");
      this.configFiles = checkNotNull(configFiles);
    }

    @Override
    public ImmutableMap<String, ConfigFile> get() {
      // We need to load all the files before knowing the set of files in the config.
      checkNotNull(configFiles, "Don't call the supplier before loading"
          + " finishes.");
      return configFiles;
    }
  }

  /**
   * A class that contains a loaded config and all the config files that were
   * accessed during the parsing.
   */
  public static class ConfigWithDependencies {
    private final ImmutableMap<String, ConfigFile> files;
    private final Config config;

    private ConfigWithDependencies(ImmutableMap<String, ConfigFile> files, Config config) {
      this.config = config;
      this.files = files;
    }

    public Config getConfig() {
      return config;
    }

    public ImmutableMap<String, ConfigFile> getFiles() {
      return files;
    }
  }

  /**
   * An utility class for traversing and evaluating the config file dependency graph.
   */
  private final class Evaluator {

    private final LinkedHashSet<String> pending = new LinkedHashSet<>();
    private final Map<String, Module> loaded = new HashMap<>();
    private final Console console;
    private final ConfigFile mainConfigFile;
    // Predeclared environment shared by all files (modules) loaded.
    private final ImmutableMap<String, Object> environment;
    private final ModuleSet moduleSet;

    private Evaluator(ModuleSet moduleSet, ConfigFile mainConfigFile,
        Supplier<ImmutableMap<String, ConfigFile>> configFilesSupplier,
        Console console) {
      this.console = checkNotNull(console);
      this.mainConfigFile = checkNotNull(mainConfigFile);
      this.moduleSet = checkNotNull(moduleSet);
      this.environment = createEnvironment(this.moduleSet, configFilesSupplier);
    }

    private Module eval(ConfigFile content)
        throws IOException, ValidationException, InterruptedException {
      if (pending.contains(content.path())) {
        throw throwCycleError(content.path());
      }
      Module module = loaded.get(content.path());
      if (module != null) {
        return module;
      }
      pending.add(content.path());

      ParserInput input = ParserInput.create(content.readContent(), content.path());
      FileOptions options =
          validation == StarlarkMode.STRICT
              ? STARLARK_STRICT_FILE_OPTIONS
              : STARLARK_LOOSE_FILE_OPTIONS;
      StarlarkFile file = StarlarkFile.parse(input, options);

      Map<String, Module> loadedModules = new HashMap<>();
      for (Statement stmt :  file.getStatements()) {
        if (stmt instanceof LoadStatement) {
          String moduleName = ((LoadStatement) stmt).getImport().getValue();
          Module imp = eval(content.resolve(moduleName + BARA_SKY));
          loadedModules.put(moduleName, imp);
        }
      }
      StarlarkThread.PrintHandler printHandler =
          (thread, msg) -> console.verbose(thread.getCallerLocation() + ": " + msg);
      updateEnvironmentForConfigFile(printHandler, content, mainConfigFile, environment, moduleSet);

      // Create a Starlark thread making the modules available as predeclared bindings.
      // For modules that implement OptionsAwareModule, options are set in the object so
      // that the module can construct objects that require options.
      StarlarkSemantics semantics = createSemantics();
      module = Module.withPredeclared(semantics, environment);

      // resolve
      Resolver.resolveFile(file, module);
      if (!file.ok()) {
        for (SyntaxError error : file.errors()) {
          console.error(error.toString());
        }
        checkCondition(false, "Error loading config file.");
      }

      // execute
      try (Mutability mu = Mutability.create("CopybaraModules")) {
        StarlarkThread thread = new StarlarkThread(mu, semantics);
        thread.setLoader(loadedModules::get);
        thread.setPrintHandler(printHandler);
        EvalUtils.exec(file, module, thread);
      } catch (EvalException ex) {
        console.error(ex.getLocation() + ": " + ex.getMessage());
        checkCondition(false, "Error loading config file");
      }

      pending.remove(content.path());
      loaded.put(content.path(), module);
      return module;
    }

    private ValidationException throwCycleError(String cycleElement)
        throws ValidationException {
      StringBuilder sb = new StringBuilder();
      for (String element : pending) {
        sb.append(element.equals(cycleElement) ? "* " : "  ");
        sb.append(element).append("\n");
      }
      sb.append("* ").append(cycleElement).append("\n");
      console.error("Cycle was detected in the configuration: \n" + sb);
      throw new ValidationException("Cycle was detected");
    }
  }

  // Even in strict mode, we allow top-level if and for statements.
  private static final FileOptions STARLARK_STRICT_FILE_OPTIONS =
      FileOptions.DEFAULT.toBuilder() //
          .allowToplevelRebinding(true)
          .build();

  private static final FileOptions STARLARK_LOOSE_FILE_OPTIONS =
      STARLARK_STRICT_FILE_OPTIONS.toBuilder()
          // TODO(malcon): stop allowing invalid escapes such as "[\s\S]",
          // which appears in devtools/blaze/bazel/admin/copybara/docs.bara.sky.
          // This is a breaking change but trivially fixed.
          .restrictStringEscapes(false)
          .requireLoadStatementsFirst(false)
          .build();

  private StarlarkSemantics createSemantics() {
    return StarlarkSemantics.DEFAULT;
  }

  /** Updates the module globals with information about the current loaded config file. */
  // TODO(copybara-team): evaluate the cleaner approach of saving the varying parts in the
  // StarlarkThread.setThreadLocal and leaving the modules alone as nature intended.
  private void updateEnvironmentForConfigFile(
      StarlarkThread.PrintHandler printHandler,
      ConfigFile currentConfigFile,
      ConfigFile mainConfigFile,
      Map<String, Object> environment,
      ModuleSet moduleSet) {
    for (Object module : moduleSet.getModules().values()) {
      // We mutate the module per file loaded. Not ideal but it is the best we can do.
      if (module instanceof LabelsAwareModule) {
        LabelsAwareModule m = (LabelsAwareModule) module;
        m.setConfigFile(mainConfigFile, currentConfigFile);
        // TODO(malcon): these two setDynamicEnvironment calls are identical.
        // Eliminate the feature?
        m.setDynamicEnvironment(
            () -> {
              StarlarkThread thread =
                  new StarlarkThread(
                      Mutability.create("dynamic_action"), StarlarkSemantics.DEFAULT);
              thread.setPrintHandler(printHandler);
              return thread;
            });
      }
    }
    for (Class<?> module : modules) {
      logger.atInfo().log("Creating variable for %s", module.getName());
      // We mutate the module per file loaded. Not ideal but it is the best we can do.
      if (LabelsAwareModule.class.isAssignableFrom(module)) {
        LabelsAwareModule m = (LabelsAwareModule) environment.get(getModuleName(module));
        m.setConfigFile(mainConfigFile, currentConfigFile);
        m.setDynamicEnvironment(
            () -> {
              StarlarkThread thread =
                  new StarlarkThread(
                      Mutability.create("dynamic_action"), StarlarkSemantics.DEFAULT);
              thread.setPrintHandler(printHandler);
              return thread;
            });
      }
    }
  }

  /**
   * Create the environment for all evaluations (will be shared between all the dependent files
   * loaded).
   */
  private ImmutableMap<String, Object> createEnvironment(
      ModuleSet moduleSet, Supplier<ImmutableMap<String, ConfigFile>> configFilesSupplier) {
    Map<String, Object> env = Maps.newHashMap();
    for (Entry<String, Object> module : moduleSet.getModules().entrySet()) {
      logger.atInfo().log("Creating variable for %s", module.getKey());
      if (module.getValue() instanceof LabelsAwareModule) {
        ((LabelsAwareModule) module.getValue()).setAllConfigResources(configFilesSupplier);
      }
      // Modules shouldn't use the same name
      env.put(module.getKey(), module.getValue());
    }

    for (Class<?> module : modules) {
      logger.atInfo().log("Creating variable for %s", module.getName());
      // Create the module object and associate it with the functions
      ImmutableMap.Builder<String, Object> envBuilder = ImmutableMap.builder();
      try {
        if (StarlarkInterfaceUtils.getStarlarkBuiltin(module) != null) {
          Starlark.addModule(envBuilder, module.getConstructor().newInstance());
        } else if (StarlarkInterfaceUtils.hasStarlarkGlobalLibrary(module)) {
          Starlark.addMethods(envBuilder, module.getConstructor().newInstance());
        }
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
      env.putAll(envBuilder.build());

      // Add the options to the module that require them
      if (OptionsAwareModule.class.isAssignableFrom(module)) {
        ((OptionsAwareModule) env.get(getModuleName(module))).setOptions(moduleSet.getOptions());
      }
      if (LabelsAwareModule.class.isAssignableFrom(module)) {
        ((LabelsAwareModule) env.get(getModuleName(module)))
            .setAllConfigResources(configFilesSupplier);
      }
    }
    return ImmutableMap.copyOf(env);
  }

  private static String getModuleName(Class<?> cls) {
    return cls.getAnnotation(StarlarkBuiltin.class).name();
  }
}
