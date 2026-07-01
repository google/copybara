/*
 * Copyright (C) 2020 Google Inc.
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

using Copybara.Common;
using Copybara.Config;
using Copybara.Exceptions;
using Copybara.Util;
using Starlark.Annot;

namespace Copybara.Git;

/// <summary>
/// A DestinationReader for reading files from a GitDestination. Port of
/// <c>com.google.copybara.git.GitDestinationReader</c>.
/// </summary>
[StarlarkBuiltin(
    "git_destination_reader",
    Doc = "Handle to read from a git destination",
    Documented = false)]
public class GitDestinationReader : DestinationReader
{
    private readonly GitRepository _repository;
    private readonly string _workDir;
    private readonly GitRevision _baseline;

    public GitDestinationReader(GitRepository repository, GitRevision baseline, string workDir)
    {
        _repository = Preconditions.CheckNotNull(repository);
        _baseline = Preconditions.CheckNotNull(baseline);
        _workDir = Preconditions.CheckNotNull(workDir);
    }

    public override string ReadFile(string path) =>
        _repository.ReadFile(_baseline.GetHash(), path);

    public override void CopyDestinationFiles(object glob, object path)
    {
        var checkoutPath = SkylarkUtil.ConvertFromNoneable<CheckoutPath>(path, null);
        Glob resolvedGlob = Glob.WrapGlob(glob, null)!;
        if (checkoutPath == null)
        {
            CopyDestinationFilesToDirectory(resolvedGlob, _workDir);
        }
        else
        {
            CopyDestinationFilesToDirectory(resolvedGlob, checkoutPath.FullPath());
        }
    }

    public override void CopyDestinationFilesToDirectory(Glob glob, string directory)
    {
        var treeElements = _repository.LsTree(_baseline, null, true, true);
        var pathMatcher = glob.RelativeTo(directory);
        foreach (var file in treeElements)
        {
            string path = Path.Combine(directory, file.Path);
            if (pathMatcher.Matches(path))
            {
                try
                {
                    string? parent = Path.GetDirectoryName(path);
                    if (parent != null)
                    {
                        Directory.CreateDirectory(parent);
                    }
                }
                catch (IOException e)
                {
                    throw new RepoException(
                        $"Cannot create parent directory for {path}", e);
                }
            }
        }
        _repository.Checkout(glob, directory, _baseline);
    }

    public override bool Exists(string path)
    {
        try
        {
            return _repository.ReadFile(_baseline.GetHash(), path) != null;
        }
        catch (RepoException)
        {
            return false;
        }
    }

    public override string? LastModified(string path) =>
        _repository.LastModified(_baseline.GetHash(), path);
}
