/*
 * Copyright (C) 2023 Google LLC.
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

using Copybara.Common;
using Starlark.Eval;

namespace Copybara.Credentials;

/// <summary>
/// An object able to mint credentials. The issuer should handle caching etc.
/// </summary>
public interface CredentialIssuer : IStarlarkValue
{
    /// <summary>Issue a <see cref="Credential"/> to be used by an endpoint.</summary>
    /// <exception cref="CredentialIssuingException">if minting the credential fails.</exception>
    Credential Issue();

    /// <summary>Metadata describing this issuer.</summary>
    ImmutableSetMultimap<string, string> Describe();
}
