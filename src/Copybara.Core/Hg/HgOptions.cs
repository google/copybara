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

using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Util;

namespace Copybara.Hg;

/// <summary>Common arguments for Hg components.</summary>
public class HgOptions : IOption
{
    private const string HgDirPath = ".hg";

    private readonly GeneralOptions _generalOptions;

    /// <summary>
    /// Depth of hg changes to visit at a time. For example, if depth is set to 2, visit the start
    /// change and at most 2 of its next descendants if they exist.
    /// </summary>
    public int VisitChangeDepth { get; set; } = 200;

    public HgOptions(GeneralOptions generalOptions)
    {
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
    }

    public HgRepository CachedBareRepoForUrl(string url)
    {
        Preconditions.CheckNotNull(url);
        try
        {
            return CreateBareRepo(url, GetRepoStorage());
        }
        catch (IOException e)
        {
            throw new RepoException("Cannot create a cached repo for " + url, e);
        }
    }

    /// <summary>
    /// Returns an initialized repository in the <paramref name="path"/> location. If an initialized
    /// repository already exists in the location, returns that repository.
    /// </summary>
    private HgRepository CreateBareRepo(string url, string path)
    {
        string repoPath = FileUtil.ResolveDirInCache(url, path);
        string hgDir = Path.Combine(repoPath, HgDirPath);

        var repo = new HgRepository(hgDir, _generalOptions.IsVerbose(), _generalOptions.RepoTimeout);
        if (!Directory.Exists(hgDir))
        {
            repo.Init();
        }

        repo.CleanUpdate("null");
        return repo;
    }

    private string GetRepoStorage() => _generalOptions.GetDirFactory().GetCacheDir("hg_repos");
}
