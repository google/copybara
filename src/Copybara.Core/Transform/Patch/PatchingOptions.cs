/*
 * Copyright (C) 2018 Google Inc.
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
using System.Runtime.InteropServices;
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Git;
using Copybara.Util;

namespace Copybara.Transform.Patch;

/// <summary>Options related to applying patches to directories (non-git).</summary>
public class PatchingOptions : IOption
{
    private static readonly Regex PatchVersionFormat =
        new(@"[\w ]+ (?<major>[0-9]+)\.(?<minor>[0-9]+)(\.[0-9]+)?.*",
            RegexOptions.Singleline);

    public const string SkipVersionCheckFlag = "--patch-skip-version-check";

    private readonly GeneralOptions _generalOptions;

    public PatchingOptions(GeneralOptions generalOptions)
    {
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
    }

    [Flag(
        "--patch-validate-on-load",
        "Override transform's validation level and force full or no validation",
        Arity = 1)]
    public bool? ValidateOnLoad { get; set; }

    [Flag(SkipVersionCheckFlag, "Skip checking the version of patch and assume it is fine")]
    public bool SkipVersionCheck { get; set; }

    [Flag(
        "--patch-use-git-apply",
        "Don't use GNU Patch and instead use 'git apply'",
        Arity = 1)]
    public bool UseGitApply { get; set; } = true;

    [Flag("--quilt-bin", "Path to quilt command")]
    internal string QuiltBin { get; set; } = "quilt";

    /// <summary>
    /// Applies the diff into a directory tree.
    ///
    /// <para><paramref name="diffContents"/> is the result of invoking
    /// <see cref="DiffUtil.Diff"/>.</para>
    /// </summary>
    /// <exception cref="InsideGitDirException"/>
    /// <exception cref="ValidationException"/>
    public void Patch(
        string rootDir,
        byte[] diffContents,
        IReadOnlyList<string> excludedPaths,
        int stripSlashes,
        bool reverse,
        string? gitDir)
    {
        if (diffContents.Length == 0)
        {
            return;
        }
        Preconditions.CheckArgument(stripSlashes >= 0, "stripSlashes must be >= 0.");
        bool verbose = _generalOptions.IsVerbose();
        var env = _generalOptions.GetEnvironment();
        if (ShouldUsePatch(gitDir, excludedPaths))
        {
            Preconditions.CheckState(excludedPaths.Count == 0, "Not supported by GNU Patch");
            PatchWithGnuPatch(rootDir, diffContents, stripSlashes, verbose, reverse, env);
        }
        else
        {
            PatchWithGitApply(
                rootDir, diffContents, excludedPaths, stripSlashes, verbose, reverse, env, gitDir);
        }
    }

    internal GeneralOptions GetGeneralOptions() => _generalOptions;

    internal sealed class Version
    {
        private readonly int _major;
        private readonly int _minor;

        public Version(int major, int minor)
        {
            _major = major;
            _minor = minor;
        }

        public override string ToString() => _major + "." + _minor;

        /// <summary>If GNU Patch is too old for understanding renames, etc. (at least 2.7.0).</summary>
        public bool IsTooOld() => _major <= 2 && (_major != 2 || _minor < 7);
    }

    private bool ShouldUsePatch(string? gitDir, IReadOnlyList<string> excludedPaths)
    {
        // We are going to patch a git checkout dir. We should use git apply three way.
        if (gitDir != null)
        {
            return false;
        }
        if (SkipVersionCheck)
        {
            ValidationException.CheckCondition(
                excludedPaths.Count == 0,
                "%s is incompatible with patch transformations that uses excluded paths: %s",
                SkipVersionCheckFlag, string.Join(", ", excludedPaths));
            return true;
        }
        // GNU Patch doesn't have a way to exclude paths
        if (UseGitApply || excludedPaths.Count != 0)
        {
            return false;
        }

        try
        {
            Version version = GetPatchVersion(_generalOptions.PatchBin);
            if (!version.IsTooOld())
            {
                return true;
            }

            if (IsMac())
            {
                _generalOptions
                    .GetConsole()
                    .WarnFmt(
                        "GNU Patch version is too old ({0}) to be used by Copybara. "
                            + "Defaulting to 'git apply'. Use {1} if patch is available in a"
                            + " different location",
                        version, GeneralOptions.PatchBinFlag);
                return false;
            }

            throw new ValidationException(
                string.Format(
                    "Too old version of GNU Patch ({0}). Copybara required at least 2.7 version."
                        + " Path used: {1}. Use {2} to use a different path",
                    version, _generalOptions.PatchBin, GeneralOptions.PatchBinFlag));
        }
        catch (CommandException e)
        {
            // While this might be an environment error, normally it is attributable to the user
            // (not having patch available).
            throw new ValidationException(
                string.Format(
                    "Error using GNU Patch. Path used: {0}. Use {1} to use a different path",
                    _generalOptions.PatchBin, GeneralOptions.PatchBinFlag),
                e);
        }
    }

    internal Version GetPatchVersion(string patchBin)
    {
        string @out = new CommandRunner(new Command(new[] { patchBin, "-v" }))
            .WithVerbose(_generalOptions.IsVerbose())
            .Execute()
            .GetStdout()
            .Trim();
        Match matcher = PatchVersionFormat.Match(@out);
        ValidationException.CheckCondition(
            matcher.Success,
            "Unknown version of GNU Patch. Path used: %s. Use %s to use a different path",
            patchBin,
            GeneralOptions.PatchBinFlag);
        int major = int.Parse(matcher.Groups["major"].Value);
        int minor = int.Parse(matcher.Groups["minor"].Value);
        return new Version(major, minor);
    }

    private static bool IsMac() => RuntimeInformation.IsOSPlatform(OSPlatform.OSX);

    private static void PatchWithGitApply(
        string rootDir,
        byte[] diffContents,
        IReadOnlyList<string> excludedPaths,
        int stripSlashes,
        bool verbose,
        bool reverse,
        IReadOnlyDictionary<string, string> environment,
        string? gitDir)
    {
        var gitEnv = new GitEnvironment(environment);
        var @params = ImmutableArray.CreateBuilder<string>();

        // Show verbose output unconditionally since it is helpful for debugging issues with patches.
        @params.Add(gitEnv.ResolveGitBinary());
        // If there is no git dir, we force it to /dev/null to make sure git doesn't accidentally
        // pick up some git repo from higher up the directory tree.
        @params.Add(
            "--git-dir=" + (gitDir == null ? "/dev/null" : System.IO.Path.GetFullPath(gitDir)));
        @params.Add("apply");
        @params.Add("-v");
        @params.Add("--stat");
        @params.Add("--apply");
        @params.Add("-p" + stripSlashes);
        if (gitDir != null)
        {
            @params.Add("--3way");
        }
        foreach (string excludedPath in excludedPaths)
        {
            @params.Add("--exclude");
            @params.Add(excludedPath);
        }
        if (reverse)
        {
            @params.Add("-R");
        }

        @params.Add("-");
        var cmd = new Command(@params.ToArray(), environment, rootDir);
        try
        {
            new CommandRunner(cmd)
                .WithVerbose(verbose)
                .WithInput(diffContents)
                .Execute();
        }
        catch (BadExitStatusWithOutputException e)
        {
            throw new IOException(
                string.Format(
                    "Error executing 'git apply': {0}. Stderr: \n{1}",
                    e.Message, e.GetOutput().GetStderr()),
                e);
        }
        catch (CommandException e)
        {
            throw new IOException("Error executing 'git apply'", e);
        }
    }

    private void PatchWithGnuPatch(
        string rootDir,
        byte[] diffContents,
        int stripSlashes,
        bool verbose,
        bool reverse,
        IReadOnlyDictionary<string, string> environment)
    {
        var @params = ImmutableArray.CreateBuilder<string>();

        // Show verbose output unconditionally since it is helpful for debugging issues with patches.
        // When the patch file doesn't match the file exactly, GNU patch creates backup files, but we
        // disable creating those as they don't make sense for Copybara and otherwise they would need
        // to be excluded.
        @params.Add(_generalOptions.PatchBin);
        @params.Add("--no-backup-if-mismatch");
        @params.Add("-t");
        @params.Add("-p" + stripSlashes);
        if (reverse)
        {
            @params.Add("-R");
        }

        // Only apply in the direction requested. Yes, -R --forward semantics is that it reverses
        // and only applies if can be applied like that (-R will try to apply reverse and forward).
        @params.Add("--forward");

        var cmd = new Command(@params.ToArray(), environment, rootDir);
        try
        {
            CommandOutputWithStatus output = _generalOptions.NewCommandRunner(cmd)
                .WithVerbose(verbose)
                .WithInput(diffContents)
                .Execute();
            System.Console.Error.WriteLine(output);
        }
        catch (BadExitStatusWithOutputException e)
        {
            throw new IOException(
                string.Format(
                    "Error executing 'patch': {0}. Stderr: \n{1}",
                    e.Message, e.GetOutput().GetStdout()),
                e);
        }
        catch (CommandException e)
        {
            throw new IOException("Error executing 'patch'", e);
        }
    }
}
