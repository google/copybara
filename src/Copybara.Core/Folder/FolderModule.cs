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

using Copybara.Authoring;
using Copybara.Common;
using Copybara.Util;
using Starlark.Annot;
using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Folder;

/// <summary>Main module that groups all the functions related to folders.</summary>
[StarlarkBuiltin("folder", Doc = "Module for dealing with local filesystem folders")]
public class FolderModule : IStarlarkValue
{
    private const string DestinationVar = "destination";

    private readonly FolderOriginOptions _originOptions;
    private readonly FolderDestinationOptions _destinationOptions;
    private readonly GeneralOptions _generalOptions;

    public FolderModule(
        FolderOriginOptions originOptions,
        FolderDestinationOptions destinationOptions,
        GeneralOptions generalOptions)
    {
        _originOptions = Preconditions.CheckNotNull(originOptions);
        _destinationOptions = Preconditions.CheckNotNull(destinationOptions);
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
    }

    [StarlarkMethod(
        DestinationVar,
        Doc =
            "A folder destination is a destination that puts the output in a folder. It can be used"
            + " both for testing or real production migrations."
            + "Given that folder destination does not support a lot of the features of real VCS, "
            + "there are some limitations on how to use it:"
            + "<ul>"
            + "<li>It requires passing a ref as an argument, as there is no way of calculating "
            + "previous migrated changes. Alternatively, --last-rev can be used, which could migrate "
            + "N changes."
            + "<li>Most likely, the workflow should use 'SQUASH' mode, as history is not supported."
            + "<li>If 'ITERATIVE' mode is used, a new temp directory will be created for each change "
            + "migrated."
            + "</ul>")]
    public FolderDestination Destination() =>
        new(_generalOptions, _destinationOptions);

