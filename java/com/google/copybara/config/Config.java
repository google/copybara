// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Destination;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.Workflow;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.transform.Transformation;

import java.util.List;

/**
 * Configuration for a Copybara project.
 *
 * <p> Object of this class represents a parsed Copybara configuration.
 */
public final class Config {

  private final String name;
  private final Origin<?> origin;
  private final Destination destination;
  private final List<Transformation> transformations;

  private Config(String name, Destination destination, Origin<?> origin,
      ImmutableList<Transformation> transformations) {
    this.name = name;
    this.destination = destination;
    this.origin = origin;
    this.transformations = transformations;
  }

  /**
   * The name of the configuration. The recommended value is to use the project name.
   */
  public String getName() {
    return name;
  }

  /**
   * The destination repository to copy to.
   */
  public Destination getDestination() {
    return destination;
  }

  /**
   * The repository that represents the source of truth
   */
  public Origin<?> getOrigin() {
    return origin;
  }

  public List<Transformation> getTransformations() {
    return transformations;
  }

  @Override
  public String toString() {
    return "Config{" +
        "name='" + name + '\'' +
        ", destination=" + destination +
        ", origin=" + origin +
        ", transformations=" + transformations +
        '}';
  }

  /**
   * Config builder. YAML parser needs this to be public.
   */
  @DocElement(yamlName = "!Config",
      description = "The single top level object of the configuration file.",
      elementKind = Config.class)
  public static final class Yaml {

    private String name;

    // TODO(matvore): remove this field once all tests and exispting configs have been converted to
    // using explicit workflows
    private ImmutableList<Workflow.Yaml> workflows = ImmutableList.of(new Workflow.Yaml());

    @DocField(description = "Name of the project", required = true)
    public void setName(String name) {
      this.name = name;
    }

    // TODO(matvore): Remove this field once everyone is using explicit workflows.
    @DocField(description = "Use workflows field instead", required = false)
    public void setDestination(Destination.Yaml destination) {
      this.workflows.get(0).setDestination(destination);
    }

    // TODO(matvore): Remove this field once everyone is using explicit workflows.
    @DocField(description = "Use workflows field instead", required = false)
    public void setOrigin(Origin.Yaml origin) {
      this.workflows.get(0).setOrigin(origin);
    }

    // TODO(matvore): Remove this field once everyone is using explicit workflows.
    @DocField(description = "Use workflows field instead", required = false)
    public void setTransformations(List<? extends Transformation.Yaml> transformations)
        throws ConfigValidationException {
      this.workflows.get(0).setTransformations(transformations);
    }

    @DocField(description = "All workflows (migration operations) associated with this project.",
        required = true)
    public void setWorkflows(List<? extends Workflow.Yaml> workflows)
        throws ConfigValidationException {
      this.workflows = ImmutableList.copyOf(workflows);
    }

    public Config withOptions(Options options) throws ConfigValidationException {
      if (workflows.isEmpty()) {
        throw new ConfigValidationException("At least one element in 'workflows' is required.");
      }
      Workflow workflow = workflows.get(0).withOptions(options);
      return new Config(this.name, workflow.getDestination(), workflow.getOrigin(),
          ImmutableList.copyOf(workflow.getTransformations()));
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
