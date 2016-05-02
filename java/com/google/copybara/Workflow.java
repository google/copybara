// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.transform.Transformation;

import java.util.List;

/**
 * Represents a particular migration operation that can occur for a project. Each project can have
 * multiple workflows. Each workflow has a particular origin and destination.
 */
public final class Workflow {
  private final Origin<?> origin;
  private final Destination destination;
  private final List<Transformation> transformations;

  private Workflow(
      Origin<?> origin, Destination destination, ImmutableList<Transformation> transformations) {
    this.origin = Preconditions.checkNotNull(origin);
    this.destination = Preconditions.checkNotNull(destination);
    this.transformations = Preconditions.checkNotNull(transformations);
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

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
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
    private Origin.Yaml<?> origin;
    private Destination.Yaml destination;
    private ImmutableList<Transformation.Yaml> transformations = ImmutableList.of();

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

    public Workflow withOptions(Options options) throws ConfigValidationException {
      ImmutableList.Builder<Transformation> transformations = new ImmutableList.Builder<>();
      for (Transformation.Yaml transformation : this.transformations) {
        transformations.add(transformation.withOptions(options));
      }
      return new Workflow(
          origin.withOptions(options), destination.withOptions(options), transformations.build());
    }
  }
}
