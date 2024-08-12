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

  private final ImmutableMap<String, CredentialIssuer> issuers;

  public HttpSecretInterceptor(ImmutableMap<String, CredentialIssuer> issuers) {
    this.issuers = issuers;
  }

  public String resolveStringSecrets(String value)
      throws CredentialIssuingException, CredentialRetrievalException {
    String template = "\\$\\{\\{(.*?)\\}\\}";
    Matcher matcher = Pattern.compile(template).matcher(value);
    while (matcher.find()) {
      String issuerName = matcher.group(1);
      CredentialIssuer issuer = issuers.get(issuerName);
      if (issuer == null) {
        throw new IllegalArgumentException(
            String.format("Credential issuer %s is not found", issuerName));
      }
      value = value.replace(matcher.group(0), issuer.issue().provideSecret());
    }
    return value;
  }
}
