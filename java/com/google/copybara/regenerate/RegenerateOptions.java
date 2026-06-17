/*
 * Copyright (C) 2023 Google LLC
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
package com.google.copybara.regenerate;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.copybara.Option;
import java.util.Optional;
import javax.annotation.Nullable;

/** RegenerateOptions modifies behavior of RegenerateCmd */
@Parameters(separators = "=")
public class RegenerateOptions implements Option {

  @VisibleForTesting
  public RegenerateOptions() {}

  public Optional<String> getRegenBaseline() {
    return Optional.ofNullable(regenBaseline);
  }

  public boolean getUseImportBaseline() {
    return regenImportBaseline;
  }

  public Optional<String> getRegenTarget() {
    return Optional.ofNullable(regenTarget);
  }

  public Optional<String> getRegenPatchFile() {
    return Optional.ofNullable(regenPatchFile);
  }

  @Nullable
  @Parameter(
      names = "--regen-baseline",
      description = "a value identifying a destination revision with consistent patch files state")
  private String regenBaseline;

  @VisibleForTesting
  public void setRegenBaseline(@Nullable String regenBaseline) {
    this.regenBaseline = regenBaseline;
  }

  @Nullable
  @Parameter(
      names = "--regen-patch-file",
      description =
          "Config-relative path to an explicit patch file to (re-)generate. Only affects "
              + "migrations not using merge-import mode. Overrides the patchFilePath specified in "
              + "the consistency config to create a custom-named patch file. The specified patch "
              + "does not get applied during patch transformations if it exists and instead "
              + "recreated containing all currently untracked diffs after all transformations. "
              + "Can be used to create a new patch file for all newly added changes, leaving pre-"
              + "existing patches intact.")
  private String regenPatchFile;

  @VisibleForTesting
  public void setRegenPatchFile(@Nullable String regenPatchFile) {
    this.regenPatchFile = regenPatchFile;
  }

  @Parameter(
      names = "--regen-import-baseline",
      arity = 1,
      description = "create the baseline by doing a workflow import")
  private boolean regenImportBaseline = false;

  @VisibleForTesting
  public void setRegenImportBaseline(boolean regenImportBaseline) {
    this.regenImportBaseline = regenImportBaseline;
  }

  @Nullable
  @Parameter(
      names = "--regen-target",
      description =
          "a value identifying the current destination revision to generate patch files against")
  private String regenTarget;

  @VisibleForTesting
  public void setRegenTarget(@Nullable String regenTarget) {
    this.regenTarget = regenTarget;
  }
}
