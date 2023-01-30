/*
 * Copyright (C) 2022 Google Inc.
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
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.StarlarkValue;

@AutoValue
@StarlarkBuiltin(
    name = "core.autopatch_config",
    doc = "The configuration that describes automatic patch file generation")
abstract class AutoPatchfileConfiguration implements StarlarkValue {

  public static AutoPatchfileConfiguration create(
      String header,
      String suffix,
      String directoryPrefix,
      String directory,
      boolean stripFileNamesAndLineNumbers) {
    return new AutoValue_AutoPatchfileConfiguration(
        header, suffix, directoryPrefix, directory, stripFileNamesAndLineNumbers);
  }

  @Nullable
  public abstract String header();

  public abstract String suffix();

  public abstract String directoryPrefix();

  @Nullable
  public abstract String directory();

  public abstract boolean stripFileNamesAndLineNumbers();
}
