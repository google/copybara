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

package com.google.copybara;

import static com.google.copybara.MainArguments.COPYBARA_SKYLARK_CONFIG_FILENAME;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import com.google.common.base.Preconditions;
import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.PathBasedConfigFile;
import com.google.copybara.config.SkylarkParser;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.console.AnsiConsole;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.LogConsole;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Main class that invokes {@link Copybara} from command-line.
 *
 * <p>This class should only know about how to validate and parse command-line arguments in order
 * to invoke {@link Copybara}.
 */
public class Main {

  private static final String COPYBARA_NAMESPACE = "com.google.copybara";

  private static final Logger logger = Logger.getLogger(Main.class.getName());
  /**
   * Represents the environment, typically {@code System.getEnv()}. Injected to make easier tests.
   *
   * <p>Should not be mutated.
   */
  protected final Map<String, String> environment;

  public Main() {
    this(System.getenv());
  }

  public Main(Map<String, String> environment) {
    this.environment = Preconditions.checkNotNull(environment);
  }

  public static void main(String[] args) {
    System.exit(new Main(System.getenv()).run(args).getCode());
  }

  protected ExitCode run(String[] args) {
    // We need a console before parsing the args because it could fail with wrong
    // arguments and we need to show the error.
    Console console = getConsole(args);
    // Configure logs location correctly before anything else. We want to write to the
    // correct location in case of any error.
    FileSystem fs = FileSystems.getDefault();
    try {
      configureLog(fs);
    } catch (IOException e) {
      handleUnexpectedError(console, e.getMessage(), e);
      return ExitCode.ENVIRONMENT_ERROR;
    }
    console.startupMessage();

    ConfigurationSupplier configurationSupplier = newConfigurationSupplier();
    Copybara copybara = newCopybaraTool(configurationSupplier);

    final MainArguments mainArgs = new MainArguments();
    GeneralOptions.Args generalOptionsArgs = new GeneralOptions.Args();
    List<Option> allOptions = new ArrayList<>(configurationSupplier.newOptions());
    JCommander jcommander = new JCommander(ImmutableList.builder()
        .addAll(allOptions)
        .add(mainArgs)
        .add(generalOptionsArgs)
        .build());
    jcommander.setProgramName("copybara");

    String version = getVersion();
    try {
      logger.log(Level.INFO, "Copybara version: " + version);
      jcommander.parse(args);
      if (mainArgs.help) {
        System.out.print(usage(jcommander, version));
        return ExitCode.SUCCESS;
      } else if (mainArgs.version) {
        System.out.println(getBinaryInfo());
        return ExitCode.SUCCESS;
      }
      mainArgs.parseUnnamedArgs();

      GeneralOptions generalOptions = generalOptionsArgs.init(environment, fs, console);
      allOptions.add(generalOptions);
      Options options = new Options(allOptions);

      initEnvironment(options, mainArgs, jcommander);

      final Path configPath = fs.getPath(mainArgs.getConfigPath());
      ConfigFile configFile = loadConfig(configPath, generalOptions.getConfigRoot());
      switch (mainArgs.getSubcommand()) {
        case VALIDATE:
          return copybara.validate(options, configFile)
              ? ExitCode.SUCCESS : ExitCode.CONFIGURATION_ERROR;
        case MIGRATE:
          copybara.run(
              options,
              configFile,
              mainArgs.getWorkflowName(),
              mainArgs.getBaseWorkdir(fs),
              mainArgs.getSourceRef());
          break;
        case INFO:
          copybara.info(options, configFile, mainArgs.getWorkflowName());
          break;
        default:
          console.error(String.format("Subcommand %s not implemented.", mainArgs.getSubcommand()));
          return ExitCode.COMMAND_LINE_ERROR;
      }
    } catch (CommandLineException | ParameterException e) {
      printCauseChain(console, e);
      System.err.print(usage(jcommander, version));
      return ExitCode.COMMAND_LINE_ERROR;
    } catch (RepoException e) {
      logger.log(Level.SEVERE, "Repository exception", e);
      printCauseChain(console, e);
      return ExitCode.REPOSITORY_ERROR;
    } catch (ValidationException e) {
      printCauseChain(console, e);
      return ExitCode.CONFIGURATION_ERROR;
    } catch (IOException e) {
      handleUnexpectedError(console, e.getMessage(), e);
      return ExitCode.ENVIRONMENT_ERROR;
    } catch (RuntimeException e) {
      // This usually indicates a serious programming error that will require Copybara team
      // intervention. Print stack trace without concern for presentation.
      e.printStackTrace();
      handleUnexpectedError(console, "Unexpected error (please file a bug): " + e.getMessage(), e);
      return ExitCode.INTERNAL_ERROR;
    } finally {
      try {
        shutdown();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        handleUnexpectedError(console, "Execution was interrupted.", e);
      }
    }
    return ExitCode.SUCCESS;
  }

  private ConfigFile loadConfig(Path configPath, @Nullable Path rootCfgPath)
      throws IOException, CommandLineException, ValidationException {
    String fileName = configPath.getFileName().toString();

    ValidationException.checkCondition(
        fileName.contentEquals(COPYBARA_SKYLARK_CONFIG_FILENAME),
        String.format("Copybara config file filename should be '%s' but it is '%s'.",
            COPYBARA_SKYLARK_CONFIG_FILENAME, configPath.getFileName()));

    // Treat the top level element specially since it is passed thru the command line.
    if (!Files.exists(configPath)) {
      throw new CommandLineException("Configuration file not found: " + configPath);
    }
    Path root = rootCfgPath;
    if (root == null) {
      root = findConfigRootHeuristic(configPath.toAbsolutePath());
    }
    return new PathBasedConfigFile(configPath.toAbsolutePath(), root).withContentLogging();
  }

