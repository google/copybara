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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * A credential with a limited TTL
 */
public class TtlSecret extends StaticSecret {
  private final Instant ttl;
  private final Clock clock;

  public TtlSecret(String secret, String name, Instant ttl, Clock clock) {
    super(name, secret);
    this.ttl = Preconditions.checkNotNull(ttl);
    this.clock = Preconditions.checkNotNull(clock);
  }

  @Override
  public String printableValue() {
    return String.format("<static secret name %s with expiration %s>", name, ttl);
  }

  @Override
  public String provideSecret() throws CredentialRetrievalException {
    Instant now = clock.instant();
    if (ttl.isBefore(now)) {
      throw new CredentialRetrievalException(
          String.format(
              "Credential %s expired %d seconds ago.",
              printableValue(), Duration.between(ttl, now).getSeconds()));
    }
    return super.provideSecret();
  }

  @Override
  public boolean valid() {
    return ttl.isAfter(clock.instant().plusSeconds(/* 10s grace */ 10));
  }

  @Override
  public String toString() {
    return printableValue();
  }
}