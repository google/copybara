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

using System.Collections.Immutable;
using System.Linq;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Http.Auth;
using Copybara.RemoteFile;
using Copybara.Revision;
using Copybara.Version;

namespace Copybara.Go;

/// <summary>Object used to turn a ref into a version listed in go proxy.</summary>
public class GoProxyVersionResolver : IVersionResolver
{
    private readonly string _module;
    private readonly RemoteFileOptions _remoteFileOptions;
    private readonly IAuthInterceptor? _auth;

    public GoProxyVersionResolver(
        string module, RemoteFileOptions remoteFileOptions, IAuthInterceptor? auth)
    {
        _module = module;
        _remoteFileOptions = remoteFileOptions;
        _auth = auth;
    }

    /// <exception cref="ValidationException"/>
    private string ResolveFromVersionList(string @ref)
    {
        IReadOnlySet<string> versions =
            GoProxyVersionList.ForVersion(_module, _remoteFileOptions, _auth).List();
        // Go proxy list endpoint only returns versions with a "v" prefix.
        // Optimistically add "v" to ref if absent and before trying resolve it as a version.
        string proxyRef = @ref.StartsWith("v", StringComparison.Ordinal) ? @ref : "v" + @ref;
        if (versions.Contains(proxyRef))
        {
            return proxyRef;
        }

        throw new CannotResolveRevisionException(
            $"Failed to resolve ref '{@ref}' as a version. Available versions:"
            + $" [{string.Join(", ", versions)}]");
    }

    /// <exception cref="ValidationException"/>
    private string ResolveFromInfo(string @ref) =>
        GoProxyVersionList.ForInfo(_module, @ref, _remoteFileOptions, _auth).List().Single();

    /// <summary>
    /// Will try to load go proxy version that the <paramref name="ref"/> points to in go proxy.
    /// First with ref as a version literal and if that does not work then try to resolve it as a
    /// .info reference.
    /// </summary>
    /// <param name="ref">
    /// reference to version known to go proxy (e.g., "1.2.3", "v1.2.3", "main", &lt;hash&gt;).
    /// </param>
    /// <exception cref="ValidationException"/>
    private string Resolve(string @ref)
    {
        try
        {
            return ResolveFromVersionList(@ref);
        }
        catch (ValidationException)
        {
            // Failed to resolve ref as a version. Ref could be a pseudo-version or branch/commit
            // hash. Trying to resolve via .info.
        }

        try
        {
            return ResolveFromInfo(@ref);
        }
        catch (ValidationException)
        {
            // Failed to resolve ref as a .info reference. Check for missing "v" prefix and try
            // again.
            if (!@ref.StartsWith("v", StringComparison.Ordinal))
            {
                return ResolveFromInfo("v" + @ref);
            }

            throw;
        }
    }

    /// <summary>
    /// Uses go proxy to look up <paramref name="ref"/> as an offered version or a known
    /// branch/revision with a release tied to it.
    /// </summary>
    /// <param name="ref">e.g. v1.1.1 or main.info</param>
    /// <param name="assemblyStrategy">how to assemble the url after resolving <paramref name="ref"/>.</param>
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
