// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.base.Preconditions;
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
import com.google.copybara.transform.TransformOptions;
import com.google.copybara.transform.ValidationException;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Copybara tool main class.
 *
 * <p>Executes Copybara workflows independently from the environment that they are invoked from
 * (command-line, service).
 */
public class Copybara {

  protected static final ImmutableSet<Class<?>> BASIC_MODULES = ImmutableSet.<Class<?>>of(
      FolderDestination.Module.class);

  private final SkylarkParser skylarkParser;

  /**
   * Delete this method once imported and fixed
   */
  @Deprecated
  public Copybara() {
    skylarkParser = null;
  }

  public Copybara(SkylarkParser skylarkParser) {
    this.skylarkParser = Preconditions.checkNotNull(skylarkParser);
  }

  protected List<Option> getAllOptions() {
    return ImmutableList.of(
        new FolderDestinationOptions(),
        new GitOptions(),
        new GerritOptions(),
        new TransformOptions(),
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
    Console console = generalOptions.console();
    console.progress("Validating configuration");

    validate(options, configContents, workflowName);

    config.getActiveWorkflow().run(baseWorkdir, sourceRef);
  }

  public void validate(Options options, ConfigFile configContent, String workflowName)
      throws RepoException, ValidationException, IOException, EnvironmentException {
    options.get(WorkflowOptions.class).setWorkflowName(workflowName);
    Config config = skylarkParser.loadConfig(configContent, options);
    Console console = options.get(GeneralOptions.class).console();
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

  private void runConfigValidation(Config config, Console console) throws ConfigValidationException {
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
}
