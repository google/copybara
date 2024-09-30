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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.http.HttpTransport;
import com.google.common.io.BaseEncoding;
import com.google.common.io.MoreFiles;
import com.google.copybara.credentials.CredentialOptions;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.http.HttpOptions;
import com.google.copybara.http.testing.MockHttpTester;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AuthTest {
  private SkylarkTestExecutor starlark;
  private MockHttpTester http;
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();
  
  private final HttpOptions httpOptions =
      new HttpOptions() {
        @Override
        public HttpTransport getTransport() {
          return http.getTransport();
        }
      };

  private CredentialOptions credentialOptions;

  @Before
  public void setUp() {
    http = new MockHttpTester();
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    credentialOptions = new CredentialOptions();
    optionsBuilder.http = httpOptions;
    optionsBuilder.credentialOptions = credentialOptions;
    starlark = new SkylarkTestExecutor(optionsBuilder);
  }

  private String basicAuth(String username, String password) {
    return String.format(
        "Basic %s",
        BaseEncoding.base64().encode(String.format("%s:%s", username, password).getBytes(UTF_8)));
  }

  @Test
  public void testBasicAuth() throws ValidationException {
    String username = "testuser";
    String password = "testpassword";

    String expectedAuthString = basicAuth(username, password);
    http.mockHttp(
        (method, url, req, resp) -> {
          assertThat(req.getHeaders().get("authorization")).containsExactly(expectedAuthString);
        });
    starlark.eval(
        "resp",
        String.format(
            ""
                + "endpoint = testing.get_endpoint(\n"
                + "  http.endpoint(hosts = ["
                + "    http.host(host='foo.com',"
                + "      auth=credentials.username_password("
                + "        credentials.static_value('%s'),"
                + "        credentials.static_secret('password', '%s')))])\n"
                + ")\n"
                + "resp = endpoint.post(\n"
                + "  url = \"http://foo.com\",\n"
                + "  auth = True"
                + ")",
            username, password));
  }

  @Test
  public void testBearerAuth() throws ValidationException {
    String token = "test-token-123";

    String expectedAuthString = String.format("Bearer %s", token);
    http.mockHttp(
        (method, url, req, resp) ->
            assertThat(req.getHeaders().get("authorization")).containsExactly(expectedAuthString));
    starlark.eval(
        "resp",
        String.format(
            ""
                + "endpoint = testing.get_endpoint(\n"
                + "  http.endpoint(hosts = ["
                + "    http.host(host='foo.com',"
                + "      auth=http.bearer_auth("
                + "        creds=credentials.static_value('%s')"
                + "      ))])\n"
                + ")\n"
                + "resp = endpoint.post(\n"
                + "  url = \"http://foo.com\",\n"
                + "  auth = True"
                + ")",
            token));
  }

  @Test
  public void testTomlDataSource() throws ValidationException, IOException {
    String username = "testuser";
    String password = "testpassword";

    Path configPath = tempFolder.getRoot().toPath().resolve("credentials.toml");
    credentialOptions.credentialFile = configPath;

    MoreFiles.asByteSink(configPath)
        .asCharSink(UTF_8)
        .write(String.format("" + "[copybara.test]\n" + "username = \"%s\"\n", username));
    String tomlKeyPath = "copybara.test.username";

    String expectedAuthString = basicAuth(username, password);

    http.mockHttp(
        (method, url, req, resp) -> {
          assertThat(req.getHeaders().get("authorization")).containsExactly(expectedAuthString);
        });

    starlark.eval(
        "resp",
        String.format(
            ""
                + "endpoint = testing.get_endpoint(\n"
                + "  http.endpoint(hosts = ["
                + "    http.host(host='foo.com',"
                + "      auth=credentials.username_password("
                + "        credentials.toml_key_source('%s'),"
                + "        credentials.static_secret('password', '%s'),"
                + "        ))])\n"
                + ")\n"
                + "resp = endpoint.post(\n"
                + "  url = \"http://foo.com\",\n"
                + "  auth = True"
                + ")",
            tomlKeyPath, password));
  }
}
