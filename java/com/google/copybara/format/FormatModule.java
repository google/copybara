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

package com.google.copybara.format;

import static com.google.copybara.config.SkylarkUtil.check;
import static com.google.copybara.config.SkylarkUtil.convertFromNoneable;
import static com.google.copybara.config.SkylarkUtil.stringToEnum;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Transformation;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.doc.annotations.DocDefault;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.copybara.format.BuildifierFormat.LintMode;
import com.google.copybara.util.Glob;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkValue;

/** Skylark module for transforming the code to Google's style/guidelines. */
@StarlarkBuiltin(
    name = "format",
    doc = "Module for formatting the code to Google's style/guidelines")
public class FormatModule implements StarlarkValue {

  private static final ImmutableSet<String> BUILDIFIER_TYPE_VALUES =
      ImmutableSet.of("auto", "bzl", "build", "workspace");
  private static final Glob DEFAULT_BUILDIFIER_PATHS = Glob
      .createGlob(ImmutableList.of("**.bzl", "**/BUILD", "BUILD"));
  protected final WorkflowOptions workflowOptions;
  protected final BuildifierOptions buildifierOptions;
  protected final GeneralOptions generalOptions;

  public FormatModule(WorkflowOptions workflowOptions, BuildifierOptions buildifierOptions,
      GeneralOptions generalOptions) {
    this.workflowOptions = Preconditions.checkNotNull(workflowOptions);
    this.buildifierOptions = Preconditions.checkNotNull(buildifierOptions);
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
  }

  @StarlarkMethod(
      name = "buildifier",
      doc = "Formats the BUILD files using buildifier.",
      parameters = {
        @Param(
            name = "paths",
            allowedTypes = {
              @ParamType(type = Glob.class),
              @ParamType(type = NoneType.class),
            },
            doc = "Paths of the files to format relative to the workdir.",
            defaultValue = "None",
            named = true),
        @Param(
            name = "type",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "The type of the files. Can be 'auto', 'bzl', 'build' or 'workspace'. Note that"
                    + " this is not recommended to be set and might break in the future. The"
                    + " default is 'auto'. This mode formats as BUILD files \"BUILD\","
                    + " \"BUILD.bazel\", \"WORKSPACE\" and \"WORKSPACE.bazel\" files. The rest as"
                    + " bzl files. Prefer to use those names for BUILD files instead of setting"
                    + " this flag.",
            defaultValue = "'auto'",
            named = true),
        @Param(
            name = "lint",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "If buildifier --lint should be used. This fixes several common issues. Note that"
                    + " this transformation is difficult to revert. For example if it removes a"
                    + " load statement because is not used after removing a rule, then the reverse"
                    + " workflow needs to add back the load statement (core.replace or similar). "
                    + " Possible values: `OFF`, `FIX`. Default is `OFF`",
            defaultValue = "None",
            named = true),
        @Param(
            name = "lint_warnings",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
            },
            defaultValue = "[]",
            doc = "Warnings used in the lint mode. Default is buildifier default`",
            named = true)
      })
  @DocDefault(field = "lint", value = "\"OFF\"")
  @UsesFlags(BuildifierOptions.class)
  @Example(
      title = "Default usage",
      before = "The default parameters formats all BUILD and bzl files in the checkout directory:",
      code = "format.buildifier()")
  @Example(
      title = "Enable lint",
      before = "Enable lint for buildifier",
      code = "format.buildifier(lint = \"FIX\")")
  @Example(
      title = "Using globs",
      before = "Globs can be used to match only certain files:",
      code =
          "format.buildifier(\n"
              + "    paths = glob([\"foo/BUILD\", \"foo/**/BUILD\"], exclude = [\"foo/bar/BUILD\"])"
              + "\n)",
      after = "Formats all the BUILD files inside `foo` except for `foo/bar/BUILD`")
  @DocDefault(field = "paths", value = "glob([\"**.bzl\", \"**/BUILD\", \"BUILD\"])")
  public Transformation buildifier(
      Object paths, Object type, Object lint, Sequence<?> warnings // <String>
      ) throws EvalException {
    String typeStr = convertFromNoneable(type, null);
    if (typeStr != null) {
      check(
          BUILDIFIER_TYPE_VALUES.contains(typeStr),
          "Non-valid type: %s. Valid types: %s",
          typeStr,
          BUILDIFIER_TYPE_VALUES);
    }

    LintMode lintMode = stringToEnum("lint", convertFromNoneable(lint, "OFF"), LintMode.class);
    check(
        lintMode != LintMode.OFF || warnings.isEmpty(),
        "Warnings can only be used when lint is set to FIX");

    return new BuildifierFormat(
        buildifierOptions,
        generalOptions,
        convertFromNoneable(paths, DEFAULT_BUILDIFIER_PATHS),
        lintMode,
        ImmutableList.copyOf(Sequence.cast(warnings, String.class, "lint_warnings")),
        typeStr);
  }
}
