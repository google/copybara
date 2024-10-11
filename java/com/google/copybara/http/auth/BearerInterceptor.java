/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.copybara.http.auth;

import com.google.api.client.http.HttpExecuteInterceptor;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.credentials.CredentialIssuer;
import com.google.copybara.credentials.CredentialIssuingException;
import com.google.copybara.credentials.CredentialRetrievalException;

/** An interceptor for adding Bearer authentication to an HTTP request. */
public class BearerInterceptor implements AuthInterceptor {

  private final CredentialIssuer issuer;

  public BearerInterceptor(CredentialIssuer issuer) {
    this.issuer = Preconditions.checkNotNull(issuer);
  }

  @Override
  public HttpExecuteInterceptor interceptor()
      throws CredentialRetrievalException, CredentialIssuingException {
    String token = issuer.issue().provideSecret();
    return (req) -> {
      var unused = req.getHeaders().setAuthorization("Bearer " + token);
    };
  }

  @Override
  public ImmutableList<ImmutableSetMultimap<String, String>> describeCredentials() {
    return ImmutableList.of(issuer.describe());
  }
}
