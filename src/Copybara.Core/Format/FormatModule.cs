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

using System.Collections.Immutable;
using Copybara.Common;
using Copybara.Config;
using Copybara.Format;
using Copybara.Util;
using Starlark.Annot;
using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Format;

/// <summary>Skylark module for transforming the code to Google's style/guidelines.</summary>
[StarlarkBuiltin(
    "format",
    Doc = "Module for formatting the code to Google's style/guidelines")]
public class FormatModule : IStarlarkValue
{
    private static readonly ImmutableHashSet<string> BuildifierTypeValues =
        ImmutableHashSet.Create("auto", "bzl", "build", "workspace");

    private static readonly Glob DefaultBuildifierPaths =
        Glob.CreateGlob(ImmutableArray.Create("**.bzl", "**/BUILD", "BUILD"));

    protected readonly WorkflowOptions WorkflowOptions;
    protected readonly BuildifierOptions BuildifierOptions;
    protected readonly GeneralOptions GeneralOptions;

    public FormatModule(
        WorkflowOptions workflowOptions,
        BuildifierOptions buildifierOptions,
        GeneralOptions generalOptions)
    {
        WorkflowOptions = Preconditions.CheckNotNull(workflowOptions);
        BuildifierOptions = Preconditions.CheckNotNull(buildifierOptions);
        GeneralOptions = Preconditions.CheckNotNull(generalOptions);
    }

    [StarlarkMethod(
        "buildifier",
        Doc = "Formats the BUILD files using buildifier.")]
    public ITransformation Buildifier(
        [Param(
            Name = "paths",
            AllowedTypes = new[] { typeof(Glob), typeof(StarlarkList), typeof(NoneType) },
            Doc = "Paths of the files to format relative to the workdir.",
            DefaultValue = "None",
            Named = true)]
        object? paths,
        [Param(
            Name = "type",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) },
            Doc =
                "The type of the files. Can be 'auto', 'bzl', 'build' or 'workspace'. Note that"
                + " this is not recommended to be set and might break in the future. The"
                + " default is 'auto'. This mode formats as BUILD files \"BUILD\","
                + " \"BUILD.bazel\", \"WORKSPACE\" and \"WORKSPACE.bazel\" files. The rest as"
                + " bzl files. Prefer to use those names for BUILD files instead of setting"
                + " this flag.",
            DefaultValue = "'auto'",
            Named = true)]
        object? type,
        [Param(
            Name = "lint",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) },
            Doc =
                "If buildifier --lint should be used. This fixes several common issues. Note that"
                + " this transformation is difficult to revert. For example if it removes a"
                + " load statement because is not used after removing a rule, then the reverse"
                + " workflow needs to add back the load statement (core.replace or similar). "
                + " Possible values: `OFF`, `FIX`. Default is `OFF`",
            DefaultValue = "None",
            Named = true)]
        object? lint,
        [Param(
            Name = "lint_warnings",
            AllowedTypes = new[] { typeof(Sequence) },
            DefaultValue = "[]",
            Doc = "Warnings used in the lint mode. Default is buildifier default",
            Named = true)]
        object warnings)
    {
        string? typeStr = SkylarkUtil.ConvertFromNoneable<string>(type, null);
        if (typeStr != null)
        {
            SkylarkUtil.Check(
                BuildifierTypeValues.Contains(typeStr),
                "Non-valid type: {0}. Valid types: {1}",
                typeStr,
                string.Join(", ", BuildifierTypeValues));
        }

        string lintStr = SkylarkUtil.ConvertFromNoneable(lint, "OFF")!;
        BuildifierFormat.LintMode lintMode = ParseLintMode(lintStr);

        var warningsList = SkylarkUtil.ConvertStringList(warnings, "lint_warnings");
        SkylarkUtil.Check(
            lintMode != BuildifierFormat.LintMode.Off || warningsList.Count == 0,
            "Warnings can only be used when lint is set to FIX");

        return new BuildifierFormat(
            BuildifierOptions,
            GeneralOptions,
            Glob.WrapGlob(paths, DefaultBuildifierPaths)!,
            lintMode,
            warningsList.ToImmutableArray(),
            typeStr);
    }

    private static BuildifierFormat.LintMode ParseLintMode(string value)
    {
        if (Enum.TryParse<BuildifierFormat.LintMode>(value, ignoreCase: true, out var result)
            && Enum.IsDefined(result))
        {
            return result;
        }

        throw StarlarkRt.Errorf(
            "Invalid value '{0}' for field 'lint'. Valid values are: {1}",
            value,
            string.Join(", ", Enum.GetNames<BuildifierFormat.LintMode>()));
    }
}
