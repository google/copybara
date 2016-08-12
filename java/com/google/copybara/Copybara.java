// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.base.Preconditions;
import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.config.skylark.ConfigFile;
import com.google.copybara.config.skylark.SkylarkParser;
import com.google.copybara.folder.FolderDestination;
import com.google.copybara.folder.FolderDestinationOptions;
import com.google.copybara.git.GerritOptions;
import com.google.copybara.git.GitOptions;
import com.google.copybara.transform.ValidationException;
import com.google.copybara.util.console.Console;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.LogManager;
import javax.annotation.Nullable;

/**
 * Copybara tool main class.
 *
 * <p>Executes Copybara workflows independently from the environment that they are invoked from
 * (command-line, service).
 */
public class Copybara {

  protected static final String COPYBARA_NAMESPACE = "com.google.copybara";
  protected static final ImmutableSet<Class<?>> BASIC_MODULES = ImmutableSet.<Class<?>>of(
      FolderDestination.Module.class);

  private final SkylarkParser skylarkParser;
  private final Console console;

  public Copybara(SkylarkParser skylarkParser, Console console) {
    this.skylarkParser = Preconditions.checkNotNull(skylarkParser);
    this.console = Preconditions.checkNotNull(console);
  }

  protected List<Option> getAllOptions() {
    return ImmutableList.of(
        new FolderDestinationOptions(),
        new GitOptions(),
        new GerritOptions(),
        new WorkflowOptions());
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

  public void run(Options options, ConfigFile configContents, String workflowName,
      Path baseWorkdir, @Nullable String sourceRef)
      throws RepoException, ValidationException, IOException, EnvironmentException {
    options.get(WorkflowOptions.class).setWorkflowName(workflowName);
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    Preconditions.checkArgument(!generalOptions.isValidate(), "Call validate() instead");
    Config config = skylarkParser.loadConfig(configContents, options);
    console.progress("Validating configuration");

    validate(options, configContents, workflowName);

    config.getActiveWorkflow().run(baseWorkdir, sourceRef);
  }

  public void validate(Options options, ConfigFile configContent, String workflowName)
      throws RepoException, ValidationException, IOException, EnvironmentException {
    options.get(WorkflowOptions.class).setWorkflowName(workflowName);
    Config config = skylarkParser.loadConfig(configContent, options);
    List<String> validationMessages = validateConfig(config);
    if (!validationMessages.isEmpty()) {
      console.error("Configuration is invalid:");
      for (String validationMessage : validationMessages) {
        console.error(validationMessage);
      }
      throw new ConfigValidationException(
          "Error validating configuration: Configuration is invalid.");
    }
  }

  private void runConfigValidation(Config config) throws ConfigValidationException {
    List<String> validationMessages = validateConfig(config);
    if (!validationMessages.isEmpty()) {
      console.error("Configuration is invalid:");
      for (String validationMessage : validationMessages) {
        console.error(validationMessage);
      }
      throw new ConfigValidationException(
          "Error validating configuration: Configuration is invalid.");
    }
  }

  /**
   * Returns a list of validation error messages, if any, for the given configuration.
   */
  protected List<String> validateConfig(Config config) {
    // TODO(danielromero): Move here SkylarkParser validations once Config has all the workflows.
    // checkCondition(!workflows.isEmpty(), ...)
    return ImmutableList.of();
  }

  void configureLogging(FileSystem fs) throws IOException {
    Path baseDir = fs.getPath(getBaseLoggingDir());
    Files.createDirectories(baseDir);
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
      console.info("Writing execution logs to: " + baseDir);
    }
  }

  /**
   * Returns the base directory to be used by Copybara to write execution logs.
   *
   * <p>Other implementations of {@link Copybara} can override the base logging directory.
   */
  protected String getBaseLoggingDir() {
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
}