  /**
   * Find the root path for resolving configuration file paths and resources. This method
   * assumes that the .git containing directory is the root path.
   *
   * <p>This could be extended to other kind of source control systems.
   */
  @Nullable
  protected Path findConfigRootHeuristic(Path configPath) {
    Path parent = configPath.getParent();
    while (parent != null) {
      if (Files.isDirectory(parent.resolve(".git"))) {
        return parent;
      }
      parent = parent.getParent();
    }
    return null;
  }


  /**
   * Returns a short String representing the version of the binary
   */
  protected String getVersion() {
    return "Unknown version";
  }

  /**
   * Returns a String (can be multiline) representing all the information about who and when the
   * Copybara was built.
   */
  protected String getBinaryInfo() {
    return "Unknown version";
  }

  /**
   * Returns a new instance of {@link Copybara}.
   */
  protected Copybara newCopybaraTool(ConfigurationSupplier configurationSupplier) {
    return new Copybara(new SkylarkParser(configurationSupplier.getModules()));
  }

  /**
   * Returns a {@link Supplier} of {@link Option}s.
   */
  protected ConfigurationSupplier newConfigurationSupplier() {
    return new ConfigurationSupplier();
  }

  /**
   * Performs cleanup tasks after executing Copybara.
   */
  protected void shutdown() throws InterruptedException {}

  private Console getConsole(String[] args) {
    // If System.console() is not present, we are forced to use LogConsole
    if (System.console() == null) {
      return LogConsole.writeOnlyConsole(System.err);
    }
    if (Arrays.asList(args).contains(GeneralOptions.NOANSI)) {
      // The System.console doesn't detect redirects/pipes, but at least we have
      // jobs covered.
      return LogConsole.readWriteConsole(System.in, System.err);
    }
    return new AnsiConsole(System.in, System.err);
  }

  protected void configureLog(FileSystem fs) throws IOException {
    String baseDir = getBaseExecDir();
    Files.createDirectories(fs.getPath(baseDir));
    if (System.getProperty("java.util.logging.config.file") == null) {
      LogManager.getLogManager().readConfiguration(new ByteArrayInputStream((
          "handlers=java.util.logging.FileHandler\n"
              + ".level=INFO\n"
              + "java.util.logging.FileHandler.level=INFO\n"
              + "java.util.logging.FileHandler.pattern="
              + baseDir + "/copybara-%g.log\n"
              + "java.util.logging.FileHandler.count=10\n"
              + "java.util.logging.FileHandler.formatter=java.util.logging.SimpleFormatter\n"
              + "java.util.logging.SimpleFormatter.format="
              + "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n")
          .getBytes(StandardCharsets.UTF_8)
      ));
    }
  }

  /**
   * Hook to allow setting variables that are not run or validation specific, based on options.
   * Sample use case are remote logging, test harnesses and others. Called after command line
   * options are parsed, but before a file is read or a run started.
   */
  protected void initEnvironment(Options options, MainArguments mainArgs, JCommander jcommander) {
    // intentional no-op
  }

  /**
   * Returns the base directory to be used by Copybara to write execution related files (Like
   * logs).
   */
  private String getBaseExecDir() {
    // In this case we are not using GeneralOptions.getEnvironment() because we still haven't built
    // the options, but it's fine. This is the tool's Main and is also injecting System.getEnv()
    // to the options, so the value is the same.
    String userHome = StandardSystemProperty.USER_HOME.value();

    switch (StandardSystemProperty.OS_NAME.value()) {
      case "Linux":
        String xdgCacheHome = System.getenv("XDG_CACHE_HOME");
        return Strings.isNullOrEmpty(xdgCacheHome)
            ? userHome + "/.cache/" + COPYBARA_NAMESPACE
            : xdgCacheHome + COPYBARA_NAMESPACE;
      case "Mac OS X":
        return userHome + "/Library/Logs/" + COPYBARA_NAMESPACE;
      default:
        return "/var/tmp/" + COPYBARA_NAMESPACE;
    }
  }

  private void printCauseChain(Console console, Throwable e) {
    StringBuilder error = new StringBuilder(e.getMessage()).append("\n");
    Throwable cause = e.getCause();
    while (cause != null) {
      error.append("  CAUSED BY: ").append(cause.getMessage()).append("\n");
      cause = cause.getCause();
    }
    if (console != null) {
      console.error(error.toString());
    } else {
      // When options are incorrect we don't have a console.
      // We should fix this. But not for now.
      System.err.println("ERROR: " + error.toString());
    }
  }

  private void handleUnexpectedError(Console console, String msg, Throwable e) {
    logger.log(Level.SEVERE, msg, e);
    console.error(msg + " (" + e + ")");
  }

  private static String usage(JCommander jcommander, String version) {
    StringBuilder fullUsage = new StringBuilder();
    fullUsage.append("Copybara version: ").append(version).append("\n");
    jcommander.usage(fullUsage);
    fullUsage
        .append("\n")
        .append("Example:\n")
        .append("  copybara ").append(COPYBARA_SKYLARK_CONFIG_FILENAME).append(" origin/master\n");
    return fullUsage.toString();
  }
}
