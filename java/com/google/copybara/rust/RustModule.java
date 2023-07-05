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
import com.google.copybara.exception.ValidationException;
import com.google.copybara.remotefile.RemoteFileOptions;
import com.google.copybara.version.VersionResolver;
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

  @StarlarkMethod(
      name = "crates_io_version_resolver",
      doc = "A version resolver for Rust crates from crates.io",
      documented = false,
      parameters = {@Param(name = "crate", named = true, doc = "The name of the rust crate.")})
  @SuppressWarnings("unused")
  public VersionResolver getResolver(String crate) {
    return new RustCratesIoVersionResolver(crate, options);
  }

  @StarlarkMethod(
      name = "create_version_requirement",
      doc =
          "Represents a Cargo version requirement. You can compare version strings against this"
              + "object to determine if they meet this requirement or not. ",
      documented = true,
      parameters = {
        @Param(name = "requirement", named = true, doc = "The Cargo version requirement"),
      })
  @Example(
      title = "Create a version requirement object",
      before = "Example:  Create a requirement object and compare a version string against it.",
      code =
          "rust.create_version_requirement(\">= 0.5\")")
  @SuppressWarnings("unused")
  public RustVersionRequirement getVersionRequirement(String requirement)
      throws ValidationException {
    return RustVersionRequirement.getVersionRequirement(requirement);
  }

  @StarlarkMethod(
      name = "check_version_requirement",
      doc =
          "Checks a version against a Cargo version requirement. Currently, default, caret, and"
              + " comparison requirements are supported. Please see"
              + " https://doc.rust-lang.org/cargo/reference/specifying-dependencies.html for more"
              + " information.",
      documented = false,
      parameters = {
        @Param(name = "requirement", named = true, doc = "The Cargo version requirement"),
        @Param(name = "version", named = true, doc = "The version to check")
      })
  public boolean checkVersionRequirement(String requirement, String version)
      throws ValidationException {
    // TODO(chriscampos): Remove this in favor of getVersionRequirement
    return RustVersionRequirement.getVersionRequirement(requirement).fulfills(version);
  }
}
