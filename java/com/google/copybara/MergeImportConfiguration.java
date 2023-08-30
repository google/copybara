/*
 * Copyright (C) 2023 Google LLC.
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

import com.google.auto.value.AutoValue;
import com.google.copybara.util.Glob;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.StarlarkValue;

/** An object used to configure Merge Import. */
@AutoValue
@StarlarkBuiltin(
    name = "core.merge_import_config",
    doc =
        "The paths for which to perpetuate destination-only changes in non source"
            + " of truth repositories.")
public abstract class MergeImportConfiguration implements StarlarkValue {

  public static MergeImportConfiguration create(String packagePath, Glob glob) {
    return new AutoValue_MergeImportConfiguration(packagePath, glob);
  }

  public abstract String packagePath();

  public abstract Glob glob();
}
