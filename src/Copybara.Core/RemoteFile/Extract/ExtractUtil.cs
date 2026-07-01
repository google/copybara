/*
 * Copyright (C) 2023 Google Inc.
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

using System.Formats.Tar;
using System.IO.Compression;
using Copybara.Exceptions;
using Copybara.Util;

namespace Copybara.RemoteFile.Extract;

/// <summary>
/// A utility to extract a compressed archive to a target folder. Accepts a <see cref="Glob"/> to
/// filter out which files should be copied.
///
/// <para>Upstream uses commons-compress. This port uses the .NET in-box archive readers:
/// <see cref="ZipArchive"/> for zip/jar, <see cref="System.Formats.Tar.TarReader"/> for tar (with
/// <see cref="GZipStream"/> for tar.gz). XZ and BZip2 are not supported by the BCL and are left as
/// TODO(port).</para>
/// </summary>
public static class ExtractUtil
{
    /// <summary>Helper to read an archive from a stream.</summary>
    /// <exception cref="IOException"/>
    /// <exception cref="ValidationException"/>
    public static void ExtractArchive(
        Stream contents, string targetPath, ExtractType type, Glob? fileFilter)
    {
        string root = PathOps.Normalize(Path.GetFullPath(targetPath));
        IPathMatcher? rootedFilter = fileFilter?.RelativeTo(root);

        switch (type)
        {
            case ExtractType.JAR:
            case ExtractType.ZIP:
                ExtractZip(contents, root, rootedFilter);
                break;
            case ExtractType.TAR:
                ExtractTar(contents, root, rootedFilter);
                break;
            case ExtractType.TAR_GZ:
                using (var gz = new GZipStream(contents, CompressionMode.Decompress, leaveOpen: true))
                {
                    ExtractTar(gz, root, rootedFilter);
                }
                break;
            case ExtractType.TAR_XZ:
                // TODO(port): XZ decompression is not available in the .NET BCL. A third-party
                // package (e.g. SharpCompress or XZ.NET) would be required to support TAR_XZ.
                throw new ValidationException(
                    "TAR_XZ archives are not yet supported in the .NET port (no in-box XZ codec).");
            case ExtractType.TAR_BZ2:
                // TODO(port): BZip2 decompression is not available in the .NET BCL. A third-party
                // package (e.g. SharpCompress) would be required to support TAR_BZ2.
                throw new ValidationException(
                    "TAR_BZ2 archives are not yet supported in the .NET port (no in-box BZip2 codec).");
            default:
                throw new ValidationException(
                    $"Failed to get archive input stream for file type: {type}");
        }
    }

    private static void ExtractZip(Stream contents, string root, IPathMatcher? rootedFilter)
    {
        using var zip = new ZipArchive(contents, ZipArchiveMode.Read, leaveOpen: true);
        foreach (var entry in zip.Entries)
        {
            // A directory entry in zip has an empty name / a trailing slash and zero length.
            bool isDirectory = entry.Name.Length == 0 || entry.FullName.EndsWith('/');
            if (!TryResolveEntry(entry.FullName, root, isDirectory, rootedFilter, out string resolvedPath))
            {
                continue;
            }
            Directory.CreateDirectory(Path.GetDirectoryName(resolvedPath)!);
            using var source = entry.Open();
            using var dest = File.Create(resolvedPath);
            source.CopyTo(dest);
        }
    }

    private static void ExtractTar(Stream contents, string root, IPathMatcher? rootedFilter)
    {
        using var reader = new TarReader(contents, leaveOpen: true);
        while (reader.GetNextEntry() is { } entry)
        {
            bool isDirectory = entry.EntryType is TarEntryType.Directory;
            if (!TryResolveEntry(entry.Name, root, isDirectory, rootedFilter, out string resolvedPath))
            {
                continue;
            }
            Directory.CreateDirectory(Path.GetDirectoryName(resolvedPath)!);
            if (entry.DataStream is { } data)
            {
                using var dest = File.Create(resolvedPath);
                data.CopyTo(dest);
            }
        }
    }

    /// <summary>
    /// Resolves an archive entry name against <paramref name="root"/>, applying the zip-slip check
    /// and the file filter. Returns false if the entry should be skipped.
    /// </summary>
    /// <exception cref="IOException">if the entry escapes the target dir.</exception>
    private static bool TryResolveEntry(
        string entryName, string root, bool isDirectory, IPathMatcher? rootedFilter,
        out string resolvedPath)
    {
        resolvedPath = PathOps.Normalize(PathOps.Resolve(root, entryName));

        // Security check: Prevent Zip Slip vulnerability
        if (!PathOps.StartsWith(resolvedPath, root))
        {
            throw new IOException("Zip entry is outside of the target dir: " + entryName);
        }

        if ((rootedFilter != null && !rootedFilter.Matches(resolvedPath)) || isDirectory)
        {
            return false;
        }
        return true;
    }
}
