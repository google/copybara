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
using System.Text;
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Util.Console;

namespace Copybara.Util;

/// <summary>
/// Diff utilities that are repository-agnostic. Port of
/// <c>com.google.copybara.util.DiffUtil</c>.
///
/// <para>Diffing is implemented by shelling out to the <c>git</c> binary via
/// <see cref="CommandRunner"/> (<c>git diff --no-index</c> / <c>git apply</c>), mirroring the
/// upstream <c>FoldersDiff</c> behavior. This uses git as a repo-agnostic diff/patch engine
/// (<c>--git-dir=/dev/null</c>) so no repository is required.</para>
/// </summary>
public static class DiffUtil
{
    private static readonly byte[] EmptyDiff = Array.Empty<byte>();

    /// <summary>
    /// Calculates the diff between two sibling directory trees.
    ///
    /// <para>Returns the diff as an encoding-independent <c>byte[]</c>.</para>
    /// </summary>
    public static byte[] Diff(
        string one, string other, bool verbose, IReadOnlyDictionary<string, string> environment) =>
        new FoldersDiff(verbose, environment).Run(GetParent(one), one, other);

    /// <summary>
    /// Calculates the diff between two sibling directory trees while setting --ignore-cr-at-eol.
    ///
    /// <para>Returns the diff as an encoding-independent <c>byte[]</c>.</para>
    /// </summary>
    public static byte[] DiffWithIgnoreCrAtEol(
        string one, string other, bool verbose, IReadOnlyDictionary<string, string> environment) =>
        new FoldersDiff(verbose, environment)
            .WithIgnoreCrAtEol()
            .Run(GetParent(one), one, other);

    /// <summary>
    /// Calculates the diff between two files with --ignore-cr-at-eol set.
    ///
    /// <para>Returns the single file diff as an encoding-independent <c>byte[]</c>.</para>
    /// </summary>
    public static byte[] DiffFileWithIgnoreCrAtEol(
        string root,
        string one,
        string other,
        bool verbose,
        IReadOnlyDictionary<string, string> environment) =>
        new FoldersDiff(verbose, environment)
            .WithIgnoreCrAtEol()
            .WithSingleFile()
            .Run(root, one, other);

    /// <summary>Filter a diff output to only include diffs for original files that match a filter.</summary>
    public static string FilterDiff(byte[] diff, Func<string, bool> pathFilter)
    {
        bool include = true;
        var filteredDiff = new StringBuilder();
        foreach (var line in Encoding.UTF8.GetString(diff).Split('\n'))
        {
            if (line.StartsWith("diff ", StringComparison.Ordinal))
            {
                var diffHeader = line.Split(' ');
                // Given a diff in the format of:
                //     diff --git a/left/copybara/util/Test.java b/right/copybara/util/Test.java
                // Returns "left/copybara/util/Test.java"
                string path = diffHeader[2].Substring(2);
                include = pathFilter(path);
            }
            if (include)
            {
                filteredDiff.Append(line).Append('\n');
            }
        }
        // Nothing to add
        if (filteredDiff.Length == 0)
        {
            return "";
        }
        return filteredDiff.ToString();
    }

    /// <summary>
    /// Return the changed files without computing renames/copies.
    ///
    /// <para>Each file name is relative to one/other paths.</para>
    /// </summary>
    public static ImmutableArray<DiffFile> DiffFiles(
        string one, string other, bool verbose, IReadOnlyDictionary<string, string>? environment)
    {
        string cmdResult =
            Encoding.UTF8.GetString(
                new FoldersDiff(verbose, environment)
                    .WithZOption()
                    .WithNameStatus()
                    .WithNoRenames()
                    .Run(GetParent(one), one, other));

        var result = ImmutableArray.CreateBuilder<DiffFile>();
        // Split on NUL. Consume the resulting tokens in (status, file) pairs.
        var tokens = cmdResult.Split('\0');
        int i = 0;
        while (i < tokens.Length)
        {
            string strOp = tokens[i++];
            if (string.IsNullOrEmpty(strOp))
            {
                continue;
            }
            if (!DiffFile.OpByChar.TryGetValue(strOp, out var op))
            {
                throw new InvalidOperationException(
                    $"Unknown type '{strOp}'. Text:\n{cmdResult}");
            }
            Preconditions.CheckState(i < tokens.Length, "Missing file name after status '{0}'", strOp);
            string file = tokens[i++];
            Preconditions.CheckState(file.Contains('/'), "Expected a path with a separator: {0}", file);
            result.Add(new DiffFile(file.Substring(file.IndexOf('/') + 1), op));
        }
        return result.ToImmutable();
    }

