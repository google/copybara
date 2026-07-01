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

using System.Linq;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using Copybara.Common;
using Copybara.Credentials;

namespace Copybara.Http.Auth;

/// <summary>Representation for authentication information for an http request.</summary>
public class UsernamePasswordInterceptor : IAuthInterceptor
{
    private readonly UsernamePasswordIssuer _issuer;

    public UsernamePasswordInterceptor(UsernamePasswordIssuer issuer)
    {
        _issuer = Preconditions.CheckNotNull(issuer);
    }

    public Action<HttpRequestMessage> Interceptor()
    {
        string un = _issuer.Username.Issue().ProvideSecret();
        string pwd = _issuer.Password.Issue().ProvideSecret();
        string encoded = Convert.ToBase64String(Encoding.UTF8.GetBytes($"{un}:{pwd}"));
        return req => req.Headers.Authorization = new AuthenticationHeaderValue("Basic", encoded);
    }

    public IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials() =>
        _issuer.DescribeCredentials()
            .Select(MultimapConversions.ToListMultimap)
            .ToList();
}
