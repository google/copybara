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

import static com.google.common.base.Preconditions.checkState;
import static com.google.copybara.ConfigValidationException.checkCondition;
import static com.google.copybara.ConfigValidationException.checkNotMissing;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.Authoring;
import com.google.copybara.ConfigValidationException;
import com.google.copybara.Core;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Workflow;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.syntax.BuildFileAST;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.Environment.Frame;
import com.google.devtools.build.lib.syntax.Mutability;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.SkylarkSignatureProcessor;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import com.google.devtools.build.lib.vfs.inmemoryfs.InMemoryFileSystem;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads Copybara configs out of Skylark files.
 */
public class SkylarkParser {

  private static final Logger logger = Logger.getLogger(SkylarkParser.class.getName());
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

  public Config loadConfig(ConfigFile content, Options options)
      throws IOException, ConfigValidationException {
    Core core;
    try {
      Environment env = executeSkylark(content, options);

      core = (Core) env.getGlobals().get(Core.CORE_VAR);
    } catch (InterruptedException e) {
      // This should not happen since we shouldn't have anything interruptable during loading.
      throw new RuntimeException("Internal error", e);
    }
    return createConfig(options, core.getWorkflows(), core.getProjectName());
  }

  @VisibleForTesting
  public Environment executeSkylark(ConfigFile content, Options options)
      throws IOException, ConfigValidationException, InterruptedException {
    Console console = options.get(GeneralOptions.class).console();
    EventHandler eventHandler = new ConsoleEventHandler(console);

    Frame globals = createGlobals(eventHandler, options, content);
    Environment env = createEnvironment(eventHandler, globals);

    BuildFileAST buildFileAST = parseFile(content, eventHandler);
    // TODO(copybara-team): multifile support
    checkState(buildFileAST.getImports().isEmpty(),
        "load() statements are still not supported: %s", buildFileAST.getImports());

    checkCondition(buildFileAST.exec(env, eventHandler), "Error loading config file");
    return env;
  }

  private Config createConfig(Options options, Map<String, Workflow<?>> workflows,
      String projectName)
      throws ConfigValidationException {

    checkCondition(!workflows.isEmpty(), "At least one workflow is required.");

    String workflowName = options.get(WorkflowOptions.class).getWorkflowName();
    Workflow<?> workflow = workflows.get(workflowName);
    checkCondition(workflow != null, String.format(
        "No workflow with '%s' name exists. Valid workflows: %s",
        workflowName, workflows.keySet()));
    //TODO(copybara-team): After skylark migration config should have all the workflows and we should
    // move the validation above outside of the loading.
    return new Config(checkNotMissing(projectName, "project"), workflow);
  }

  private BuildFileAST parseFile(ConfigFile content, EventHandler eventHandler)
      throws IOException {
    InMemoryFileSystem fs = new InMemoryFileSystem();
    // TODO(copybara-team): Use real file name
    com.google.devtools.build.lib.vfs.Path config = fs.getPath("/config.bzl");
    FileSystemUtils.writeContent(config, content.content());

    return BuildFileAST.parseSkylarkFile(config, eventHandler);
  }

  /**
   * Creates a Skylark environment making the {@code modules} available as global variables.
   *
   * <p>For the modules that implement {@link OptionsAwareModule}, options are set in the object so that
   * the module can construct objects that require options.
   */
  static Environment createEnvironment(EventHandler eventHandler, Environment.Frame globals) {
    return Environment.builder(Mutability.create("CopybaraModules"))
        .setGlobals(globals)
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
    Environment env = createEnvironment(eventHandler, Environment.SKYLARK);

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
}
