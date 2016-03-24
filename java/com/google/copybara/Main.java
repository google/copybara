// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.copybara.config.Config;
import com.google.copybara.config.YamlParser;
import com.google.copybara.util.ExitCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class for Copybara
 */
public class Main {

  private static final Logger logger = Logger.getLogger(Main.class.getName());

  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.println("ERROR: Insufficient parameters\n" + usage());
      System.exit(ExitCode.COMMAND_LINE_ERROR.getCode());
    }
    String configPath = args[0];
    String repoRef = args[1];

    FileSystem fs = FileSystems.getDefault();

    try {
      Config config = loadConfig(fs.getPath(configPath));
      new Copybara().runForRef(config, repoRef);
    } catch (CommandLineException e) {
      System.err.println("ERROR: " + e.getMessage());
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

  private static String usage() {
    return "Usage:\n" +
        "  copybara <copybara_config> <repo_reference>\n" +
        "\n" +
        "Example:\n" +
        "  copybara myproject.copybara origin/master";
  }
}
