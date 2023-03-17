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

package com.google.copybara.json;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.Json;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GsonParserUtilTest {
  private final GsonParserUtil parserUtil = new GsonParserUtil();
  private final JsonFactory jsonFactory = new GsonFactory();

  @Test
  public void testParseJsonHttpResponse() throws Exception {
    JsonObject object = new JsonObject();
    object.addProperty("foo", 1);
    object.addProperty("bar", "baz");

    Map<String, ?> parsed =
        parserUtil.parseHttpResponse(
            getHttpResponse(
                object.toString().getBytes(StandardCharsets.UTF_8)), Map.class, false);

    // GSON parses integers as BigDecimals
    assertThat(parsed).isNotNull();
    assertThat(parsed.get("foo")).isEqualTo(new BigDecimal(1));
    assertThat(parsed.get("bar")).isEqualTo("baz");
  }

  @Test
  public void testParseJsonHttpResponse_removePrefix() throws Exception {
    JsonObject object = new JsonObject();
    object.addProperty("foo", 2);
    object.addProperty("bar", "fizzbuzz");

    String content = GsonParserUtil.GSON_NO_EXECUTE_PREFIX + "\n" + object;
    Map<String, ?> parsed =
        parserUtil.parseHttpResponse(
            getHttpResponse(content.getBytes(StandardCharsets.UTF_8)), Map.class, true);

    // GSON parses integers as BigDecimals
    assertThat(parsed).isNotNull();
    assertThat(parsed.get("foo")).isEqualTo(new BigDecimal(2));
    assertThat(parsed.get("bar")).isEqualTo("fizzbuzz");
  }

  @Test
  public void testParseJsonString() throws Exception {
    JsonObject object = new JsonObject();
    object.addProperty("foo", 3);
    object.addProperty("bar", "foo");

    String content = GsonParserUtil.GSON_NO_EXECUTE_PREFIX + "\n" + object;
    Map<String, ?> parsed = parserUtil.parseString(content, Map.class, true);

    // GSON parses integers as BigDecimals
    assertThat(parsed).isNotNull();
    assertThat(parsed.get("foo")).isEqualTo(new BigDecimal(3));
    assertThat(parsed.get("bar")).isEqualTo("foo");
  }

  @Test
  public void testParseJsonBytes() throws Exception {
    JsonObject object = new JsonObject();
    object.addProperty("foo", 4);
    object.addProperty("baz", "test");

    String content = GsonParserUtil.GSON_NO_EXECUTE_PREFIX + "\n" + object;
    Charset charset = StandardCharsets.UTF_8;
    Map<String, ?> parsed =
        parserUtil.parseBytes(content.getBytes(charset), charset, Map.class, true);

    // GSON parses integers as BigDecimals
    assertThat(parsed).isNotNull();
    assertThat(parsed.get("foo")).isEqualTo(new BigDecimal(4));
    assertThat(parsed.get("baz")).isEqualTo("test");
  }

  @Test
  public void testParseJson_noMalformedJsonException() throws Exception {
    String badJson = "%foo{)'";

    assertThrows(IllegalArgumentException.class,
        () -> parserUtil.parseString(badJson, Map.class, false));
  }

  private HttpResponse getHttpResponse(byte[] content) throws Exception {
    MockHttpTransport transport = new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) {
        MockLowLevelHttpRequest request = new MockLowLevelHttpRequest();
        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
        request.setResponse(response);
        response.setStatusCode(200);
        response.setContent(content);
        response.setContentType(Json.MEDIA_TYPE);
        return request;
      }

    };
    HttpRequestFactory requestFactory = transport.createRequestFactory(httpRequest -> {
      httpRequest.setParser(new JsonObjectParser(jsonFactory));
    });
    return requestFactory.buildGetRequest(new GenericUrl("https://foo/bar/")).execute();
  }
}