    /// <summary>
    /// Apply the patches in reverse to the directory using git apply. At least one of either
    /// <paramref name="patchBytes"/> or a nonempty <paramref name="patchFiles"/> should be supplied.
    /// </summary>
    /// <param name="patchBytes">an optional diff that will be streamed to the command through stdin.</param>
    /// <param name="patchFiles">a list of paths to patch files that will be supplied to the command.</param>
    public static void ReverseApplyPatches(
        byte[]? patchBytes,
        IReadOnlyList<string> patchFiles,
        string applyDirectory,
        IReadOnlyDictionary<string, string> environment) =>
        FoldersDiff.ReverseApplyPatches(patchBytes, patchFiles, applyDirectory, environment);

    /// <summary>Given a git compatible diff, returns the diff colorized if the console allows it.</summary>
    public static string Colorize(Console.Console console, string diffText)
    {
        var sb = new StringBuilder();
        foreach (var line in diffText.Split('\n'))
        {
            sb.Append('\n');
            if (line.StartsWith("diff ", StringComparison.Ordinal))
            {
                sb.Append(console.Colorize(AnsiColor.Cyan, line));
            }
            else if (line.StartsWith("rename ", StringComparison.Ordinal))
            {
                sb.Append(console.Colorize(AnsiColor.Yellow, line));
            }
            else if (line.StartsWith("+", StringComparison.Ordinal))
            {
                sb.Append(console.Colorize(AnsiColor.Green, line));
            }
            else if (line.StartsWith("-", StringComparison.Ordinal))
            {
                sb.Append(console.Colorize(AnsiColor.Red, line));
            }
            else
            {
                sb.Append(line);
            }
        }
        return sb.ToString();
    }

    private static string GetParent(string path)
    {
        string? parent = System.IO.Path.GetDirectoryName(path);
        return parent ?? throw new ArgumentException($"Path '{path}' has no parent directory.");
    }

    /// <summary>Resolves the git binary honoring GIT_EXEC_PATH, mirroring GitEnvironment.</summary>
    private static string ResolveGitBinary(IReadOnlyDictionary<string, string> environment)
    {
        if (environment.TryGetValue("GIT_EXEC_PATH", out var execPath))
        {
            return System.IO.Path.Combine(execPath, "git");
        }
        return "git";
    }

    /// <summary>Executes git diff between two folders.</summary>
    private sealed class FoldersDiff
    {
        private static readonly Regex OutputErrorPattern =
            new("^error:", RegexOptions.Multiline);

        private readonly bool _nameStatus;
        private readonly bool _noRenames;
        private readonly bool _zOption;
        private readonly bool _ignoreCrAtEol;
        private readonly bool _singleFile;
        private readonly bool _verbose;
        private readonly IReadOnlyDictionary<string, string> _environment;

        internal FoldersDiff(bool verbose, IReadOnlyDictionary<string, string>? environment)
            : this(verbose, environment ?? new Dictionary<string, string>(),
                false, false, false, false, false)
        {
        }

        private FoldersDiff(
            bool verbose,
            IReadOnlyDictionary<string, string> environment,
            bool nameStatus,
            bool noRenames,
            bool zOption,
            bool ignoreCrAtEol,
            bool singleFile)
        {
            _verbose = verbose;
            _environment = environment;
            _nameStatus = nameStatus;
            _noRenames = noRenames;
            _zOption = zOption;
            _ignoreCrAtEol = ignoreCrAtEol;
            _singleFile = singleFile;
        }

