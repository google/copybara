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
using System.Text.RegularExpressions;
using Copybara.Credentials;

namespace Copybara.Http.Endpoint;

/// <summary>HttpSecretInterceptor replaces secrets with their corresponding values.</summary>
internal class HttpSecretInterceptor
{
    private static readonly Regex Template = new(@"\$\{\{(.*?)\}\}");
    private static readonly Regex EncodedTemplate = new(@"\%24\%7B\%7B(.*?)\%7D\%7D");

    private readonly ImmutableDictionary<string, CredentialIssuer> _issuers;

    public HttpSecretInterceptor(ImmutableDictionary<string, CredentialIssuer> issuers)
    {
        _issuers = issuers;
    }

    public string ResolveStringSecrets(string value)
    {
        value = ReplaceMatchesWithSecrets(value, Template);
        value = ReplaceMatchesWithSecrets(value, EncodedTemplate);
        return value;
    }

    private string ReplaceMatchesWithSecrets(string value, Regex pattern)
    {
        foreach (Match match in pattern.Matches(value))
        {
            string issuerName = match.Groups[1].Value;
            if (!_issuers.TryGetValue(issuerName, out var issuer))
            {
                throw new ArgumentException($"Credential issuer {issuerName} is not found");
            }

            value = value.Replace(match.Groups[0].Value, issuer.Issue().ProvideSecret());
        }

        return value;
    }
}
