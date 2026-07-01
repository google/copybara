/*
 * Copyright (C) 2023 Google LLC
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

using Copybara.Config;
using Copybara.Exceptions;
using Copybara.RemoteFile.Extract;
using Copybara.Util;
using Starlark.Annot;
using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Archive;

/// <summary>A module for handling archives in Starlark.</summary>
[StarlarkBuiltin("archive", Doc = "Functions to work with archives.")]
public class ArchiveModule : IStarlarkValue
{
    public ArchiveModule()
    {
    }

    [StarlarkMethod(
        "create",
        Doc = "Creates an archive, possibly compressed, from a list of files.")]
    public void CreateArchive(
        [Param(Name = "archive", Doc = "Expected path of the generated archive file.", Named = true)]
        CheckoutPath archivePath,
        [Param(
            Name = "files",
            Doc =
                "An optional glob to describe the list of file paths that are to be included in the"
                + " archive. If not specified, all files under the current working directory"
                + " will be included. Note, the original file path in the filesystem will be"
                + " preserved when archiving it.",
            Named = true,
            DefaultValue = "None",
            AllowedTypes = new[] { typeof(Glob), typeof(NoneType) })]
        object files)
    {
        ExtractType type = ResolveArchiveType(archivePath);
        try
        {
            using Stream os = File.Create(archivePath.FullPath());
            ArchiveUtil.CreateArchive(
                os, type, archivePath, SkylarkUtil.ConvertFromNoneable<Glob?>(files, null));
        }
        catch (Exception e) when (e is IOException or ValidationException)
        {
            throw StarlarkRt.Errorf("There was an error creating the archive: {0}", e.ToString());
        }
    }

    [StarlarkMethod(
        "extract",
        Doc = "Extract the contents of the archive to a path.")]
    public void Extract(
        [Param(Name = "archive", Named = true, Doc = "The path to the archive file.")]
        CheckoutPath archivePath,
        [Param(
            Name = "type",
            Named = true,
            Doc =
                "The archive type. Supported types: AUTO, JAR, ZIP, TAR, TAR_GZ, TAR_XZ, and"
                + " TAR_BZ2. AUTO will try to infer the archive type automatically.",
            DefaultValue = "\"AUTO\"")]
        string typeStr,
        [Param(
            Name = "destination_folder",
            Named = true,
            Doc =
                "The path to extract the archive to. This defaults to the directory where the"
                + " archive is located.",
            DefaultValue = "None",
            AllowedTypes = new[] { typeof(CheckoutPath), typeof(NoneType) })]
        object maybeDestination,
        [Param(
            Name = "paths",
            Named = true,
            Doc = "An optional glob that is used to filter the files extracted from the archive.",
            DefaultValue = "None",
            AllowedTypes = new[] { typeof(Glob), typeof(NoneType) })]
        object paths)
    {
        ExtractType type = typeStr.Equals("AUTO")
            ? ResolveArchiveType(archivePath)
            : SkylarkUtil.StringToEnum<ExtractType>("type", typeStr);

        CheckoutPath destination =
            SkylarkUtil.ConvertFromNoneable(maybeDestination, archivePath.Resolve(".."))!;

        try
        {
            using Stream contents = File.OpenRead(archivePath.FullPath());
            ExtractUtil.ExtractArchive(
                contents,
                destination.FullPath(),
                type,
                SkylarkUtil.ConvertFromNoneable<Glob?>(paths, null));
        }
        catch (Exception e) when (e is IOException or ValidationException)
        {
            throw StarlarkRt.Errorf("There was an error extracting the archive: {0}", e.ToString());
        }
    }

    private static ExtractType ResolveArchiveType(CheckoutPath archivePath)
    {
        string filename = PathOps.GetFileName(archivePath.GetPath());
        string extension = GetFileExtension(filename);
        switch (extension)
        {
            case "zip":
                return ExtractType.ZIP;
            case "jar":
                return ExtractType.JAR;
            case "tar":
                return ExtractType.TAR;
            case "tgz":
                return ExtractType.TAR_GZ;
            case "gz":
                if (filename.EndsWith(".tar.gz"))
                {
                    return ExtractType.TAR_GZ;
                }
                goto default;
            case "xz":
                if (filename.EndsWith(".tar.xz"))
                {
                    return ExtractType.TAR_XZ;
                }
                goto default;
            case "bz2":
                if (filename.EndsWith(".tar.bz2"))
                {
                    return ExtractType.TAR_BZ2;
                }
                goto default;
            default:
                throw StarlarkRt.Errorf(
                    "The archive type couldn't be inferred for the file: {0}",
                    archivePath.GetPath());
        }
    }

    /// <summary>
    /// Mirrors Guava's <c>Files.getFileExtension</c>: returns the substring after the last '.' in
    /// the file name, or the empty string if there is no dot.
    /// </summary>
    private static string GetFileExtension(string fileName)
    {
        int dotIndex = fileName.LastIndexOf('.');
        return dotIndex == -1 ? "" : fileName[(dotIndex + 1)..];
    }
}
