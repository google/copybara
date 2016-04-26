// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Destination;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.transform.Transformation;

import java.util.ArrayList;
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
  public static final class Yaml {

    private String name;
    private Destination.Yaml destination;
    private Origin.Yaml origin;
    private List<Transformation.Yaml> transformations = new ArrayList<>();

    public void setName(String name) {
      this.name = name;
    }

    public void setDestination(Destination.Yaml destination) {
      this.destination = destination;
    }

    public void setOrigin(Origin.Yaml origin) {
      this.origin = origin;
    }

    public void setTransformations(List<? extends Transformation.Yaml> transformations)
        throws ConfigValidationException {
      this.transformations.clear();
      for (Object transformation : transformations) {
        // The instanceof check is necessary when parsing Yaml because this method is invoked using
        // reflection and generic constraints are ignored.
        if (!(transformation instanceof Transformation.Yaml)) {
          throw new ConfigValidationException(
              "Object parsed from Yaml is not a recognized Transformation: " + transformation);
        }
        this.transformations.add(((Transformation.Yaml) transformation));
      }
    }

    public Config withOptions(Options options) throws ConfigValidationException {
      ConfigValidationException.checkNotMissing(origin, "origin");
      ConfigValidationException.checkNotMissing(destination, "destination");

      ImmutableList.Builder<Transformation> transformations = ImmutableList.builder();
      for (Transformation.Yaml yaml : this.transformations) {
        transformations.add(yaml.withOptions(options));
      }
      return new Config(this.name, this.destination.withOptions(options),
          this.origin.withOptions(options), transformations.build());
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
