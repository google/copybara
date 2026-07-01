/*
 * Copyright (C) 2016 Google Inc.
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
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Config;
using Copybara.Doc.Annotations;
using Copybara.Exceptions;
using Copybara.Util;
using Starlark.Annot;
using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Transform.Patch;

/// <summary>Skylark module that provides a basic transform to apply patchfiles.</summary>
[StarlarkBuiltin("patch", Doc = "Module for applying patches.")]
[UsesFlags(typeof(PatchingOptions))]
public class PatchModule : ILabelsAwareModule, IStarlarkValue
{
    private enum ValidationLevel
    {
        Full,
        OptionalSeries,
        None,
    }

    private static readonly Regex Lines = new(@"\r?\n", RegexOptions.None);

    private ConfigFile _configFile = null!;
    private readonly PatchingOptions _patchingOptions;
    private readonly GeneralOptions _generalOptions;

    public PatchModule(PatchingOptions patchingOptions, GeneralOptions generalOptions)
    {
        _patchingOptions = Preconditions.CheckNotNull(patchingOptions);
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
    }

    public void SetConfigFile(ConfigFile mainConfigFile, ConfigFile currentConfigFile)
    {
        _configFile = currentConfigFile;
    }

    [StarlarkMethod(
        "apply",
        Doc =
            "A transformation that applies the given patch files. If a path does not exist in a"
            + " patch, it will be ignored.",
        UseStarlarkThread = true)]
    public PatchTransformation Apply(
        [Param(
            Name = "patches",
            Named = true,
            DefaultValue = "[]",
            AllowedTypes = new[] { typeof(StarlarkList) },
            Doc =
                "The list of patchfiles to apply, relative to the current config file. The files"
                + " will be applied relative to the checkout dir and the leading path component"
                + " will be stripped (-p1).\n\nIf `series` is also specified, these patches will be"
                + " applied before those ones.\n\n**This field doesn't accept a glob.**")]
        object patches,
        [Param(
            Name = "excluded_patch_paths",
            AllowedTypes = new[] { typeof(StarlarkList) },
            Named = true,
            DefaultValue = "[]",
            Doc =
                "The list of paths to exclude from each of the patches. Each of the paths will be"
                + " excluded from all the patches. Note that these are not workdir paths, but paths"
                + " relative to the patch itself. If not empty, the patch will be applied using 'git"
                + " apply' instead of GNU Patch.")]
        ISequence<object?> excludedPaths,
        [Param(
            Name = "series",
            Named = true,
            AllowedTypes = new[] { typeof(string), typeof(NoneType) },
            Positional = false,
            DefaultValue = "None",
            Doc =
                "A file which contains a list of patches to apply. The patch files to apply are"
                + " interpreted relative to this file and must be written one per line. The patches"
                + " listed in this file will be applied relative to the checkout dir and the leading"
                + " path component will be stripped (via the `-p1` flag).\n\nIf `patches` is also"
                + " specified, those patches will be applied before these ones.")]
        object seriesOrNone,
        [Param(
            Name = "strip",
            Named = true,
            Positional = false,
            DefaultValue = "1",
            Doc =
                "Number of segments to strip. (This sets the `-pX` flag, for example `-p0`, `-p1`,"
                + " etc.) By default it uses `-p1`.")]
        StarlarkInt stripI,
        [Param(
            Name = "directory",
            Named = true,
            Positional = false,
            DefaultValue = "''",
            Doc =
                "Path relative to the working directory from which to apply patches. This supports"
                + " patches that specify relative paths in their file diffs but use a different"
                + " relative path base than the working directory. (This sets the `-d` flag, for"
                + " example `-d sub/dir/`). By default, it uses the current directory.")]
        string directory,
        [Param(
            Name = "validation_level",
            Named = true,
            Positional = false,
            DefaultValue = "\"OPTIONAL_SERIES\"",
            Doc = "The validation level to use for patch files and series.")]
        string validationLevelString,
        StarlarkThread thread)
    {
        ValidationLevel validationLevel = GetValidationLevel(validationLevelString);

        int strip = stripI.ToInt("strip");
        var patchFiles = ImmutableArray.CreateBuilder<ConfigFile>();
        foreach (string patch in SkylarkUtil.ConvertStringList(patches, "patches"))
        {
            ConfigFile? resolved = Resolve(patch, validationLevel);
            if (resolved != null)
            {
                patchFiles.Add(resolved);
            }
        }
        string? series = SkylarkUtil.ConvertOptionalString(seriesOrNone);
        if (series != null && series.Trim().Length != 0)
        {
            ParseSeries(series, patchFiles, validationLevel);
        }
        return new PatchTransformation(
            patchFiles.ToImmutable(),
            SkylarkUtil.ConvertStringList(excludedPaths, "excludedPaths").ToImmutableArray(),
            _patchingOptions,
            reverse: false,
            strip,
            directory,
            thread.GetCallerLocation());
    }

    [StarlarkMethod(
        "quilt_apply",
        Doc =
            "A transformation that applies and updates patch files using Quilt. Compared to"
            + " `patch.apply`, this transformation supports updating the content of patch files if"
            + " they can be successfully applied with fuzz.\n\nThe series and patch files must be"
            + " included in the destination_files glob in order to get updated. The updated files"
            + " end up in workingDirectory/`directory`/`patchesDirectory`.",
        UseStarlarkThread = true)]
    [Example(
        "Workflow to apply and update patches",
        "Suppose the destination repository's directory structure looks like:\n"
            + "```\nsource_root/BUILD\nsource_root/copy.bara.sky\nsource_root/migrated_file1\n"
            + "source_root/migrated_file2\nsource_root/patches/series\n"
            + "source_root/patches/patch1.patch\n```\n"
            + "Then the transformations in `source_root/copy.bara.sky` should look like:",
        "[\n    patch.quilt_apply(series = \"patches/series\"),\n"
            + "    core.move(\"\", \"source_root\"),\n]",
        After =
            "In this example, `patch1.patch` is applied to `migrated_file1` and/or"
            + " `migrated_file2`. `patch1.patch` itself will be updated during the migration if it"
            + " is applied with fuzz.")]
    public QuiltTransformation QuiltApply(
        [Param(
            Name = "series",
            Named = true,
            Positional = false,
            Doc =
                "A path to a series file to apply using Quilt, relative to the Copybara config"
                + " directory.")]
        string series,
        [Param(
            Name = "directory",
            Named = true,
            Positional = false,
            DefaultValue = "''",
            Doc =
                "Path relative to the working directory from which to run quilt and apply patches.")]
        string directory,
        [Param(
            Name = "validation_level",
            Named = true,
            Positional = false,
            DefaultValue = "\"FULL\"",
            Doc = "The validation level to use for patch files and series.")]
        string validationLevelString,
        StarlarkThread thread)
    {
        ValidateQuiltSeriesParameter(series);

        ValidationLevel validationLevel = GetValidationLevel(validationLevelString);
        var patchFiles = ImmutableArray.CreateBuilder<ConfigFile>();
        ConfigFile? seriesFile = ParseSeries(series, patchFiles, validationLevel);
        return new QuiltTransformation(
            seriesFile,
            patchFiles.ToImmutable(),
            _patchingOptions,
            reverse: false,
            directory,
            thread.GetCallerLocation(),
            GetPatchesDirName(series));
    }

    private static void ValidateQuiltSeriesParameter(string series)
    {
        if (string.IsNullOrEmpty(series) || series.Trim().Length == 0)
        {
            throw new ValidationException("Series parameter is required and cannot be empty.");
        }

        try
        {
            FileUtil.CheckNormalizedRelative(series);
        }
        catch (ArgumentException e)
        {
            throw new ValidationException(e.Message, e);
        }

        ValidationException.CheckCondition(
            PathOps.GetFileName(series).Equals("series", StringComparison.Ordinal),
            string.Format(
                "Custom patch series file names besides `series` are not supported. Please update"
                    + " your series parameter {0} to end in `series`.",
                series));
    }

    private ValidationLevel GetValidationLevel(string validationLevelString)
    {
        ValidationLevel validationLevel = StringToValidationLevel(validationLevelString);
        if (_patchingOptions.ValidateOnLoad == false)
        {
            validationLevel = ValidationLevel.None;
        }
        else if (_patchingOptions.ValidateOnLoad == true)
        {
            validationLevel = ValidationLevel.Full;
        }
        return validationLevel;
    }

    private static ValidationLevel StringToValidationLevel(string value) =>
        value switch
        {
            "FULL" => ValidationLevel.Full,
            "OPTIONAL_SERIES" => ValidationLevel.OptionalSeries,
            "NONE" => ValidationLevel.None,
            _ => throw StarlarkRt.Errorf(
                "Invalid value '{0}' for field 'validation_level'. Valid values are: FULL,"
                    + " OPTIONAL_SERIES, NONE",
                value),
        };

    private static string GetPatchesDirName(string series)
    {
        string? parentFolder = PathOps.GetParent(series);
        if (parentFolder != null)
        {
            return PathOps.GetFileName(parentFolder);
        }
        return ".";
    }

    private ConfigFile? Resolve(string path, ValidationLevel validationLevel)
    {
        try
        {
            return _configFile.Resolve(path);
        }
        catch (CannotResolveLabel)
        {
            if (validationLevel == ValidationLevel.None)
            {
                _generalOptions.GetConsole().InfoFmt("Cannot load: {0}", path);
                return null;
            }
            throw StarlarkRt.Errorf("Failed to resolve patch: {0}", path);
        }
    }

    private ConfigFile? ParseSeries(
        string series,
        ImmutableArray<ConfigFile>.Builder outputBuilder,
        ValidationLevel validationLevel)
    {
        ConfigFile? seriesFile = null;
        try
        {
            // Don't use this.Resolve(), because its error message mentions patch file not series.
            try
            {
                seriesFile = _configFile.Resolve(series.Trim());
            }
            catch (CannotResolveLabel e)
            {
                switch (validationLevel)
                {
                    case ValidationLevel.None:
                    case ValidationLevel.OptionalSeries:
                        _generalOptions.GetConsole().InfoFmt("Cannot load {0}: {1}", series.Trim(), e);
                        return null;
                    default:
                        throw;
                }
            }

            var patchesBuilder = ImmutableArray.CreateBuilder<ConfigFile>();
            foreach (string rawLine in SplitLines(seriesFile.ReadContent()))
            {
                string line = rawLine;
                // Comment at the beginning of the line or a whitespace followed by the hash char.
                int comment = line.IndexOf('#');
                if (comment != 0)
                {
                    if (comment > 0 && char.IsWhiteSpace(line[comment - 1]))
                    {
                        line = line.Substring(0, comment - 1).Trim();
                    }
                    if (line.Length != 0)
                    {
                        try
                        {
                            patchesBuilder.Add(seriesFile.Resolve(line));
                        }
                        catch (CannotResolveLabel e)
                        {
                            if (validationLevel == ValidationLevel.None)
                            {
                                _generalOptions.GetConsole().InfoFmt("Cannot load {0}: {1}", line, e);
                            }
                            else
                            {
                                throw;
                            }
                        }
                    }
                }
            }
            outputBuilder.AddRange(patchesBuilder);
        }
        catch (CannotResolveLabel e)
        {
            throw StarlarkRt.Errorf(
                "Error reading patch series file: {0}. Caused by: {1}", series, e.ToString());
        }
        catch (IOException e)
        {
            throw StarlarkRt.Errorf(
                "Error reading patch series file: {0}. Caused by: {1}", series, e.ToString());
        }
        if (validationLevel == ValidationLevel.Full)
        {
            ValidationException.CheckCondition(
                seriesFile != null && outputBuilder.Count != 0,
                string.Format(
                    "Patch series {0} cannot be empty for full validation.", series));
        }
        return seriesFile;
    }

    // Splitter.onPattern("\\r?\\n").omitEmptyStrings().trimResults()
    private static IEnumerable<string> SplitLines(string content)
    {
        foreach (string raw in Lines.Split(content))
        {
            string trimmed = raw.Trim();
            if (trimmed.Length != 0)
            {
                yield return trimmed;
            }
        }
    }
}
