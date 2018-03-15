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

import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.EvalException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SkylarkModule(
  name = GlobalMigrations.GLOBAL_MIGRATIONS,
  doc = "Global variable that holds the registered migrations in the " + "config files",
  category = SkylarkModuleCategory.BUILTIN,
  documented = false)
public class GlobalMigrations {

  static final String GLOBAL_MIGRATIONS = "global_migrations";

  private final Map<String, Migration> migrations = new HashMap<>();

  public static GlobalMigrations getGlobalMigrations(Environment env) {
    return (GlobalMigrations) Objects.requireNonNull(env.getGlobals().get(GLOBAL_MIGRATIONS));
  }

  public Map<String, Migration> getMigrations() {
    return migrations;
  }

  public void addMigration(Location location, String name, Migration migration)
      throws EvalException {
    if (migrations.put(name, migration) != null) {
      throw new EvalException(
          location, String.format("A migration with the name '%s' is already defined", name));
    }
  }
}
