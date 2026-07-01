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

using System.Net.Http;
using Copybara.Common;
using Copybara.Http.Auth;

namespace Copybara.RemoteFile;

/// <summary>
/// An <see cref="IHttpStreamFactory"/> backed by <see cref="HttpClient"/>.
///
/// <para>Upstream wraps the Google GHttp Client; this port uses the .NET
/// <see cref="System.Net.Http.HttpClient"/> to perform the GET and return the response content
/// stream.</para>
/// </summary>
public class GclientHttpStreamFactory : IHttpStreamFactory
{
    private readonly HttpClient _httpClient;

    public GclientHttpStreamFactory(TimeSpan timeout)
        : this(NewHttpClient(timeout))
    {
    }

    public GclientHttpStreamFactory(HttpClient httpClient)
    {
        _httpClient = Preconditions.CheckNotNull(httpClient);
    }

    private static HttpClient NewHttpClient(TimeSpan timeout)
    {
        var handler = new HttpClientHandler
        {
            AllowAutoRedirect = true,
        };
        return new HttpClient(handler) { Timeout = timeout };
    }

    public Stream Open(Uri url, IAuthInterceptor? auth, ImmutableListMultimap<string, string> headers)
    {
        using var request = BuildHttpRequest(url, auth, headers);
        HttpResponseMessage response =
            _httpClient.Send(request, HttpCompletionOption.ResponseHeadersRead);
        response.EnsureSuccessStatusCode();
        return response.Content.ReadAsStream();
    }

    /// <summary>Constructs an <see cref="HttpRequestMessage"/> object.</summary>
    /// <param name="url">The URL to send the request to.</param>
    /// <param name="auth">The authentication to use.</param>
    /// <param name="headers">The headers to set in the request.</param>
    /// <exception cref="Copybara.Credentials.CredentialRetrievalException"/>
    /// <exception cref="Copybara.Credentials.CredentialIssuingException"/>
    protected HttpRequestMessage BuildHttpRequest(
        Uri url, IAuthInterceptor? auth, ImmutableListMultimap<string, string> headers)
    {
        var req = new HttpRequestMessage(HttpMethod.Get, url);
        foreach (var key in headers.Keys)
        {
            foreach (string value in headers.Get(key))
            {
                req.Headers.TryAddWithoutValidation(key, value);
            }
        }
        auth?.Interceptor()(req);
        return req;
    }
}
