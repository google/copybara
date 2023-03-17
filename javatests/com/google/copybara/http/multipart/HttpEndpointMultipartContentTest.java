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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.common.base.Splitter;
import com.google.common.io.MoreFiles;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.http.HttpOptions;
import com.google.copybara.http.endpoint.HttpEndpointResponse;
import com.google.copybara.http.testing.MockHttpTester;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HttpEndpointMultipartContentTest {
  private SkylarkTestExecutor starlark;
  private MockHttpTester http;
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

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
                    })
                .setCheckoutDirectory(tempFolder.getRoot().toPath()));
  }

  @Test
  public void testContentType() throws ValidationException {
    http.mockHttp(
        (method, url, req, resp) -> {
          assertThat(req.getContentType()).contains("multipart/form-data");
        });
    HttpEndpointResponse resp =
        starlark.eval(
            "resp",
            "endpoint = testing.get_endpoint(\n"
                + "  http.endpoint(host = \"foo.com\")\n"
                + "  )\n"
                + "resp = endpoint.post(\n"
                + "  url = \"http://foo.com\",\n"
                + "  content = http.multipart_form([])\n"
                + ")");
  }

  @Test
  public void testTextField() throws ValidationException {
    http.mockHttp(
        (method, url, req, resp) -> {
          assertThat(getFormParts(req).size()).isEqualTo(1);
          String formPart = getFormParts(req).get(0);
          assertThat(getMessageContent(formPart)).isEqualTo("testtext");
          Map<String, String> headers = getHeaders(formPart);
          assertThat(getHeaderValueKey(headers.get("content-disposition"), "name"))
              .isEqualTo("\"testfield\"");
        });
    starlark.eval(
        "resp",
        "endpoint = testing.get_endpoint(\n"
            + "  http.endpoint(host = \"foo.com\")\n"
            + "  )\n"
            + "resp = endpoint.post(\n"
            + "  url = \"http://foo.com\",\n"
            + "  content = http.multipart_form([\n"
            + "    http.multipart_form_text(\"testfield\", \"testtext\")\n"
            + "  ])\n"
            + ")");
  }

  @Test
  public void testFileField() throws ValidationException, IOException {
    http.mockHttp(
        (method, url, req, resp) -> {
          assertThat(getFormParts(req).size()).isEqualTo(1);
          String formPart = getFormParts(req).get(0);
          Map<String, String> headers = getHeaders(formPart);
          assertThat(getHeaderValueKey(headers.get("content-disposition"), "name"))
              .isEqualTo("\"testfield\"");
          assertThat(headers.get("content-type")).isEqualTo("application/json");
          assertThat(getMessageContent(formPart)).isEqualTo("testtext");
        });
    // create a file in the checkout directory
    MoreFiles.asByteSink(tempFolder.getRoot().toPath().resolve("testfile.txt"))
        .asCharSink(UTF_8)
        .write("testtext");

    starlark.eval(
        "resp",
        "endpoint = testing.get_endpoint(\n"
            + "  http.endpoint(host = \"foo.com\")\n"
            + "  )\n"
            + "test_file = testing.get_checkout(\"testfile.txt\")\n"
            + "resp = endpoint.post(\n"
            + "  url = \"http://foo.com\",\n"
            + "  content = http.multipart_form([\n"
            + "    http.multipart_form_file(\n"
            + "      \"testfield\",\n"
            + "      test_file,\n"
            + "      content_type=\"application/json\",\n"
            + "      filename=\"newname.txt\"\n"
            + "    )\n"
            + "  ])\n"
            + ")");
  }

  @Test
  public void testMultipleFields() throws ValidationException, IOException {
    http.mockHttp(
        (method, url, req, resp) -> {
          // these assertions rely on deterministic field ordering by the http client
          assertThat(getFormParts(req).size()).isEqualTo(2);
          String testPart = getFormParts(req).get(0);
          Map<String, String> testHeaders = getHeaders(testPart);
          assertThat(getHeaderValueKey(testHeaders.get("content-disposition"), "name"))
              .isEqualTo("\"testfield\"");
          assertThat(getMessageContent(testPart)).isEqualTo("testtext");

          String fooPart = getFormParts(req).get(1);
          Map<String, String> fooHeaders = getHeaders(fooPart);
          assertThat(getHeaderValueKey(fooHeaders.get("content-disposition"), "name"))
              .isEqualTo("\"foofield\"");
          assertThat(getMessageContent(fooPart)).isEqualTo("footext");
        });

    starlark.eval(
        "resp",
        "endpoint = testing.get_endpoint(\n"
            + "  http.endpoint(host = \"foo.com\")\n"
            + "  )\n"
            + "resp = endpoint.post(\n"
            + "  url = \"http://foo.com\",\n"
            + "  content = http.multipart_form([\n"
            + "    http.multipart_form_text(\"testfield\", \"testtext\"),\n"
            + "    http.multipart_form_text(\"foofield\", \"footext\")\n"
            + "  ])\n"
            + ")");
  }

  private Map<String, String> getHeaders(String message) {
    List<String> messageLines = Splitter.on("\n").splitToList(message);
    Map<String, String> headers = new HashMap<>();
    for (String line : messageLines) {
      if (line.equals("")) {
        return headers;
      }
      List<String> headerParts = Splitter.on(":").limit(2).splitToList(line);
      headers.put(headerParts.get(0).toLowerCase(), headerParts.get(1).strip());
    }
    return headers;
  }

  private String getMessageContent(String message) {
    List<String> messageLines = Splitter.on("\n").splitToList(message);
    StringJoiner joiner = new StringJoiner("\n");

    boolean pastBoundary = false;
    for (String line : messageLines) {
      if (!pastBoundary && line.equals("")) {
        pastBoundary = true;
      } else if (pastBoundary) {
        joiner.add(line);
      }
    }
    return joiner.toString();
  }

  // getHeaderValueKey("application/form-data; boundary=foo", "boundary")
  // should return the value of the "boundary" parameter in the header value
  // which in the example is foo
  private String getHeaderValueKey(String headerValue, String paramKey) {
    String[] parts = headerValue.split(";");
    for (String part : parts) {
      if (part.stripLeading().startsWith(paramKey + '=')) {
        return part.split("=")[1];
      }
    }
    throw new RuntimeException(
        String.format(
            "param key %s not found in header %s",
            paramKey,
            headerValue));
  }

  private String getBoundary(MockLowLevelHttpRequest req) {
    String contentType = req.getContentType();
    return "--" + getHeaderValueKey(contentType, "boundary");
  }

  private String getEndBoundary(MockLowLevelHttpRequest req) {
    return getBoundary(req) + "--";
  }

  // return the data within the boundaries of each part in an http multipart request as a string
  private List<String> getFormParts(MockLowLevelHttpRequest req) throws IOException {
    List<String> parts = new ArrayList<>();
    String content = req.getContentAsString();
    String[] lines = content.split("\n");

    StringJoiner joiner = null;
    String boundary = getBoundary(req);
    String endBoundary = getEndBoundary(req);

    for (String line : lines) {
      line = line.strip();
      if (line.equals(boundary)) {
        if (joiner != null) {
          parts.add(joiner.toString());
        }
        joiner = new StringJoiner("\n");
      } else if (line.equals(endBoundary)) {
        if (joiner != null) {
          parts.add(joiner.toString());
        } else {
          // message format error or parse error
          throw new RuntimeException("failed to parse form parts");
        }
      } else {
        if (joiner != null) {
          joiner.add(line);
        }
      }
    }
    return parts;
  }
}
