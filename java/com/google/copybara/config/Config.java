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

  public Config(String name, String repository) {
    this.name = name;
    this.repository = repository;
  }

  public String getName() {
    return name;
  }

  public String getRepository() {
    return repository;
  }

  @Override
  public String toString() {
    return "Config{" +
        "name='" + name + '\'' +
        ", repository='" + repository + '\'' +
        '}';
  }

  // Public needed by YAML parser
  @SuppressWarnings("WeakerAccess")
  public static final class Builder {

    private String name;
    private String repository;

    public Builder() {
    }

    public void setName(String name) {
      this.name = name;
    }

    public void setRepository(String repository) {
      this.repository = repository;
    }

    Config build() {
      return new Config(name, repository);
    }
  }
}
