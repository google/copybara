/*
 * Copyright (C) 2026 Google LLC.
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
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.StarlarkValue;

/** An object used to configure Consistency File options. */
@AutoValue
@StarlarkBuiltin(name = "core.consistency_file_config", documented = true)
public abstract class ConsistencyFileConfiguration implements StarlarkValue {

  public static ConsistencyFileConfiguration create(String path, boolean excludeBuildFiles) {
    return new AutoValue_ConsistencyFileConfiguration(path, excludeBuildFiles);
  }

  public abstract String path();

  public abstract boolean excludeBuildFiles();
}
