// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Configuration for a Copybara project.
 *
 * <p> Object of this class represents a parsed Copybara configuration.
 */
public final class Config {

  private final String name;
  private final String repository;
  private final String destinationPath;
  private final List<Transformation> transformations;

  private Config(Builder builder) {
    this.name = builder.name;
    this.repository = builder.repository;
    this.destinationPath = builder.destinationPath;
    this.transformations = ImmutableList.copyOf(builder.transformations);
  }

  public String getName() {
    return name;
  }

  public String getRepository() {
    return repository;
  }

  public String getDestinationPath() {
    return destinationPath;
  }

  public List<Transformation> getTransformations() {
    return transformations;
  }

  @Override
  public String toString() {
    return "Config {"
        + "name='" + name + '\''
        + ", repository='" + repository + '\''
        + ", destinationPath='" + destinationPath + '\''
        + ", transformations=" + transformations +
        + '}';
  }

  /**
   * Config builder. YAML parser needs this to be public.
   */
  public static final class Builder {

    private String name;
    private String repository;
    private String destinationPath;
    private List<Transformation> transformations = new ArrayList<>();

    public Builder() {
    }

    public void setName(String name) {
      this.name = name;
    }

    public void setRepository(String repository) {
      this.repository = repository;
    }

    public void setDestinationPath(String destinationPath) {
      this.destinationPath = destinationPath;
    }

    public void setTransformations(List<? extends Transformation.Builder> transformations) {
      this.transformations.clear();
      for (Object transformation : transformations) {
        // The instanceof check is necessary when parsing Yaml because this method is invoked using
        // reflection and generic constraints are ignored.
        if (!(transformation instanceof Transformation.Builder)) {
          throw new ConfigValidationException(
              "Object parsed from Yaml is not a recognized Transformation: " + transformation);
        }
        this.transformations.add(((Transformation.Builder) transformation).build());
      }
    }

    public Config build() {
      return new Config(this);
    }
  }
}
