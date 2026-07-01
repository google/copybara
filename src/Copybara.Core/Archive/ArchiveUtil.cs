/*
 * Copyright (C) 2025 Google LLC
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
using System.Formats.Tar;
using System.IO.Compression;
using Copybara.Exceptions;
using Copybara.RemoteFile.Extract;
using Copybara.Util;

namespace Copybara.Archive;

/// <summary>
/// A utility class to generate a (compressed) archive at a target directory path. Accepts a
/// <see cref="Glob"/> to filter out which files need to be archived.
///
/// <para>Upstream uses commons-compress. This port uses the .NET in-box archive writers:
/// <see cref="ZipArchive"/> for zip/jar, <see cref="System.Formats.Tar.TarWriter"/> for tar (with
/// <see cref="GZipStream"/> for tar.gz). XZ is not supported by the BCL and is left as TODO(port).</para>
/// </summary>
public static class ArchiveUtil
{
    /// <summary>Internal utility to create an archive.</summary>
    /// <param name="os">generic <see cref="Stream"/> configured to write to the target archive file.</param>
    /// <param name="type">category of archive to generate based on target file extension.</param>
    /// <param name="archivePath">copybara checkout path to the archive file.</param>
    /// <param name="fileFilter">glob to filter the set of files to be included in the archive.</param>
    /// <exception cref="IOException"/>
    /// <exception cref="ValidationException"/>
    public static void CreateArchive(
        Stream os, ExtractType type, CheckoutPath archivePath, Glob? fileFilter)
    {
        switch (type)
        {
            case ExtractType.JAR:
            case ExtractType.ZIP:
                using (var zip = new ZipArchive(os, ZipArchiveMode.Create, leaveOpen: true))
                {
                    WriteFiles(archivePath, fileFilter, (relativePath, filePath) =>
                    {
                        var entry = zip.CreateEntry(relativePath);
                        using var entryStream = entry.Open();
                        using var source = File.OpenRead(filePath);
                        source.CopyTo(entryStream);
                    });
                }
                break;
            case ExtractType.TAR:
                using (var tar = new TarWriter(os, leaveOpen: true))
                {
                    WriteFiles(archivePath, fileFilter, (relativePath, filePath) =>
                        tar.WriteEntry(filePath, relativePath));
                }
                break;
            case ExtractType.TAR_GZ:
                using (var gz = new GZipStream(os, CompressionMode.Compress, leaveOpen: true))
                using (var tar = new TarWriter(gz, leaveOpen: true))
                {
                    WriteFiles(archivePath, fileFilter, (relativePath, filePath) =>
                        tar.WriteEntry(filePath, relativePath));
                }
                break;
            case ExtractType.TAR_XZ:
                // TODO(port): XZ compression is not available in the .NET BCL. A third-party
                // package (e.g. SharpCompress or XZ.NET) would be required to support TAR_XZ.
                throw new ValidationException(
                    "TAR_XZ archives are not yet supported in the .NET port (no in-box XZ codec).");
            default:
                throw new ValidationException(
                    $"Failed to get archive output stream for file type: {type}");
        }
    }

    private static void WriteFiles(
        CheckoutPath archivePath, Glob? fileFilter, Action<string, string> writeEntry)
    {
        // Get the current working directory
        string workdir = archivePath.GetCheckoutDir();

        // Exclude the "archive file" itself from getting added to the archived bundle.
        fileFilter ??= Glob.CreateGlob(ImmutableArray.Create("**"));
        fileFilter = Glob.Difference(
            fileFilter, Glob.CreateGlob(ImmutableArray.Create(archivePath.GetPath())));

        var matcher = fileFilter.RelativeTo(workdir);

        foreach (string filePath in Directory.EnumerateFiles(
                     workdir, "*", SearchOption.AllDirectories))
        {
            string full = PathOps.Normalize(Path.GetFullPath(filePath));
            if (!matcher.Matches(full))
            {
                continue;
            }
            string relativePath = PathOps.Relativize(workdir, full);
            writeEntry(relativePath, full);
        }
    }
}
