/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.copybara.transform.format;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Transformation;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.util.Glob;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.NoneType;
import com.google.devtools.build.lib.syntax.Sequence;
import com.google.devtools.build.lib.syntax.StarlarkValue;

/** Main module that groups all the transforms formatting files. */
@SkylarkModule(
    name = "format",
    namespace = true,
    doc = "Set of functions to format files",
    category = SkylarkModuleCategory.BUILTIN)
public final class FormatModule implements StarlarkValue {
  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "buildifier",
      doc =
          "Runs <a href=\"https://github.com/bazelbuild/buildtools/tree/master/buildifier\">"
              + "buildifier</a>, a tool for formatting bazel BUILD and .bzl files with a standard "
              + "convention.",
      parameters = {
        @Param(
            name = "files",
            type = Glob.class,
            named = true,
            doc =
                "A glob of files to format, relative to the workdir "
                    + "(for example `glob([\"**/BUILD\", \"**/BUILD.bazel\", \"**/*.bzl\"])`)."),
        @Param(
            name = "mode",
            type = String.class,
            named = true,
            defaultValue = "\"fix\"",
            // TODO(yannic): Implement mode={check,diff}.
            doc = "The formatting mode to pass to buildifier. Valid values are `fix`."),
        @Param(
            name = "lint",
            type = String.class,
            named = true,
            defaultValue = "\"off\"",
            // TODO(yannic): Implement lint=warn.
            doc = "The lint mode to pass to buildifier. Valid values are `off` and `fix`."),
        @Param(
            name = "warnings",
            named = true,
            defaultValue = "None",
            noneable = true,
            doc = "A list of buildifier warnings to enable."),
        @Param(
            name = "type",
            type = String.class,
            named = true,
            defaultValue = "\"auto\"",
            // TODO(yannic): Implement type={build,bzl,workspace,default}.
            doc = "The lint mode to pass to buildifier. Valid values are `auto`."),
        // TODO(yannic): Add support for custom tables.
      })
  public Transformation buildifier(
      Glob files, String mode, String lint, Object warningsValue, String type)
      throws EvalException {
    // TODO(yannic): Validate the combination of parameters makes sense.
    ImmutableList.Builder<String> warnings = ImmutableList.builder();
    if (null == warningsValue || warningsValue instanceof NoneType) {
      // No warnings to add.
    } else if (warningsValue instanceof String) {
      if (!"all".equals(warningsValue)) {
        throw new EvalException(
            null, "Only value 'all' allowed if string is passed as parameter 'warnings'");
      }
      warnings.add("all");
    } else if (warningsValue instanceof Sequence) {
      warnings.addAll(SkylarkUtil.convertStringList(warningsValue, "warnings"));
    } else {
      throw new EvalException(
          null,
          "expected value of type 'string, sequence of strings, or NoneType' for parameter "
              + "'warnings', for call to method buildifier(files, mode = \"fix\", "
              + "lint = \"off\", warnings = None) of 'format (a language module)'");
    }
    return new BuildifierTransform(files, mode, lint, warnings.build(), type);
  }
}
