/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara.config;

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.exception.ValidationException;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for a Copybara project.
 *
 * <p> Objects of this class represent a parsed Copybara configuration.
 */
public final class Config {
  private final ImmutableMap<String, Migration> migrations;
  private final String location;
  private final Map<String, Object> globals;

  public Config(Map<String, Migration> migrations, String location, Map<String, Object> globals) {
    this.migrations = ImmutableMap.copyOf(migrations);
    this.location = Preconditions.checkNotNull(location);
    this.globals = ImmutableMap.copyOf(globals);
  }

  /**
   * Returns the {@link Migration} named after {@code migrationName}.
   */
  public Migration getMigration(String migrationName) throws ValidationException {
    checkCondition(migrations.containsKey(migrationName),
        String.format("No migration with name '%s' exists. Valid migrations: %s",
        migrationName, migrations.keySet()));
    return migrations.get(migrationName);
  }

  /**
   * Location of the top-level config file. An arbitrary string meant to be used
   * for logging/debugging. It shouldn't be parsed, as the format might change.
   */
  public String getLocation() {
    return location;
  }

  /**
   * Reads values from the global frame of the skylark environment, i.e. global variables.
   */
  public <T> T getGlobalEnvironmentVariable(String name, Class<T> clazz) {
    return clazz.cast(globals.get(name));
  }

  /**
   * Returns all the migrations in this configuration.
   */
  public ImmutableMap<String, Migration> getMigrations() {
    return migrations;
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
    return Objects.equals(migrations, config.migrations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(migrations);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("migrations", migrations)
        .add("location", location)
        .toString();
  }
}
