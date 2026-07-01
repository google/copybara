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
using System.Net.Http;
using System.Net.Http.Headers;
using Copybara.Common;
using Copybara.Credentials;

namespace Copybara.Http.Auth;

/// <summary>An interceptor for adding Bearer authentication to an HTTP request.</summary>
public class BearerInterceptor : IAuthInterceptor
{
    private readonly CredentialIssuer _issuer;

    public BearerInterceptor(CredentialIssuer issuer)
    {
        _issuer = Preconditions.CheckNotNull(issuer);
    }

    public Action<HttpRequestMessage> Interceptor()
    {
        string token = _issuer.Issue().ProvideSecret();
        return req => req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
    }

    public IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials() =>
        ImmutableArray.Create(MultimapConversions.ToListMultimap(_issuer.Describe()));
}
