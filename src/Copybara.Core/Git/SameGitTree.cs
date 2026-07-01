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

using Copybara.Exceptions;

namespace Copybara.Git;

/// <summary>
/// A class comparing the git tree of a repo's HEAD sha1 with any sha1. Port of
/// <c>com.google.copybara.git.SameGitTree</c>.
/// </summary>
public sealed class SameGitTree
{
    private readonly GitRepository _repo;
    private readonly string _repoUrl;
    private readonly GeneralOptions _generalOptions;
    private readonly bool _partialFetch;

    public SameGitTree(
        GitRepository repo, string repoUrl, GeneralOptions generalOptions, bool partialFetch)
    {
        _repo = repo;
        _repoUrl = repoUrl;
        _generalOptions = generalOptions;
        _partialFetch = partialFetch;
    }

    private string SaveOldHead()
    {
        GitRevision gitRevision = _repo.GetHeadRef();
        return gitRevision.ContextReference() ?? gitRevision.GetHash();
    }

    /// <summary>
    /// Compare the git tree of the repo's HEAD with the given sha1.
    ///
    /// <para>It will save the current head at the repo, fetch the sha1, then compare their git trees.
    /// Regardless of the checking status, the repo will be force set back to its previous head.</para>
    /// </summary>
    public bool HasSameTree(string sha1)
    {
        string oldHead = SaveOldHead();
        try
        {
            using (_generalOptions.Profiler().Start("fetch_remote_sha1"))
            {
                _repo.Fetch(
                    _repoUrl,
                    prune: false,
                    force: true,
                    new[] { sha1 },
                    _partialFetch,
                    depth: null,
                    tags: false);
                return _repo.HasSameTree(sha1);
            }
        }
        catch (Exception e) when (e is RepoException or ValidationException)
        {
            _generalOptions.GetConsole().WarnFmt(
                "Cannot compare git tree of head %s with sha1 %s.", oldHead, sha1);
        }
        finally
        {
            _repo.ForceCheckout(oldHead);
        }
        return false;
    }
}
