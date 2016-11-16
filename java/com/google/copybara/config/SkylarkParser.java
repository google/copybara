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

import static com.google.copybara.ValidationException.checkCondition;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.Config;
import com.google.copybara.Core;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.ValidationException;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.config.base.OptionsAwareModule;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.syntax.BuildFileAST;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.Environment.Extension;
import com.google.devtools.build.lib.syntax.Environment.Frame;
import com.google.devtools.build.lib.syntax.Mutability;
import com.google.devtools.build.lib.syntax.ParserInputSource;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.SkylarkSignatureProcessor;
import com.google.devtools.build.lib.syntax.StringLiteral;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Loads Copybara configs out of Skylark files.
 */
public class SkylarkParser {

  private static final Logger logger = Logger.getLogger(SkylarkParser.class.getName());
  private static final String BARA_SKY = ".bara.sky";
  // For now all the modules are namespaces. We don't use variables except for 'core'.
  private final Iterable<Class<?>> modules;

  public SkylarkParser(Set<Class<?>> modules) {
    this.modules = ImmutableSet.<Class<?>>builder()
        .add(Authoring.Module.class)
        .add(Core.class)
        .addAll(modules).build();

    // Register module functions
    for (Class<?> module : this.modules) {
      // This method should be only called once for VM or at least not concurrently,
      // since it registers functions in an static HashMap.
      try {
        SkylarkSignatureProcessor.configureSkylarkFunctions(module);
      } catch (Exception e) {
        throw new RuntimeException("Cannot register module " + module.getName(), e);
      }
    }
  }

  @VisibleForTesting
  public Iterable<Class<?>> getModules() {
    return modules;
  }

  public Config loadConfig(ConfigFile content, Options options)
      throws IOException, ValidationException {
    Core core;
    try {
      Environment env = executeSkylark(content, options);
      core = (Core) env.getGlobals().get(Core.CORE_VAR);
    } catch (InterruptedException e) {
      // This should not happen since we shouldn't have anything interruptable during loading.
      throw new RuntimeException("Internal error", e);
    }
    return new Config(core.getMigrations());
  }

  @VisibleForTesting
  public Environment executeSkylark(ConfigFile content, Options options)
      throws IOException, ValidationException, InterruptedException {
    return new Evaluator(options).eval(content);
  }

  /**
   * Collect all ConfigFiles retrieved by the parser while loading {code config}.
   *
   * @param config Root file of the configuration.
   * @return A map linking paths to the captured ConfigFiles and the parsed Config
   * @throws IOException If files cannot be read
   * @throws ValidationException If config is invalid, references an invalid file or contains
   *     dependency cycles.
   */
  public <T> ConfigWithDependencies<T> getConfigWithTransitiveImports(
      ConfigFile<T> config, Options options) throws IOException, ValidationException {
    CapturingConfigFile<T> capturingConfigFile = new CapturingConfigFile<T>(config);
    Config parsedConfig = loadConfig(capturingConfigFile, options);
    return new ConfigWithDependencies<T>(capturingConfigFile.getAllLoadedFiles(), parsedConfig);
  };

  public static class ConfigWithDependencies <T> {
    public final ImmutableMap<String, ConfigFile<T>> files;
    public final Config config;

    private ConfigWithDependencies(ImmutableMap<String, ConfigFile<T>> files, Config config) {
      this.config = config;
      this.files = files;
    }
  }

  /**
   * Collect all ConfigFiles retrieved by the parser while loading {code config}.
   *
   * @param config Root file of the configuration.
   * @return A map linking paths to the captured ConfigFiles
   * @throws IOException If files cannot be read
   * @throws ValidationException If config is invalid, references an invalid file or contains
   *     dependency cycles.
   */
  public <T> ImmutableMap<String, ConfigFile<T>> getContentWithTransitiveImports(
      ConfigFile<T> config, Options options) throws IOException, ValidationException {
    return getConfigWithTransitiveImports(config, options).files;
  };

  /**
   * An utility class for traversing and evaluating the config file dependency graph.
   */
  private final class Evaluator {

    private final LinkedHashSet<String> pending = new LinkedHashSet<>();
    private final Map<String, Environment> loaded = new HashMap<>();
    private final Options options;
    private final Console console;
    private final EventHandler eventHandler;

    private Evaluator(Options options) {
      this.options = Preconditions.checkNotNull(options);
      console = options.get(GeneralOptions.class).console();
      eventHandler = new ConsoleEventHandler(console);
    }

    private Environment eval(ConfigFile content)
        throws IOException, ValidationException, InterruptedException {
      if (pending.contains(content.path())) {
        throw throwCycleError(content.path());
      } else if (loaded.containsKey(content.path())) {
        return loaded.get(content.path());
      }
      pending.add(content.path());

      Frame globals = createGlobals(eventHandler, options, content);

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
   * <p>For the modules that implement {@link OptionsAwareModule}, options are set in the object so that
   * the module can construct objects that require options.
   */
  private static Environment createEnvironment(EventHandler eventHandler, Frame globals,
      Map<String, Extension> imports) {
    return Environment.builder(Mutability.create("CopybaraModules"))
        .setGlobals(globals)
        .setImportedExtensions(imports)
        .setSkylark()
        .setEventHandler(eventHandler)
        .build();
  }

  /**
   * Create native global variables from the modules
   *
   * <p>The returned object can be reused for different instances of environments.
   */
  private Environment.Frame createGlobals(
      EventHandler eventHandler, Options options, ConfigFile configFile) {
    Environment env = createEnvironment(eventHandler, Environment.SKYLARK,
        ImmutableMap.<String, Extension>of());

    for (Class<?> module : modules) {
      logger.log(Level.INFO, "Creating variable for " + module.getName());
      // Create the module object and associate it with the functions
      Runtime.registerModuleGlobals(env, module);
      // Add the options to the module that require them
      if (OptionsAwareModule.class.isAssignableFrom(module)) {
        ((OptionsAwareModule) getModuleGlobal(env, module)).setOptions(options);
      }
      if (LabelsAwareModule.class.isAssignableFrom(module)) {
        ((LabelsAwareModule) getModuleGlobal(env, module)).setConfigFile(configFile);
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

      this.content = Preconditions.checkNotNull(new String(content.content(), UTF_8));
      path = Preconditions.checkNotNull(content.path());
    }

    @Override
    public char[] getContent() {
      return content.toCharArray();
    }

    @Override
    public PathFragment getPath() {
      return new PathFragment(path);
    }
  }
}
