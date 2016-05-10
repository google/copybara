// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.Destination;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.Workflow;
import com.google.copybara.WorkflowNameOptions;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.transform.Transformation;

import java.util.HashMap;
import java.util.List;

/**
 * Configuration for a Copybara project.
 *
 * <p> Object of this class represents a parsed Copybara configuration.
 */
public final class Config {

  private final String name;
  private final Workflow activeWorkflow;

  private Config(String name, Workflow activeWorkflow) {
    this.name = Preconditions.checkNotNull(name);
    this.activeWorkflow = Preconditions.checkNotNull(activeWorkflow);
  }

  /**
   * The name of the configuration. The recommended value is to use the project name.
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the currently use
   */
  public Workflow getActiveWorkflow() {
    return activeWorkflow;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("activeWorkflow", activeWorkflow)
        .toString();
  }

  /**
   * Config builder. YAML parser needs this to be public.
   */
  @DocElement(yamlName = "!Config",
      description = "The single top level object of the configuration file.",
      elementKind = Config.class)
  public static final class Yaml {

    private String name;
    private ImmutableMap<String, Workflow.Yaml> workflows = ImmutableMap.of();

    @DocField(description = "Name of the project", required = true)
    public void setName(String name) {
      this.name = name;
    }

    @DocField(description = "All workflows (migration operations) associated with this project.",
        required = true)
    public void setWorkflows(List<Workflow.Yaml> workflows)
        throws ConfigValidationException {
      HashMap<String, Workflow.Yaml> map = new HashMap<>();
      for (Workflow.Yaml workflow : workflows) {
        if (map.put(workflow.getName(), workflow) != null) {
          String nameFieldHint = "";
          if (workflow.getName().equals("default")) {
            nameFieldHint =
                ". Multiple workflows in the same config file require giving a name to the"
                + " workflow. Use 'name: myName' in one of the workflows.";
          }

          throw new ConfigValidationException(
              "More than one workflow with name: " + workflow.getName() + nameFieldHint);
        }
      }
      this.workflows = ImmutableMap.copyOf(map);
    }

    public Config withOptions(Options options) throws ConfigValidationException {
      ConfigValidationException.checkNotMissing(name, "name");
      if (workflows.isEmpty()) {
        throw new ConfigValidationException("At least one element in 'workflows' is required.");
      }

      String workflowName = options.get(WorkflowNameOptions.class).get();
      Workflow.Yaml workflow = workflows.get(workflowName);
      if (workflow == null) {
        throw new ConfigValidationException("No workflow with this name exists: " + workflowName);
      }
      return new Config(this.name, workflow.withOptions(options, this.name));
    }

    /**
     * We ignore the global values. This is only a placeholder so that the user can define in one
     * place all its global values. Snakeyaml replaces while parsing the references with the
     * values.
     */
    @DocField(description = "Global values for the scope of the file. Values are defined and referenced using standard YAML notation (& and * prefixes)", required = false)
    public void setGlobal(List<Object> global) {

    }
  }
}
