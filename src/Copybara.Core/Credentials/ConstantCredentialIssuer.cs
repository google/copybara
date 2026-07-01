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

namespace Copybara.Credentials;

/// <summary>A static CredentialIssuer, e.g. a password, username, api key, etc.</summary>
public class ConstantCredentialIssuer : CredentialIssuer
{
    private readonly string _secret;
    private readonly string _name;
    private readonly bool _open;

    public static ConstantCredentialIssuer CreateConstantSecret(string name, string secret) =>
        new(Preconditions.CheckNotNull(name), Preconditions.CheckNotNull(secret), false);

    public static ConstantCredentialIssuer CreateConstantOpenValue(string value) =>
        new(Preconditions.CheckNotNull(value), value, true);

    private ConstantCredentialIssuer(string name, string secret, bool open)
    {
        _secret = secret;
        _name = name;
        _open = open;
    }

    public Credential Issue() =>
        _open ? new OpenCredential(_secret) : new StaticSecret(_name, _secret);

    public ImmutableSetMultimap<string, string> Describe() =>
        ImmutableSetMultimap<string, string>.CreateBuilder()
            .Put("type", "constant")
            .Put("name", _name)
            .Put("open", _open ? "true" : "false")
            .Build();
}
