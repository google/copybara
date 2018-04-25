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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.Options;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.syntax.BuildFileAST;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.Environment.Extension;
import com.google.devtools.build.lib.syntax.Environment.GlobalFrame;
import com.google.devtools.build.lib.syntax.Mutability;
import com.google.devtools.build.lib.syntax.ParserInputSource;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.SkylarkSemantics;
import com.google.devtools.build.lib.syntax.SkylarkSignatureProcessor;
import com.google.devtools.build.lib.syntax.StringLiteral;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
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

  private static final Object initializationLock = new Object();

  private static final Set<Class<?>> initializedModules = new HashSet<>();
  public SkylarkParser(Set<Class<?>> modules) {
    this.modules = ImmutableSet.<Class<?>>builder()
        .add(GlobalMigrations.class)
        .addAll(modules).build();

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

  @VisibleForTesting
  public Iterable<Class<?>> getModules() {
    return modules;
  }

  @SuppressWarnings("unchecked")
  public Config loadConfig(ConfigFile<?> config, Options options, Console console)
      throws IOException, ValidationException {
    return getConfigWithTransitiveImports(config, options, console).config;
  }

  private Config loadConfigInternal(ConfigFile content, Options options,
      Supplier<ImmutableMap<String, ? extends ConfigFile<?>>> configFilesSupplier, Console console)
      throws IOException, ValidationException {
    GlobalMigrations globalMigrations;
    Environment env;
    try {
      env = new Evaluator(options, content, configFilesSupplier, console).eval(content);
      globalMigrations = GlobalMigrations.getGlobalMigrations(env);
    } catch (InterruptedException e) {
      // This should not happen since we shouldn't have anything interruptable during loading.
      throw new RuntimeException("Internal error", e);
    }
    return new Config(
        globalMigrations.getMigrations(), content.path(), env.getGlobals().getTransitiveBindings());
  }

  @VisibleForTesting
  public <T> Environment executeSkylark(ConfigFile<T> content, Options options, Console console)
      throws IOException, ValidationException, InterruptedException {
    CapturingConfigFile<T> capturingConfigFile = new CapturingConfigFile<>(content);
    ConfigFilesSupplier<T> configFilesSupplier = new ConfigFilesSupplier<>();

    Environment eval = new Evaluator(options, content, configFilesSupplier, console).eval(content);

    ImmutableMap<String, ConfigFile<T>> allLoadedFiles = capturingConfigFile.getAllLoadedFiles();
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
  public <T> ConfigWithDependencies<T> getConfigWithTransitiveImports(
      ConfigFile<T> config, Options options, Console console)
      throws IOException, ValidationException {
    CapturingConfigFile<T> capturingConfigFile = new CapturingConfigFile<>(config);
    ConfigFilesSupplier<T> configFilesSupplier = new ConfigFilesSupplier<>();

    Config parsedConfig = loadConfigInternal(capturingConfigFile, options, configFilesSupplier,
        console);

    ImmutableMap<String, ConfigFile<T>> allLoadedFiles = capturingConfigFile.getAllLoadedFiles();

    configFilesSupplier.setConfigFiles(allLoadedFiles);

    return new ConfigWithDependencies<>(allLoadedFiles, parsedConfig);
  }

  private static class ConfigFilesSupplier<T>
      implements Supplier<ImmutableMap<String, ? extends ConfigFile<?>>> {

    private ImmutableMap<String, ConfigFile<T>> configFiles = null;

    public void setConfigFiles(ImmutableMap<String, ConfigFile<T>> configFiles) {
      Preconditions.checkState(this.configFiles == null, "Already set");
      this.configFiles = Preconditions.checkNotNull(configFiles);
    }

    @Override
    public ImmutableMap<String, ? extends ConfigFile<?>> get() {
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
  public static class ConfigWithDependencies <T> {
    public final ImmutableMap<String, ConfigFile<T>> files;
    public final Config config;

    private ConfigWithDependencies(ImmutableMap<String, ConfigFile<T>> files, Config config) {
      this.config = config;
      this.files = files;
    }
  }

  /**
   * An utility class for traversing and evaluating the config file dependency graph.
   */
  private final class Evaluator {

    private final LinkedHashSet<String> pending = new LinkedHashSet<>();
    private final Map<String, Environment> loaded = new HashMap<>();
    private final Console console;
    private final ConfigFile<?> mainConfigFile;
    private final EventHandler eventHandler;
    // Globals shared by all the files loaded
    private final GlobalFrame moduleGlobals;

    private Evaluator(Options options, ConfigFile<?> mainConfigFile,
        Supplier<ImmutableMap<String, ? extends ConfigFile<?>>> configFilesSupplier,
        Console console) {
      this.console = Preconditions.checkNotNull(console);
      this.mainConfigFile = Preconditions.checkNotNull(mainConfigFile);
      eventHandler = new ConsoleEventHandler(this.console);
      moduleGlobals = createModuleGlobals(eventHandler, options, configFilesSupplier);
    }

    private Environment eval(ConfigFile<?> content)
        throws IOException, ValidationException, InterruptedException {
      if (pending.contains(content.path())) {
        throw throwCycleError(content.path());
      } else if (loaded.containsKey(content.path())) {
        return loaded.get(content.path());
      }
      pending.add(content.path());

      GlobalFrame globals = createGlobalsForConfigFile(eventHandler, content, mainConfigFile,
          moduleGlobals);

      BuildFileAST buildFileAST = BuildFileAST.parseSkylarkFileWithoutImports(
          new InputSourceForConfigFile(content), eventHandler);

      Map<String, Extension> imports = new HashMap<>();
      for (StringLiteral anImport : buildFileAST.getRawImports()) {
        imports.put(anImport.getValue(),
            new Extension(eval(content.resolve(anImport.getValue() + BARA_SKY))));
      }
      Environment env = createEnvironment(eventHandler, globals, imports);

      checkCondition(buildFileAST.exec(env, eventHandler), "Error loading config file");
      pending.remove(content.path());
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
  private static Environment createEnvironment(EventHandler eventHandler, GlobalFrame globals,
      Map<String, Extension> imports) {
    return Environment.builder(Mutability.create("CopybaraModules"))
        .setSemantics(SkylarkSemantics.DEFAULT_SEMANTICS)
        .setGlobals(globals)
        .setImportedExtensions(imports)
        .setSkylark()
        .setEventHandler(eventHandler)
        .build();
  }

  /**
   * Create a global enviroment to be used per file loaded. As a side effect it mutates the
   * module globals with information about the current file loaded.
   */
  private GlobalFrame createGlobalsForConfigFile(
      EventHandler eventHandler, ConfigFile<?> currentConfigFile, ConfigFile<?> mainConfigFile,
      GlobalFrame moduleGlobals) {
    Environment env = createEnvironment(eventHandler, moduleGlobals,
        ImmutableMap.of());

    for (Class<?> module : modules) {
      logger.atInfo().log("Creating variable for %s", module.getName());
      // We mutate the module per file loaded. Not ideal but it is the best we can do.
      if (LabelsAwareModule.class.isAssignableFrom(module)) {
        ((LabelsAwareModule) getModuleGlobal(env, module))
            .setConfigFile(mainConfigFile, currentConfigFile);
      }
    }
    env.mutability().close();
    return env.getGlobals();
  }

  /**
   * Create a global enviroment for one evaluation (will be shared between all the dependant
   * files loaded).
   */
  private GlobalFrame createModuleGlobals(EventHandler eventHandler, Options options,
      Supplier<ImmutableMap<String, ? extends ConfigFile<?>>> configFilesSupplier) {
    Environment env = createEnvironment(eventHandler, Environment.SKYLARK,
        ImmutableMap.of());

    for (Class<?> module : modules) {
      logger.atInfo().log("Creating variable for %s", module.getName());
      // Create the module object and associate it with the functions
      Runtime.setupModuleGlobals(env, module);
      // Add the options to the module that require them
      if (OptionsAwareModule.class.isAssignableFrom(module)) {
        ((OptionsAwareModule) getModuleGlobal(env, module)).setOptions(options);
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

  private static class InputSourceForConfigFile extends ParserInputSource {

    private final String content;
    private final String path;

    private InputSourceForConfigFile(ConfigFile content) throws IOException {
      this.content = new String(content.content(), UTF_8);
      path = Preconditions.checkNotNull(content.path());
    }

    @Override
    public char[] getContent() {
      return content.toCharArray();
    }

    @Override
    public PathFragment getPath() {
      return PathFragment.create(path);
    }
  }
}
