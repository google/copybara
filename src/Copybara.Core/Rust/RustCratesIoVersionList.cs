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
using System.Text;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Http.Auth;
using Copybara.Json;
using Copybara.RemoteFile;
using Copybara.Version;
using Starlark.Eval;

namespace Copybara.Rust;

/// <summary>Used to fetch a list of versions for a Rust crate at crates.io.</summary>
public sealed class RustCratesIoVersionList : IVersionList, IStarlarkValue
{
    private const string CratesIoIndexUrl = "https://index.crates.io";

    private readonly string _crateName;
    private readonly RemoteFileOptions _remoteFileOptions;
    private readonly bool _matchPreReleaseVersions;
    private readonly bool _ignoreYankedVersions;
    private readonly IAuthInterceptor? _auth;

    public static RustCratesIoVersionList ForCrate(
        string crate,
        RemoteFileOptions remoteFileOptions,
        bool matchPreReleaseVersions,
        bool ignoreYankedVersions,
        IAuthInterceptor? auth) =>
        new(crate, remoteFileOptions, matchPreReleaseVersions, ignoreYankedVersions, auth);

    private RustCratesIoVersionList(
        string crateName,
        RemoteFileOptions remoteFileOptions,
        bool matchPreReleaseVersions,
        bool ignoreYankedVersions,
        IAuthInterceptor? auth)
    {
        _crateName = crateName;
        _remoteFileOptions = remoteFileOptions;
        _matchPreReleaseVersions = matchPreReleaseVersions;
        _ignoreYankedVersions = ignoreYankedVersions;
        _auth = auth;
    }

    /// <exception cref="RepoException"/>
    /// <exception cref="ValidationException"/>
    public IReadOnlySet<string> List()
    {
        try
        {
            return GetVersionList()
                .Where(MaybeFilterYankedVersions)
                .Select(v => v.GetVers())
                .Where(FilterPreReleaseVersions)
                .ToImmutableHashSet();
        }
        catch (ArgumentException e)
        {
            if (e.InnerException is ValidationException ve)
            {
                throw ve;
            }

            throw;
        }
    }

    private bool FilterPreReleaseVersions(string version)
    {
        if (!_matchPreReleaseVersions)
        {
            try
            {
                return RustVersionRequirement.SemanticVersion
                    .CreateFromVersionString(version)
                    .PreReleaseIdentifier == null;
            }
            catch (ValidationException e)
            {
                throw new ArgumentException(e.Message, e);
            }
        }

        return true;
    }

    private bool MaybeFilterYankedVersions(RustRegistryVersionObject versionObj) =>
        !_ignoreYankedVersions || !versionObj.IsYanked();

    /// <exception cref="RepoException"/>
    internal IReadOnlySet<RustRegistryVersionObject> GetVersionList()
    {
        string url = CratesIoIndexUrl;

        int nameLength = _crateName.Length;
        string indexCrateName = _crateName.ToLowerInvariant();

        if (nameLength <= 2)
        {
            // If the crate name's length is less than or equal to 2, then the version info is
            // located at /<name length>/<crate name>
            url += $"/{nameLength}/{indexCrateName}";
        }
        else if (nameLength == 3)
        {
            // If the crate name's length is equal to 3, then the version info is at:
            // /3/<first char>/<crate name>
            url += $"/{nameLength}/{indexCrateName[0]}/{indexCrateName}";
        }
        else
        {
            url +=
                $"/{indexCrateName.Substring(0, 2)}/{indexCrateName.Substring(2, 2)}/{indexCrateName}";
        }

        var versionList = ImmutableHashSet.CreateBuilder<RustRegistryVersionObject>();
        try
        {
            using var reader = new StringReader(ExecuteHttpQuery(url));
            string? jsonString;
            while ((jsonString = reader.ReadLine()) != null)
            {
                if (jsonString.Length == 0)
                {
                    continue;
                }

                RustRegistryVersionObject? obj =
                    GsonParserUtil.ParseString<RustRegistryVersionObject>(jsonString, false);
                if (obj != null)
                {
                    versionList.Add(obj);
                }
            }
        }
        catch (Exception e) when (e is IOException or ArgumentException)
        {
            throw new RepoException(
                $"Failed to query crates.io-index for version list at {url}", e);
        }

        return versionList.ToImmutable();
    }

    /// <exception cref="RepoException"/>
    private string ExecuteHttpQuery(string url)
    {
        try
        {
            using Stream inputStream = _remoteFileOptions.GetTransport().Open(new Uri(url), _auth);
            using var ms = new MemoryStream();
            inputStream.CopyTo(ms);
            return Encoding.UTF8.GetString(ms.ToArray());
        }
        catch (Exception e) when (e is IOException or ValidationException)
        {
            throw new RepoException(
                $"Failed to query crates.io-index for version list at {url}", e);
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
