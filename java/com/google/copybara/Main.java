// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.copybara.config.Config;
import com.google.copybara.config.YamlParser;
import com.google.copybara.util.ExitCode;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class for Copybara
 */
public class Main {

  @Parameters(separators = "=")
  private static final class Arguments {
    @Parameter(description = "CONFIG_PATH SOURCE_REF")
    List<String> mainArgs = new ArrayList<>();

    @Parameter(names = "--help", help = true, description = "Shows this help text")
    boolean help;
  }

  private static final Logger logger = Logger.getLogger(Main.class.getName());

  public static void main(String[] args) {
    Arguments arguments = new Arguments();
    JCommander jcommander = new JCommander(arguments);
    jcommander.setProgramName("copybara");

    FileSystem fs = FileSystems.getDefault();

    try {
      jcommander.parse(args);
      if (arguments.help) {
        System.out.print(usage(jcommander));
      } else if (arguments.mainArgs.size() != 2) {
        throw new CommandLineException("Expect exactly two arguments.");
      } else {
        String configPath = arguments.mainArgs.get(0);
        String sourceRef = arguments.mainArgs.get(1);
        Config config = loadConfig(fs.getPath(configPath));
        new Copybara().runForSourceRef(config, sourceRef);
      }
    } catch (CommandLineException | ParameterException e) {
      System.err.println("ERROR: " + e.getMessage());
      System.err.print(usage(jcommander));
      System.exit(ExitCode.COMMAND_LINE_ERROR.getCode());
    } catch (IOException e) {
      handleUnexpectedError(ExitCode.ENVIRONMENT_ERROR, "ERROR:" + e.getMessage(), e);
    } catch (RuntimeException e) {
      handleUnexpectedError(ExitCode.INTERNAL_ERROR, "Unexpected error: " + e.getMessage(), e);
    }
  }

  private static void handleUnexpectedError(ExitCode errorType, String msg, Throwable e) {
    logger.log(Level.SEVERE, msg, e);
    System.err.println(msg + ":" + e.getMessage());
    System.exit(errorType.getCode());
  }

  private static Config loadConfig(Path path) throws IOException, CommandLineException {
    try {
      String configContent = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      return new YamlParser().parse(configContent);
    } catch (NoSuchFileException e) {
      throw new CommandLineException("Config file '" + path + "' cannot be found.");
    }
  }

  private static String usage(JCommander jcommander) {
    StringBuilder fullUsage = new StringBuilder();
    jcommander.usage(fullUsage);
    fullUsage
        .append("\n")
        .append("Example:\n")
        .append("  copybara myproject.copybara origin/master\n");
    return fullUsage.toString();
  }
}
