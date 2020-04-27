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

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.StarlarkBuiltin;
import com.google.devtools.build.lib.skylarkinterface.StarlarkDocumentationCategory;
import com.google.devtools.build.lib.syntax.Sequence;
import com.google.devtools.build.lib.syntax.StarlarkList;
import com.google.devtools.build.lib.syntax.StarlarkValue;

/** Information about the changes being imported */
@StarlarkBuiltin(
    name = "Changes",
    category = StarlarkDocumentationCategory.BUILTIN,
    doc =
        "Data about the set of changes that are being migrated. "
            + "Each change includes information like: original author, change message, "
            + "labels, etc. You receive this as a field in TransformWork object for used defined "
            + "transformations")
public final class Changes implements StarlarkValue {

  public static final Changes EMPTY = new Changes(ImmutableList.of(), ImmutableList.of());

  private final Sequence<? extends Change<?>> current;
  private final Sequence<? extends Change<?>> migrated;

  public Changes(Iterable<? extends Change<?>> current, Iterable<? extends Change<?>> migrated) {
    this.current = StarlarkList.immutableCopyOf(current);
    this.migrated = StarlarkList.immutableCopyOf(migrated);
  }

  @SkylarkCallable(
      name = "current",
      doc = "List of changes that will be migrated",
      structField = true)
  public final Sequence<? extends Change<?>> getCurrent() {
    return current;
  }

  @SkylarkCallable(
      name = "migrated",
      doc =
          "List of changes that where migrated in previous Copybara executions or if using"
              + " ITERATIVE mode in previous iterations of this workflow.",
      structField = true)
  public Sequence<? extends Change<?>> getMigrated() {
    return migrated;
  }
}
