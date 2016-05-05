// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.transform.Transformation;
import com.google.copybara.util.console.Console;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

/**
 * Represents a particular migration operation that can occur for a project. Each project can have
 * multiple workflows. Each workflow has a particular origin and destination.
 */
public abstract class Workflow {

  protected final Logger logger = Logger.getLogger(this.getClass().getName());

  private final String name;
  private final Origin<?> origin;
  private final Destination destination;
  private final List<Transformation> transformations;
  protected final Console console;

  Workflow(String name,
      Origin<?> origin, Destination destination, ImmutableList<Transformation> transformations,
      Console console) {
    this.name = Preconditions.checkNotNull(name);
    this.origin = Preconditions.checkNotNull(origin);
    this.destination = Preconditions.checkNotNull(destination);
    this.transformations = Preconditions.checkNotNull(transformations);
    this.console = Preconditions.checkNotNull(console);
  }

  public String getName() {
    return name;
  }

  /**
   * The repository that represents the source of truth
   */
  public Origin<?> getOrigin() {
    return origin;
  }

  /**
   * The destination repository to copy to.
   */
  public Destination getDestination() {
    return destination;
  }

  /**
   * Transformations to run before writing them to the destination.
   */
  public List<Transformation> getTransformations() {
    return transformations;
  }

  abstract void run(Path workdir, @Nullable String sourceRef)
      throws RepoException, IOException;

  void runTransformations(Path workdir) throws RepoException {
    for (int i = 0; i < transformations.size(); i++) {
      Transformation transformation = transformations.get(i);
      String transformMsg = String.format(
          "[%2d/%d] Transform: %s", i + 1, transformations.size(), transformation.describe());
      logger.log(Level.INFO, transformMsg);

      console.progress(transformMsg);
      try {
        transformation.transform(workdir);
      } catch (IOException e) {
        throw new RepoException("Error applying transformation: " + transformation, e);
      }
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("origin", origin)
        .add("destination", destination)
        .add("transformations", transformations)
        .toString();
  }

  /**
   * Config builder used by YAML.
   */
  @DocElement(yamlName = "!Workflow",
      description = "Defines a migration pipeline which can be invoked via the Copybara command.",
      elementKind = Workflow.class)
  public static final class Yaml {
    private String name = "default";
    private Origin.Yaml<?> origin;
    private Destination.Yaml destination;
    private WorkflowMode mode = WorkflowMode.SQUASH;
    private ImmutableList<Transformation.Yaml> transformations = ImmutableList.of();

    public String getName() {
      return name;
    }

    @DocField(description = "The name of the workflow.", required = false, defaultValue = "default")
    public void setName(String name) {
      this.name = name;
    }

    @DocField(description = "Where to read the migration code from.", required = true)
    public void setOrigin(Origin.Yaml<?> origin) {
      this.origin = origin;
    }

    @DocField(description = "Where to write the migration code to after transforms.",
        required = true)
    public void setDestination(Destination.Yaml destination) {
      this.destination = destination;
    }

    @DocField(description = "Transformations to run on the migration code.",
        required = false)
    public void setTransformations(List<? extends Transformation.Yaml> transformations)
        throws ConfigValidationException {
      this.transformations = ImmutableList.copyOf(transformations);
    }

    @DocField(description = "Import/export mode of the changes. For example if the changes should be imported by squashing all the pending changes or imported individually",
        required = false, defaultValue = "SQUASH")
    public void setMode(WorkflowMode mode) {
      this.mode = mode;
    }

    public Workflow withOptions(Options options, String configName) throws ConfigValidationException {
      ImmutableList.Builder<Transformation> transformations = new ImmutableList.Builder<>();
      for (Transformation.Yaml transformation : this.transformations) {
        transformations.add(transformation.withOptions(options));
      }
      switch (mode) {
        case SQUASH:
          return new SquashWorkflow(configName,
              name,
              origin.withOptions(options),
              destination.withOptions(options, configName),
              transformations.build(),
              options.get(GeneralOptions.class).console()
          );
        default:
          throw new UnsupportedOperationException(mode + " still not implemented");
      }
    }
  }
}
