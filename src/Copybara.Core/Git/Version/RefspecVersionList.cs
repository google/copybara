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
using System.Linq;
using Copybara.Exceptions;
using Copybara.Version;

namespace Copybara.Git.Version;

/// <summary>
/// A <see cref="IVersionList"/> that uses a git ls-remote to list versions from a remote Git
/// repository. Port of <c>com.google.copybara.git.version.RefspecVersionList</c>.
/// </summary>
public class RefspecVersionList : IVersionList
{
    private readonly GitRepository _repo;
    private readonly IReadOnlyCollection<Refspec> _refspecs;
    private readonly string _url;

    public RefspecVersionList(GitRepository repo, IReadOnlyCollection<Refspec> refspecs, string url)
    {
        _repo = repo;
        _refspecs = refspecs;
        _url = url;
    }

    /// <exception cref="ValidationException"/>
    /// <exception cref="RepoException"/>
    public virtual IReadOnlySet<string> List()
    {
        var origins = _refspecs.Select(r => r.GetOrigin()).ToImmutableHashSet();
        return _repo.LsRemote(_url, origins).Keys.ToImmutableHashSet();
    }

    /// <summary>A <see cref="RefspecVersionList"/> for listing git tags.</summary>
    public sealed class TagVersionList : RefspecVersionList
    {
        public TagVersionList(GitRepository repo, string url)
            : base(repo, TagBranchRefspec(repo, "tags"), url)
        {
        }

        public override IReadOnlySet<string> List() =>
            base.List()
                .Select(s => s.Substring("refs/tags/".Length))
                .ToImmutableHashSet();
    }

    /// <summary>A <see cref="RefspecVersionList"/> for listing git branches.</summary>
    public sealed class BranchVersionList : RefspecVersionList
    {
        public BranchVersionList(GitRepository repo, string url)
            : base(repo, TagBranchRefspec(repo, "heads"), url)
        {
        }

        public override IReadOnlySet<string> List() =>
            base.List()
                .Select(s => s.Substring("refs/heads/".Length))
                .ToImmutableHashSet();
    }

    private static ImmutableArray<Refspec> TagBranchRefspec(GitRepository repo, string type)
    {
        try
        {
            return ImmutableArray.Create(repo.CreateRefSpec("refs/" + type + "/*"));
        }
        catch (ValidationException e)
        {
            throw new InvalidOperationException(
                "Unexpected error constructing refspec from constant. This shouldn't happen. Fill a"
                    + " Copybara bug", e);
        }
    }
}
