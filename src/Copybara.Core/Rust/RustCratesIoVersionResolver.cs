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

using System.Collections.Immutable;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Http.Auth;
using Copybara.RemoteFile;
using Copybara.Revision;
using Copybara.Version;

namespace Copybara.Rust;

/// <summary>Class that can resolve a ref to a Rust crate version from crates.io.</summary>
public class RustCratesIoVersionResolver : IVersionResolver
{
    private readonly string _crate;
    private readonly RemoteFileOptions _remoteFileOptions;
    private readonly bool _matchPreReleaseVersions;
    private readonly IAuthInterceptor? _auth;

    public RustCratesIoVersionResolver(
        string crate,
        RemoteFileOptions remoteFileOptions,
        bool matchPreReleaseVersions,
        IAuthInterceptor? auth)
    {
        _crate = crate;
        _remoteFileOptions = remoteFileOptions;
        _matchPreReleaseVersions = matchPreReleaseVersions;
        _auth = auth;
    }

    /// <exception cref="ValidationException"/>
    private string Resolve(string @ref)
    {
        IReadOnlySet<string> versionList = ImmutableHashSet<string>.Empty;
        try
        {
            versionList =
                RustCratesIoVersionList.ForCrate(
                        _crate,
                        _remoteFileOptions,
                        _matchPreReleaseVersions,
                        ignoreYankedVersions: false,
                        _auth)
                    .List();
            if (!versionList.Contains(@ref))
            {
                throw new CannotResolveRevisionException(
                    $"Could not locate version with ref '{@ref}' as a version.");
            }
        }
        catch (RepoException e)
        {
            throw new ValidationException(
                $"There was an issue querying the crates.io index for ref {@ref}. The version list"
                + $" fetched from crates.io was [{string.Join(", ", versionList)}].",
                e);
        }

        return @ref;
    }

    /// <exception cref="ValidationException"/>
    public IRevision Resolve(string @ref, Func<string, string?> assemblyStrategy)
    {
        string version = Resolve(@ref);
        string? fullUrl = assemblyStrategy(version);
        if (fullUrl == null)
        {
            throw new ValidationException(
                "Failed to assemble url template with provided assembly strategy."
                + $" Provided ref = '{@ref}' and resolved version = '{version}'.");
        }

        var remoteArchiveVersion = new RemoteArchiveVersion(fullUrl, version);
        return new RemoteArchiveRevision(remoteArchiveVersion);
    }

    public IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials()
    {
        if (_auth == null)
        {
            return ImmutableArray<ImmutableListMultimap<string, string>>.Empty;
        }

        return _auth.DescribeCredentials();
    }
}
