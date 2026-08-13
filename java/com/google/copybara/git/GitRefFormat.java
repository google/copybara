/*
 * Copyright (C) 2026 Google LLC
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Ascii;
import com.google.common.base.Enums;

/** Supported Git reference storage backends. */
public enum GitRefFormat {
  /** Traditional loose ref files and packed-refs. */
  FILES,
  /** Binary reftable format (Git 2.45+). */
  REFTABLE;

  /**
   * Returns the Git command-line configuration name for this reference format (e.g. "reftable").
   */
  public String getFormatName() {
    return Ascii.toLowerCase(name());
  }

  /**
   * Parses a string into the corresponding {@link GitRefFormat}.
   *
   * @throws IllegalArgumentException if the format is unknown
   */
  public static GitRefFormat fromString(String format) {
    checkNotNull(format, "format must not be null");
    return Enums.getIfPresent(GitRefFormat.class, Ascii.toUpperCase(format))
        .toJavaUtil()
        .orElseThrow(
            () -> new IllegalArgumentException(String.format("Unknown ref format '%s'.", format)));
  }
}
