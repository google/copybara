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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSetMultimap;

/**
 * A static CredentialIssuer, e.g. a password, username, api key, etc
 */
public class ConstantCredentialIssuer implements CredentialIssuer {

  private final String secret;
  private final String name;

  private final boolean open;

  public static ConstantCredentialIssuer createConstantSecret(String name, String secret) {
    return new ConstantCredentialIssuer(
        Preconditions.checkNotNull(name), Preconditions.checkNotNull(secret), false);
  }

  public static ConstantCredentialIssuer createConstantOpenValue(String value) {
    return new ConstantCredentialIssuer(Preconditions.checkNotNull(value), value, true);
  }

  private ConstantCredentialIssuer(String name, String secret, boolean open) {
    this.secret = secret;
    this.name = name;
    this.open = open;
  }

  @Override
  public Credential issue() throws CredentialIssuingException {
    return open ? new OpenCredential(secret) : new StaticSecret(name, secret);
  }

  @Override
  public ImmutableSetMultimap<String, String> describe() {
    return ImmutableSetMultimap.of("type", "constant", "name", name, "open", "" + open);
  }
}
