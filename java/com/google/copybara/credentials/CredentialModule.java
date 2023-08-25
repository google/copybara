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
package com.google.copybara.credentials;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkValue;

/** Starlark builtins to handle credentials. */
@StarlarkBuiltin(name = "credentials", doc = "Module for working with credentials.")
public class CredentialModule implements StarlarkValue {

  protected CredentialOptions options;
  protected Console console;

  public CredentialModule(Console console, CredentialOptions options) {
    this.console = console;
    this.options = options;
  }

  @StarlarkMethod(
      name = "static_secret",
      doc = "Holder for secrets that can be in plaintext within the config.",
      parameters = {
        @Param(name = "name", doc = "A name for this secret."),
        @Param(name = "secret", doc = "The secret value.")
      })
  public CredentialIssuer staticSecret(String name, String secret) throws EvalException {
    return ConstantCredentialIssuer.createConstantSecret(name, secret);
  }

  @StarlarkMethod(
      name = "static_value",
      doc = "Holder for credentials that are safe to read/log (e.g. 'x-access-token') .",
      parameters = {@Param(name = "value", doc = "The open value.")})
  public CredentialIssuer staticValue(String value) throws EvalException {
    return ConstantCredentialIssuer.createConstantOpenValue(value);
  }

  @StarlarkMethod(
      name = "toml_key_source",
      doc =
          "Supply an authentication credential from the "
              + "file pointed to by the --http-credential-file flag.",
      parameters = {
        @Param(
            name = "dot_path",
            doc = "Dot path to the data field containing the credential.",
            allowedTypes = {@ParamType(type = String.class)})
      })
  public CredentialIssuer tomlKeySource(String dotPath) throws ValidationException {
    if (options.credentialFile == null) {
      throw new ValidationException("Credential file for toml key source has not been supplied");
    }
    return new TomlKeySource(options.credentialFile, dotPath);
  }

  @StarlarkMethod(
      name = "username_password",
      doc = "A pair of username and password credential issuers.",
      parameters = {
        @Param(
            name = "username",
            doc = "Username credential.",
            allowedTypes = {@ParamType(type = CredentialIssuer.class)}),
        @Param(
            name = "password",
            doc = "Password credential.",
            allowedTypes = {@ParamType(type = CredentialIssuer.class)})
      })
  public UsernamePasswordIssuer usernamePassword(
      CredentialIssuer username, CredentialIssuer password) {
    return UsernamePasswordIssuer.create(username, password);
  }

  /** A username/password pair issuer */
  @AutoValue
  public abstract static class UsernamePasswordIssuer implements StarlarkValue {
    public abstract CredentialIssuer username();

    public abstract CredentialIssuer password();

    public ImmutableList<ImmutableSetMultimap<String, String>> describeCredentials() {
      return ImmutableList.of(username().describe(), password().describe());
    }

    public static UsernamePasswordIssuer create(
        CredentialIssuer username, CredentialIssuer password) {
      return new AutoValue_CredentialModule_UsernamePasswordIssuer(username, password);
    }
  }
}
