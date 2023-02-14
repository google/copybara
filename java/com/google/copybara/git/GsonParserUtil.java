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

package com.google.copybara.git;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.util.ObjectParser;
import com.google.gson.stream.MalformedJsonException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import javax.annotation.Nullable;

/**
 * Utility class for parsing remote API JSON responses using GSON
 */
public class GsonParserUtil {
  public static final String GERRIT_JSON_PREFIX = ")]}'";

  /**
   * Parses a HttpResponse using GSON, with special handling if a
   * {@link MalformedJsonException MalformedJsonException} is encountered.
   *
   * <p>When a MalformedJsonException occurs, we can't retrieve the response as a
   * string because it has already been consumed. To work around this, we will copy the response
   * contents first to a ByteArrayInputStream. This allows us to return the string in an exception.
   *
   * @param response The HttpResponse to parse
   * @param responseType The Type to parse the response as
   */
  @Nullable
  public <T> T parseJson(
      HttpResponse response,
      Type responseType) throws IOException, IllegalArgumentException {

    if (isResponseEmpty(response)) {
      return null;
    }

    HttpRequest request = response.getRequest();
    InputStream content = new ByteArrayInputStream(response.getContent().readAllBytes());

    return parseResponseInputStream(request.getParser(),
        content, responseType, response.getContentCharset(), request.getUrl());
  }

  /**
   * Parses a HttpResponse from Gerrit API using GSON, with special handling if a
   * {@link MalformedJsonException MalformedJsonException} is encountered.
   *
   * <p>This is similar to the {@link #parseJson(HttpResponse, Type) parseJson} method.
   * However, this method trims Gerrit's magic prefix line (used to mitigate XSSI attacks)
   * from the response before parsing.
   *
   * @param response The HttpResponse to parse
   * @param responseType The Type to parse the response as
   */

  @Nullable
  public <T> T parseJsonFromGerrit(
      HttpResponse response,
      Type responseType)
      throws IOException, IllegalArgumentException {

    if (isResponseEmpty(response)) {
      return null;
    }

    HttpRequest request = response.getRequest();
    Charset charset = response.getContentCharset();
    InputStream content = new ByteArrayInputStream(response.getContent().readAllBytes());

    /*  Read the first few bytes to check for Gerrit's magic prefix line, respecting original
        encoding. If the string read isn't the prefix line, we return the stream position
        back to the beginning. */
    byte[] prefix = content.readNBytes(charset.encode(GERRIT_JSON_PREFIX).capacity());
    if (new String(prefix, charset).compareTo(GERRIT_JSON_PREFIX) != 0) {
      content.reset();
    }

    return parseResponseInputStream(
        request.getParser(), content, responseType, charset, request.getUrl());
  }

  private boolean isResponseEmpty(HttpResponse response) throws IOException {
    return response.getContent() == null
        || response.getStatusCode() == HttpStatusCodes.STATUS_CODE_NO_CONTENT;
  }

  @SuppressWarnings("unchecked")
  private <T> T parseResponseInputStream(
      ObjectParser parser,
      InputStream content,
      Type responseType,
      Charset charset,
      GenericUrl url)
      throws IOException {
    try {
      return (T) parser.parseAndClose(content, charset, responseType);
    } catch (MalformedJsonException | IllegalArgumentException e) {
      content.reset();
      throw new IllegalArgumentException(
          String.format("Cannot parse response as type %s.\n"
                  + "Request: %s\n"
                  + "Response:\n%s", responseType,
              url, new String(content.readAllBytes(), charset)), e);
    }
  }
}
