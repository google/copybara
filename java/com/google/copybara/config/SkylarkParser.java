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
import com.google.copybara.doc.annotations.Library;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.StarlarkMode;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Supplier;
import net.starlark.java.annot.StarlarkAnnotations;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.Mutability;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.syntax.FileOptions;
import net.starlark.java.syntax.ParserInput;
import net.starlark.java.syntax.Program;
import net.starlark.java.syntax.StarlarkFile;
import net.starlark.java.syntax.SyntaxError;

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
        globalMigrations.getMigrations(), content.path(), module.getPredeclaredBindings());
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

      // Make the modules available as predeclared bindings.
      // For modules that implement OptionsAwareModule, options are set in the object so
      // that the module can construct objects that require options.
      StarlarkSemantics semantics = StarlarkSemantics.DEFAULT;
      module = Module.withPredeclared(semantics, environment);

      // parse & compile
      ParserInput input = ParserInput.fromUTF8(content.readContentBytes(), content.path());
      FileOptions options =
          FileOptions.DEFAULT.toBuilder()
              // Ordinarily, load statements should create file-local variables.
              // For now, we make them create first-class members of Module.globals.
              .loadBindsGlobally(true)
              .allowToplevelRebinding(true) // allow e.g. x=1; x=2 at top level
              .requireLoadStatementsFirst(validation == StarlarkMode.STRICT)
              .build();

      Program prog;
      try {
        prog = Program.compileFile(StarlarkFile.parse(input, options), module);
      } catch (SyntaxError.Exception ex) {
        for (SyntaxError error : ex.errors()) {
          console.error(error.toString());
        }
        checkCondition(false, "Error loading config file.");
        return null; // unreachable
      }

      // process loads
      Map<String, Module> loadedModules = new HashMap<>();
      for (String load : prog.getLoads()) {
        Module loadedModule = eval(content.resolve(load + BARA_SKY));
        loadedModules.put(load, loadedModule);
      }

      // execute
      updateEnvironmentForConfigFile(
          this::starlarkPrint, content, mainConfigFile, environment, moduleSet);
      try (Mutability mu = Mutability.create("CopybaraModules")) {
        StarlarkThread thread = new StarlarkThread(mu, semantics);
        thread.setLoader(loadedModules::get);
        thread.setPrintHandler(this::starlarkPrint);
        Starlark.execFileProgram(prog, module, thread);
      } catch (EvalException ex) {
        console.error(ex.getMessageWithStack());
        throw new ValidationException("Error loading config file", ex);
      } catch (Starlark.UncheckedEvalException uex) {
        console.error(uex.toString());
        // rethrow the UEX because it has the starlark stacktrace.
        if (uex.getCause() != null) {
          throw new ValidationException(
              "Error loading config file: " + uex.getCause().getMessage(), uex);
        }
        throw new ValidationException("Error loading config file", uex);
      }

      pending.remove(content.path());
      loaded.put(content.path(), module);
      return module;
    }

    private void starlarkPrint(StarlarkThread thread, String msg) {
      console.verbose(thread.getCallerLocation() + ": " + msg);
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
        m.setPrintHandler(printHandler);
      }
    }
    for (Class<?> module : modules) {
      logger.atInfo().log("Creating variable for %s", module.getName());
      // We mutate the module per file loaded. Not ideal but it is the best we can do.
      if (LabelsAwareModule.class.isAssignableFrom(module)) {
        LabelsAwareModule m = (LabelsAwareModule) environment.get(getModuleName(module));
        m.setConfigFile(mainConfigFile, currentConfigFile);
        m.setPrintHandler(printHandler);
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
        StarlarkBuiltin annot = StarlarkAnnotations.getStarlarkBuiltin(module);
        if (annot != null) {
          envBuilder.put(annot.name(), module.getConstructor().newInstance());
        } else if (module.isAnnotationPresent(Library.class)) {
          Starlark.addMethods(envBuilder, module.getConstructor().newInstance());
        }
      } catch (ReflectiveOperationException e) {
        throw new LinkageError(e.getMessage(), e);
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
