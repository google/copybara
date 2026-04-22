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

package com.google.copybara.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Utility methods for handling SCP-like URLs. */
public final class ScpUtil {

  private ScpUtil() {}

  /** Parses the given URL as an SCP-like URL. */
  public static Optional<ScpUrl> parseScpUrl(String url) {
    return ScpUrl.parse(url);
  }

  /**
   * Represents a parsed SCP-like URL ([user@]host:path).
   *
   * @param user the user part of the URL, or null if not present
   * @param host the host part of the URL
   * @param path the path part of the URL
   */
  public record ScpUrl(@Nullable String user, String host, String path) {

    private static final Pattern SCP_URI_PATTERN =
        Pattern.compile("^(?:([a-z][a-z0-9+-]+)@)?([a-zA-Z0-9_.-]+):([^/].*|/|/[^/].*)$");

    /**
     * Attempts to parse the given URL as an SCP-like URL.
     *
     * @param url the URL to parse
     * @return an Optional containing the parsed ScpUrl, or empty if it doesn't match
     */
    public static Optional<ScpUrl> parse(String url) {
      Matcher matcher = SCP_URI_PATTERN.matcher(url);
      if (matcher.matches()) {
        return Optional.of(
            new ScpUrl(
                /* user= */ matcher.group(1),
                /* host= */ matcher.group(2),
                /* path= */ matcher.group(3)));
      }
      return Optional.empty();
    }
  }
}
