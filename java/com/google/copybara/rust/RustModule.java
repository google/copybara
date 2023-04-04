/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.copybara.rust;

import com.google.copybara.doc.annotations.Example;
import com.google.copybara.remotefile.RemoteFileOptions;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/** A module for importing Rust crates from crates.io. */
@StarlarkBuiltin(name = "rust", doc = "A module for importing Rust crates", documented = false)
public final class RustModule implements StarlarkValue {
  private final RemoteFileOptions options;

  public RustModule(RemoteFileOptions options) {
    this.options = options;
  }

  @StarlarkMethod(
      name = "crates_io_version_list",
      doc = "Returns a crates.io version_list object",
      documented = false,
      parameters = {
        @Param(name = "crate", named = true, doc = "The name of the crate, e.g. \"libc\"")
      })
  @Example(
      title = "Create a version list for a given rust crate",
      before = "Example: creating a version list for libc",
      code = "rust.crates_io_version_list(\n" + "crate = \"libc\"\n)")
  public RustCratesIoVersionList getRustCratesIoVersionList(String crateName) {
    return RustCratesIoVersionList.forCrate(crateName, options);
  }
}
