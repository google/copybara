// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.copybara.Workflow;
import java.util.Objects;

/**
 * Configuration for a Copybara project.
 *
 * <p> Object of this class represents a parsed Copybara configuration.
 */
public final class Config {
  private final String name;
  private final Workflow activeWorkflow;

  public Config(String name, Workflow activeWorkflow) {
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
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Config config = (Config) o;
    return Objects.equals(name, config.name) &&
        Objects.equals(activeWorkflow, config.activeWorkflow);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, activeWorkflow);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("activeWorkflow", activeWorkflow)
        .toString();
  }
}
