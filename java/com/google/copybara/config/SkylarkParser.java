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
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.skylarkinterface.SkylarkInterfaceUtils;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.EvalUtils;
import com.google.devtools.build.lib.syntax.LoadStatement;
import com.google.devtools.build.lib.syntax.Module;
import com.google.devtools.build.lib.syntax.Mutability;
import com.google.devtools.build.lib.syntax.ParserInput;
import com.google.devtools.build.lib.syntax.Starlark;
import com.google.devtools.build.lib.syntax.StarlarkFile;
import com.google.devtools.build.lib.syntax.StarlarkSemantics;
import com.google.devtools.build.lib.syntax.StarlarkThread;
import com.google.devtools.build.lib.syntax.StarlarkThread.Extension;
import com.google.devtools.build.lib.syntax.Statement;
import com.google.devtools.build.lib.syntax.StringLiteral;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Loads Copybara configs out of Skylark files.
 */
public class SkylarkParser {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String BARA_SKY = ".bara.sky";
  // For now all the modules are namespaces. We don't use variables except for 'core'.
  private final Iterable<Class<?>> modules;

  public SkylarkParser(Set<Class<?>> staticModules) {
    this.modules = ImmutableSet.<Class<?>>builder()
        .add(GlobalMigrations.class)
        .addAll(staticModules).build();
  }

  public Config loadConfig(ConfigFile config, ModuleSet moduleSet, Console console)
      throws IOException, ValidationException {
    return getConfigWithTransitiveImports(config, moduleSet, console).config;
  }

  private Config loadConfigInternal(ConfigFile content, ModuleSet moduleSet,
      Supplier<ImmutableMap<String, ConfigFile>> configFilesSupplier, Console console)
      throws IOException, ValidationException {
    GlobalMigrations globalMigrations;
    StarlarkThread thread;
    try {
      thread = new Evaluator(moduleSet, content, configFilesSupplier, console).eval(content);
      globalMigrations = GlobalMigrations.getGlobalMigrations(thread);
    } catch (InterruptedException e) {
      // This should not happen since we shouldn't have anything interruptable during loading.
      throw new RuntimeException("Internal error", e);
    }
    return new Config(
        globalMigrations.getMigrations(),
        content.path(),
        thread.getGlobals().getTransitiveBindings());
  }

