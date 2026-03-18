/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.copybara.remotefile;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.collect.ImmutableMultimap;
import com.google.copybara.credentials.CredentialIssuingException;
import com.google.copybara.credentials.CredentialRetrievalException;
import com.google.copybara.http.auth.AuthInterceptor;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import javax.annotation.Nullable;

/**
 * A HttpStreamFactory wrapping the Google GHttp Client without side effects
 */
public class GclientHttpStreamFactory implements HttpStreamFactory {

  private final HttpTransport javaNet;
  private final Duration timeout;


  GclientHttpStreamFactory(Duration timeout) {
    this(new NetHttpTransport(), timeout);
  }

  public GclientHttpStreamFactory(HttpTransport javaNet, Duration timeout) {
    this.javaNet = checkNotNull(javaNet);
    this.timeout = checkNotNull(timeout);
  }

  @Override
  public InputStream open(
      URL url, @Nullable AuthInterceptor auth, ImmutableMultimap<String, String> headers)
      throws IOException, CredentialRetrievalException, CredentialIssuingException {
    HttpRequest req = buildHttpRequest(url, auth, headers);
    return req.execute().getContent();
  }

  /**
   * Constructs an {@link HttpRequest} object.
   *
   * @param url The URL to send the request to
   * @param auth The authentication to use
   * @param headers The headers to set in the request
   * @return the request
   * @throws IOException if there is an issue obtaining the request factory, or asking it to build a
   *     request object
   * @throws CredentialRetrievalException if there is an issue using the auth object
   * @throws CredentialIssuingException if there is an issue using the auth object
   */
  protected HttpRequest buildHttpRequest(
      URL url, @Nullable AuthInterceptor auth, ImmutableMultimap<String, String> headers)
      throws IOException, CredentialRetrievalException, CredentialIssuingException {
    HttpRequest req =
        javaNet
            .createRequestFactory()
            .buildGetRequest(new GenericUrl(url))
            .setReadTimeout((int) timeout.toMillis())
            .setConnectTimeout((int) timeout.toMillis())
            .setUseRawRedirectUrls(true);
    headers.forEach((k, v) -> req.getHeaders().set(k, v));
    if (auth != null) {
      req.setInterceptor(auth.interceptor());
    }
    return req;
  }

  /**
   * Returns the {@link HttpTransport} used by this factory.
   *
   * @return the {@link HttpTransport}
   */
  protected HttpTransport getHttpTransport() {
    return javaNet;
  }
}
