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
package com.google.copybara.http.multipart;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.client.http.HttpTransport;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.http.HttpOptions;
import com.google.copybara.http.testing.MockHttpTester;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HttpEndpointUrlEncodedFormContentTest {
  private SkylarkTestExecutor starlark;
  private MockHttpTester http;
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

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
    optionsBuilder.testingOptions.checkoutDirectory = tempFolder.getRoot().toPath();
    starlark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void testContentType() throws ValidationException {
    http.mockHttp(
        (method, url, req, resp) ->
            assertThat(req.getContentType()).contains("application/x-www-form-urlencoded"));
    var unused =
        starlark.eval(
            "resp",
            "endpoint = testing.get_endpoint(\n"
                + "  http.endpoint(host = \"foo.com\")\n"
                + "  )\n"
                + "resp = endpoint.post(\n"
                + "  url = \"http://foo.com\",\n"
                + "  content = http.urlencoded_form({})\n"
                + ")");
  }

  @Test
  public void testFormContentsAreEncoded() throws ValidationException {
    http.mockHttp(
        (method, url, req, resp) -> {
          String content = req.getContentAsString();
          String expectedContent = "testfield=http%3A%2F%2Ffoo.com&foofield=%40special%23";
          assertThat(content).isEqualTo(expectedContent);
        });
    starlark.eval(
        "resp",
        "endpoint = testing.get_endpoint(\n"
            + "  http.endpoint(host = \"foo.com\")\n"
            + "  )\n"
            + "resp = endpoint.post(\n"
            + "  url = \"http://foo.com\",\n"
            + "  content = http.urlencoded_form({\n"
            + "    \"testfield\": \"http://foo.com\",\n"
            + "    \"foofield\": \"@special#\",\n"
            + "  })\n"
            + ")");
  }
}
