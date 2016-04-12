// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.collect.ImmutableList;
import com.google.copybara.config.Config;
import com.google.copybara.config.YamlParser;
import com.google.copybara.git.GitOptions;
import com.google.copybara.localdir.LocalDestinationOptions;
import com.google.copybara.util.ExitCode;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
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
  private static final class Arguments implements Option {

    @Parameter(description = "CONFIG_PATH [SOURCE_REF]")
    List<String> mainArgs = new ArrayList<>();

    @Parameter(names = "--help", help = true, description = "Shows this help text")
    boolean help;
  }

  private static final Logger logger = Logger.getLogger(Main.class.getName());

  protected Iterable<Option> getAllOptions() {
    return ImmutableList.of(new LocalDestinationOptions(), new GitOptions());
  }

  public static void main(String[] args) {
    new Main().run(args);
  }

  protected void run(String[] args) {
    Arguments generalArgs = new Arguments();
    GeneralOptions generalOptions = new GeneralOptions();
    ImmutableList<Option> options =
        ImmutableList.<Option>builder().add(generalOptions).addAll(getAllOptions()).build();
    JCommander jcommander =
        new JCommander(ImmutableList.builder().addAll(options).add(generalArgs).build());
    jcommander.setProgramName("copybara");

    FileSystem fs = FileSystems.getDefault();

    try {
      jcommander.parse(args);
      if (generalArgs.help) {
        System.out.print(usage(jcommander));
      } else if (generalArgs.mainArgs.size() < 1) {
        throw new CommandLineException("Expected at least a configuration file.");
      } else if (generalArgs.mainArgs.size() > 2) {
        throw new CommandLineException("Expect at most two arguments.");
      } else {
        generalOptions.init();
        String configPath = generalArgs.mainArgs.get(0);
        String sourceRef = generalArgs.mainArgs.size() > 1 ? generalArgs.mainArgs.get(1) : null;
        Config config = loadConfig(fs.getPath(configPath), new Options(options));
        Path workdir = generalOptions.getWorkdir();
        new Copybara(workdir).runForSourceRef(config, sourceRef);
      }
    } catch (CommandLineException | ParameterException e) {
      System.err.println("ERROR: " + e.getMessage());
      System.err.print(usage(jcommander));
      System.exit(ExitCode.COMMAND_LINE_ERROR.getCode());
    } catch (RepoException e) {
      System.err.println("ERROR: " + e.getMessage());
      System.exit(ExitCode.REPOSITORY_ERROR.getCode());
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

  private static Config loadConfig(Path path, Options options)
      throws IOException, CommandLineException {
    try {
      return YamlParser.createParser().loadConfig(path, options);
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
