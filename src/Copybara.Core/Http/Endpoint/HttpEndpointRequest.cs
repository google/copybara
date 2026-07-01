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
using System.Net.Http;
using Copybara.Http.Auth;
using Starlark.Eval;

namespace Copybara.Http.Endpoint;

/// <summary>
/// Holder for all the data required to make a request. It has a method that builds the underlying
/// <see cref="HttpRequestMessage"/>.
///
/// <para>The Java version relies on Google http-client's <c>HttpTransport</c>/<c>HttpRequest</c>;
/// here requests are constructed as <see cref="HttpRequestMessage"/> and executed via a
/// <see cref="HttpClient"/>.</para>
/// </summary>
public class HttpEndpointRequest : IStarlarkValue
{
    // Request parameters.
    private readonly Uri _url;
    private readonly HttpMethod _method;
    private readonly IReadOnlyList<KeyValuePair<string, string>> _headers;
    private readonly HttpContent? _content;
    private readonly IAuthInterceptor? _auth;

    private HttpRequestMessage? _request;

    public HttpEndpointRequest(
        Uri url,
        string method,
        IReadOnlyList<KeyValuePair<string, string>> headers,
        HttpContent? content,
        IAuthInterceptor? auth)
    {
        _url = url;
        _method = new HttpMethod(method);
        _headers = headers;
        _content = content;
        _auth = auth;
    }

    /// <exception cref="Copybara.Credentials.CredentialRetrievalException">If credential retrieval fails.</exception>
    /// <exception cref="Copybara.Credentials.CredentialIssuingException">If credential issuing fails.</exception>
    public HttpRequestMessage Build()
    {
        if (_request == null)
        {
            var request = new HttpRequestMessage(_method, _url);
            if (_content != null)
            {
                request.Content = _content;
            }

            foreach (var header in _headers)
            {
                if (!request.Headers.TryAddWithoutValidation(header.Key, header.Value)
                    && request.Content != null)
                {
                    request.Content.Headers.TryAddWithoutValidation(header.Key, header.Value);
                }
            }

            if (_auth != null)
            {
                _auth.Interceptor()(request);
            }

            _request = request;
        }

        return _request;
    }
}