    [StarlarkMethod(
        "origin",
        Doc =
            "A folder origin is a origin that uses a folder as input. The folder is specified via "
            + "the source_ref argument.")]
    public FolderOrigin Origin(
        [Param(
            Name = "materialize_outside_symlinks",
            Doc = "DEPRECATED - equivalent to outside_symlinks_mode='MATERIALIZE'",
            DefaultValue = "None",
            AllowedTypes = new[] { typeof(bool), typeof(NoneType) },
            Named = true)]
        object materializeOutsideSymlinks,
        [Param(
            Name = "inside_symlinks_mode",
            Doc =
                "How to handle symlinks pointing inside the origin folder. Possible values:"
                + " 'COPY_AS_IS' (copy the symlink as-is), 'MATERIALIZE' (copy the content of"
                + " the target instead of the symlink), 'IGNORE' (ignore the symlink), 'FAIL'"
                + " (fail the operation). Defaults to 'COPY_AS_IS'.",
            DefaultValue = "None",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) },
            Named = true)]
        object insideSymlinksMode,
        [Param(
            Name = "outside_symlinks_mode",
            Doc =
                "How to handle symlinks pointing outside the origin folder. See"
                + " inside_symlinks_mode for possible values. Defaults to 'FAIL'.",
            DefaultValue = "None",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) },
            Named = true)]
        object outsideSymlinksMode,
        [Param(
            Name = "broken_symlinks_mode",
            Doc =
                "How to handle broken symlinks. See inside_symlinks_mode for possible values"
                + " (except 'MATERIALIZE', which is invalid for broken symlinks). Defaults to"
                + " 'FAIL'.",
            DefaultValue = "None",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) },
            Named = true)]
        object brokenSymlinksMode)
    {
        bool materializeOutsideSymlinksSet = !ReferenceEquals(materializeOutsideSymlinks, StarlarkRt.None);
        bool ignoreInvalidSymlinksSet = _originOptions.IgnoreInvalidSymlinks != null;
        bool modernSymlinkOptionsSet =
            !ReferenceEquals(insideSymlinksMode, StarlarkRt.None)
            || !ReferenceEquals(outsideSymlinksMode, StarlarkRt.None)
            || !ReferenceEquals(brokenSymlinksMode, StarlarkRt.None);

        if (materializeOutsideSymlinksSet)
        {
            _generalOptions
                .GetConsole()
                .Warn(
                    "folder.origin(materialize_outside_symlinks = ...) is deprecated. Use"
                        + " outside_symlinks_mode instead.");
        }
        if (ignoreInvalidSymlinksSet)
        {
            _generalOptions
                .GetConsole()
                .Warn(
                    "--folder-origin-ignore-invalid-symlinks is deprecated. Use"
                        + " folder.origin(outside_symlinks_mode = ..., broken_symlinks_mode = ...)"
                        + " instead.");
        }
        if (modernSymlinkOptionsSet && (materializeOutsideSymlinksSet || ignoreInvalidSymlinksSet))
        {
            throw StarlarkRt.Errorf(
                "Cannot mix deprecated symlink configuration ('materialize_outside_symlinks' Starlark"
                    + " parameter or '--folder-origin-ignore-invalid-symlinks' CLI flag) with new"
                    + " symlink mode parameters ('inside_symlinks_mode', 'outside_symlinks_mode',"
                    + " 'broken_symlinks_mode')");
        }

        FileUtil.CopySymlinkStrategy symlinkStrategy;
        if (modernSymlinkOptionsSet)
        {
            try
            {
                FileUtil.SymlinkMode inside =
                    ReferenceEquals(insideSymlinksMode, StarlarkRt.None)
                        ? FileUtil.SymlinkMode.CopyAsIs
                        : ParseSymlinkMode((string)insideSymlinksMode);
                FileUtil.SymlinkMode outside =
                    ReferenceEquals(outsideSymlinksMode, StarlarkRt.None)
                        ? FileUtil.SymlinkMode.Fail
                        : ParseSymlinkMode((string)outsideSymlinksMode);
                FileUtil.SymlinkMode broken =
                    ReferenceEquals(brokenSymlinksMode, StarlarkRt.None)
                        ? FileUtil.SymlinkMode.Fail
                        : ParseSymlinkMode((string)brokenSymlinksMode);
                symlinkStrategy = new FileUtil.CopySymlinkStrategy(inside, outside, broken);
            }
            catch (ArgumentException e)
            {
                throw StarlarkRt.Errorf("Invalid symlink configuration: {0}", e.Message);
            }
        }
        else
        {
            bool materializeOutside =
                materializeOutsideSymlinksSet && (bool)materializeOutsideSymlinks;
            bool ignoreInvalid =
                ignoreInvalidSymlinksSet && _originOptions.IgnoreInvalidSymlinks!.Value;
            symlinkStrategy =
                new FileUtil.CopySymlinkStrategy(
                    inside: FileUtil.SymlinkMode.CopyAsIs,
                    outside: ignoreInvalid
                        ? FileUtil.SymlinkMode.Ignore
                        : (materializeOutside
                            ? FileUtil.SymlinkMode.Materialize
                            : FileUtil.SymlinkMode.Fail),
                    broken: ignoreInvalid ? FileUtil.SymlinkMode.Ignore : FileUtil.SymlinkMode.Fail);
        }

        string fs = _generalOptions.GetFileSystem();
        return new FolderOrigin(
            fs,
            Author.Parse(_originOptions.Author),
            _originOptions.Message,
            _generalOptions.GetCwd(),
            symlinkStrategy,
            _originOptions.Version);
    }

    /// <summary>
    /// Parses the Starlark symlink-mode strings ('COPY_AS_IS', 'MATERIALIZE', 'IGNORE', 'FAIL') into
    /// the C# <see cref="FileUtil.SymlinkMode"/> enum (whose members are PascalCase).
    /// </summary>
    private static FileUtil.SymlinkMode ParseSymlinkMode(string value) =>
        value switch
        {
            "COPY_AS_IS" => FileUtil.SymlinkMode.CopyAsIs,
            "MATERIALIZE" => FileUtil.SymlinkMode.Materialize,
            "IGNORE" => FileUtil.SymlinkMode.Ignore,
            "FAIL" => FileUtil.SymlinkMode.Fail,
            _ => throw new ArgumentException($"No enum constant for symlink mode: {value}"),
        };
}
