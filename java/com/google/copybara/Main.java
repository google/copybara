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
import static com.google.copybara.exception.ValidationException.checkCondition;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.MainArguments.CommandWithArgs;
import com.google.copybara.config.ConfigValidator;
import com.google.copybara.config.Migration;
import com.google.copybara.config.PathBasedConfigFile;
import com.google.copybara.exception.CommandLineException;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.jcommander.DurationConverter;
import com.google.copybara.onboard.OnboardCmd;
import com.google.copybara.profiler.ConsoleProfilerListener;
import com.google.copybara.profiler.Listener;
import com.google.copybara.profiler.LogProfilerListener;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.console.AnsiConsole;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.FileConsole;
import com.google.copybara.util.console.LogConsole;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.LogManager;
import javax.annotation.Nullable;
import net.starlark.java.eval.EvalException;

/**
 * Main class that invokes Copybara from command-line.
 *
 * <p>This class should only know about how to validate and parse command-line arguments in order to
 * invoke Copybara.
 */
public class Main {

  private static final String COPYBARA_NAMESPACE = "com.google.copybara";

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  /**
   * Represents the environment, typically {@code System.getEnv()}. Injected to make easier tests.
   *
   * <p>Should not be mutated.
   */
  protected final ImmutableMap<String, String> environment;
  protected Profiler profiler;
  protected JCommander jCommander;

  private Console console;

  public Main() {
    this(System.getenv());
  }

  public Main(Map<String, String> environment) {
    this.environment = Preconditions.checkNotNull(ImmutableMap.copyOf(environment));
  }

  public static void main(String[] args) {
    System.exit(new Main(System.getenv()).run(args).getCode());
  }

  protected final ExitCode run(String[] args) {
    // We need a console before parsing the args because it could fail with wrong
    // arguments and we need to show the error.
    this.console = getConsole(args);
    // Configure logs location correctly before anything else. We want to write to the
    // correct location in case of any error.
    FileSystem fs = FileSystems.getDefault();
    try {
      configureLog(fs, args);
    } catch (IOException e) {
      handleUnexpectedError(console, e.getMessage(), args, e);
      return ExitCode.ENVIRONMENT_ERROR;
    }
    // This is useful when debugging user issues
    logger.atInfo().log("Running: %s", Joiner.on(' ').join(args));

    console.startupMessage(getVersion());

    CommandResult result = runInternal(args, console, fs);
    try {
      shutdown(result);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      handleUnexpectedError(console, "Execution was interrupted.", args, e);
    }
    return result.exitCode;
  }

  /** Helper to find out about verbose output before JCommander has been initialized .*/
  protected static boolean isVerbose(String[] args) {
    return Arrays.stream(args).anyMatch(s -> s.equals("-v") || s.equals("--verbose"));
  }

  /** Helper to find out if logging is enabled before JCommander has been initialized . */
  protected static boolean isEnableLogging(String[] args) {
    return !Arrays.asList(args).contains("--nologging");
  }