        internal FoldersDiff WithNameStatus() =>
            new(_verbose, _environment, true, _noRenames, _zOption, _ignoreCrAtEol, _singleFile);

        internal FoldersDiff WithNoRenames() =>
            new(_verbose, _environment, _nameStatus, true, _zOption, _ignoreCrAtEol, _singleFile);

        internal FoldersDiff WithZOption() =>
            new(_verbose, _environment, _nameStatus, _noRenames, true, _ignoreCrAtEol, _singleFile);

        internal FoldersDiff WithIgnoreCrAtEol() =>
            new(_verbose, _environment, _nameStatus, _noRenames, _zOption, true, _singleFile);

        internal FoldersDiff WithSingleFile() =>
            new(_verbose, _environment, _nameStatus, _noRenames, _zOption, _ignoreCrAtEol, true);

        internal byte[] Run(string root, string one, string other)
        {
            Preconditions.CheckArgument(
                _singleFile || string.Equals(GetParent(one), GetParent(other)),
                "Paths 'one' and 'other' must be sibling directories.");

            var @params = new List<string>
            {
                ResolveGitBinary(_environment),
                // We want to use `git apply`/`git diff` as a glorified diff command without any
                // git repo involvement. Make sure git doesn't accidentally pick up some
                // git repo from higher up the directory tree.
                "--git-dir=/dev/null",
                // override diff.noprefix for consistent diff output, must come after "git"
                "-c",
                "diff.noprefix=false",
                "diff",
                "--no-color",
                "--no-index",
                // Be careful, no test coverage for these:
                "--no-ext-diff",
            };
            if (_nameStatus)
            {
                @params.Add("--name-status");
            }
            if (_noRenames)
            {
                @params.Add("--no-renames");
            }
            if (_zOption)
            {
                @params.Add("-z");
            }
            if (_ignoreCrAtEol)
            {
                @params.Add("--ignore-cr-at-eol");
            }

            @params.Add("--");
            @params.Add(Relativize(root, one));
            @params.Add(Relativize(root, other));

            var cmd = new Command(@params.ToArray(), _environment, root);
            try
            {
                new CommandRunner(cmd).WithVerbose(_verbose).Execute();
                return EmptyDiff;
            }
            catch (BadExitStatusWithOutputException e)
            {
                CommandOutput output = e.GetOutput();
                // git diff returns exit status 0 when contents are identical, or 1 when different.
                string outputError = output.GetStderr();
                if (!string.IsNullOrEmpty(outputError) && OutputErrorPattern.IsMatch(outputError))
                {
                    throw new IOException(
                        $"Error executing 'git diff': {e.Message}. Stderr: \n{output.GetStderr()}", e);
                }
                return output.GetStdoutBytes();
            }
            catch (CommandException e)
            {
                throw new IOException("Error executing 'git diff'", e);
            }
        }

        internal static void ReverseApplyPatches(
            byte[]? patchBytes,
            IReadOnlyList<string> patchFiles,
            string applyDirectory,
            IReadOnlyDictionary<string, string> environment)
        {
            var @params = new List<string>
            {
                ResolveGitBinary(environment),
                // Same rationale as in Run: keep git from picking up a surrounding repo.
                "--git-dir=/dev/null",
                "apply",
                "--reverse",
                "-p2",
                "--allow-empty",
            };
            @params.AddRange(patchFiles);
            if (patchBytes != null)
            {
                @params.Add("-");
            }

            var cmd = new Command(@params.ToArray(), environment, applyDirectory);
            try
            {
                var runner = new CommandRunner(cmd).WithVerbose(true);
                if (patchBytes != null)
                {
                    runner = runner.WithInput(patchBytes);
                }
                runner.Execute();
            }
            catch (CommandException e)
            {
                throw new IOException("Error executing 'git apply'", e);
            }
        }

        private static string Relativize(string root, string path) =>
            System.IO.Path.GetRelativePath(root, path);
    }
}
