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

using System.Collections.Immutable;
using Copybara.Common;
using Starlark.Eval;

namespace Copybara.Credentials;

/// <summary>A username/password pair issuer.</summary>
/// <remarks>
/// In upstream this is a nested <c>@AutoValue</c> class of <c>CredentialModule</c>. It is lifted to
/// a top-level record here so peer packages can reference it as
/// <c>Copybara.Credentials.UsernamePasswordIssuer</c>.
/// </remarks>
public sealed record UsernamePasswordIssuer(
    CredentialIssuer Username,
    CredentialIssuer Password) : IStarlarkValue
{
    public ImmutableArray<ImmutableSetMultimap<string, string>> DescribeCredentials() =>
        ImmutableArray.Create(Username.Describe(), Password.Describe());

    public static UsernamePasswordIssuer Create(
        CredentialIssuer username, CredentialIssuer password) => new(username, password);
}
