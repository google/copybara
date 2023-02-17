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

import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.stream.MalformedJsonException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;

/**
 * Utility class for parsing remote API JSON responses using GSON, with special handling if a
 * {@link MalformedJsonException} is encountered.
 *
 * <p>When a MalformedJsonException occurs, we can't retrieve the response as a
 * string because it has already been consumed. To work around this, we will copy the response
 * contents first to a ByteArrayInputStream. This allows us to return the string in an exception.
 */
public class GsonParserUtil {
  public static final String GSON_NO_EXECUTE_PREFIX = ")]}'\n";
  public static final GsonFactory GSON_FACTORY = new GsonFactory();

  /**
   * Parses a HttpResponse using GSON, with special handling if a
   * {@link MalformedJsonException} is encountered.
   *
   * @param response The HttpResponse to parse
   * @param responseType The Type to parse the response as
   * @param stripNoExecutePrefix Whether to filter out the non execute prefix from GSON
   * @throws IllegalArgumentException if the JSON can't be parsed to the given {@link Type}
   */
  @Nullable
  public <T> T parseHttpResponse(
      HttpResponse response,
      Type responseType,
      boolean stripNoExecutePrefix) throws IOException {

    if (isResponseEmpty(response)) {
      return null;
    }

    byte[] bytes = response.getContent().readAllBytes();
    Charset charset = response.getContentCharset();

    return parseBytes(bytes, charset, responseType, stripNoExecutePrefix);
  }

  private boolean isResponseEmpty(HttpResponse response) throws IOException {
    return response.getContent() == null
        || response.getStatusCode() == HttpStatusCodes.STATUS_CODE_NO_CONTENT;
  }

  /**
   * Parses a String using GSON, with special handling if a
   * {@link MalformedJsonException} is encountered.
   *
   * @param string The {@link String} to parse
   * @param dataType The {@link Type} to parse the data as
   * @param stripNoExecutePrefix Whether to filter out the non execute prefix from GSON
   * @throws IllegalArgumentException if the JSON can't be parsed to the given {@link Type}
   */
  @SuppressWarnings("unused")
  public <T> T parseString(
      String string,
      Type dataType,
      boolean stripNoExecutePrefix)
      throws IOException {
    Charset charset = StandardCharsets.UTF_8;
    return parseBytes(string.getBytes(charset), charset, dataType, stripNoExecutePrefix);
  }

  /**
   * Parses a byte array using GSON, with special handling if a
   * {@link MalformedJsonException} is encountered.
   *
   * @param bytes The bytes to parse
   * @param dataType The {@link Type} to parse the data as
   * @param stripNoExecutePrefix Whether to filter out the non execute prefix from GSON
   * @throws IllegalArgumentException if the JSON can't be parsed to the given {@link Type}
   */
  @SuppressWarnings("unchecked")
  public <T> T parseBytes(
      byte[] bytes,
      Charset charset,
      Type dataType,
      boolean stripNoExecutePrefix)
      throws IOException {
    InputStream stream = new ByteArrayInputStream(bytes);

    if (stripNoExecutePrefix) {
      /*  Read the first few bytes to check for Gerrit's magic prefix line, respecting original
          encoding. If the string read isn't the prefix line, we return the stream position
          back to the beginning. */
      byte[] prefix = stream.readNBytes(charset.encode(GSON_NO_EXECUTE_PREFIX).capacity());
      if (new String(prefix, charset).compareTo(GSON_NO_EXECUTE_PREFIX) != 0) {
        stream.reset();
      }
    }

    try {
      return (T) GSON_FACTORY.createJsonParser(stream, charset).parse(dataType, true);
    } catch (MalformedJsonException | IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format("Cannot parse content as type %s.\n"
                  + "Content: %s\n", dataType, new String(bytes, charset)), e);
    }
  }
}
