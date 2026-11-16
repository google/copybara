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

package com.google.copybara.http.endpoint;

import com.google.common.collect.ImmutableMap;
import com.google.copybara.credentials.CredentialIssuer;
import com.google.copybara.credentials.CredentialIssuingException;
import com.google.copybara.credentials.CredentialRetrievalException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HttpSecretInterceptor replaces secrets with their corresponding values. */
class HttpSecretInterceptor {

  private static final Pattern SECRET_PATTERN = Pattern.compile("\\$\\{\\{(.*?)\\}\\}");
  private static final Pattern ENCODED_SECRET_PATTERN =
      Pattern.compile("\\%24\\%7B\\%7B(.*?)\\%7D\\%7D");

  private final ImmutableMap<String, CredentialIssuer> issuers;

  /** Result of resolving {@code issuers} secret placeholders in a string. */
  record ResolvedString(String value, boolean resolvedSecret) {}

  /** Constructs an {@link HttpSecretInterceptor} backed by {@code issuers}. */
  public HttpSecretInterceptor(ImmutableMap<String, CredentialIssuer> issuers) {
    this.issuers = issuers;
  }

  /** Resolves {@code ${{...}}} and URL-encoded secret placeholders in {@code value}. */
  public String resolveStringSecrets(String value)
      throws CredentialIssuingException, CredentialRetrievalException {
    return resolve(value).value();
  }

  /**
   * Resolves {@code ${{...}}} and URL-encoded {@code %24%7B%7B...%7D%7D} secret placeholders in
   * {@code value} and reports whether at least one secret placeholder was resolved.
   */
  ResolvedString resolve(String value)
      throws CredentialIssuingException, CredentialRetrievalException {
    ResolvedString plain = replaceMatchesWithSecrets(value, SECRET_PATTERN.matcher(value));
    ResolvedString encoded =
        replaceMatchesWithSecrets(plain.value(), ENCODED_SECRET_PATTERN.matcher(plain.value()));
    return new ResolvedString(encoded.value(), plain.resolvedSecret() || encoded.resolvedSecret());
  }

  private ResolvedString replaceMatchesWithSecrets(String value, Matcher matcher)
      throws CredentialIssuingException, CredentialRetrievalException {
    boolean resolvedSecret = false;
    while (matcher.find()) {
      String issuerName = matcher.group(1);
      CredentialIssuer issuer = this.issuers.get(issuerName);
      if (issuer == null) {
        throw new IllegalArgumentException(
            String.format("Credential issuer %s is not found", issuerName));
      }
      value = value.replace(matcher.group(0), issuer.issue().provideSecret());
      resolvedSecret = true;
    }
    return new ResolvedString(value, resolvedSecret);
  }
}
