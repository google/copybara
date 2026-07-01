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
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Config;
using Copybara.Exceptions;
using Copybara.Http.Auth;
using Copybara.Json;
using Copybara.RemoteFile;
using Copybara.Version;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Go;

/// <summary>Used to fetch versions available for a given module at go proxy.</summary>
[StarlarkBuiltin("goproxy_version_list", Doc = "Fetch versions from goproxy")]
public class GoProxyVersionList : IVersionList, IStarlarkValue, ILabelsAwareModule
{
    private static readonly Regex Uppercase = new("[A-Z]", RegexOptions.Compiled);

    private readonly string? _listVersionsUrl;
    private readonly string? _latestVersionUrl;
    private readonly string? _dotInfoUrl;
    private readonly RemoteFileOptions _remoteFileOptions;
    private readonly string _module;
    private readonly IAuthInterceptor? _auth;

    public static GoProxyVersionList ForInfo(
        string module,
        string dotInfo,
        RemoteFileOptions remoteFileOptions,
        IAuthInterceptor? auth) =>
        new(module, dotInfo, remoteFileOptions, auth);

    public static GoProxyVersionList ForVersion(
        string module, RemoteFileOptions remoteFileOptions, IAuthInterceptor? auth) =>
        new(module, remoteFileOptions, auth);

    private GoProxyVersionList(
        string module, RemoteFileOptions remoteFileOptions, IAuthInterceptor? auth)
    {
        _module = module;
        _dotInfoUrl = null;
        // returns plain text
        _listVersionsUrl =
            $"https://proxy.golang.org/{NormalizeModuleName(module)}/@v/list";
        // returns json. This is an optionally implemented endpoint, that goproxy recommends as
        // fallback if /@v/list is empty
        _latestVersionUrl =
            $"https://proxy.golang.org/{NormalizeModuleName(module)}/@latest";
        _remoteFileOptions = remoteFileOptions;
        _auth = auth;
    }

    private GoProxyVersionList(
        string module,
        string dotInfo,
        RemoteFileOptions remoteFileOptions,
        IAuthInterceptor? auth)
    {
        _module = module;
        _dotInfoUrl = GetDotInfoUrl(module, dotInfo);
        _listVersionsUrl = null;
        _latestVersionUrl = null;
        _remoteFileOptions = remoteFileOptions;
        _auth = auth;
    }

    private static string GetDotInfoUrl(string module, string dotInfo) =>
        $"https://proxy.golang.org/{NormalizeModuleName(module)}/@v/{dotInfo}.info";

    /// <summary>Takes upper case A-Z characters and replaces them with !a-z.</summary>
    private static string NormalizeModuleName(string module) =>
        Uppercase.Replace(module, "!$0").ToLowerInvariant();

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
                $"Failed to query proxy.golang.org for version list at {url}", e);
        }
    }

    /// <exception cref="RepoException"/>
    private T ExecuteHttpQuery<T>(string url)
    {
        try
        {
            string jsonString = ExecuteHttpQuery(url);
            T? parsed = GsonParserUtil.ParseString<T>(jsonString, false);
            return parsed!;
        }
        catch (Exception e) when (e is ArgumentException or IOException)
        {
            throw new RepoException(
                $"Failed to query proxy.golang.org for version list at {url}", e);
        }
    }

    /// <exception cref="ValidationException"/>
    public IReadOnlySet<string> List()
    {
        try
        {
            // API caller has a very specific revision/branch release in mind. Just return that or
            // fail trying.
            if (_dotInfoUrl != null)
            {
                GoVersionObject dotInfoVersion = GetVersionObject(_dotInfoUrl);
                return ImmutableHashSet.Create(dotInfoVersion.GetVersion());
            }

            string versionListResponseString = ExecuteHttpQuery(_listVersionsUrl!);
            if (!string.IsNullOrEmpty(versionListResponseString))
            {
                return versionListResponseString.Split('\n').ToImmutableHashSet();
            }

            // try the back up endpoint.
            GoVersionObject latestVersion = GetVersionObject(_latestVersionUrl!);
            return ImmutableHashSet.Create(latestVersion.GetVersion());
        }
        catch (RepoException e)
        {
            throw new ValidationException("Failed to obtain go proxy version list", e);
        }
    }

    /// <exception cref="RepoException"/>
    private GoVersionObject GetVersionObject(string dotInfoUrl) =>
        ExecuteHttpQuery<GoVersionObject>(dotInfoUrl);

    [StarlarkMethod("get_info",
        Doc =
            "Return the results of an info query. An object is only returned if a ref was specified.",
        AllowReturnNones = true)]
    public GoVersionObject? GetInfoQuery(
        [Param(
            Name = "ref",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) },
            Doc =
                "The reference to query for. This is optional, and the default will be the latest"
                + " version, or the ref if passed into this object during creation.",
            Named = true,
            DefaultValue = "None")]
        object? @ref)
    {
        string? maybeDotInfo = SkylarkUtil.ConvertFromNoneable<string>(@ref, null);
        try
        {
            if (maybeDotInfo != null)
            {
                return GetVersionObject(GetDotInfoUrl(_module, maybeDotInfo));
            }

            if (_dotInfoUrl != null)
            {
                return GetVersionObject(_dotInfoUrl);
            }

            if (_latestVersionUrl != null)
            {
                return GetVersionObject(_latestVersionUrl);
            }
        }
        catch (RepoException e)
        {
            throw new ValidationException("Failed to obtain go proxy version info", e);
        }

        return null;
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
