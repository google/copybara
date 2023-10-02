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
import java.nio.file.Path;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.StarlarkValue;

/** An object used to configure Merge Import. */
@AutoValue
@StarlarkBuiltin(
    name = "core.merge_import_config",
    documented = false)
public abstract class MergeImportConfiguration implements StarlarkValue {
  public static final String DEFAULT_SINGLE_PATCH_PATH
    = "do-not-edit.bara.singlepatch";

  public static MergeImportConfiguration create(
      String packagePath,
      Glob glob,
      boolean useSinglePatch,
      String singlePatchPath) {
    return new AutoValue_MergeImportConfiguration(
        packagePath, glob, useSinglePatch, singlePatchPath);
  }

  public static MergeImportConfiguration create(
      String packagePath,
      Glob glob,
      boolean useSinglePatch) {
    return new AutoValue_MergeImportConfiguration(
        packagePath, glob, useSinglePatch, DEFAULT_SINGLE_PATCH_PATH);
  }

  public abstract String packagePath();

  public abstract Glob glob();

  public abstract boolean useSinglePatch();

  public abstract String singlePatchPath();

  public String fullSinglePatchPath() {
    return Path.of(packagePath()).resolve(singlePatchPath()).toString();
  }
}
