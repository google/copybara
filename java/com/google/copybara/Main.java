// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigParserException;
import com.google.copybara.config.YamlParser;
import com.google.copybara.git.GerritDestination;
import com.google.copybara.git.GerritOptions;
import com.google.copybara.git.GitDestination;
import com.google.copybara.git.GitOptions;
import com.google.copybara.git.GitOrigin;
import com.google.copybara.localdir.FolderDestination;
import com.google.copybara.localdir.LocalDestinationOptions;
import com.google.copybara.transform.DeletePath;
import com.google.copybara.transform.Replace;
import com.google.copybara.util.ExitCode;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

import org.yaml.snakeyaml.TypeDescription;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Main class for Copybara
 */
public class Main {

  private static final String COPYBARA_NAMESPACE = "com.google.copybara";

  @Parameters(separators = "=")
  private static final class Arguments {

    @Parameter(description = "CONFIG_PATH [SOURCE_REF]")
    List<String> unnamed = new ArrayList<>();

    @Parameter(names = "--help", help = true, description = "Shows this help text")
    boolean help;
  }

  private static final Logger logger = Logger.getLogger(Main.class.getName());

  protected List<Option> getAllOptions() {
    return ImmutableList.of(new LocalDestinationOptions(), new GitOptions(), new GerritOptions());
  }

  protected Iterable<TypeDescription> getYamlTypeDescriptions() {
    return ImmutableList.of(
        // Transformations
        new TypeDescription(DeletePath.Yaml.class, "!DeletePath"),
        new TypeDescription(Replace.Yaml.class, "!Replace"),
        // Origins
        new TypeDescription(GitOrigin.Yaml.class, "!GitOrigin"),
        // Destinations
        new TypeDescription(GerritDestination.Yaml.class, "!GerritDestination"),
        new TypeDescription(GitDestination.Yaml.class, "!GitDestination"),
        new TypeDescription(FolderDestination.Yaml.class, "!FolderDestination"));
  }

  public static void main(String[] args) {
    new Main().run(args);
  }

  protected void run(String[] args) {
    Arguments mainArgs = new Arguments();
    GeneralOptions.Args generalOptionsArgs = new GeneralOptions.Args();
    List<Option> options = new ArrayList<>(getAllOptions());
    JCommander jcommander = new JCommander(ImmutableList.builder()
        .addAll(options)
        .add(mainArgs)
        .add(generalOptionsArgs)
        .build());
    jcommander.setProgramName("copybara");

    FileSystem fs = FileSystems.getDefault();

    try {
      configureLog(fs);
      jcommander.parse(args);
      if (mainArgs.help) {
        System.out.print(usage(jcommander));
      } else if (mainArgs.unnamed.size() < 1) {
        throw new CommandLineException("Expected at least a configuration file.");
      } else if (mainArgs.unnamed.size() > 2) {
        throw new CommandLineException("Expect at most two arguments.");
      } else {
        GeneralOptions generalOptions = generalOptionsArgs.init(FileSystems.getDefault());
        options.add(generalOptions);
        String configPath = mainArgs.unnamed.get(0);
        String sourceRef = mainArgs.unnamed.size() > 1 ? mainArgs.unnamed.get(1) : null;
        Config config = loadConfig(fs.getPath(configPath), new Options(options));
        Path workdir = generalOptions.getWorkdir();
        new Copybara(workdir).runForSourceRef(config, sourceRef);
      }
    } catch (CommandLineException | ParameterException e) {
      printCauseChain(e);
      System.err.print(usage(jcommander));
      System.exit(ExitCode.COMMAND_LINE_ERROR.getCode());
    } catch (RepoException | ConfigParserException e) {
      printCauseChain(e);
      System.exit(ExitCode.REPOSITORY_ERROR.getCode());
    } catch (IOException e) {
      handleUnexpectedError(ExitCode.ENVIRONMENT_ERROR, "ERROR:" + e.getMessage(), e);
    } catch (RuntimeException e) {
      handleUnexpectedError(ExitCode.INTERNAL_ERROR, "Unexpected error: " + e.getMessage(), e);
    }
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

  private static void printCauseChain(Throwable e) {
    String prefix = "ERROR: ";
    do {
      System.err.println(prefix + e.getMessage());
      prefix = "  CAUSED BY: ";
      e = e.getCause();
    } while (e != null);
  }

  private static void handleUnexpectedError(ExitCode errorType, String msg, Throwable e) {
    logger.log(Level.SEVERE, msg, e);
    System.err.println(msg + ":" + e.getMessage());
    System.exit(errorType.getCode());
  }

  private Config loadConfig(Path path, Options options)
      throws IOException, CommandLineException, ConfigParserException {
    try {
      return new YamlParser(getYamlTypeDescriptions()).loadConfig(path, options);
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
