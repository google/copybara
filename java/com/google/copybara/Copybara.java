// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.config.YamlParser;
import com.google.copybara.config.skylark.SkylarkParser;
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
import com.google.copybara.transform.TransformOptions;
import com.google.copybara.transform.ValidationException;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import org.yaml.snakeyaml.TypeDescription;

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

  public void run(Options options, String configContents, String workflowName,
      Path baseWorkdir, @Nullable String sourceRef)
      throws RepoException, ValidationException, IOException, EnvironmentException {
    options.get(WorkflowOptions.class).setWorkflowName(workflowName);
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    boolean skylark = generalOptions.isSkylark();
    Config config = parseConfig(skylark, configContents, options);
    Console console = generalOptions.console();
    console.progress("Validating configuration");
    // TODO(danielromero): Rename runConfigValidation to validate once we finish migration
    runConfigValidation(config, console);
    // TODO(danielromero): Add test to check that the workflow is not run, once we finish migration
    if (generalOptions.isValidate()) {
      console.info("Configuration is valid.");
      return;
    }
    config.getActiveWorkflow().run(baseWorkdir, sourceRef);
  }

  public void validate(Options options, String configContent, String yamlConfigContent,
      String workflowName)
      throws RepoException, ValidationException, IOException, EnvironmentException {
    options.get(WorkflowOptions.class).setWorkflowName(workflowName);
    boolean skylark = options.get(GeneralOptions.class).isSkylark();
    Config config = parseConfig(skylark, configContent, options);
    Config yamlConfig = parseConfig(/*skylark=*/false, yamlConfigContent, options);
    Console console = options.get(GeneralOptions.class).console();
    if (!config.equals(yamlConfig)) {
      console.error("Skylark config file and Yaml one are not equivalent:\n"
          + "YAML: " + yamlConfig + "\n"
          + "BZL : " + config);
      throw new ConfigValidationException("Error validating configuration: "
          + "Skylark config file and Yaml one are not equivalent");
    }
    runConfigValidation(config, console);
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

  private Config parseConfig(boolean skylark, String configContents, Options options)
      throws IOException, ConfigValidationException, EnvironmentException {
    if (skylark) {
      return skylarkParser.loadConfig(configContents, options);
    }
    return loadYamlConfig(configContents, options);
  }

  private Config loadYamlConfig(String configContents, Options options)
      throws IOException, ConfigValidationException, EnvironmentException {
    return new YamlParser(getYamlTypeDescriptions()).parseConfig(configContents, options);
  }
}
