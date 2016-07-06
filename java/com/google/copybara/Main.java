// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.config.YamlParser;
import com.google.copybara.folder.FolderDestination;
import com.google.copybara.folder.FolderDestinationOptions;
import com.google.copybara.git.GerritDestination;
import com.google.copybara.git.GerritOptions;
import com.google.copybara.git.GitDestination;
import com.google.copybara.git.GitOptions;
import com.google.copybara.git.GitOrigin;
import com.google.copybara.transform.MoveFiles;
import com.google.copybara.transform.Replace;
import com.google.copybara.transform.Reverse;
import com.google.copybara.transform.Sequence;
import com.google.copybara.transform.ValidationException;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.console.AnsiConsole;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.LogConsole;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import org.yaml.snakeyaml.TypeDescription;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Main class for Copybara
 */
public class Main {

  private static final String COPYBARA_NAMESPACE = "com.google.copybara";

  private static final Logger logger = Logger.getLogger(Main.class.getName());
  private static final String COPYBARA_CONFIG_FILENAME = "copybara.yaml";

  protected List<Option> getAllOptions() {
    return ImmutableList.of(
        new FolderDestinationOptions(),
        new GitOptions(),
        new GerritOptions(),
        new WorkflowOptions());
  }

  protected Iterable<TypeDescription> getYamlTypeDescriptions() {
    return ImmutableList.of(
        // Transformations
        YamlParser.docTypeDescription(Replace.Yaml.class),
        YamlParser.docTypeDescription(Reverse.Yaml.class),
        YamlParser.docTypeDescription(MoveFiles.Yaml.class),
        YamlParser.docTypeDescription(Sequence.Yaml.class),
        // Origins
        YamlParser.docTypeDescription(GitOrigin.Yaml.class),
        // Destinations
        YamlParser.docTypeDescription(GerritDestination.Yaml.class),
        YamlParser.docTypeDescription(GitDestination.Yaml.class),
        YamlParser.docTypeDescription(FolderDestination.Yaml.class));
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

  public static void main(String[] args) {
    new Main().run(args);
  }

  protected void run(String[] args) {
    MainArguments mainArgs = new MainArguments();
    GeneralOptions.Args generalOptionsArgs = new GeneralOptions.Args();
    List<Option> allOptions = new ArrayList<>(getAllOptions());
    JCommander jcommander = new JCommander(ImmutableList.builder()
        .addAll(allOptions)
        .add(mainArgs)
        .add(generalOptionsArgs)
        .build());
    jcommander.setProgramName("copybara");

    FileSystem fs = FileSystems.getDefault();

    // We need a console before parsing the args because it could fail with wrong
    // arguments and we need to show the error.
    Console console = getConsole(args);
    String version = getVersion();
    try {
      configureLog(fs);
      logger.log(Level.INFO, "Copybara version: " + version);
      jcommander.parse(args);
      Path baseWorkdir = mainArgs.getBaseWorkdir(fs);
      GeneralOptions generalOptions = generalOptionsArgs.init(fs, console);

      if (mainArgs.help) {
        System.out.print(usage(jcommander, version));
        return;
      } else if (mainArgs.version) {
        System.out.println(getBinaryInfo());
        return;
      }
      mainArgs.validateUnnamedArgs();
      allOptions.add(generalOptions);
      Options options = new Options(allOptions);
      options.get(WorkflowOptions.class).setWorkflowName(mainArgs.getWorkflowName());
      Config config = loadConfig(fs.getPath(mainArgs.getConfigPath()), options);

      config.getActiveWorkflow().run(baseWorkdir, mainArgs.getSourceRef());
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
      handleUnexpectedError(console, ExitCode.INTERNAL_ERROR, "Unexpected error: " + e.getMessage(),
          e);
    }
  }

  protected Console getConsole(String[] args) {
    // The System.console doesn't detect redirects/pipes, but at least we have
    // jobs covered.
    return Arrays.asList(args).contains(GeneralOptions.NOANSI) || System.console() == null
        ? new LogConsole(System.err)
        : new AnsiConsole(System.err);
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
          .getBytes()
      ));
    }

  }

  /**
   * Returns the base directory to be used by Copybara to write execution related files (Like
   * logs).
   */
  protected String getBaseExecDir() {
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

  private void handleUnexpectedError(Console console, ExitCode errorType, String msg, Throwable e) {
    logger.log(Level.SEVERE, msg, e);
    console.error(msg + " (" + e + ")");
    System.exit(errorType.getCode());
  }

  private Config loadConfig(Path path, Options options)
      throws IOException, CommandLineException, ConfigValidationException, EnvironmentException {
    if (!path.getFileName().toString().equals(COPYBARA_CONFIG_FILENAME)) {
      throw new CommandLineException(
          String.format("Copybara config file filename should be '%s' but is '%s'.",
              COPYBARA_CONFIG_FILENAME, path.getFileName()));
    }
    try {
      return new YamlParser(getYamlTypeDescriptions()).loadConfig(path, options);
    } catch (NoSuchFileException e) {
      throw new CommandLineException("Config file '" + path + "' cannot be found.");
    }
  }

  private static String usage(JCommander jcommander, String version) {
    StringBuilder fullUsage = new StringBuilder();
    fullUsage.append("Copybara version: ").append(version).append("\n");
    jcommander.usage(fullUsage);
    fullUsage
        .append("\n")
        .append("Example:\n")
        .append("  copybara ").append(COPYBARA_CONFIG_FILENAME).append(" origin/master\n");
    return fullUsage.toString();
  }
}
