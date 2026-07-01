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

using Copybara.Exceptions;
using Copybara.Util;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Folder;

/// <summary>A <see cref="DestinationReader"/> for reading files from a <see cref="FolderDestination"/>.</summary>
public class FolderDestinationReader : DestinationReader
{
    private readonly string _folderPath;
    private readonly string _workDir;

    public FolderDestinationReader(string folderPath, string workDir)
    {
        _folderPath = folderPath;
        _workDir = workDir;
    }

    public override string ReadFile(string path)
    {
        try
        {
            return File.ReadAllText(Path.Combine(_folderPath, path));
        }
        catch (IOException e)
        {
            throw new RepoException($"Unable to read file {path}.", e);
        }
    }

    public override void CopyDestinationFiles(object glob, object path)
    {
        CheckoutPath? checkoutPath =
            ReferenceEquals(path, StarlarkRt.None) || path is null ? null : (CheckoutPath)path;
        Glob resolvedGlob = Glob.WrapGlob(glob, null)!;
        if (checkoutPath == null)
        {
            CopyDestinationFilesToDirectory(resolvedGlob, _workDir);
        }
        else
        {
            CopyDestinationFilesToDirectory(
                resolvedGlob,
                PathOps.Resolve(checkoutPath.GetCheckoutDir(), checkoutPath.GetPath()));
        }
    }

    public override void CopyDestinationFilesToDirectory(Glob glob, string directory)
    {
        IPathMatcher pathMatcher = glob.RelativeTo(_folderPath);

        foreach (var root in glob.Roots())
        {
            string rootPath = string.IsNullOrEmpty(root) ? _folderPath : Path.Combine(_folderPath, root);
            if (!Directory.Exists(rootPath))
            {
                continue;
            }
            try
            {
                foreach (var sourcePath in Directory.EnumerateFiles(
                    rootPath, "*", SearchOption.AllDirectories))
                {
                    string fullSource = Path.GetFullPath(sourcePath);
                    if (!pathMatcher.Matches(fullSource))
                    {
                        continue;
                    }
                    string relative = Path.GetRelativePath(_folderPath, fullSource);
                    string targetPath = Path.Combine(directory, relative);
                    string? parent = Path.GetDirectoryName(targetPath);
                    if (parent != null)
                    {
                        Directory.CreateDirectory(parent);
                    }
                    File.Copy(fullSource, targetPath);
                }
            }
            catch (IOException e)
            {
                throw new RepoException($"Failed to copy files from {_folderPath}.", e);
            }
        }
    }

    public override bool Exists(string path) =>
        File.Exists(Path.Combine(_folderPath, path)) || Directory.Exists(Path.Combine(_folderPath, path));
}
