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
using System.Text;
using Copybara.Common;
using Copybara.Config;
using Copybara.Exceptions;
using Copybara.Http.Auth;
using Copybara.Json;
using Copybara.RemoteFile;
using Copybara.Version;
using Starlark.Eval;

namespace Copybara.TsJs.Npm;

/// <summary>Fetches versions available for a given package in the NPM Registry.</summary>
public class NpmVersionList : IVersionList, IStarlarkValue, ILabelsAwareModule
{
    private readonly NpmPackageIdentifier _pkg;
    private readonly string _listVersionsUrl;
    private readonly RemoteFileOptions _remoteFileOptions;
    private readonly IAuthInterceptor? _auth;

    /// <exception cref="ValidationException"/>
    public static NpmVersionList ForPackage(
        string packageName,
        string registryUrl,
        RemoteFileOptions remoteFileOptions,
        IAuthInterceptor? auth) =>
        new(NpmPackageIdentifier.FromPackage(packageName), registryUrl, remoteFileOptions, auth);

    private NpmVersionList(
        NpmPackageIdentifier pkg,
        string registryUrl,
        RemoteFileOptions remoteFileOptions,
        IAuthInterceptor? auth)
    {
        _pkg = pkg;
        string endpoint = pkg.ToHumanReadableName();
        // returns JSON listing high-level package information, including distribution info for all
        // published versions
        _listVersionsUrl = $"{registryUrl}/{endpoint}";
        // Specific versions can be listed using https://registry.npmjs.com/%s/<version> where
        // <version> can sometimes be specific dist tags (e.g. latest).
        _remoteFileOptions = remoteFileOptions;
        _auth = auth;
    }

    /// <exception cref="ValidationException"/>
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
            // TODO can we detect a 404? this would indicate some form of validation problem with
            // user input, vs a repoexception for something probably broken with the registry itself.
            throw new ValidationException(
                $"Failed to query NPM registry for {_pkg.ToHumanReadableName()} (URL: {url})", e);
        }
    }

    /// <exception cref="RepoException"/>
    /// <exception cref="ValidationException"/>
    private T ExecuteHttpQuery<T>(string url)
    {
        string jsonString = ExecuteHttpQuery(url);
        try
        {
            T? parsed = GsonParserUtil.ParseString<T>(jsonString, false);
            return parsed!;
        }
        catch (Exception e)
        {
            throw new RepoException(
                $"Failed to parse NPM registry response for version list at {url}", e);
        }
    }

    /// <exception cref="RepoException"/>
    /// <exception cref="ValidationException"/>
    public IReadOnlySet<string> List()
    {
        NpmVersionListResponseObject r = ListVersions();
        return r.GetAllVersions().ToImmutableHashSet();
    }

    /// <exception cref="RepoException"/>
    /// <exception cref="ValidationException"/>
    public NpmVersionListResponseObject ListVersions() =>
        ExecuteHttpQuery<NpmVersionListResponseObject>(_listVersionsUrl);

    public IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials()
    {
        if (_auth == null)
        {
            return ImmutableArray<ImmutableListMultimap<string, string>>.Empty;
        }

        return _auth.DescribeCredentials();
    }
}
