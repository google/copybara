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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.testing.TestLogHandler;
import com.google.copybara.http.testing.MockHttpTester;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RedirectLoggingInterceptorTest {

  // Keep a strong reference so the JDK doesn't garbage collect the logger during the test.
  private final Logger logger = Logger.getLogger(RedirectLoggingInterceptor.class.getName());
  private final TestLogHandler logHandler = new TestLogHandler();
  private final MockHttpTester http = new MockHttpTester();
  private final Map<String, Optional<String>> authByUrl = new HashMap<>();

  @Before
  public void setUp() {
    logHandler.setLevel(Level.ALL);
    logger.addHandler(logHandler);
  }

  @After
  public void tearDown() {
    logger.removeHandler(logHandler);
  }

  /** Mocks each URL in {@code redirects} returning a 302 to its value, and 200 otherwise. */
  private void mockRedirects(ImmutableMap<String, String> redirects) {
    http.mockHttp((method, url, req, resp) -> {
      authByUrl.put(url, Optional.ofNullable(req.getFirstHeaderValue("Authorization")));
      if (redirects.containsKey(url)) {
        resp.setStatusCode(302);
        resp.addHeader("Location", redirects.get(url));
      } else {
        resp.setStatusCode(200);
      }
    });
  }

  private void execute(String url) throws IOException {
    GenericUrl genericUrl = new GenericUrl(url);
    HttpRequest request = http.getTransport().createRequestFactory().buildGetRequest(genericUrl);
    request.setInterceptor(
        RedirectLoggingInterceptor.wrap(
            req -> req.getHeaders().setAuthorization("Bearer token"), genericUrl));
    request.execute();
  }

  private ImmutableList<String> loggedMessages() {
    return logHandler.getStoredLogRecords().stream()
        .map(LogRecord::getMessage)
        .collect(toImmutableList());
  }

  @Test
  public void redirectToSameHost_doesNotLog() throws Exception {
    mockRedirects(ImmutableMap.of("https://foo.com/start", "https://foo.com/file"));

    execute("https://foo.com/start");

    assertThat(loggedMessages()).isEmpty();
    assertThat(authByUrl).containsEntry("https://foo.com/file", Optional.of("Bearer token"));
  }

  @Test
  public void redirectToOtherHost_logsHostsOnly() throws Exception {
    mockRedirects(ImmutableMap.of("https://foo.com/start", "https://bar.com/file"));

    execute("https://foo.com/start");

    assertThat(logHandler.getStoredLogRecords()).hasSize(1);
    assertThat(logHandler.getStoredLogRecords().get(0).getLevel()).isEqualTo(Level.WARNING);
    assertThat(logHandler.getStoredLogRecords().get(0).getThrown()).isNotNull();
    String message = loggedMessages().get(0);
    assertThat(message)
        .contains("from=foo.com to=bar.com other_host=true https_to_http=false signed_url=false");
    assertThat(message).doesNotContain("/start");
    assertThat(message).doesNotContain("/file");
  }

  /**
   * Verifies that cross-host redirects withhold credentials on that hop and any later hops, even if
   * a subsequent hop returns to the initial host.
   */
  @Test
  public void redirectToOtherHostAndBack_withholdsCredentialsStickily() throws Exception {
    mockRedirects(
        ImmutableMap.of(
            "https://foo.com/start", "https://bar.com/hop",
            "https://bar.com/hop", "https://foo.com/final"));

    execute("https://foo.com/start");

    assertThat(authByUrl).containsEntry("https://foo.com/start", Optional.of("Bearer token"));
    assertThat(authByUrl).containsEntry("https://bar.com/hop", Optional.empty());
    assertThat(authByUrl).containsEntry("https://foo.com/final", Optional.empty());
    assertThat(loggedMessages()).hasSize(1);
    assertThat(loggedMessages().get(0))
        .contains("from=foo.com to=bar.com other_host=true https_to_http=false signed_url=false");
  }

  /**
   * Verifies that {@code https}-to-{@code http} redirects on the same host log a warning and
   * withhold credentials on the {@code http} hop and any subsequent {@code https} hop.
   */
  @Test
  public void redirectFromHttpsToHttpAndBackToHttps_withholdsCredentialsStickilyAndLogs()
      throws Exception {
    mockRedirects(
        ImmutableMap.of(
            "https://foo.com/start", "http://foo.com/hop",
            "http://foo.com/hop", "https://foo.com/final"));

    execute("https://foo.com/start");

    assertThat(authByUrl).containsEntry("https://foo.com/start", Optional.of("Bearer token"));
    assertThat(authByUrl).containsEntry("http://foo.com/hop", Optional.empty());
    assertThat(authByUrl).containsEntry("https://foo.com/final", Optional.empty());
    assertThat(loggedMessages()).hasSize(1);
    assertThat(loggedMessages().get(0))
        .contains("from=foo.com to=foo.com other_host=false https_to_http=true signed_url=false");
  }

  /**
   * Verifies that a redirect chain starting on {@code http} and staying on the same host continues
   * delegating credentials across {@code http} and {@code https} hops without logging.
   */
  @Test
  public void redirectHttpInitialSameHostChain_alwaysDelegates() throws Exception {
    mockRedirects(
        ImmutableMap.of(
            "http://foo.com/start", "http://foo.com/hop",
            "http://foo.com/hop", "https://foo.com/upgraded",
            "https://foo.com/upgraded", "http://foo.com/final"));

    execute("http://foo.com/start");

    assertThat(authByUrl).containsEntry("http://foo.com/start", Optional.of("Bearer token"));
    assertThat(authByUrl).containsEntry("http://foo.com/hop", Optional.of("Bearer token"));
    assertThat(authByUrl).containsEntry("https://foo.com/upgraded", Optional.of("Bearer token"));
    assertThat(authByUrl).containsEntry("http://foo.com/final", Optional.of("Bearer token"));
    assertThat(loggedMessages()).isEmpty();
  }

  @Test
  public void redirectToSignedUrl_logsMarkerButNotValue() throws Exception {
    mockRedirects(
        ImmutableMap.of(
            "https://foo.com/start", "https://bar.com/file?X-Goog-Signature=secretvalue"));

    execute("https://foo.com/start");

    assertThat(loggedMessages()).hasSize(1);
    assertThat(loggedMessages().get(0)).contains("signed_url=true");
    assertThat(loggedMessages().get(0)).doesNotContain("secretvalue");
  }

  @Test
  public void redirectToSameHostWithDifferentCaseAndPort_doesNotLog() throws Exception {
    mockRedirects(ImmutableMap.of("https://foo.com/start", "https://FOO.com:8443/file"));

    execute("https://foo.com/start");

    assertThat(loggedMessages()).isEmpty();
  }

  @Test
  public void repeatedRedirectsToSameHost_logOnce() throws Exception {
    mockRedirects(
        ImmutableMap.of(
            "https://foo.com/start", "https://bar.com/a",
            "https://bar.com/a", "https://bar.com/b"));

    execute("https://foo.com/start");

    assertThat(loggedMessages()).hasSize(1);
    assertThat(authByUrl).containsKey("https://bar.com/b");
  }
}
