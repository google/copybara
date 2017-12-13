/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.copybara.git.gerritapi;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utilities for dealing with Gerrit API
 */
public final class GerritApiUtil {

  /**
   * Parse dates like "2014-12-21 17:30:08.000000000"
   */
  private static final DateTimeFormatter timestampFormat =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.n").withZone(ZoneOffset.UTC);

  private GerritApiUtil() {}

  static ZonedDateTime parseTimestamp(String date) {
    return ZonedDateTime.ofInstant(timestampFormat.parse(date, Instant::from), ZoneOffset.UTC);
  }
}
