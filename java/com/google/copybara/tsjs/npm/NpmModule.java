/*
 * Copyright (C) 2024 Google LLC.
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

package com.google.copybara.tsjs.npm;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.copybara.doc.annotations.Example;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.remotefile.RemoteFileOptions;
import com.google.copybara.version.VersionResolver;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkValue;

/** Module used for NPM related Starklark operations */
@StarlarkBuiltin(
    name = "npm",
    doc = "Module for NPM related starlark operations",
    documented = false)
public class NpmModule implements StarlarkValue {
  private final RemoteFileOptions remoteFileOptions;

  public NpmModule(RemoteFileOptions remoteFileOptions) {
    this.remoteFileOptions = checkNotNull(remoteFileOptions);
  }

  @StarlarkMethod(
      name = "npm_version_list",
      doc = "Returns npm version list object",
      documented = false,
      parameters = {
        @Param(
            name = "package_name",
            named = true,
            allowedTypes = {
              @ParamType(type = String.class),
            },
            doc = "The Npm package name, including scope with @ if applicable.")
      })
  @Example(
      title = "Create a version list for a given Npm package",
      before =
          "Example of how create a version list for an unscoped package (e.g."
              + " https://npmjs.com/package/chalk)",
      code = "npm.npm_version_list(\n" + "        package_name='chalk'\n" + ")")
  @Example(
      title = "Create a version list for a given Npm package",
      before =
          "Example of how create a version list for a scoped package (e.g."
              + " https://npmjs.com/package/@angular/core)",
      code = "npm.npm_version_list(\n" + "        package_name='@angular/core'\n" + ")")
  public NpmVersionList getNpmVersionList(String packageName)
      throws EvalException, ValidationException {
    return NpmVersionList.forPackage(packageName, remoteFileOptions);
  }

  @StarlarkMethod(
      name = "npm_resolver",
      doc = "Npm resolver that knows what to do with command line passed refs.",
      documented = false,
      parameters = {
        @Param(
            name = "package_name",
            named = true,
            allowedTypes = {
              @ParamType(type = String.class),
            },
            doc = "The Npm package name"),
      })
  public VersionResolver getResolver(String packageName) {
    return new NpmVersionResolver(packageName, remoteFileOptions);
  }
}
