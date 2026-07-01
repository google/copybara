/*
 * Copyright (C) 2021 Google Inc.
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
using Copybara.Exceptions;
using Copybara.Util;
using Starlark.Syntax;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Transform.Patch;

/// <summary>
/// Transformation for applying and updating patch files using Quilt during a workflow. Instantiated
/// by <see cref="PatchModule"/>.
/// </summary>
public sealed class QuiltTransformation : ITransformation
{
    private readonly ConfigFile? _series;
    private readonly ImmutableArray<ConfigFile> _patchFiles;
    private readonly PatchingOptions _options;

    // TODO(copybara-team): Add support for reverse=True.
    private readonly bool _reverse;
    private readonly string _directory;
    private readonly Location _location;
    private readonly string _patchesDirName;

    internal QuiltTransformation(
        ConfigFile? series,
        ImmutableArray<ConfigFile> patchFiles,
        PatchingOptions options,
        bool reverse,
        string directory,
        Location location,
        string patchesDirName)
    {
        _series = series;
        _patchFiles = patchFiles;
        _options = options;
        _reverse = reverse;
        _directory = Preconditions.CheckNotNull(directory);
        _location = Preconditions.CheckNotNull(location);
        _patchesDirName = Preconditions.CheckNotNull(patchesDirName);
    }

    /// <exception cref="ValidationException"/>
    public TransformationStatus Transform(TransformWork work)
    {
        if (_series == null)
        {
            return TransformationStatus.Success();
        }

        string quiltRunDir = PathOps.Resolve(work.GetCheckoutDir(), _directory);
        CreatePatchDirectory(quiltRunDir, work.GetConsole());

        // avoid setting up and cleaning up quilt if not needed
        if (_patchFiles.Length == 0)
        {
            CopySeriesFile(quiltRunDir);
            return TransformationStatus.Success();
        }

        bool verbose = _options.GetGeneralOptions().IsVerbose();
        work.GetConsole().InfoFmt("Applying and updating patches with quilt.");
        // "quilt import <patch_path>" only works for a local path, so we copy the patch files to a
        // local temp directory first and setup a fresh empty series file using these tmp paths.
        ImmutableArray<string> patches = CopyPatchFilesToTmpDir();
        var env = _options.GetGeneralOptions().GetEnvironment();
        env = InitializeQuilt(quiltRunDir, env);
        ImportPatches(quiltRunDir, patches, env, verbose);
        // restore the original series file with real paths
        CopySeriesFile(quiltRunDir);
        CleanupQuilt(quiltRunDir);
        return TransformationStatus.Success();
    }

    public ITransformation Reverse()
    {
        return new QuiltTransformation(
            _series, _patchFiles, _options, !_reverse, _directory, _location, _patchesDirName);
    }

    public string Describe() =>
        "Patch.quilt_apply: using quilt to apply and update patches: "
        + string.Join(", ", _patchFiles.Select(p => p.Path()));

    public Location Location() => _location;

    internal string GetPatchesDirName() => _patchesDirName;

    /// <exception cref="ValidationException"/>
    private void RunQuiltCommand(
        string quiltRunDir,
        IReadOnlyDictionary<string, string> env,
        bool verbose,
        params string[] args)
    {
        var @params = ImmutableArray.CreateBuilder<string>();
        @params.Add(_options.QuiltBin);
        @params.AddRange(args);
        ImmutableArray<string> paramsList = @params.ToImmutable();
        var cmd = new Command(paramsList.ToArray(), env, quiltRunDir);
        try
        {
            _options.GetGeneralOptions().NewCommandRunner(cmd)
                .WithVerbose(verbose)
                .Execute();
        }
        catch (BadExitStatusWithOutputException e)
        {
            var patchDoesNotApplyMsgMatcher = new Regex("Patch .* does not apply");
            if (patchDoesNotApplyMsgMatcher.IsMatch(e.GetOutput().GetStdout()))
            {
                throw new ValidationException(
                    string.Format(
                        "Error executing '{0}': Patch file does not apply. Stderr: \n{1}",
                        string.Join(" ", paramsList), e.GetOutput().GetStdout()),
                    e);
            }

            throw new IOException(
                string.Format(
                    "Error executing '{0}': {1}. Stderr: \n{2}",
                    string.Join(" ", paramsList), e.Message, e.GetOutput().GetStdout()),
                e);
        }
        catch (CommandException e)
        {
            throw new IOException("Error executing quilt", e);
        }
    }

    private ImmutableArray<string> CopyPatchFilesToTmpDir()
    {
        var builder = ImmutableArray.CreateBuilder<string>();
        string patchDir = _options.GetGeneralOptions().GetDirFactory().NewTempDir("inputpatches");
        foreach (ConfigFile patch in _patchFiles)
        {
            string baseName = PathOps.GetFileName(patch.Path());
            string patchFile = PathOps.Resolve(patchDir, baseName);
            builder.Add(patchFile);
            try
            {
                File.WriteAllBytes(patchFile, patch.ReadContentBytes());
            }
            catch (CannotResolveLabel e)
            {
                throw new IOException("Error reading input patch", e);
            }
        }
        return builder.ToImmutable();
    }

    private void CreatePatchDirectory(string quiltRunDir, Console console)
    {
        string patchesDir = PathOps.Resolve(quiltRunDir, _patchesDirName);
        if (Directory.Exists(patchesDir) || File.Exists(patchesDir))
        {
            console.WarnFmt(
                "Destination already has a '{0}' directory. Replacing files.", _patchesDirName);
        }
        Directory.CreateDirectory(patchesDir);
    }

    private void CopySeriesFile(string quiltRunDir)
    {
        string patchesDir = PathOps.Resolve(quiltRunDir, _patchesDirName);
        try
        {
            if (_series == null)
            {
                throw new CannotResolveLabel("Cannot find series file");
            }
            File.WriteAllBytes(PathOps.Resolve(patchesDir, "series"), _series.ReadContentBytes());
        }
        catch (CannotResolveLabel e)
        {
            throw new IOException("Error reading original 'series' file", e);
        }
    }

    /// <exception cref="ValidationException"/>
    private ImmutableDictionary<string, string> InitializeQuilt(
        string quiltRunDir, IReadOnlyDictionary<string, string> env)
    {
        // Creates quiltrc file and sets up QUILTRC environment variable.
        var quiltOptions = ImmutableDictionary.CreateBuilder<string, string>();
        quiltOptions["QUILT_NO_DIFF_TIMESTAMPS"] = "1";
        quiltOptions["QUILT_DIFF_OPTS"] = "--show-c-function";
        // Uses the "-p ab" format in order to keep patch files' content independent of the
        // parent directory's name.
        quiltOptions["QUILT_DIFF_ARGS"] = "-p ab --no-index";
        quiltOptions["QUILT_REFRESH_ARGS"] = "-p ab --no-index";
        quiltOptions["QUILT_PATCHES_PREFIX"] = "yes";
        quiltOptions["QUILT_PATCHES"] = _patchesDirName;

        // It overwrites any existing copybara.quiltrc file, which is OK because it is in the
        // temporary directory and its content is always the same.
        string quiltrcPath =
            PathOps.Resolve(_options.GetGeneralOptions().GetDirFactory().GetTmpRoot(),
                "copybara.quiltrc");
        Directory.CreateDirectory(_options.GetGeneralOptions().GetDirFactory().GetTmpRoot());
        using (var wr = new StreamWriter(quiltrcPath, append: false))
        {
            foreach (var entry in quiltOptions)
            {
                wr.Write(string.Format("{0}=\"{1}\"\n", entry.Key, entry.Value));
            }
        }

        var envBuilder = ImmutableDictionary.CreateBuilder<string, string>();
        // Don't pass user settings through to Quilt.
        foreach (var var in env)
        {
            if (var.Key.StartsWith("QUILT_", StringComparison.Ordinal))
            {
                continue;
            }
            envBuilder[var.Key] = var.Value;
        }
        envBuilder["QUILTRC"] = System.IO.Path.GetFullPath(quiltrcPath);

        // Creates and checks for necessary directories.
        string pcDir = PathOps.Resolve(quiltRunDir, ".pc");
        if (Directory.Exists(pcDir) || File.Exists(pcDir))
        {
            throw new ValidationException(
                string.Format(
                    "Destination already has a '.pc' directory: {0}",
                    System.IO.Path.GetFullPath(pcDir)));
        }
        return envBuilder.ToImmutable();
    }

    /// <exception cref="ValidationException"/>
    private void ImportPatches(
        string quiltRunDir,
        ImmutableArray<string> patches,
        IReadOnlyDictionary<string, string> env,
        bool verbose)
    {
        foreach (string patch in patches)
        {
            string targetPatch =
                PathOps.Resolve(
                    PathOps.Resolve(quiltRunDir, _patchesDirName), PathOps.GetFileName(patch));
            if (File.Exists(targetPatch))
            {
                File.Delete(targetPatch);
            }
            RunQuiltCommand(quiltRunDir, env, verbose, "import", patch);
            RunQuiltCommand(quiltRunDir, env, verbose, "push");
            RunQuiltCommand(quiltRunDir, env, verbose, "refresh");
        }
    }

    private static void CleanupQuilt(string quiltRunDir)
    {
        // Deletes ".pc" directory.
        FileUtil.DeleteRecursively(PathOps.Resolve(quiltRunDir, ".pc"));
    }
}
