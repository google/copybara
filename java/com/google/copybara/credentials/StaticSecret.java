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

/**
 * A static secret Credential, e.g. a password, api key, etc
 */
public class StaticSecret implements Credential {

  private final String secret;
  final String name;

  public StaticSecret(String name, String secret) {
    this.secret = Preconditions.checkNotNull(secret);
    this.name = Preconditions.checkNotNull(name);
  }

  @Override
  public String printableValue() {
    return String.format("<static secret named %s>", name);
  }

  @Override
  public boolean valid() {
    return true;
  }

  @Override
  public String provideSecret() throws CredentialRetrievalException {
    return secret;
  }

  @Override
  public String toString() {
    return printableValue();
  }
}
