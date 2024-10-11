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
    documented = false)
public abstract class MergeImportConfiguration implements StarlarkValue {

  /**
   * The strategy to use for merging files.
   *
   * <p>DIFF3 shells out to diff3 with the -m flag to perform a 3-way merge. PATCH_MERGE creates a
   * patch file by diffing the baseline and destination files, and then applies the patch to the
   * origin file.
   */
  public enum MergeStrategy {
    DIFF3,
    PATCH_MERGE,
    UNKNOWN
  }

  public static MergeImportConfiguration create(
      String packagePath, Glob paths, boolean useConsistencyFile, MergeStrategy mergeStrategy) {
    return new AutoValue_MergeImportConfiguration(
        packagePath, paths, useConsistencyFile, mergeStrategy);
  }

  public abstract String packagePath();

  public abstract Glob paths();

  public abstract boolean useConsistencyFile();

  public abstract MergeStrategy mergeStrategy();
}
