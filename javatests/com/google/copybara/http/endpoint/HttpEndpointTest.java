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
import static org.junit.Assert.assertThrows;

import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpTransport;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.checks.CheckerException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.http.HttpOptions;
import com.google.copybara.http.testing.MockHttpTester;
import com.google.copybara.testing.DummyChecker;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import net.starlark.java.eval.Dict;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HttpEndpointTest {
  private SkylarkTestExecutor starlark;
  private MockHttpTester http;

  @Before
  public void setUp() {
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
            "endpoint = testing.get_endpoint(http.endpoint(host = \"foo.com\"))\n"
                + "resp = endpoint.get(url = \"http://foo.com\")\n");
    assertThat(resp.getStatusCode()).isEqualTo(204);
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
            "endpoint = testing.get_endpoint(http.endpoint(host = \"foo.com\"))\n"
                + "resp = endpoint.get(url = \"http://foo.com/1\")\n");
    assertThat(resp.getStatusCode()).isEqualTo(204);
    assertThat(resp.responseAsString()).isEqualTo("redirected");

    // Don't follow redirects
    HttpEndpointResponse resp2 =
        starlark.eval(
            "resp",
            "endpoint = testing.get_endpoint(http.endpoint(host = \"foo.com\"))\n"
                + "endpoint.followRedirects(False) \n"
                + "resp = endpoint.get(url = \"http://foo.com/1\")\n");
    assertThat(resp2.getStatusCode()).isEqualTo(307);
    assertThat(resp2.responseAsString()).isEqualTo("didn't redirect");
  }

  @Test
  public void testGet_auth() throws ValidationException {

      String username = "testuser";
      String password = "testpassword";
    http.mockHttp(
        (method, url, req, resp) -> assertThat(req.getHeaders().get("authorization")).isNotEmpty());
      starlark.eval(
          "resp",
          String.format(""
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
              + ")", username, password));
    }

  @Test
  public void testDescribe() throws ValidationException {

    String username = "testuser";
    String password = "testpassword";
    http.mockHttp(
        (method, url, req, resp) -> assertThat(req.getHeaders().get("authorization")).isNotEmpty());
    HttpEndpoint endpoint = starlark.eval(
        "endpoint",
        String.format(""
            + "endpoint = testing.get_endpoint(\n"
            + "  http.endpoint(hosts = ["
            + "    http.host(host='foo.com',"
            + "      auth=credentials.username_password("
            + "        credentials.static_value('%s'),"
            + "        credentials.static_secret('password', '%s')))])\n"
            + ")\n", username, password));
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
  public void testGetMultiHost() throws ValidationException {
    http.mockHttp(
        (method, url, req, resp) -> {
          assertThat(method).isEqualTo("GET");
          resp.setStatusCode(204);
        });
    HttpEndpointResponse fooRes =
        starlark.eval(
            "fooRes",
            "endpoint = testing.get_endpoint(http.endpoint(hosts = [\"foo.com\","
                + " \"bar.com\"]))\n"
                + "fooRes = endpoint.get(url = \"http://foo.com\")\n"
                + "barRes = endpoint.get(url = \"http://bar.com\")\n");
    assertThat(fooRes.getStatusCode()).isEqualTo(204);
    HttpEndpointResponse barRes =
        starlark.eval(
            "barRes",
            "endpoint = testing.get_endpoint(http.endpoint(hosts = [\"foo.com\","
                + " \"bar.com\"]))\n"
                + "fooRes = endpoint.get(url = \"http://foo.com\")\n"
                + "barRes = endpoint.get(url = \"http://bar.com\")\n");
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
            "endpoint = testing.get_endpoint(\n"
                + "  http.endpoint(host = \"foo.com\")\n"
                + ")\n"
                + "resp = endpoint.post(url = \"http://foo.com\")\n");
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
            "endpoint = testing.get_endpoint(\n"
                + "  http.endpoint(host = \"foo.com\")\n"
                + ")\n"
                + "resp = endpoint.delete(url = \"http://foo.com\")\n");
    assertThat(resp.getStatusCode()).isEqualTo(204);
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
            "endpoint = testing.get_endpoint(\n"
                + "  http.endpoint(host = \"foo.com\")\n"
                + ")\n"
                + "resp = endpoint.get(\n"
                + "  url = \"http://foo.com\",\n"
                + "  headers = {\n"
                + "    \"Content-Type\": \"application/json\",\n"
                + "    \"Authorization\": \"Basic asdf\",\n"
                + "  }"
                + ")\n");
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
            "endpoint = testing.get_endpoint(\n"
                + "  http.endpoint(host = \"foo.com\")\n"
                + ")\n"
                + "resp = endpoint.get(url = \"http://foo.com\")\n");

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
                    "endpoint = testing.get_endpoint(\n"
                        + "  http.endpoint(host = \"notfoohost.com\")\n"
                        + ")\n"
                        + "resp = endpoint.get(\n"
                        + "  url = \"http://foohost.com/fooresource\",\n"
                        + ")\n"));
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
                    "endpoint = testing.get_endpoint(\n"
                        + "  http.endpoint(hosts = [\"foohost.com\", \"foohost2.com\","
                        + " \"foohost3.com\"])\n"
                        + ")\n"
                        + "resp = endpoint.get(\n"
                        + "  url = \"http://notfoohost.com\",\n"
                        + ")\n"));
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
            "endpoint = testing.get_endpoint(\n"
                + "  http.endpoint(host = \"foo.com\")\n"
                + ")\n"
                + "resp = endpoint.get(url = \"http://foo.com\")\n");
    assertThat(resp.getStatusCode()).isEqualTo(404);
  }

  @Test
  public void testCheckerFailsUrlCheck() throws Exception {
    HttpEndpoint endpoint =
        starlark.eval(
            "endpoint",
            "endpoint = testing.get_endpoint(\n"
                + "  http.endpoint(host = \"badword.com\", checker ="
                + " testing.dummy_checker())\n"
                + ")\n");
    assertThrows(
        CheckerException.class, () -> endpoint.get("http://badword.com", Dict.of(null), false));
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
            "endpoint = testing.get_endpoint(\n"
                + "  http.endpoint(host = \"foo.com\", checker ="
                + " testing.dummy_checker())\n"
                + ")\n");
    assertThrows(
        CheckerException.class,
        () -> endpoint.post("http://foo.com", Dict.of(null), new TestContent(), false));
  }
}
