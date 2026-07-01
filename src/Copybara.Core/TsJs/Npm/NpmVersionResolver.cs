/*
 * Copyright (C) 2024 Google LLC.
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

namespace Copybara.TsJs.Npm;

/// <summary>Object used to turn a ref into a version listed in the NPM registry.</summary>
public class NpmVersionResolver : IVersionResolver
{
    private readonly string _packageName;
    private readonly string _registryUrl;
    private readonly RemoteFileOptions _remoteFileOptions;
    private readonly IAuthInterceptor? _auth;

    public NpmVersionResolver(
        string packageName,
        string registryUrl,
        RemoteFileOptions remoteFileOptions,
        IAuthInterceptor? auth)
    {
        _packageName = packageName;
        _registryUrl = registryUrl;
        _remoteFileOptions = remoteFileOptions;
        _auth = auth;
    }

    /// <summary>Resolves the given reference as if it was an NPM Package version.</summary>
    /// <exception cref="RepoException"/>
    /// <exception cref="ValidationException"/>
    private NpmVersionInfo Resolve(string @ref)
    {
        // TODO depending on what ref could be, maybe ref could be semver-lang and that might resolve
        // to a bunch of versions?
        NpmVersionListResponseObject allVersions =
            NpmVersionList.ForPackage(_packageName, _registryUrl, _remoteFileOptions, _auth)
                .ListVersions();
        if (@ref != null)
        {
            if (!allVersions.GetAllVersions().Contains(@ref))
            {
                throw new CannotResolveRevisionException(
                    $"Could not locate version with ref '{@ref}' as a version.");
            }

            return allVersions.GetVersionInfo(@ref);
        }

        // No ref should return latest version available?
        return allVersions.GetLatestVersion();
    }

    /// <summary>
    /// Uses the NPM registry to look up the distributed tarball for the given <paramref name="ref"/>.
    /// </summary>
    /// <param name="ref">e.g. 1.1.1</param>
    /// <param name="assemblyStrategy">how to assemble the url after resolving <paramref name="ref"/>.</param>
    /// <exception cref="ValidationException"/>
    public IRevision Resolve(string @ref, Func<string, string?> assemblyStrategy)
    {
        try
        {
            NpmVersionInfo version = Resolve(@ref);
            var remoteArchiveVersion =
                new RemoteArchiveVersion(version.GetTarball(), version.GetVersion());
            return new RemoteArchiveRevision(remoteArchiveVersion);
        }
        catch (RepoException e)
        {
            // TODO should resolve also throw a repoexception?
            throw new ValidationException("repository error resolving reference", e);
        }
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
