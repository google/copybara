// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Options;
import com.google.copybara.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a Copybara project.
 *
 * <p> Object of this class represents a parsed Copybara configuration.
 */
public final class Config {

  private final String name;
  private final Repository sourceOfTruth;
  private final String destinationPath;
  private final List<Transformation> transformations;

  private Config(String name, String destinationPath, Repository sourceOfTruth,
      ImmutableList<Transformation> transformations) {
    this.name = name;
    this.sourceOfTruth = sourceOfTruth;
    this.destinationPath = destinationPath;
    this.transformations = transformations;
  }

  /**
   * The name of the configuration. The recommended value is to use the project name.
   */
  public String getName() {
    return name;
  }

  /**
   * The repository that represents the source of truth
   */
  public Repository getSourceOfTruth() {
    return sourceOfTruth;
  }

  public String getDestinationPath() {
    return destinationPath;
  }

  public List<Transformation> getTransformations() {
    return transformations;
  }

  @Override
  public String toString() {
    return "Config{" +
        "name='" + name + '\'' +
        ", sourceOfTruth=" + sourceOfTruth +
        ", destinationPath='" + destinationPath + '\'' +
        ", transformations=" + transformations +
        '}';
  }

  /**
   * Config builder. YAML parser needs this to be public.
   */
  public static final class Yaml {

    private String name;
    private String destinationPath;
    private Repository.Yaml sourceOfTruth;
    private List<Transformation.Yaml> transformations = new ArrayList<>();

    public void setName(String name) {
      this.name = name;
    }

    public void setDestinationPath(String destinationPath) {
      this.destinationPath = destinationPath;
    }

    public void setSourceOfTruth(Repository.Yaml sourceOfTruth) {

      this.sourceOfTruth = sourceOfTruth;
    }

    public void setTransformations(List<? extends Transformation.Yaml> transformations) {
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

    public Config withOptions(Options options) {
      ImmutableList.Builder<Transformation> transformations = ImmutableList.builder();
      for (Transformation.Yaml yaml : this.transformations) {
        transformations.add(yaml.build());
      }
      return new Config(this.name, this.destinationPath, this.sourceOfTruth.withOptions(options),
          transformations.build());
    }
  }
}
