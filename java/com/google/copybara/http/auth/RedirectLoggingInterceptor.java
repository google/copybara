/*
 * Copyright (C) 2026 Google LLC.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.nullToEmpty;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpExecuteInterceptor;
import com.google.api.client.http.HttpRequest;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.StackSize;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Wraps an authentication interceptor and logs when an authenticated request follows a redirect to
 * a different host, or from https to http. This helps debug authentication failures on redirects.
 * It does not change what the wrapped interceptor does.
 *
 * <p>Only host names are logged, never paths, query values, or headers. Each log line includes a
 * stack trace so that the caller can be identified.
 */
public final class RedirectLoggingInterceptor implements HttpExecuteInterceptor {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableSet<String> SIGNATURE_PARAMS =
      ImmutableSet.of("x-goog-signature", "x-amz-signature", "signature", "sig");

  private final HttpExecuteInterceptor delegate;
  private final String initialHost;
  private final boolean initialIsHttps;
  private final Set<String> logged = new HashSet<>();

  private RedirectLoggingInterceptor(HttpExecuteInterceptor delegate, GenericUrl initialUrl) {
    this.delegate = checkNotNull(delegate);
    this.initialHost = normalizeHost(initialUrl.getHost());
    this.initialIsHttps = Ascii.equalsIgnoreCase("https", nullToEmpty(initialUrl.getScheme()));
  }

  /**
   * Returns an interceptor that runs {@code delegate} on every request hop, and logs hops that go
   * to a different host than {@code initialUrl} or from https to http.
   *
   * @param delegate the interceptor that adds authentication to the request
   * @param initialUrl the URL of the request before any redirect
   */
  public static HttpExecuteInterceptor wrap(
      HttpExecuteInterceptor delegate, GenericUrl initialUrl) {
    return new RedirectLoggingInterceptor(delegate, initialUrl);
  }

  @Override
  public void intercept(HttpRequest request) throws IOException {
    delegate.intercept(request);

    GenericUrl url = request.getUrl();
    String host = normalizeHost(url.getHost());
    boolean otherHost = !host.equals(initialHost);
    boolean httpsToHttp =
        initialIsHttps && Ascii.equalsIgnoreCase("http", nullToEmpty(url.getScheme()));
    if (!otherHost && !httpsToHttp) {
      return;
    }
    if (!logged.add(host + "|" + httpsToHttp)) {
      return;
    }
    logger.atWarning().withStackTrace(StackSize.MEDIUM).log(
        "Authenticated request redirected: from=%s to=%s other_host=%s https_to_http=%s"
            + " signed_url=%s",
        initialHost, host, otherHost, httpsToHttp, hasSignatureParam(url));
  }

  private static String normalizeHost(@Nullable String host) {
    return host == null ? "" : Ascii.toLowerCase(host);
  }

  private static boolean hasSignatureParam(GenericUrl url) {
    for (String key : url.keySet()) {
      if (SIGNATURE_PARAMS.contains(Ascii.toLowerCase(key))) {
        return true;
      }
    }
    return false;
  }
}