  @VisibleForTesting
  public StarlarkThread executeSkylark(ConfigFile content, ModuleSet moduleSet, Console console)
      throws IOException, ValidationException, InterruptedException {
    CapturingConfigFile capturingConfigFile = new CapturingConfigFile(content);
    ConfigFilesSupplier configFilesSupplier = new ConfigFilesSupplier();

    StarlarkThread eval =
        new Evaluator(moduleSet, content, configFilesSupplier, console).eval(content);

    ImmutableMap<String, ConfigFile> allLoadedFiles = capturingConfigFile.getAllLoadedFiles();
    configFilesSupplier.setConfigFiles(allLoadedFiles);
    return eval;
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
  public  ConfigWithDependencies getConfigWithTransitiveImports(
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
      this.configFiles = Preconditions.checkNotNull(configFiles);
    }

    @Override
    public ImmutableMap<String, ConfigFile> get() {
      // We need to load all the files before knowing the set of files in the config.
      Preconditions.checkNotNull(configFiles, "Don't call the supplier before loading"
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
    private final Map<String, StarlarkThread> loaded = new HashMap<>();
    private final Console console;
    private final ConfigFile mainConfigFile;
    private final EventHandler eventHandler;
    // Predeclared environment shared by all files (modules) loaded.
    private final ImmutableMap<String, Object> environment;
    private final ModuleSet moduleSet;

    private Evaluator(ModuleSet moduleSet, ConfigFile mainConfigFile,
        Supplier<ImmutableMap<String, ConfigFile>> configFilesSupplier,
        Console console) {
      this.console = Preconditions.checkNotNull(console);
      this.mainConfigFile = Preconditions.checkNotNull(mainConfigFile);
      this.moduleSet = Preconditions.checkNotNull(moduleSet);
      this.eventHandler = new ConsoleEventHandler(this.console);
      this.environment = createEnvironment(this.moduleSet, configFilesSupplier);
    }

    private StarlarkThread eval(ConfigFile content)
        throws IOException, ValidationException, InterruptedException {
      if (pending.contains(content.path())) {
        throw throwCycleError(content.path());
      }
      if (loaded.containsKey(content.path())) {
        return loaded.get(content.path());
      }
      pending.add(content.path());

      ParserInput input = ParserInput.create(content.readContent(), content.path());
      StarlarkFile file = StarlarkFile.parse(input);
      Event.replayEventsOn(eventHandler, file.errors());
      Map<String, Extension> imports = new HashMap<>();
      for (Statement stmt : file.getStatements()) {
        if (stmt instanceof LoadStatement) {
          StringLiteral module = ((LoadStatement) stmt).getImport();
          imports.put(
              module.getValue(),
              new Extension(eval(content.resolve(module.getValue() + BARA_SKY))));
        }
      }
      updateEnvironmentForConfigFile(eventHandler, content, mainConfigFile, environment, moduleSet);
      StarlarkThread thread = createStarlarkThread(eventHandler, environment, imports);

      // TODO(adonovan): copybara really needs to be calling
      // ValidationException.validateFile(file, thread, false)
      // to catch various static errors prior to execution;
      // this will soon become mandatory. But we can't unconditionally
      // add this statement without risking breakage of users' configs.

      try {
        EvalUtils.exec(file, thread);
      } catch (EvalException ex) {
        eventHandler.handle(Event.error(ex.getLocation(), ex.getMessage()));
        checkCondition(false, "Error loading config file");
      }

      pending.remove(content.path());
      thread.mutability().freeze();
      loaded.put(content.path(), thread);
      return thread;
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

  /**
   * Creates a Starlark thread making the {@code modules} available as global variables. {@code env}
   * specifies the predeclared environment.
   *
   * <p>For the modules that implement {@link OptionsAwareModule}, options are set in the object so
   * that the module can construct objects that require options.
   */
  private StarlarkThread createStarlarkThread(
      EventHandler printHandler, Map<String, Object> environment, Map<String, Extension> imports) {
    return StarlarkThread.builder(Mutability.create("CopybaraModules"))
        .setSemantics(createSemantics())
        .setGlobals(Module.createForBuiltins(environment))
        .setImportedExtensions(imports)
        .setEventHandler(printHandler)
        .build();
  }

  private StarlarkSemantics createSemantics() {
    return StarlarkSemantics.DEFAULT_SEMANTICS
        .toBuilder()
        // TODO(malcon): Remove this one too. Requires user migration.
        .incompatibleRestrictNamedParams(false)
        .build();
  }

  /** Updates the module globals with information about the current loaded config file. */
  // TODO(copybara-team): evaluate the cleaner approach of saving the varying parts in the
  // StarlarkThread.setThreadLocal and leaving the modules alone as nature intended.
  private void updateEnvironmentForConfigFile(
      EventHandler printHandler,
      ConfigFile currentConfigFile,
      ConfigFile mainConfigFile,
      Map<String, Object> environment,
      ModuleSet moduleSet) {
    for (Object module : moduleSet.getModules().values()) {
      // We mutate the module per file loaded. Not ideal but it is the best we can do.
      if (module instanceof LabelsAwareModule) {
        LabelsAwareModule m = (LabelsAwareModule) module;
        m.setConfigFile(mainConfigFile, currentConfigFile);
        m.setDynamicEnvironment(
            () ->
                StarlarkThread.builder(Mutability.create("dynamic_action"))
                    .setSemantics(createSemantics())
                    .setEventHandler(printHandler)
                    .build());
      }
    }
    for (Class<?> module : modules) {
      logger.atInfo().log("Creating variable for %s", module.getName());
      // We mutate the module per file loaded. Not ideal but it is the best we can do.
      if (LabelsAwareModule.class.isAssignableFrom(module)) {
        LabelsAwareModule m = (LabelsAwareModule) environment.get(getModuleName(module));
        m.setConfigFile(mainConfigFile, currentConfigFile);
        m.setDynamicEnvironment(
            () ->
                StarlarkThread.builder(Mutability.create("dynamic_action"))
                    .useDefaultSemantics()
                    .setEventHandler(printHandler)
                    .build());
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
    env.putAll(Starlark.UNIVERSE);
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
        if (SkylarkInterfaceUtils.getSkylarkModule(module) != null) {
          Starlark.addModule(envBuilder, module.getConstructor().newInstance());
        } else if (SkylarkInterfaceUtils.hasSkylarkGlobalLibrary(module)) {
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
    return cls.getAnnotation(SkylarkModule.class).name();
  }

  /**
   * An EventHandler that does the translation to {@link Console} events.
   */
  private static class ConsoleEventHandler implements EventHandler {

    private final Console console;

    private ConsoleEventHandler(Console console) {
      this.console = console;
    }

    @Override
    public void handle(Event event) {
      switch (event.getKind()) {
        case ERROR:
          console.error(messageWithLocation(event));
          break;
        case WARNING:
          console.warn(messageWithLocation(event));
          break;
        case DEBUG:
          console.verbose(messageWithLocation(event));
          break;
        case INFO:
          console.info(messageWithLocation(event));
          break;
        case PROGRESS:
          console.progress(messageWithLocation(event));
          break;
        case STDOUT:
          System.out.println(event);
          break;
        case STDERR:
          System.err.println(event);
          break;
        default:
          System.err.println("Unknown message type: " + event);
      }
    }

    private String messageWithLocation(Event event) {
      String location =
          event.getLocation() == null ? "<no location>" : event.getLocation().toString();
      return location + ": " + event.getMessage();
    }
  }
}
