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

package com.google.copybara.http.endpoint;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpTransport;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.jimfs.Jimfs;
import com.google.common.testing.TestLogHandler;
import com.google.copybara.checks.CheckerException;
import com.google.copybara.config.Config;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.config.Migration;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.http.HttpOptions;
import com.google.copybara.http.auth.RedirectLoggingInterceptor;
import com.google.copybara.http.testing.MockHttpTester;
import com.google.copybara.testing.DummyChecker;
import com.google.copybara.testing.DummyTrigger;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import net.starlark.java.eval.Dict;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HttpEndpointTest {
  private SkylarkTestExecutor starlark;
  private MockHttpTester http;
  private Path workdir;

  @Before
  public void setUp() {
    workdir = Jimfs.newFileSystem().getPath("/");
    http = new MockHttpTester();
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    optionsBuilder.http =
        new HttpOptions() {
          @Override
          public HttpTransport getTransport() {
            return http.getTransport();
          }
        };
    optionsBuilder.testingOptions.checker = new DummyChecker(ImmutableSet.of("badword"));
    optionsBuilder.testingOptions.feedbackTrigger = new DummyTrigger();
    starlark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void testGet() throws ValidationException {
    http.mockHttp(
        (method, url, req, resp) -> {
          assertThat(method).isEqualTo("GET");
          resp.setStatusCode(204);
        });
    HttpEndpointResponse resp =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(http.endpoint(host = "foo.com"))
            resp = endpoint.get(url = "http://foo.com")
            """);
    assertThat(resp.getStatusCode()).isEqualTo(204);
  }

  @Test
  public void testStatus_nullReasonPhrase_returnsEmptyString() throws ValidationException {
    http.mockHttp((method, url, req, resp) -> {
      assertThat(method).isEqualTo("GET");
      resp.setStatusCode(200);
    });
    String status =
        starlark.eval(
            "status",
            """
            endpoint = testing.get_endpoint(http.endpoint(host = "foo.com"))
            resp = endpoint.get(url = "http://foo.com")
            status = resp.status()
            """);
    assertThat(status).isEmpty();
  }

  @Test
  public void testStatus_nonNullReasonPhrase_returnsReasonPhrase() throws ValidationException {
    http.mockHttp((method, url, req, resp) -> {
      if (url.endsWith("/ok")) {
        resp.setStatusCode(200);
        resp.setReasonPhrase("OK");
      } else {
        resp.setStatusCode(404);
        resp.setReasonPhrase("Not Found");
      }
    });
    String okStatus =
        starlark.eval(
            "status",
            """
            endpoint = testing.get_endpoint(http.endpoint(host = "foo.com"))
            resp = endpoint.get(url = "http://foo.com/ok")
            status = resp.status()
            """);
    assertThat(okStatus).isEqualTo("OK");

    String notFoundStatus =
        starlark.eval(
            "status",
            """
            endpoint = testing.get_endpoint(http.endpoint(host = "foo.com"))
            resp = endpoint.get(url = "http://foo.com/missing")
            status = resp.status()
            """);
    assertThat(notFoundStatus).isEqualTo("Not Found");
  }

  @Test
  public void testFollowRedirect() throws ValidationException, IOException {
    http.mockHttp(
        (method, url, req, resp) -> {
          String lastChar = Character.toString(url.charAt(url.length() - 1));
          int num = Integer.parseInt(lastChar);
          if (num == 1) {
            resp.setStatusCode(307);
            resp.addHeader("Location", "http://foo.com/2");
            resp.setContent("didn't redirect");
          } else {
            resp.setStatusCode(204);
            resp.setContent("redirected");
          }
        });

    // Default case, automatically follow redirects
    HttpEndpointResponse resp =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(http.endpoint(host = "foo.com"))
            resp = endpoint.get(url = "http://foo.com/1")
            """);
    assertThat(resp.getStatusCode()).isEqualTo(204);
    assertThat(resp.responseAsString()).isEqualTo("redirected");

    // Don't follow redirects
    HttpEndpointResponse resp2 =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(http.endpoint(host = "foo.com"))
            endpoint.followRedirects(False)\s
            resp = endpoint.get(url = "http://foo.com/1")
            """);
    assertThat(resp2.getStatusCode()).isEqualTo(307);
    assertThat(resp2.responseAsString()).isEqualTo("didn't redirect");
  }

  /**
   * Verifies that redirecting to a host outside {@code hosts} throws {@link ValidationException}
   * without sending any request to the unlisted host.
   */
  @Test
  public void testRedirect_unlistedHost_throwsValidationExceptionWithoutSendingRequest() {
    List<String> requestedUrls = new ArrayList<>();
    http.mockHttp((method, url, req, resp) -> {
      requestedUrls.add(url);
      resp.setStatusCode(302);
      resp.addHeader("Location", "https://unlisted.example/path");
    });

    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                starlark.eval(
                    "resp",
                    """
                    endpoint = testing.get_endpoint(
                        http.endpoint(
                            hosts = [
                                http.host(
                                    host = 'foo.com',
                                    auth = http.bearer_auth(
                                        creds = credentials.static_secret('token', 'foo-token'),
                                    ),
                                ),
                                'allowed.com',
                            ],
                        ),
                    )
                    resp = endpoint.get(url = "https://foo.com/start", auth = True)
                    """));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Illegal host: redirect from foo.com to unlisted.example matches none of endpoint hosts"
                + " {foo.com,allowed.com}");
    assertThat(requestedUrls).containsExactly("https://foo.com/start");
  }

  /**
   * Verifies that {@code http.trigger} checks {@code hosts} on each redirect hop and never contacts
   * an unlisted redirect target.
   */
  @Test
  public void testTriggerRedirect_unlistedHost_throwsValidationExceptionWithoutSendingRequest()
      throws Exception {
    List<String> requestedUrls = new ArrayList<>();
    http.mockHttp((method, url, req, resp) -> {
      requestedUrls.add(url);
      resp.setStatusCode(302);
      resp.addHeader("Location", "https://unlisted.example/path");
    });

    String config =
        """
        def test_action(ctx):
            ctx.origin.get(url = "https://foo.com/start")
            return ctx.success()

        core.action_migration(
            name = 'default',
            origin = http.trigger(
                hosts = ["foo.com"],
            ),
            endpoints = struct(
                destination = http.endpoint(host = "foo.com"),
            ),
            action = test_action,
        )
        """;
    Migration migration = loadConfig(config).getMigration("default");

    ValidationException e =
        assertThrows(
            ValidationException.class, () -> migration.run(workdir, ImmutableList.of("1")));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Illegal host: redirect from foo.com to unlisted.example matches none of endpoint"
                + " hosts");
    assertThat(requestedUrls).containsExactly("https://foo.com/start");
  }

  /** Verifies that redirect hop host matching is case-insensitive against {@code hosts}. */
  @Test
  public void testRedirect_caseInsensitiveListedHost_followsRedirect() throws Exception {
    http.mockHttp((method, url, req, resp) -> {
      if (url.equals("https://Foo.COM/start")) {
        resp.setStatusCode(302);
        resp.addHeader("Location", "https://BAR.example/final");
      } else {
        resp.setStatusCode(200);
      }
    });

    HttpEndpointResponse resp =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(
                http.endpoint(
                    hosts = [
                        "Foo.COM",
                        "Bar.Example",
                    ],
                ),
            )
            resp = endpoint.get(url = "https://Foo.COM/start")
            """);

    assertThat(resp.getStatusCode()).isEqualTo(200);
  }

  /**
   * Verifies that redirecting to another listed host with no configured credentials withholds the
   * initial host's credentials and logs the cross-host redirect warning once.
   */
  @Test
  public void testAuth_redirectToListedHostWithoutCreds_withholdsCredentialsAndLogs()
      throws Exception {
    Logger logger = Logger.getLogger(RedirectLoggingInterceptor.class.getName());
    TestLogHandler logHandler = new TestLogHandler();
    logger.addHandler(logHandler);
    Map<String, Optional<String>> authByUrl = new HashMap<>();
    http.mockHttp((method, url, req, resp) -> {
      authByUrl.put(url, Optional.ofNullable(req.getFirstHeaderValue("Authorization")));
      if (url.equals("https://foo.com/start")) {
        resp.setStatusCode(302);
        resp.addHeader("Location", "https://bar.com/file");
      } else {
        resp.setStatusCode(200);
      }
    });

    try {
      HttpEndpointResponse resp =
          starlark.eval(
              "resp",
              """
              endpoint = testing.get_endpoint(
                  http.endpoint(
                      hosts = [
                          http.host(
                              host = 'foo.com',
                              auth = http.bearer_auth(
                                  creds = credentials.static_secret('token', 'foo-token'),
                              ),
                          ),
                          'bar.com',
                      ],
                  ),
              )
              resp = endpoint.get(url = "https://foo.com/start", auth = True)
              """);

      assertThat(resp.getStatusCode()).isEqualTo(200);
      assertThat(authByUrl).containsEntry("https://foo.com/start", Optional.of("Bearer foo-token"));
      assertThat(authByUrl).containsEntry("https://bar.com/file", Optional.empty());
      assertThat(logHandler.getStoredLogRecords()).hasSize(1);
      assertThat(logHandler.getStoredLogRecords().get(0).getMessage())
          .contains(
              "Authenticated request redirected: from=foo.com to=bar.com other_host=true"
                  + " https_to_http=false signed_url=false");
    } finally {
      logger.removeHandler(logHandler);
    }
  }

  /**
   * Verifies that redirecting to another listed host withholds credentials even when that target
   * host has its own {@code http.host} credentials configured.
   */
  @Test
  public void testAuth_redirectToListedHostWithOwnCreds_withholdsCredentialsAndLogs()
      throws Exception {
    Logger logger = Logger.getLogger(RedirectLoggingInterceptor.class.getName());
    TestLogHandler logHandler = new TestLogHandler();
    logger.addHandler(logHandler);
    Map<String, Optional<String>> authByUrl = new HashMap<>();
    http.mockHttp((method, url, req, resp) -> {
      authByUrl.put(url, Optional.ofNullable(req.getFirstHeaderValue("Authorization")));
      if (url.equals("https://foo.com/start")) {
        resp.setStatusCode(302);
        resp.addHeader("Location", "https://BAR.COM/file");
      } else {
        resp.setStatusCode(200);
      }
    });

    try {
      HttpEndpointResponse resp =
          starlark.eval(
              "resp",
              """
              endpoint = testing.get_endpoint(
                  http.endpoint(
                      hosts = [
                          http.host(
                              host = 'foo.com',
                              auth = http.bearer_auth(
                                  creds = credentials.static_secret('token', 'foo-token'),
                              ),
                          ),
                          http.host(
                              host = 'bar.com',
                              auth = http.bearer_auth(
                                  creds = credentials.static_secret('token', 'bar-token'),
                              ),
                          ),
                      ],
                  ),
              )
              resp = endpoint.get(url = "https://foo.com/start", auth = True)
              """);

      assertThat(resp.getStatusCode()).isEqualTo(200);
      assertThat(authByUrl).containsEntry("https://foo.com/start", Optional.of("Bearer foo-token"));
      assertThat(authByUrl).containsEntry("https://BAR.COM/file", Optional.empty());
      assertThat(logHandler.getStoredLogRecords()).hasSize(1);
      assertThat(logHandler.getStoredLogRecords().get(0).getMessage())
          .contains("from=foo.com to=bar.com other_host=true https_to_http=false");
    } finally {
      logger.removeHandler(logHandler);
    }
  }

  /**
   * Verifies that same-host redirects (case-insensitive, ignoring port) retain the host's
   * credentials without logging a warning.
   */
  @Test
  public void testAuth_redirectToSameHostDifferentCaseAndPort_keepsCredentials() throws Exception {
    Logger logger = Logger.getLogger(RedirectLoggingInterceptor.class.getName());
    TestLogHandler logHandler = new TestLogHandler();
    logger.addHandler(logHandler);
    Map<String, Optional<String>> authByUrl = new HashMap<>();
    http.mockHttp((method, url, req, resp) -> {
      authByUrl.put(url, Optional.ofNullable(req.getFirstHeaderValue("Authorization")));
      if (url.equals("https://foo.com/start")) {
        resp.setStatusCode(302);
        resp.addHeader("Location", "https://FOO.COM:8443/file");
      } else {
        resp.setStatusCode(200);
      }
    });

    try {
      HttpEndpointResponse resp =
          starlark.eval(
              "resp",
              """
              endpoint = testing.get_endpoint(
                  http.endpoint(
                      hosts = [
                          http.host(
                              host = 'foo.com',
                              auth = http.bearer_auth(
                                  creds = credentials.static_secret('token', 'foo-token'),
                              ),
                          ),
                      ],
                  ),
              )
              resp = endpoint.get(url = "https://foo.com/start", auth = True)
              """);

      assertThat(resp.getStatusCode()).isEqualTo(200);
      assertThat(authByUrl)
          .containsEntry("https://FOO.COM:8443/file", Optional.of("Bearer foo-token"));
      assertThat(logHandler.getStoredLogRecords()).isEmpty();
    } finally {
      logger.removeHandler(logHandler);
    }
  }

  /**
   * Verifies that same-host {@code https} to {@code http} redirects withhold credentials on the
   * {@code http} hop and any subsequent hop, and log the redirect warning once.
   */
  @Test
  public void testAuth_redirectHttpsToHttpSameHost_withholdsCredentialsStickilyAndLogs()
      throws Exception {
    Logger logger = Logger.getLogger(RedirectLoggingInterceptor.class.getName());
    TestLogHandler logHandler = new TestLogHandler();
    logger.addHandler(logHandler);
    Map<String, Optional<String>> authByUrl = new HashMap<>();
    http.mockHttp((method, url, req, resp) -> {
      authByUrl.put(url, Optional.ofNullable(req.getFirstHeaderValue("Authorization")));
      switch (url) {
        case "https://foo.com/start" -> {
          resp.setStatusCode(302);
          resp.addHeader("Location", "http://foo.com/same");
        }
        case "http://foo.com/same" -> {
          resp.setStatusCode(302);
          resp.addHeader("Location", "https://foo.com/back");
        }
        default -> resp.setStatusCode(200);
      }
    });

    try {
      HttpEndpointResponse resp =
          starlark.eval(
              "resp",
              """
              endpoint = testing.get_endpoint(
                  http.endpoint(
                      hosts = [
                          http.host(
                              host = 'foo.com',
                              auth = http.bearer_auth(
                                  creds = credentials.static_secret('token', 'foo-token'),
                              ),
                          ),
                      ],
                  ),
              )
              resp = endpoint.get(url = "https://foo.com/start", auth = True)
              """);

      assertThat(resp.getStatusCode()).isEqualTo(200);
      assertThat(authByUrl).containsEntry("https://foo.com/start", Optional.of("Bearer foo-token"));
      assertThat(authByUrl).containsEntry("http://foo.com/same", Optional.empty());
      assertThat(authByUrl).containsEntry("https://foo.com/back", Optional.empty());
      assertThat(logHandler.getStoredLogRecords()).hasSize(1);
      assertThat(logHandler.getStoredLogRecords().get(0).getMessage())
          .contains("from=foo.com to=foo.com other_host=false https_to_http=true");
    } finally {
      logger.removeHandler(logHandler);
    }
  }

  /**
   * Verifies that when {@code auth = False}, no redirect hop receives credentials even if the
   * target host has credentials configured in {@code hosts}.
   */
  @Test
  public void testNoAuth_redirectToHostWithConfiguredCreds_neverSendsCredentials()
      throws Exception {
    Logger logger = Logger.getLogger(RedirectLoggingInterceptor.class.getName());
    TestLogHandler logHandler = new TestLogHandler();
    logger.addHandler(logHandler);
    Map<String, Optional<String>> authByUrl = new HashMap<>();
    http.mockHttp((method, url, req, resp) -> {
      authByUrl.put(url, Optional.ofNullable(req.getFirstHeaderValue("Authorization")));
      if (url.equals("https://foo.com/start")) {
        resp.setStatusCode(302);
        resp.addHeader("Location", "https://bar.com/file");
      } else {
        resp.setStatusCode(200);
      }
    });

    try {
      HttpEndpointResponse resp =
          starlark.eval(
              "resp",
              """
              endpoint = testing.get_endpoint(
                  http.endpoint(
                      hosts = [
                          http.host(
                              host = 'foo.com',
                              auth = http.bearer_auth(
                                  creds = credentials.static_secret('token', 'foo-token'),
                              ),
                          ),
                          http.host(
                              host = 'bar.com',
                              auth = http.bearer_auth(
                                  creds = credentials.static_secret('token', 'bar-token'),
                              ),
                          ),
                      ],
                  ),
              )
              resp = endpoint.get(url = "https://foo.com/start", auth = False)
              """);

      assertThat(resp.getStatusCode()).isEqualTo(200);
      assertThat(authByUrl).containsEntry("https://foo.com/start", Optional.empty());
      assertThat(authByUrl).containsEntry("https://bar.com/file", Optional.empty());
      assertThat(logHandler.getStoredLogRecords()).isEmpty();
    } finally {
      logger.removeHandler(logHandler);
    }
  }

  /**
   * Verifies that headers resolved from {@code issuers} are kept on same-host HTTPS redirects,
   * removed on cross-host listed redirects and all subsequent hops, and never sent to unlisted
   * redirect targets.
   */
  @Test
  public void testIssuersSecretHeader_keptOnSameHostAndDroppedOnCrossHostChain() throws Exception {
    Map<String, Optional<String>> apiKeyByUrl = new HashMap<>();
    Map<String, Optional<String>> cookieByUrl = new HashMap<>();
    Map<String, Optional<String>> publicHeaderByUrl = new HashMap<>();
    http.mockHttp((method, url, req, resp) -> {
      apiKeyByUrl.put(url, Optional.ofNullable(req.getFirstHeaderValue("X-Api-Key")));
      cookieByUrl.put(url, Optional.ofNullable(req.getFirstHeaderValue("Cookie")));
      publicHeaderByUrl.put(url, Optional.ofNullable(req.getFirstHeaderValue("X-Public")));
      switch (url) {
        case "https://example.com/same-1" -> {
          resp.setStatusCode(302);
          resp.addHeader("Location", "https://EXAMPLE.com:8443/same-2");
        }
        case "https://example.com/cross-1" -> {
          resp.setStatusCode(302);
          resp.addHeader("Location", "https://other.example/cross-2");
        }
        case "https://other.example/cross-2" -> {
          resp.setStatusCode(302);
          resp.addHeader("Location", "https://example.com/cross-3");
        }
        case "https://example.com/unlisted" -> {
          resp.setStatusCode(302);
          resp.addHeader("Location", "https://unlisted.example/path");
        }
        default -> resp.setStatusCode(200);
      }
    });

    HttpEndpointResponse sameHostResp =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(
                http.endpoint(
                    hosts = [
                        "example.com",
                        "other.example",
                    ],
                    issuers = {
                        "api_key": credentials.static_secret("secret", "api-key-value"),
                    },
                ),
            )
            resp = endpoint.get(
                url = "https://example.com/same-1",
                headers = {
                    "X-Api-Key": "${{api_key}}",
                    "Cookie": "sid=${{api_key}}",
                    "X-Public": "public-value",
                },
            )
            """);
    assertThat(sameHostResp.getStatusCode()).isEqualTo(200);
    assertThat(apiKeyByUrl)
        .containsEntry("https://EXAMPLE.com:8443/same-2", Optional.of("api-key-value"));
    assertThat(cookieByUrl)
        .containsEntry("https://EXAMPLE.com:8443/same-2", Optional.of("sid=api-key-value"));
    assertThat(publicHeaderByUrl)
        .containsEntry("https://EXAMPLE.com:8443/same-2", Optional.of("public-value"));

    HttpEndpointResponse crossHostResp =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(
                http.endpoint(
                    hosts = [
                        "example.com",
                        "other.example",
                    ],
                    issuers = {
                        "api_key": credentials.static_secret("secret", "api-key-value"),
                    },
                ),
            )
            resp = endpoint.get(
                url = "https://example.com/cross-1",
                headers = {
                    "X-Api-Key": "${{api_key}}",
                    "Cookie": "sid=${{api_key}}",
                    "X-Public": "public-value",
                },
            )
            """);
    assertThat(crossHostResp.getStatusCode()).isEqualTo(200);
    assertThat(apiKeyByUrl)
        .containsEntry("https://example.com/cross-1", Optional.of("api-key-value"));
    assertThat(apiKeyByUrl).containsEntry("https://other.example/cross-2", Optional.empty());
    assertThat(apiKeyByUrl).containsEntry("https://example.com/cross-3", Optional.empty());
    assertThat(cookieByUrl)
        .containsEntry("https://example.com/cross-1", Optional.of("sid=api-key-value"));
    assertThat(cookieByUrl).containsEntry("https://other.example/cross-2", Optional.empty());
    assertThat(cookieByUrl).containsEntry("https://example.com/cross-3", Optional.empty());
    assertThat(publicHeaderByUrl)
        .containsEntry("https://other.example/cross-2", Optional.of("public-value"));
    assertThat(publicHeaderByUrl)
        .containsEntry("https://example.com/cross-3", Optional.of("public-value"));

    assertThrows(
        ValidationException.class,
        () ->
            starlark.eval(
                "resp",
                """
                endpoint = testing.get_endpoint(
                    http.endpoint(
                        hosts = [
                            "example.com",
                            "other.example",
                        ],
                        issuers = {
                            "api_key": credentials.static_secret("secret", "api-key-value"),
                        },
                    ),
                )
                resp = endpoint.get(
                    url = "https://example.com/unlisted",
                    headers = {"X-Api-Key": "${{api_key}}"},
                )
                """));
    assertThat(apiKeyByUrl).doesNotContainKey("https://unlisted.example/path");
  }

  /**
   * Verifies that headers resolved from {@code issuers} are dropped when an {@code https} request
   * redirects to {@code http} on the same host and remain dropped on later {@code https} hops.
   */
  @Test
  public void testIssuersSecretHeader_droppedAfterHttpsToHttpRedirect() throws Exception {
    Map<String, Optional<String>> apiKeyByUrl = new HashMap<>();
    Map<String, Optional<String>> publicHeaderByUrl = new HashMap<>();
    http.mockHttp((method, url, req, resp) -> {
      apiKeyByUrl.put(url, Optional.ofNullable(req.getFirstHeaderValue("X-Api-Key")));
      publicHeaderByUrl.put(url, Optional.ofNullable(req.getFirstHeaderValue("X-Public")));
      switch (url) {
        case "https://example.com/start" -> {
          resp.setStatusCode(302);
          resp.addHeader("Location", "http://example.com/plain");
        }
        case "http://example.com/plain" -> {
          resp.setStatusCode(302);
          resp.addHeader("Location", "https://example.com/back");
        }
        default -> resp.setStatusCode(200);
      }
    });

    HttpEndpointResponse resp =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(
                http.endpoint(
                    hosts = ["example.com"],
                    issuers = {
                        "api_key": credentials.static_secret("secret", "api-key-value"),
                    },
                ),
            )
            resp = endpoint.get(
                url = "https://example.com/start",
                headers = {
                    "X-Api-Key": "${{api_key}}",
                    "X-Public": "public-value",
                },
            )
            """);

    assertThat(resp.getStatusCode()).isEqualTo(200);
    assertThat(apiKeyByUrl)
        .containsEntry("https://example.com/start", Optional.of("api-key-value"));
    assertThat(apiKeyByUrl).containsEntry("http://example.com/plain", Optional.empty());
    assertThat(apiKeyByUrl).containsEntry("https://example.com/back", Optional.empty());
    assertThat(publicHeaderByUrl)
        .containsEntry("http://example.com/plain", Optional.of("public-value"));
    assertThat(publicHeaderByUrl)
        .containsEntry("https://example.com/back", Optional.of("public-value"));
  }

  /**
   * Verifies that a 307 cross-host redirect on a request whose body contains an {@code issuers}
   * secret throws {@link ValidationException} without contacting the redirect target, while a
   * same-host HTTPS 307 redirect succeeds.
   */
  @Test
  public void testIssuersSecretBody_307CrossHostRedirect_throwsValidationException()
      throws Exception {
    List<String> requestedUrls = new ArrayList<>();
    http.mockHttp((method, url, req, resp) -> {
      requestedUrls.add(url);
      switch (url) {
        case "https://example.com/same-1" -> {
          resp.setStatusCode(307);
          resp.addHeader("Location", "https://example.com/same-2");
        }
        case "https://example.com/same-2" -> resp.setStatusCode(200);
        case "https://example.com/cross" -> {
          resp.setStatusCode(307);
          resp.addHeader("Location", "https://other.example/receive");
        }
        default -> resp.setStatusCode(200);
      }
    });

    HttpEndpointResponse sameHostResp =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(
                http.endpoint(
                    hosts = [
                        "example.com",
                        "other.example",
                    ],
                    issuers = {
                        "api_key": credentials.static_secret("secret", "api-key-value"),
                    },
                ),
            )
            resp = endpoint.post(
                url = "https://example.com/same-1",
                content = http.json({"token": "${{api_key}}"}),
            )
            """);
    assertThat(sameHostResp.getStatusCode()).isEqualTo(200);

    requestedUrls.clear();
    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                starlark.eval(
                    "resp",
                    """
                    endpoint = testing.get_endpoint(
                        http.endpoint(
                            hosts = [
                                "example.com",
                                "other.example",
                            ],
                            issuers = {
                                "api_key": credentials.static_secret("secret", "api-key-value"),
                            },
                        ),
                    )
                    resp = endpoint.post(
                        url = "https://example.com/cross",
                        content = http.urlencoded_form({"token": "${{api_key}}"}),
                    )
                    """));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Redirect from example.com to other.example would resend a request body containing"
                + " secrets; use followRedirects(False) to handle the redirect manually");
    assertThat(requestedUrls).containsExactly("https://example.com/cross");
  }

  /**
   * Verifies that a 303 cross-host redirect on a request whose body contains an {@code issuers}
   * secret succeeds as a body-less GET on the redirect target.
   */
  @Test
  public void testIssuersSecretBody_303CrossHostRedirect_succeedsWithNoBody() throws Exception {
    List<String> seenMethodsAndBodies = new ArrayList<>();
    http.mockHttp((method, url, req, resp) -> {
      seenMethodsAndBodies.add(method + ":" + req.getContentAsString());
      if (url.equals("https://example.com/submit")) {
        resp.setStatusCode(303);
        resp.addHeader("Location", "https://other.example/done");
      } else {
        resp.setStatusCode(200);
      }
    });

    HttpEndpointResponse resp =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(
                http.endpoint(
                    hosts = [
                        "example.com",
                        "other.example",
                    ],
                    issuers = {
                        "api_key": credentials.static_secret("secret", "api-key-value"),
                    },
                ),
            )
            resp = endpoint.post(
                url = "https://example.com/submit",
                content = http.json({"token": "${{api_key}}"}),
            )
            """);

    assertThat(resp.getStatusCode()).isEqualTo(200);
    assertThat(seenMethodsAndBodies)
        .containsExactly("POST:{\"token\":\"api-key-value\"}", "GET:")
        .inOrder();
  }

  /**
   * Verifies that {@code followRedirects(False)} returns a 3xx pointing to an unlisted host
   * untouched without throwing, redirect-budget exhaustion returns the final 3xx, and 303 converts
   * POST to GET.
   */
  @Test
  public void testRedirectUnchangedBehaviors_followFalseBudgetExhaustionAnd303PostToGet()
      throws Exception {
    List<String> seenMethodsAndBodies = new ArrayList<>();
    http.mockHttp((method, url, req, resp) -> {
      if (url.equals("https://foo.com/no-follow")) {
        resp.setStatusCode(302);
        resp.addHeader("Location", "https://unlisted.com/target");
        return;
      }
      if (url.startsWith("https://foo.com/loop/")) {
        int hop = Integer.parseInt(url.substring("https://foo.com/loop/".length()));
        resp.setStatusCode(302);
        resp.addHeader("Location", "https://foo.com/loop/" + (hop + 1));
        return;
      }
      seenMethodsAndBodies.add(method + ":" + req.getContentAsString());
      if (url.equals("https://foo.com/submit")) {
        resp.setStatusCode(303);
        resp.addHeader("Location", "https://bar.com/done");
      } else {
        resp.setStatusCode(200);
      }
    });

    HttpEndpointResponse unfollowed =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(
                http.endpoint(host = "foo.com"),
            )
            endpoint.followRedirects(False)
            resp = endpoint.get(url = "https://foo.com/no-follow")
            """);
    assertThat(unfollowed.getStatusCode()).isEqualTo(302);
    assertThat(unfollowed.responseHeader("Location"))
        .containsExactly("https://unlisted.com/target");

    HttpEndpointResponse exhausted =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(
                http.endpoint(host = "foo.com"),
            )
            resp = endpoint.get(url = "https://foo.com/loop/0")
            """);
    assertThat(exhausted.getStatusCode()).isEqualTo(302);
    assertThat(exhausted.responseHeader("Location")).containsExactly("https://foo.com/loop/11");

    HttpEndpointResponse postToGet =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(
                http.endpoint(
                    hosts = [
                        "foo.com",
                        "bar.com",
                    ],
                ),
            )
            resp = endpoint.post(
                url = "https://foo.com/submit",
                content = http.json({"k": "v"}),
            )
            """);
    assertThat(postToGet.getStatusCode()).isEqualTo(200);
    assertThat(seenMethodsAndBodies).containsExactly("POST:{\"k\":\"v\"}", "GET:").inOrder();
  }

  @Test
  public void testGet_auth() throws ValidationException {

    String username = "testuser";
    String password = "testpassword";
    http.mockHttp(
        (method, url, req, resp) -> assertThat(req.getHeaders().get("authorization")).isNotEmpty());
      starlark.eval(
          "resp",
          String.format("""
              endpoint = testing.get_endpoint(
                http.endpoint(hosts = [
                  http.host(host='foo.com',
                    auth=credentials.username_password(
                      credentials.static_value('%s'),
                      credentials.static_secret('password', '%s')))])
              )
              resp = endpoint.post(
                url = "http://foo.com",
                auth = True
              )""", username, password));
    }

  @Test
  public void testDescribe() throws ValidationException {

    String username = "testuser";
    String password = "testpassword";
    http.mockHttp(
        (method, url, req, resp) -> assertThat(req.getHeaders().get("authorization")).isNotEmpty());
    HttpEndpoint endpoint = starlark.eval(
        "endpoint",
        String.format("""
endpoint = testing.get_endpoint(
  http.endpoint(hosts = [
    http.host(host='foo.com',
      auth=credentials.username_password(
        credentials.static_value('%s'),
        credentials.static_secret('password', '%s')))])
)
""", username, password));
    ImmutableList<ImmutableSetMultimap<String, String>> creds = endpoint.describeCredentials();
    assertThat(creds).hasSize(2);
    assertThat(creds.get(0)).containsExactly(
        "type", "constant",
        "name", "testuser",
        "open", "true", "host",
        "foo.com");
    assertThat(creds.get(1)).containsExactly(
        "type", "constant",
        "name", "password",
        "open", "false",
        "host", "foo.com");
  }

  @Test
  public void testDescribeCredentials_withIssuers() throws ValidationException {
    String password = "testpassword";
    http.mockHttp(
        (method, url, req, resp) -> assertThat(req.getHeaders().get("authorization")).isNotEmpty());
    HttpEndpoint endpoint =
        starlark.eval(
            "endpoint",
            String.format(
                """
endpoint = testing.get_endpoint(
  http.endpoint(
    hosts = ['foo.com'],
    issuers = {
      'TEST_CREDS': credentials.static_secret('password', '%s'),
    }),
)
""",
                password));
    ImmutableList<ImmutableSetMultimap<String, String>> creds = endpoint.describeCredentials();
    assertThat(creds).hasSize(1);
    assertThat(creds.get(0))
        .containsExactly(
            "type", "constant",
            "name", "password",
            "open", "false",
            "key", "TEST_CREDS");
  }

  @Test
  public void testGetMultiHost() throws ValidationException {
    http.mockHttp(
        (method, url, req, resp) -> {
          assertThat(method).isEqualTo("GET");
          resp.setStatusCode(204);
        });
    HttpEndpointResponse fooRes =
        starlark.eval(
            "fooRes",
            """
            endpoint = testing.get_endpoint(http.endpoint(hosts = ["foo.com", "bar.com"]))
            fooRes = endpoint.get(url = "http://foo.com")
            """);
    assertThat(fooRes.getStatusCode()).isEqualTo(204);
    HttpEndpointResponse barRes =
        starlark.eval(
            "barRes",
            """
            endpoint = testing.get_endpoint(http.endpoint(hosts = ["foo.com", "bar.com"]))
            barRes = endpoint.get(url = "http://bar.com")
            """);
    assertThat(barRes.getStatusCode()).isEqualTo(204);
  }

  @Test
  public void testPost() throws ValidationException {
    http.mockHttp(
        (method, url, req, resp) -> {
          assertThat(method).isEqualTo("POST");
          resp.setStatusCode(204);
        });
    HttpEndpointResponse resp =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(
              http.endpoint(host = "foo.com")
            )
            resp = endpoint.post(url = "http://foo.com")
            """);
    assertThat(resp.getStatusCode()).isEqualTo(204);
  }

  @Test
  public void testDelete() throws ValidationException {
    http.mockHttp(
        (method, url, req, resp) -> {
          assertThat(method).isEqualTo("DELETE");
          resp.setStatusCode(204);
        });
    HttpEndpointResponse resp =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(
              http.endpoint(host = "foo.com")
            )
            resp = endpoint.delete(url = "http://foo.com")
            """);
    assertThat(resp.getStatusCode()).isEqualTo(204);
  }

  @Test
  public void testDownload() throws Exception {
    byte[] testContent = {0x08, 0x77, 0x12, 0x21, 0xc, 0x00, 0xd};
    http.mockHttp(
        (method, url, req, resp) -> {
          resp.setStatusCode(200);
          resp.setContent(testContent);
        });

    String config =
        """
            def test_action(ctx):
                resp = ctx.destination.get(url = "http://foo.com")
                p = ctx.fs.new_path('foo')
                resp.download(p)
                return ctx.success()

            core.action_migration(
                name = 'default',
                origin = testing.dummy_trigger(),
                endpoints = struct(
                    destination = http.endpoint(host = "foo.com")
                ),
                filesystem = True,
                action = test_action,
            )

            """;

    Migration actionMigration = loadConfig(config).getMigration("default");
    actionMigration.run(workdir, ImmutableList.of("12345"));
    assertThatPath(workdir)
        .containsFile("foo", new String(testContent, UTF_8))
        .containsNoMoreFiles();
  }

  private Config loadConfig(String content) throws IOException, ValidationException {
    return starlark.loadConfig(
        new MapConfigFile(
            ImmutableMap.of("copy.bara.sky", content.getBytes(UTF_8)), "copy.bara.sky"));
  }

  @Test
  public void testRequestHeader() throws ValidationException {
    ImmutableMap<String, List<String>> expectedHeaders =
        ImmutableMap.of(
            "content-type", ImmutableList.of("application/json"),
            "authorization", ImmutableList.of("Basic asdf"));
    http.mockHttp(
        (method, url, req, resp) -> {
          assertThat(req.getHeaders()).containsAtLeastEntriesIn(expectedHeaders);
          resp.setStatusCode(204);
        });
    HttpEndpointResponse resp =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(
              http.endpoint(host = "foo.com")
            )
            resp = endpoint.get(
              url = "http://foo.com",
              headers = {
                "Content-Type": "application/json",
                "Authorization": "Basic asdf",
              }
            )
            """);
    assertThat(resp.getStatusCode()).isEqualTo(204);
  }

  @Test
  public void testResponseHeader() throws ValidationException {
    http.mockHttp(
        (method, url, req, resp) -> {
          resp.addHeader("Content-Type", "application/json");
          resp.addHeader("Accept-Language", "en");
          resp.addHeader("Accept-Language", "de");
        });
    HttpEndpointResponse resp =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(
              http.endpoint(host = "foo.com")
            )
            resp = endpoint.get(url = "http://foo.com")
            """);

    // non existent header
    assertThat(resp.responseHeader("random")).isEmpty();

    // one item header
    assertThat(resp.responseHeader("Content-Type")).containsExactly("application/json");

    // multiple items header
    List<String> header = Arrays.asList("en", "de");
    assertThat(resp.responseHeader("Accept-Language")).containsExactlyElementsIn(header);
  }

  @Test
  public void testInvalidHostError() {
    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                starlark.eval(
                    "resp",
                    """
                    endpoint = testing.get_endpoint(
                      http.endpoint(host = "notfoohost.com")
                    )
                    resp = endpoint.get(
                      url = "http://foohost.com/fooresource",
                    )
                    """));
    assertThat(e).hasMessageThat().contains("Illegal host");
  }

  @Test
  public void testInvalidUrlError() {
    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                starlark.eval(
                    "resp",
                    """
                    endpoint = testing.get_endpoint(
                      http.endpoint(hosts = ["foohost.com", "foohost2.com", "foohost3.com"])
                    )
                    resp = endpoint.get(
                      url = "http://notfoohost.com",
                    )
                    """));
    assertThat(e).hasMessageThat().contains("Illegal host");
  }

  @Test
  public void testErrorCode() throws ValidationException {
    // verify we return error codes instead of throwing
    http.mockHttp(
        (method, url, req, resp) -> {
          assertThat(method).isEqualTo("GET");
          resp.setStatusCode(404);
        });
    HttpEndpointResponse resp =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(
              http.endpoint(host = "foo.com")
            )
            resp = endpoint.get(url = "http://foo.com")
            """);
    assertThat(resp.getStatusCode()).isEqualTo(404);
  }

  @Test
  public void testCheckerFailsUrlCheck() throws Exception {
    HttpEndpoint endpoint =
        starlark.eval(
            "endpoint",
            """
            endpoint = testing.get_endpoint(
              http.endpoint(host = "badword.com", checker = testing.dummy_checker())
            )
            """);
    assertThrows(
        CheckerException.class, () -> endpoint.get("http://badword.com", Dict.of(null), false));
  }

  @Test
  public void testResolveStringSecret() throws Exception {
    http.mockHttp(
        (method, url, req, resp) -> {
          assertThat(req.getHeaders().get("token").get(0)).isEqualTo("foo");
          assertThat(req.getContentAsString()).isEqualTo("{\"password\":\"bar\"}");
          resp.setStatusCode(200);
        });

    HttpEndpointResponse resp =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(
              http.endpoint(host = "foo.com", issuers = {
                "token": credentials.static_secret("secret", "foo"),
                "password": credentials.static_secret("secret", "bar")
            },
            ))
            resp = endpoint.post(
              url = "http://foo.com",
              headers = {"token": "${{token}}"},
              content = http.json({"password": "${{password}}"}),
            )
            """);
    assertThat(resp.getStatusCode()).isEqualTo(200);
  }

  @Test
  public void testResolveURLEncodedStringSecret() throws Exception {
    http.mockHttp(
        (method, url, req, resp) -> {
          assertThat(req.getContentAsString()).isEqualTo("password=bar");
          resp.setStatusCode(200);
        });

    HttpEndpointResponse resp =
        starlark.eval(
            "resp",
            """
            endpoint = testing.get_endpoint(
              http.endpoint(host = "foo.com", issuers = {
                "password": credentials.static_secret("secret", "bar")
              },
            ))
            resp = endpoint.post(
              url = "http://foo.com",
              content = http.urlencoded_form({"password": "${{password}}"}),
            )
            """);
    assertThat(resp.getStatusCode()).isEqualTo(200);
  }

  private static class TestContent implements HttpEndpointBody {
    @Override
    public HttpContent getContent() {
      return null;
    }
    // checker fails by default
  }

  @Test
  public void testCheckerFailsContentCheck() throws Exception {
    HttpEndpoint endpoint =
        starlark.eval(
            "endpoint",
            """
            endpoint = testing.get_endpoint(
              http.endpoint(host = "foo.com", checker = testing.dummy_checker())
            )
            """);
    assertThrows(
        CheckerException.class,
        () -> endpoint.post("http://foo.com", Dict.of(null), new TestContent(), false));
  }
}
