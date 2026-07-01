/*
 * Copyright (C) 2022 Google Inc.
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
using Copybara;

namespace Copybara.Util;

/// <summary>A utility class to automatically generate patch files.</summary>
public static class AutoPatchUtil
{
    /// <summary>
    /// Given two paths, generates patch files per-file.
    ///
    /// <para>Does not generate any patch files where there is no diff. Patch files are generated
    /// using git diff.</para>
    /// </summary>
    /// <param name="originWorkdir">workdir used on lhs of diffing statement, should be baseline or
    ///     origin workdir.</param>
    /// <param name="destinationWorkdir">workdir used on rhs of diffing statement, should be
    ///     destination workdir.</param>
    /// <param name="directoryPrefix">prefix to all filenames. patch files are written inside this
    ///     directory.</param>
    /// <param name="patchFileDirectory">optional directory, relative to directory prefix, in which
    ///     to place patch files.</param>
    /// <param name="verbose">forwards verbose setting to diffing command.</param>
    /// <param name="environment">environment variables.</param>
    /// <param name="patchFilePrefix">optional text prefix applied to the contents of all patch
    ///     files.</param>
    /// <param name="patchFileNameSuffix">suffix used for patch files e.g. .patch.</param>
    /// <param name="rootDirectory">directory in which to write all patch files.</param>
    /// <param name="stripFileNames">when true, strip filenames from patch file contents.</param>
    /// <param name="stripLineNumbers">when true, strip line numbers from patch file contents.</param>
    /// <param name="fileMatcher">used to prevent AutoPatchUtil from running on certain files.</param>
    public static void GeneratePatchFiles(
        string originWorkdir,
        string destinationWorkdir,
        string directoryPrefix,
        string? patchFileDirectory,
        bool verbose,
        IReadOnlyDictionary<string, string> environment,
        string? patchFilePrefix,
        string patchFileNameSuffix,
        string rootDirectory,
        bool stripFileNames,
        bool stripLineNumbers,
        Glob fileMatcher)
    {
        patchFilePrefix ??= "";
        patchFileDirectory ??= "";

        ImmutableArray<DiffFile> diffFiles =
            DiffUtil.DiffFiles(originWorkdir, destinationWorkdir, verbose, environment);
        var diffFileNames = diffFiles.Select(d => d.GetName()).ToImmutableHashSet();

        var relativeMatcher = fileMatcher.RelativeTo("");
        // TODO: make this configurable
        foreach (var diffFile in diffFiles)
        {
            if (diffFile.GetOperation() != DiffFile.Operation.MODIFIED)
            {
                continue;
            }
            if (!relativeMatcher.Matches("/" + diffFile.GetName()))
            {
                continue;
            }
            string fileName = diffFile.GetName();
            string onePath = PathOps.Resolve(originWorkdir, fileName);
            string otherPath = PathOps.Resolve(destinationWorkdir, fileName);
            if (!File.Exists(otherPath))
            {
                continue;
            }
            string diffString =
                Encoding.UTF8.GetString(
                    DiffUtil.DiffFileWithIgnoreCrAtEol(
                        PathOps.GetParent(originWorkdir)!, onePath, otherPath, verbose, environment));
            if (string.IsNullOrEmpty(diffString))
            {
                // diff was carriage return at end of line
                continue;
            }
            if (stripFileNames || stripLineNumbers)
            {
                diffString = StripFileNamesAndLineNumbers(diffString, stripFileNames, stripLineNumbers);
            }
            string patchFilePath =
                DerivePatchFileName(
                    directoryPrefix, patchFileDirectory, patchFileNameSuffix, rootDirectory, fileName);
            Directory.CreateDirectory(PathOps.GetParent(patchFilePath)!);
            File.WriteAllText(patchFilePath, patchFilePrefix + diffString);
        }

        string finalPatchFileDirectory = patchFileDirectory;

        // There is no longer a diff, but a patch file exists.
        foreach (var file in EnumerateFiles(originWorkdir))
        {
            string relative = PathOps.Relativize(originWorkdir, file);
            string patchFileName =
                DerivePatchFileName(
                    directoryPrefix,
                    finalPatchFileDirectory,
                    patchFileNameSuffix,
                    rootDirectory,
                    relative);
            string destPatch = PathOps.Resolve(destinationWorkdir, patchFileName);
            if (!diffFileNames.Contains(relative) && File.Exists(destPatch))
            {
                File.Delete(destPatch);
            }
        }

        // patch file exists, but the origin file was deleted.
        foreach (var file in EnumerateFiles(destinationWorkdir))
        {
            if (!file.EndsWith(patchFileNameSuffix, StringComparison.Ordinal))
            {
                continue;
            }
            string fileName =
                PathOps.Relativize(destinationWorkdir, file.Replace(patchFileNameSuffix, ""));
            fileName = fileName.Replace(directoryPrefix, "");
            fileName = TrimLeadingSeparators(fileName);
            if (!string.IsNullOrWhiteSpace(finalPatchFileDirectory))
            {
                fileName = fileName.Replace(finalPatchFileDirectory, "");
            }
            fileName = TrimLeadingSeparators(fileName);

            string originFile = PathOps.Resolve(PathOps.Resolve(originWorkdir, directoryPrefix), fileName);
            string rootDirectoryPatchFile =
                PathOps.Resolve(
                    PathOps.Resolve(
                        PathOps.Resolve(rootDirectory, directoryPrefix), finalPatchFileDirectory),
                    fileName + patchFileNameSuffix);
            if (!File.Exists(originFile) && File.Exists(rootDirectoryPatchFile))
            {
                File.Delete(rootDirectoryPatchFile);
            }
        }
    }

    public static void ReversePatchFiles(
        string diffRoot,
        string patchDir,
        string fileSuffix,
        IReadOnlyDictionary<string, string> environment)
    {
        var files = EnumerateFiles(patchDir)
            .Where(f => f.EndsWith(fileSuffix, StringComparison.Ordinal))
            .ToImmutableArray();
        DiffUtil.ReverseApplyPatches(null, files, diffRoot, environment);
    }

    public static void ReversePatch(
        string diffRoot, byte[] patchContent, IReadOnlyDictionary<string, string> environment)
    {
        DiffUtil.ReverseApplyPatches(patchContent, ImmutableArray<string>.Empty, diffRoot, environment);
    }

    public static Glob GetAutopatchGlob(string directoryPrefix, string? directory)
    {
        string autopatchDirectoryPath = directoryPrefix;
        if (directory != null)
        {
            autopatchDirectoryPath = PathOps.Resolve(autopatchDirectoryPath, directory);
        }
        autopatchDirectoryPath = PathOps.Resolve(autopatchDirectoryPath, "**");
        return Glob.CreateGlob(ImmutableArray.Create(autopatchDirectoryPath));
    }

    private static string DerivePatchFileName(
        string directoryPrefix,
        string patchFileDirectory,
        string patchFileNameSuffix,
        string rootDirectory,
        string fileName)
    {
        string fileRelativeDirectoryPrefix =
            PathOps.Relativize(directoryPrefix, fileName + patchFileNameSuffix);
        return PathOps.Resolve(
            PathOps.Resolve(rootDirectory, directoryPrefix),
            PathOps.Resolve(patchFileDirectory, fileRelativeDirectoryPrefix));
    }

    // Reimplementation of golang packaging code.
    private static string StripFileNamesAndLineNumbers(
        string diffString, bool stripFileNames, bool stripLineNumbers)
    {
        string diffChunk = diffString;
        if (stripFileNames)
        {
            string parsedDiffString = diffString.Substring(diffString.IndexOf("\n@@", StringComparison.Ordinal) + "\n".Length);
            diffChunk = "";
            while (parsedDiffString.Length > 0)
            {
                int i = parsedDiffString.IndexOf("\n@@", StringComparison.Ordinal) + "\n".Length;
                if (i <= 0 || i >= parsedDiffString.Length)
                {
                    diffChunk += parsedDiffString;
                    break;
                }
                diffChunk += parsedDiffString.Substring(0, i);
                parsedDiffString = parsedDiffString.Substring(i);
            }
        }
        if (stripLineNumbers)
        {
            // strip line numbers - of format @@ -1,1 +1,1 @@, sometimes of form @@ -1 +1 @@
            diffChunk = Regex.Replace(diffChunk, @"@@ -(\d+)(,\d+)? \+(\d+)(,\d+)? @@", "@@");
        }
        return diffChunk;
    }

    private static IEnumerable<string> EnumerateFiles(string root) =>
        Directory.Exists(root)
            ? Directory.EnumerateFiles(root, "*", SearchOption.AllDirectories)
            : Enumerable.Empty<string>();

    private static string TrimLeadingSeparators(string path) =>
        path.TrimStart('/', Path.DirectorySeparatorChar);
}
