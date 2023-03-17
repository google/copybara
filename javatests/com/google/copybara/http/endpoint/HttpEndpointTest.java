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

import com.google.api.client.http.HttpTransport;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.http.HttpOptions;
import com.google.copybara.http.testing.MockHttpTester;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import java.util.List;
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
    starlark =
        new SkylarkTestExecutor(
            new OptionsBuilder()
                .setHttpOptions(
                    new HttpOptions() {
                      @Override
                      public HttpTransport getTransport() {
                        return http.getTransport();
                      }
                    }));
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
  public void testHeader() throws ValidationException {
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
    assertThat(e).hasMessageThat().contains("does not match endpoint host");
  }
}
