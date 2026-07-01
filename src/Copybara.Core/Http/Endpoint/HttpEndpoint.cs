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

using System.Collections.Generic;
using System.Collections.Immutable;
using System.Net.Http;
using Copybara.Checks;
using Copybara.Common;
using Copybara.Config;
using Copybara.Credentials;
using Copybara.Exceptions;
using Copybara.Http.Auth;
using Starlark.Annot;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Http.Endpoint;

/// <summary>
/// Endpoint capable of making http requests.
///
/// <para>This endpoint is currently bound to a specific host, as a security restriction.</para>
/// </summary>
[StarlarkBuiltin("http_endpoint", Doc = "Calls via HTTP.")]
public class HttpEndpoint : IEndpoint
{
    // Immutable map keeping the issuers as key value pairs. The key is used to identify the issuer
    // before executing the http call.
    private readonly ImmutableDictionary<string, CredentialIssuer> _issuers;

    private readonly ImmutableDictionary<string, IAuthInterceptor?> _hosts;
    private readonly HttpClient _transport;
    private readonly Console _console;
    private readonly IChecker? _checker;

    /// <summary>Whether to automatically follow redirects, true by default.</summary>
    private bool _followRedirects = true;

    public HttpEndpoint(
        Console console,
        HttpClient transport,
        ImmutableDictionary<string, IAuthInterceptor?> hosts,
        ImmutableDictionary<string, CredentialIssuer> issuers,
        IChecker? checker)
    {
        _hosts = hosts;
        _transport = transport;
        _console = console;
        _checker = checker;
        _issuers = issuers;
    }

    [StarlarkMethod("get", Doc = "Execute a get request")]
    public HttpEndpointResponse Get(
        [Param(Name = "url", Named = true, AllowedTypes = new[] { typeof(string) })] string url,
        [Param(
            Name = "headers",
            Named = true,
            Positional = false,
            AllowedTypes = new[] { typeof(Dict) },
            DefaultValue = "{}",
            Doc = "dict of http headers for the request")]
        object headers,
        [Param(
            Name = "auth",
            Named = true,
            Positional = false,
            DefaultValue = "False",
            AllowedTypes = new[] { typeof(bool) })]
        bool auth) => HandleRequest(url, "GET", headers, null, auth);

    [StarlarkMethod("post", Doc = "Execute a post request")]
    public HttpEndpointResponse Post(
        [Param(Name = "url", Named = true, AllowedTypes = new[] { typeof(string) })] string urlIn,
        [Param(
            Name = "headers",
            Named = true,
            Positional = false,
            AllowedTypes = new[] { typeof(Dict) },
            DefaultValue = "{}",
            Doc = "dict of http headers for the request")]
        object headersIn,
        [Param(
            Name = "content",
            Named = true,
            Positional = false,
            DefaultValue = "None",
            AllowedTypes = new[] { typeof(IHttpEndpointBody), typeof(NoneType) })]
        object? content,
        [Param(Name = "auth", Named = true, Positional = false, DefaultValue = "False")]
        bool auth) => HandleRequest(urlIn, "POST", headersIn, content, auth);

    [StarlarkMethod("delete", Doc = "Execute a delete request")]
    public HttpEndpointResponse Delete(
        [Param(Name = "url", Named = true, AllowedTypes = new[] { typeof(string) })] string urlIn,
        [Param(
            Name = "headers",
            Named = true,
            Positional = false,
            AllowedTypes = new[] { typeof(Dict) },
            DefaultValue = "{}",
            Doc = "dict of http headers for the request")]
        object headersIn,
        [Param(
            Name = "auth",
            Named = true,
            Positional = false,
            DefaultValue = "False",
            AllowedTypes = new[] { typeof(bool) })]
        bool auth) => HandleRequest(urlIn, "DELETE", headersIn, null, auth);

    private HttpEndpointResponse HandleRequest(
        string urlIn, string method, object headersIn, object? endpointContentIn, bool auth)
    {
        var url = new Uri(urlIn);
        ValidateUrl(url);

        var secretInterceptor = new HttpSecretInterceptor(_issuers);
        var headersDict = SkylarkUtil.ConvertStringMap(headersIn, "headers");
        var headers = new List<KeyValuePair<string, string>>();
        foreach (var e in headersDict)
        {
            string val = secretInterceptor.ResolveStringSecrets(e.Value);
            headers.Add(new KeyValuePair<string, string>(e.Key, val));
        }

        IHttpEndpointBody? endpointContent =
            SkylarkUtil.ConvertFromNoneable<IHttpEndpointBody>(endpointContentIn, null);
        HttpContent? content = null;
        if (endpointContent != null)
        {
            content = HttpContentInterceptor.Wrap(endpointContent.GetContent(), secretInterceptor);
        }

        IAuthInterceptor? creds = null;
        if (auth)
        {
            if (!_hosts.TryGetValue(url.Host, out creds) || creds == null)
            {
                throw new EvalException(
                    $"Autentication was requested, but no creds provided for {url}");
            }
        }

        var req = new HttpEndpointRequest(
            url, method, headers, content, auth && creds != null ? creds : null);

        if (_checker != null)
        {
            _checker.DoCheck(
                ImmutableDictionary.CreateRange(new[]
                {
                    new KeyValuePair<string, string>("url", url.ToString()),
                    new KeyValuePair<string, string>(
                        "headers", string.Join(", ", headers.Select(h => $"{h.Key}: {h.Value}"))),
                }),
                _console);
            endpointContent?.CheckContent(_checker, _console);
        }

        var request = req.Build();
        // Redirect handling is configured on the transport's HttpClientHandler (see HttpOptions).
        var response = _transport.SendAsync(request).GetAwaiter().GetResult();
        return new HttpEndpointResponse(response);
    }

    public void ValidateUrl(Uri url)
    {
        ValidationException.CheckCondition(
            _hosts.ContainsKey(url.Host),
            "Illegal host: url host {0} matches none of endpoint hosts {{{1}}}",
            url.Host,
            string.Join(",", _hosts.Keys));
    }

    [StarlarkMethod("followRedirects", Doc = "Sets whether to follow redirects automatically")]
    public void SetFollowRedirects(
        [Param(
            Name = "followRedirects",
            Doc = "Whether to follow redirects automatically",
            AllowedTypes = new[] { typeof(bool) })]
        bool followRedirects) => _followRedirects = followRedirects;

    public ImmutableListMultimap<string, string> Describe()
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("type", "http_endpoint");
        builder.PutAll("host", _hosts.Keys);
        return builder.Build();
    }

    public IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials()
    {
        var list = ImmutableArray.CreateBuilder<ImmutableListMultimap<string, string>>();

        // Add credentials from auth interceptors for hosts.
        foreach (var entry in _hosts)
        {
            if (entry.Value == null)
            {
                continue;
            }

            foreach (var credEntry in entry.Value.DescribeCredentials())
            {
                var describe = ImmutableListMultimap<string, string>.CreateBuilder();
                describe.PutAll(credEntry);
                describe.PutAll("host", new[] { entry.Key });
                list.Add(describe.Build());
            }
        }

        // Add credentials from issuers used by secret interceptors.
        foreach (var entry in _issuers)
        {
            var describe = ImmutableListMultimap<string, string>.CreateBuilder();
            describe.Put("key", entry.Key);
            foreach (var credEntry in entry.Value.Describe())
            {
                describe.Put(credEntry.Key, credEntry.Value);
            }

            list.Add(describe.Build());
        }

        return list.ToImmutable();
    }
}
