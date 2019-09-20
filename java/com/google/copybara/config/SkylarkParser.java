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
import com.google.common.flogger.FluentLogger;
import com.google.copybara.ModuleSet;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.skylarkinterface.SkylarkInterfaceUtils;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.syntax.BuildFileAST;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.Environment.Extension;
import com.google.devtools.build.lib.syntax.Environment.GlobalFrame;
import com.google.devtools.build.lib.syntax.LoadStatement;
import com.google.devtools.build.lib.syntax.Mutability;
import com.google.devtools.build.lib.syntax.ParserInput;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.SkylarkSignatureProcessor;
import com.google.devtools.build.lib.syntax.StarlarkSemantics;
import com.google.devtools.build.lib.syntax.Statement;
import com.google.devtools.build.lib.syntax.StringLiteral;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
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
  private final boolean newStarlarkSemantics;

  private static final Object initializationLock = new Object();

  private static final Set<Class<?>> initializedModules = new HashSet<>();

  public SkylarkParser(Set<Class<?>> staticModules, boolean newStarlarkSemantics) {
    this.modules = ImmutableSet.<Class<?>>builder()
        .add(GlobalMigrations.class)
        .addAll(staticModules).build();
    this.newStarlarkSemantics = newStarlarkSemantics;

    // Skylark initialization is not thread safe and manipulates static fields. While calling
    // this concurrently doesn't happen in the tool, there can be other usages of this that
    // tries to create two SkylarkParsers in parallel.
    // DON'T REMOVE IT
    synchronized (initializationLock) {
      // Register module functions
      for (Class<?> module : this.modules) {
        // configureSkylarkFunctions() should be only called once for each module and java process.
        if (!initializedModules.add(module)) {
          continue;
        }
        try {
          SkylarkSignatureProcessor.configureSkylarkFunctions(module);
        } catch (Exception e) {
          throw new RuntimeException("Cannot register module " + module.getName(), e);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  public Config loadConfig(ConfigFile config, ModuleSet moduleSet, Console console)
      throws IOException, ValidationException {
    return getConfigWithTransitiveImports(config, moduleSet, console).config;
  }

  private Config loadConfigInternal(ConfigFile content, ModuleSet moduleSet,
      Supplier<ImmutableMap<String, ConfigFile>> configFilesSupplier, Console console)
      throws IOException, ValidationException {
    GlobalMigrations globalMigrations;
    Environment env;
    try {
      env = new Evaluator(moduleSet, content, configFilesSupplier, console).eval(content);
      globalMigrations = GlobalMigrations.getGlobalMigrations(env);
    } catch (InterruptedException e) {
      // This should not happen since we shouldn't have anything interruptable during loading.
      throw new RuntimeException("Internal error", e);
    }
    return new Config(
        globalMigrations.getMigrations(), content.path(), env.getGlobals().getTransitiveBindings());
  }

  @VisibleForTesting
  public  Environment executeSkylark(ConfigFile content, ModuleSet moduleSet, Console console)
      throws IOException, ValidationException, InterruptedException {
    CapturingConfigFile capturingConfigFile = new CapturingConfigFile(content);
    ConfigFilesSupplier configFilesSupplier = new ConfigFilesSupplier();

    Environment eval = new Evaluator(moduleSet, content, configFilesSupplier, console)
        .eval(content);

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
    private final Map<String, Environment> loaded = new HashMap<>();
    private final Console console;
    private final ConfigFile mainConfigFile;
    private final EventHandler eventHandler;
    // Globals shared by all the files loaded
    private final GlobalFrame moduleGlobals;
    private final ModuleSet moduleSet;

    private Evaluator(ModuleSet moduleSet, ConfigFile mainConfigFile,
        Supplier<ImmutableMap<String, ConfigFile>> configFilesSupplier,
        Console console) {
      this.console = Preconditions.checkNotNull(console);
      this.mainConfigFile = Preconditions.checkNotNull(mainConfigFile);
      this.moduleSet = Preconditions.checkNotNull(moduleSet);
      eventHandler = new ConsoleEventHandler(this.console);
      moduleGlobals = createModuleGlobals(eventHandler, this.moduleSet, configFilesSupplier);
    }

    private Environment eval(ConfigFile content)
        throws IOException, ValidationException, InterruptedException {
      if (pending.contains(content.path())) {
        throw throwCycleError(content.path());
      }
      if (loaded.containsKey(content.path())) {
        return loaded.get(content.path());
      }
      pending.add(content.path());

      ParserInput input =
          ParserInput.create(content.readContent(), PathFragment.create(content.path()));
      BuildFileAST file = BuildFileAST.parseWithoutImports(input, eventHandler);

      Map<String, Extension> imports = new HashMap<>();
      for (Statement stmt : file.getStatements()) {
        if (stmt instanceof LoadStatement) {
          StringLiteral module = ((LoadStatement) stmt).getImport();
          imports.put(
              module.getValue(),
              new Extension(eval(content.resolve(module.getValue() + BARA_SKY))));
        }
      }
      Environment env = createEnvironment(
          eventHandler,
          createGlobalsForConfigFile(eventHandler, content, mainConfigFile, moduleGlobals,
              moduleSet),
          imports);

      checkCondition(file.exec(env, eventHandler), "Error loading config file");
      pending.remove(content.path());
      env.mutability().freeze();
      loaded.put(content.path(), env);
      return env;
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
   * Creates a Skylark environment making the {@code modules} available as global variables.
   *
   * <p>For the modules that implement {@link OptionsAwareModule}, options are set in the object
   * so that the module can construct objects that require options.
   */
  private Environment createEnvironment(EventHandler eventHandler, GlobalFrame globals,
      Map<String, Extension> imports) {
    return Environment.builder(Mutability.create("CopybaraModules"))
        .setSemantics(createSemantics())
        .setGlobals(globals)
        .setImportedExtensions(imports)
        .setEventHandler(eventHandler)
        .build();
  }

  private StarlarkSemantics createSemantics() {
    if (newStarlarkSemantics) {
      return StarlarkSemantics.DEFAULT_SEMANTICS
          .toBuilder()
          // TODO(malcon): Remove this one too. Requires user migration.
          .incompatibleRestrictNamedParams(false)
          .build();
    }

    // TODO(malcon): To remove once we remove NEW_STARLARK_SEMANTICS
    return StarlarkSemantics.DEFAULT_SEMANTICS
        .toBuilder()
        .incompatibleBzlDisallowLoadAfterStatement(false)
        .incompatibleDisallowDictPlus(false)
        .incompatibleNoTransitiveLoads(false)
        .incompatibleStringJoinRequiresStrings(false)
        .incompatibleRestrictNamedParams(false)
        .build();
  }

  /**
   * Create a global environment to be used per file loaded. As a side effect it mutates the
   * module globals with information about the current file loaded.
   */
  private GlobalFrame createGlobalsForConfigFile(
      EventHandler eventHandler, ConfigFile currentConfigFile, ConfigFile mainConfigFile,
      GlobalFrame moduleGlobals, ModuleSet moduleSet) {
    Environment env = createEnvironment(eventHandler, moduleGlobals, ImmutableMap.of());

    for (Object module : moduleSet.getModules().values()) {
      // We mutate the module per file loaded. Not ideal but it is the best we can do.
      if (module instanceof LabelsAwareModule) {
        ((LabelsAwareModule) module).setConfigFile(mainConfigFile, currentConfigFile);
        ((LabelsAwareModule) module)
            .setDynamicEnvironment(() -> Environment.builder(Mutability.create("dynamic_action"))
                .setSemantics(createSemantics())
                .setEventHandler(eventHandler)
                .build());
      }
    }
    for (Class<?> module : modules) {
      logger.atInfo().log("Creating variable for %s", module.getName());
      // We mutate the module per file loaded. Not ideal but it is the best we can do.
      if (LabelsAwareModule.class.isAssignableFrom(module)) {
        ((LabelsAwareModule) getModuleGlobal(env, module))
            .setConfigFile(mainConfigFile, currentConfigFile);
        ((LabelsAwareModule) getModuleGlobal(env, module))
            .setDynamicEnvironment(() -> Environment.builder(Mutability.create("dynamic_action"))
                    .useDefaultSemantics()
                    .setEventHandler(eventHandler)
                    .build());
      }
    }
    env.mutability().close();
    return env.getGlobals();
  }

  /**
   * Create a global enviroment for one evaluation (will be shared between all the dependant
   * files loaded).
   */
  private GlobalFrame createModuleGlobals(EventHandler eventHandler, ModuleSet moduleSet,
      Supplier<ImmutableMap<String, ConfigFile>> configFilesSupplier) {
    Environment env = createEnvironment(eventHandler, Environment.SKYLARK,
        ImmutableMap.of());

    for (Entry<String, Object> module : moduleSet.getModules().entrySet()) {
      logger.atInfo().log("Creating variable for %s", module.getKey());
      if (module.getValue() instanceof LabelsAwareModule) {
        ((LabelsAwareModule) module.getValue()).setAllConfigResources(configFilesSupplier);
      }
      // Modules shouldn't use the same name
      env.setup(module.getKey(), module.getValue());
    }

    for (Class<?> module : modules) {
      logger.atInfo().log("Creating variable for %s", module.getName());
      // Create the module object and associate it with the functions
      ImmutableMap.Builder<String, Object> envBuilder = ImmutableMap.builder();
      try {
        if (SkylarkInterfaceUtils.getSkylarkModule(module) != null
            || SkylarkInterfaceUtils.hasSkylarkGlobalLibrary(module)) {
          Runtime.setupSkylarkLibrary(envBuilder, module.getConstructor().newInstance());
        }
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
      for (Map.Entry<String, Object> envEntry : envBuilder.build().entrySet()) {
        env.setup(envEntry.getKey(), envEntry.getValue());
      }
      // Add the options to the module that require them
      if (OptionsAwareModule.class.isAssignableFrom(module)) {
        ((OptionsAwareModule) getModuleGlobal(env, module)).setOptions(moduleSet.getOptions());
      }
      if (LabelsAwareModule.class.isAssignableFrom(module)) {
        ((LabelsAwareModule) getModuleGlobal(env, module))
            .setAllConfigResources(configFilesSupplier);
      }
    }
    env.mutability().close();
    return env.getGlobals();
  }

  /**
   * Given an environment, find the corresponding global object representing the module.
   */
  private Object getModuleGlobal(Environment env, Class<?> module) {
    return env.getGlobals().get(module.getAnnotation(SkylarkModule.class).name());
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
      String location = event.getLocation() == null
          ? "<no location>"
          : event.getLocation().print();
      return location + ": " + event.getMessage();
    }
  }
}