  /**
   * Finds a flag value before JCommander is initialized. Returns {@code Optional.empty()} if the
   * flag is not present.
   */
  protected static Optional<String> findFlagValue(String[] args, String flagName) {
    for (int index = 0; index < args.length - 1; index++) {
      if (args[index].equals(flagName)) {
        if (!args[index + 1].startsWith("-")) {
          return Optional.of(args[index + 1]);
        }
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  /** A wrapper of the exit code and the command executed */
  protected static class CommandResult {

    private final ExitCode exitCode;
    @Nullable private final CommandEnv commandEnv;
    @Nullable private final CopybaraCmd command;

    CommandResult(
        ExitCode exitCode, @Nullable CopybaraCmd command, @Nullable CommandEnv commandEnv) {
       this.exitCode = Preconditions.checkNotNull(exitCode);
       this.command = command;
       this.commandEnv = commandEnv;
    }

    public ExitCode getExitCode() {
      return exitCode;
    }

    /**
     * The command environment passed to the command. Can be null for executions that failed before
     * executing the command, like bad options.
     */
    @Nullable
    public CommandEnv getCommandEnv() {
      return commandEnv;
    }

    /**
     * The command that was executed. Can be null for executions that failed before executing the
     * command, like bad options.
     */
    @Nullable
    public CopybaraCmd getCommand() {
      return command;
    }
  }
  /**
   * Runs the command and returns the {@link ExitCode}.
   *
   * <p>This method is also responsible for the exception handling/logging.
   */
  private CommandResult runInternal(String[] args, Console console, FileSystem fs) {
    CommandEnv commandEnv = null;
    CopybaraCmd subcommand = null;

    try {
      ModuleSet moduleSet = newModuleSet(environment, fs, console);

      final MainArguments mainArgs = new MainArguments();
      Options options = moduleSet.getOptions();
      jCommander = new JCommander(ImmutableList.builder()
          .addAll(options.getAll())
          .add(mainArgs)
          .build());
      jCommander.setProgramName("copybara");

      String version = getVersion();
      logger.atInfo().log("Copybara version: %s", version);
      jCommander.parse(args);


      ConfigLoaderProvider configLoaderProvider = newConfigLoaderProvider(moduleSet);

      ImmutableMap<String, CopybaraCmd> commands =
          Maps.uniqueIndex(getCommands(moduleSet, configLoaderProvider, jCommander),
              CopybaraCmd::name);
      // Tell jcommander about the commands; we don't actually use the feature, this is solely for
      // generating the usage info.
      for (Map.Entry<String, CopybaraCmd> cmd : commands.entrySet()) {
        jCommander.addCommand(cmd.getKey(), cmd.getValue());
      }
      CommandWithArgs cmdToRun = mainArgs.parseCommand(commands, commands.get("migrate"));
      subcommand = cmdToRun.getSubcommand();

      initEnvironment(options, cmdToRun.getSubcommand(), ImmutableList.copyOf(args));

      GeneralOptions generalOptions = options.get(GeneralOptions.class);
      Path baseWorkdir = mainArgs.getBaseWorkdir(generalOptions, generalOptions.getFileSystem());

      commandEnv = new CommandEnv(baseWorkdir, options, cmdToRun.getArgs());
      generalOptions.console().progressFmt("Running %s", subcommand.name());

      // TODO(malcon): Remove this after 2019-09-15, once tested that temp features work.
      logger.atInfo().log("Temporary features test: %s",
          options.get(GeneralOptions.class).isTemporaryFeature("TEST_TEMP_FEATURES", true));

      ExitCode exitCode = subcommand.run(commandEnv);
      return new CommandResult(exitCode, subcommand, commandEnv);

    } catch (CommandLineException | ParameterException e) {
      printCauseChain(Level.WARNING, console, args, e);
      console.error("Try 'copybara help'.");
      return new CommandResult(ExitCode.COMMAND_LINE_ERROR, subcommand, commandEnv);
    } catch (RepoException e) {
      printCauseChain(Level.SEVERE, console, args, e);
      // TODO(malcon): Expose interrupted exception from WorkflowMode to Main so that we don't
      // have to do this hack.
      if (e.getCause() instanceof InterruptedException) {
        return new CommandResult(ExitCode.INTERRUPTED, subcommand, commandEnv);
      }
      return new CommandResult(ExitCode.REPOSITORY_ERROR, subcommand, commandEnv);
    } catch (EmptyChangeException e) {
      // This is not necessarily an error. Maybe the tool was run previously and there are no new
      // changes to import.
      console.warn(e.getMessage());
      return new CommandResult(ExitCode.NO_OP, subcommand, commandEnv);
    } catch (ValidationException e) {
      printCauseChain(Level.WARNING, console, args, e);
      return new CommandResult(ExitCode.CONFIGURATION_ERROR,
          subcommand, commandEnv);
    } catch (IOException e) {
      handleUnexpectedError(console, e.getMessage(), args, e);
      return new CommandResult(ExitCode.ENVIRONMENT_ERROR, subcommand, commandEnv);
    } catch (RuntimeException e) {
      // This usually indicates a serious programming error that will require Copybara team
      // intervention. Print stack trace without concern for presentation.
      e.printStackTrace();
      handleUnexpectedError(console,
          "Unexpected error (please file a bug against copybara): " + e.getMessage(), args, e);
      return new CommandResult(ExitCode.INTERNAL_ERROR, subcommand, commandEnv);
    }
  }

  public ImmutableSet<CopybaraCmd> getCommands(ModuleSet moduleSet,
      ConfigLoaderProvider configLoaderProvider, JCommander jcommander)
      throws CommandLineException {
    ConfigValidator validator = getConfigValidator(moduleSet.getOptions());
    Consumer<Migration> consumer = getMigrationRanConsumer();
    return ImmutableSet.of(
        new MigrateCmd(validator, consumer, configLoaderProvider, moduleSet),
        new InfoCmd(configLoaderProvider, newInfoContextProvider()),
        new ValidateCmd(validator, consumer, configLoaderProvider),
        new HelpCmd(jcommander),
        new OnboardCmd(),
        new VersionCmd());
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

  protected Consumer<Migration> getMigrationRanConsumer() {
    return migration -> {};
  }

  protected ConfigValidator getConfigValidator(Options options) throws CommandLineException {
    return new ConfigValidator() {};
  }

  /** Returns a new module set. */
  protected ModuleSet newModuleSet(ImmutableMap<String, String> environment,
      FileSystem fs, Console console) {
    return new ModuleSupplier(environment, fs, console).create();
  }

  protected ConfigLoaderProvider newConfigLoaderProvider(ModuleSet moduleSet) {
    GeneralOptions generalOptions = moduleSet.getOptions().get(GeneralOptions.class);
    return (configPath, sourceRef) -> new ConfigLoader(moduleSet,
        createConfigFileWithHeuristic(validateLocalConfig(generalOptions, configPath),
            generalOptions.getConfigRoot()), generalOptions.getStarlarkMode());
  }

  protected ContextProvider newInfoContextProvider() {
    return (config, configFileArgs, configLoaderProvider, console) ->
        ImmutableMap.of("copybara_config", config.getLocation());
  }

  /**
   * Validate that the passed config file is correct (exists, follows the correct format, parent
   * if passed is a real parent, etc.).
   *
   * <p>Returns the absolute {@link Path} of the config file.
   */
  protected Path validateLocalConfig(GeneralOptions generalOptions, String configLocation)
      throws ValidationException {
    Path configPath = generalOptions.getFileSystem().getPath(configLocation).normalize();
    String fileName = configPath.getFileName().toString();
    checkCondition(
        fileName.contentEquals(COPYBARA_SKYLARK_CONFIG_FILENAME),
        "Copybara config file filename should be '%s' but it is '%s'.",
            COPYBARA_SKYLARK_CONFIG_FILENAME, configPath.getFileName());

    // Treat the top level element specially since it is passed thru the command line.
    if (!Files.exists(configPath)) {
      throw new CommandLineException("Configuration file not found: " + configPath);
    }
    return configPath.toAbsolutePath();
  }

  /**
   * Find the root path for resolving configuration file paths and resources. This method assumes
   * that the .git containing directory is the root path.
   *
   * <p>This could be extended to other kind of source control systems.
   */
  protected PathBasedConfigFile createConfigFileWithHeuristic(
      Path configPath, @Nullable Path commandLineRoot) {
    if (commandLineRoot != null) {
      return new PathBasedConfigFile(configPath, commandLineRoot, /*identifierPrefix=*/ null);
    }
    Path parent = configPath.getParent();
    while (parent != null) {
      if (Files.isDirectory(parent.resolve(".git"))) {
        return new PathBasedConfigFile(configPath, parent, /*identifierPrefix=*/ null);
      }
      parent = parent.getParent();
    }
    return new PathBasedConfigFile(configPath, /*rootPath=*/ null, /*identifierPrefix=*/ null);
  }

  protected Console getConsole(String[] args) {
    boolean verbose = isVerbose(args);
    // If System.console() is not present, we are forced to use LogConsole
    Console console;
    if (System.console() == null) {
      console = LogConsole.writeOnlyConsole(System.err, verbose);
    } else if (Arrays.asList(args).contains(GeneralOptions.NOANSI)) {
      // The System.console doesn't detect redirects/pipes, but at least we have
      // jobs covered.
      console = LogConsole.readWriteConsole(System.in, System.err, verbose);
    } else {
      console = new AnsiConsole(System.in, System.err, verbose);
    }
    Optional<String> maybeConsoleFilePath = findFlagValue(args, GeneralOptions.CONSOLE_FILE_PATH);
    if (!maybeConsoleFilePath.isPresent()) {
      return console;
    }
    Path consoleFilePath = Paths.get(maybeConsoleFilePath.get());
    try {
      Files.createDirectories(consoleFilePath.getParent());
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          "Could not create parent directories to file: %s. Redirecting will be disabled.",
          consoleFilePath);
      return console;
    }
    return new FileConsole(console, consoleFilePath, getConsoleFlushRate(args));
  }

  /**
   * Returns the console flush rate from the flag, if present and valid, or 0 (no flush) otherwise.
   */
  protected Duration getConsoleFlushRate(String[] args) {
    return findFlagValue(args, GeneralOptions.CONSOLE_FILE_FLUSH_INTERVAL)
        .map(e -> new DurationConverter().convert(e))
        .orElse(GeneralOptions.DEFAULT_CONSOLE_FILE_FLUSH_INTERVAL);
  }

  protected void configureLog(FileSystem fs, String[] args) throws IOException {
    String baseDir = getBaseExecDir();
    Files.createDirectories(fs.getPath(baseDir));
    if (System.getProperty("java.util.logging.config.file") == null) {
      logger.atInfo().log("Setting up LogManager");
      String level = isEnableLogging(args) ? "INFO" : "OFF";
      LogManager.getLogManager().readConfiguration(new ByteArrayInputStream((
          "handlers=java.util.logging.FileHandler\n"
              + ".level=INFO\n"
              + "java.util.logging.FileHandler.level=" + level +"\n"
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
  protected void initEnvironment(Options options, CopybaraCmd copybaraCmd,
      ImmutableList<String> rawArgs)
      throws ValidationException, IOException, RepoException {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    profiler = generalOptions.profiler();
    ImmutableList.Builder<Listener> profilerListeners = ImmutableList.builder();
    profilerListeners.add(
        new LogProfilerListener(), new ConsoleProfilerListener(generalOptions.console()));
    profiler.init(profilerListeners.build());
    cleanupOutputDir(generalOptions);
  }

  protected void cleanupOutputDir(GeneralOptions generalOptions)
      throws RepoException, IOException, ValidationException {
    generalOptions.ioRepoTask(
        "clean_outputdir",
        () -> {
          if (generalOptions.isNoCleanup()) {
            return null;
          }
          generalOptions.console().progress("Cleaning output directory");
          generalOptions.getDirFactory().cleanupTempDirs();
          // Only for profiling purposes, no need to use the console
          logger.atInfo().log(
              "Cleaned output directory:%s", generalOptions.getDirFactory().getTmpRoot());
          return null;
        });
  }
  /**
   * Performs cleanup tasks after executing Copybara.
   * @param result
   */
  protected void shutdown(CommandResult result) throws InterruptedException {
    // Before profiler.stop()
    if (console != null) {
      console.close();
    }
    if (profiler != null) {
      profiler.stop();
    }
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

  private void printCauseChain(Level level, Console console, String[] args, Throwable e) {
    StringBuilder error = new StringBuilder(e.getMessage()).append("\n");
    Throwable cause = e.getCause();
    while (cause != null) {
      error.append("  CAUSED BY: ").append(printException(cause)).append("\n");
      cause = cause.getCause();
    }
    console.error(error.toString());
    logger.at(level).withCause(e).log("%s", formatLogError(e.getMessage(), args));
  }

  private String printException(Throwable t) {
    if (t instanceof EvalException) {
      return ((EvalException) t).getMessageWithStack();
    }
    return t.getMessage();
  }

  private void handleUnexpectedError(Console console, String msg, String[] args, Throwable e) {
    logger.atSevere().withCause(e).log("%s", formatLogError(msg, args));
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

  private static String formatLogError(String message, String[] args) {
    return String.format("%s (command args: %s)", message, Arrays.toString(args));
  }

  /** Prints the Copybara version */
  @Parameters(separators = "=", commandDescription = "Shows the version of Copybara.")
  private class VersionCmd implements CopybaraCmd {

    @Override
    public ExitCode run(CommandEnv commandEnv)
        throws ValidationException, IOException, RepoException {
      commandEnv.getOptions().get(GeneralOptions.class).console().info(getBinaryInfo());
      return ExitCode.SUCCESS;
    }

    @Override
    public String name() {
      return "version";
    }
  }

  /**
   * Prints the help message
   * TODO(malcon): Implement help per command
   */
  @Parameters(separators = "=", commandDescription = "Shows the help.")
  private class HelpCmd implements CopybaraCmd {

    private final JCommander jCommander;

    HelpCmd(JCommander jCommander) {
      this.jCommander = Preconditions.checkNotNull(jCommander);
    }

    @Override
    public ExitCode run(CommandEnv commandEnv)
        throws ValidationException, IOException, RepoException {
      String version = getVersion();
      commandEnv.getOptions().get(GeneralOptions.class).console().info(usage(jCommander, version));
      return ExitCode.SUCCESS;
    }

    @Override
    public String name() {
      return "help";
    }
  }
}
