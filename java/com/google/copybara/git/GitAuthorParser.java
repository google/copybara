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

package com.google.copybara.git;

import com.google.common.base.Preconditions;
import com.google.copybara.Author;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser of the Git author format.
 *
 * Git is lenient on the author validation and only requires {@code "Foo <bar>"}, but does not
 * validate that {@code bar} is actually an email. Also {@code "Foo <>"} is valid, but
 * {@code "<bar>"} is not.
 *
 * TODO(copybara-team): Consider using JGit's RawParseUtils
 */
class GitAuthorParser {

  private static final Pattern AUTHOR_PATTERN = Pattern.compile("(.+ )<(.*)>");

  /**
   * Parses a Git author {@code string} into an {@link Author}.
   */
  static Author parse(String author) {
    Preconditions.checkNotNull(author);
    Matcher matcher = AUTHOR_PATTERN.matcher(author);
    Preconditions.checkArgument(matcher.matches(),
        "Invalid author '%s'. Must be in the form of 'Name <email>'", author);
    return new Author(matcher.group(1).trim(), matcher.group(2).trim());
  }
}
