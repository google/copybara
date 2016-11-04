/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.copybara.authoring;

import com.google.common.base.Preconditions;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser for the standard autor format {@code "Name <email>"}.
 *
 * <p>This is the format used by most VCS (Git, Mercurial) and also by the Copybara configuration
 * itself. The parser is lenient: {@code email} can be empty, and it doesn't validate that is an
 * actual email.
 */
public class AuthorParser {

  private static final Pattern AUTHOR_PATTERN = Pattern.compile("(?<name>[^<]+)<(?<email>[^>]*)>");

  /**
   * Parses a Git author {@code string} into an {@link Author}.
   */
  public static Author parse(String author) throws InvalidAuthorException {
    Preconditions.checkNotNull(author);
    Matcher matcher = AUTHOR_PATTERN.matcher(author);
    if (!matcher.matches()) {
      throw new InvalidAuthorException(
          String.format("Invalid author '%s'. Must be in the form of 'Name <email>'", author));
    }
    return new Author(matcher.group(1).trim(), matcher.group(2).trim());
  }
}
