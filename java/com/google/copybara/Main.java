// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableList;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.config.skylark.ConfigFile;
import com.google.copybara.config.skylark.SimpleConfigFile;
import com.google.copybara.config.skylark.SkylarkParser;
import com.google.copybara.transform.ValidationException;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.console.AnsiConsole;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.LogConsole;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class that invokes {@link Copybara} from command-line.
 *
 * <p>This class should only know about how to validate and parse command-line arguments in order
 * to invoke {@link Copybara}.
 */
public class Main {

  private static final Logger logger = Logger.getLogger(Main.class.getName());
  private static final String COPYBARA_SKYLARK_CONFIG_FILENAME = "copy.bara.sky";

  public static void main(String[] args) {
    new Main().run(args);
  }

  protected void run(String[] args) {
    // We need a console before parsing the args because it could fail with wrong
    // arguments and we need to show the error.
    Console console = getConsole(args);
    // Configure logs location correctly before anything else. We want to write to the
    // correct location in case of any error.
    Copybara copybara = newCopybaraTool(console);

    FileSystem fs = FileSystems.getDefault();
    try {
      copybara.configureLogging(fs);
    } catch (IOException e) {
      handleUnexpectedError(console, ExitCode.ENVIRONMENT_ERROR, e.getMessage(), e);
    }
    console.startupMessage();

    final MainArguments mainArgs = new MainArguments();
    GeneralOptions.Args generalOptionsArgs = new GeneralOptions.Args();
    List<Option> allOptions = new ArrayList<>(copybara.getAllOptions());
    JCommander jcommander = new JCommander(ImmutableList.builder()
        .addAll(allOptions)
        .add(mainArgs)
        .add(generalOptionsArgs)
        .build());
    jcommander.setProgramName("copybara");

    String version = copybara.getVersion();

    try {
      logger.log(Level.INFO, "Copybara version: " + version);
      jcommander.parse(args);
      if (mainArgs.help) {
        System.out.print(usage(jcommander, version));
        return;
      } else if (mainArgs.version) {
        System.out.println(copybara.getBinaryInfo());
        return;
      }
      mainArgs.validateUnnamedArgs();

      GeneralOptions generalOptions = generalOptionsArgs.init(fs, console);
      allOptions.add(generalOptions);
      Options options = new Options(allOptions);

      final Path configPath = fs.getPath(mainArgs.getConfigPath());
      if (generalOptions.isValidate()) {
        ConfigFile skylarkContent = loadConfig(/*skylark=*/ configPath);
        copybara.validate(options, skylarkContent, mainArgs.getWorkflowName());
      } else {
        copybara.run(
            options,
            loadConfig(configPath),
            mainArgs.getWorkflowName(),
            mainArgs.getBaseWorkdir(fs),
            mainArgs.getSourceRef());
      }
    } catch (CommandLineException | ParameterException e) {
      printCauseChain(console, e);
      System.err.print(usage(jcommander, version));
      System.exit(ExitCode.COMMAND_LINE_ERROR.getCode());
    } catch (RepoException e) {
      printCauseChain(console, e);
      System.exit(ExitCode.REPOSITORY_ERROR.getCode());
    } catch (ValidationException e) {
      printCauseChain(console, e);
      System.exit(ExitCode.CONFIGURATION_ERROR.getCode());
    } catch (EnvironmentException | IOException e) {
      handleUnexpectedError(console, ExitCode.ENVIRONMENT_ERROR, e.getMessage(), e);
    } catch (RuntimeException e) {
      // This usually indicates a serious programming error that will require Copybara team
      // intervention. Print stack trace without concern for presentation.
      e.printStackTrace();
      handleUnexpectedError(console, ExitCode.INTERNAL_ERROR,
          "Unexpected error (please file a bug): " + e.getMessage(),
          e);
    }
  }

  private ConfigFile loadConfig(Path configPath)
      throws IOException, CommandLineException, ConfigValidationException {
    String fileName = configPath.getFileName().toString();

    ConfigValidationException.checkCondition(
        fileName.contentEquals(COPYBARA_SKYLARK_CONFIG_FILENAME),
        String.format("Copybara config file filename should be '%s' but it is '%s'.",
            COPYBARA_SKYLARK_CONFIG_FILENAME, configPath.getFileName()));

    // Treat the top level element specially since it is passed thru the command line.
    if (!Files.exists(configPath)) {
      throw new CommandLineException("Configuration file not found: " + configPath);
    }

    return new SimpleConfigFile(configPath.toAbsolutePath());
  }

  /**
   * Returns a new instance of {@link Copybara}.
   */
  protected Copybara newCopybaraTool(Console console) {
    return new Copybara(new SkylarkParser(Copybara.BASIC_MODULES), console);
  }

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

  private void handleUnexpectedError(Console console, ExitCode errorType, String msg, Throwable e) {
    logger.log(Level.SEVERE, msg, e);
    console.error(msg + " (" + e + ")");
    System.exit(errorType.getCode());
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
