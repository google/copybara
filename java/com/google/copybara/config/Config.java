// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

/**
 * Configuration for a Copybara project.
 *
 * <p> Object of this class represents a parsed Copybara configuration.
 */
public final class Config {

  private final String name;
  private final String repository;
  private final String destinationPath;

  public Config(String name, String repository, String destinationPath) {
    this.name = name;
    this.repository = repository;
    this.destinationPath = destinationPath;
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

  @Override
  public String toString() {
    return "Config {"
        + "name='" + name + '\''
        + ", repository='" + repository + '\''
        + ", destinationPath='" + destinationPath + '\''
        + '}';
  }

  /**
   * Config builder. YAML parser needs this to be public.
   */
  @SuppressWarnings("WeakerAccess")
  public static final class Builder {

    private String name;
    private String repository;
    private String destinationPath;

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

    Config build() {
      return new Config(name, repository, destinationPath);
    }
  }
}
