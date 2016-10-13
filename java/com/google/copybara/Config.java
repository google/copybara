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

package com.google.copybara;

import static com.google.copybara.ValidationException.checkCondition;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for a Copybara project.
 *
 * <p> Object of this class represents a parsed Copybara configuration.
 */
public final class Config {
  private final String name;
  private final ImmutableMap<String, Migration> migrations;

  public Config(String name, Map<String, Migration> migrations) {
    this.name = Preconditions.checkNotNull(name);
    this.migrations = ImmutableMap.copyOf(migrations);
  }

  /**
   * The name of the configuration. The recommended value is to use the project name.
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the {@link Migration} named after {@code migrationName}.
   */
  public Migration getMigration(String migrationName) throws ValidationException {
    checkCondition(migrations.containsKey(migrationName),
        String.format("No migration with '%s' name exists. Valid migrations: %s",
        migrationName, migrations.keySet()));
    return migrations.get(migrationName);
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
    return Objects.equals(name, config.name) &&
        Objects.equals(migrations, config.migrations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, migrations);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("migrations", migrations)
        .toString();
  }
}
