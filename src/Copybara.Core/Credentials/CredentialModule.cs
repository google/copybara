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
using Copybara.Exceptions;
using Starlark.Annot;
using Starlark.Eval;
using ConsoleImpl = Copybara.Util.Console.Console;

namespace Copybara.Credentials;

/// <summary>Starlark builtins to handle credentials.</summary>
[StarlarkBuiltin("credentials", Doc = "Module for working with credentials.")]
public class CredentialModule : IStarlarkValue
{
    protected readonly CredentialOptions Options;
    protected readonly ConsoleImpl Console;

    public CredentialModule(ConsoleImpl console, CredentialOptions options)
    {
        Console = console;
        Options = options;
    }

    [StarlarkMethod(
        "static_secret",
        Doc = "Holder for secrets that can be in plaintext within the config.")]
    public CredentialIssuer StaticSecret(
        [Param(Name = "name", Doc = "A name for this secret.")] string name,
        [Param(Name = "secret", Doc = "The secret value.")] string secret)
    {
        return ConstantCredentialIssuer.CreateConstantSecret(name, secret);
    }

    [StarlarkMethod(
        "static_value",
        Doc = "Holder for credentials that are safe to read/log (e.g. 'x-access-token') .")]
    public CredentialIssuer StaticValue(
        [Param(Name = "value", Doc = "The open value.")] string value)
    {
        return ConstantCredentialIssuer.CreateConstantOpenValue(value);
    }

    [StarlarkMethod(
        "toml_key_source",
        Doc =
            "Supply an authentication credential from the "
            + "file pointed to by the --http-credential-file flag.")]
    public CredentialIssuer TomlKeySource(
        [Param(
            Name = "dot_path",
            Doc = "Dot path to the data field containing the credential.",
            AllowedTypes = new[] { typeof(string) })]
        string dotPath)
    {
        if (Options.CredentialFile == null)
        {
            throw new ValidationException(
                "Credential file for toml key source has not been supplied");
        }

        return new TomlKeySource(Options.CredentialFile, dotPath);
    }

    [StarlarkMethod(
        "username_password",
        Doc = "A pair of username and password credential issuers.")]
    public UsernamePasswordIssuer UsernamePassword(
        [Param(
            Name = "username",
            Doc = "Username credential.",
            AllowedTypes = new[] { typeof(CredentialIssuer) })]
        CredentialIssuer username,
        [Param(
            Name = "password",
            Doc = "Password credential.",
            AllowedTypes = new[] { typeof(CredentialIssuer) })]
        CredentialIssuer password)
    {
        return Credentials.UsernamePasswordIssuer.Create(username, password);
    }
}
