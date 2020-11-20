/*
 * Copyright (C) 2018 Google Inc.
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

import static com.google.copybara.config.SkylarkUtil.check;

import com.google.re2j.Pattern;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.StarlarkValue;

@StarlarkBuiltin(
    name = GlobalMigrations.GLOBAL_MIGRATIONS,
    doc = "Global variable that holds the registered migrations in the config files",
    documented = false)
public class GlobalMigrations implements StarlarkValue {

  private static final Pattern MIGRATION_NAME_FORMAT = Pattern.compile("[a-zA-Z0-9_\\-\\./]+");

  static final String GLOBAL_MIGRATIONS = "global_migrations";

  private final Map<String, Migration> migrations = new HashMap<>();

  public static GlobalMigrations getGlobalMigrations(Module module) {
    return (GlobalMigrations)
        Objects.requireNonNull(module.getPredeclaredBindings().get(GLOBAL_MIGRATIONS));
  }

  public Map<String, Migration> getMigrations() {
    return migrations;
  }

  public void addMigration(String name, Migration migration) throws EvalException {
    checkMigrationName(name);
    check(
        migrations.put(name, migration) == null,
        "A migration with the name '%s' is already defined",
        name);
  }

  /**
   * Checks if a migration name conforms to the expected format.
   *
   * @param name Migration name
   * @throws EvalException If the name does not conform to the expected format
   */
  public static void checkMigrationName(String name) throws EvalException {
    check(
        MIGRATION_NAME_FORMAT.matches(name),
        "Migration name '%s' doesn't conform to expected pattern: %s",
        name,
        MIGRATION_NAME_FORMAT.pattern());
  }
}
