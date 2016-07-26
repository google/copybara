// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

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
import com.google.copybara.transform.TransformOptions;
import com.google.copybara.transform.ValidationException;
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
    Config config = parseConfig(configContents, options);
    config.getActiveWorkflow().run(baseWorkdir, sourceRef);
  }

  private Config parseConfig(String configContents, Options options)
      throws IOException, ConfigValidationException, EnvironmentException {
    return new YamlParser(getYamlTypeDescriptions()).parseConfig(configContents, options);
  }
}
