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
import com.google.copybara.Origin.Reference;
import com.google.copybara.RepoException;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * A Git repository reference
 */
public final class GitReference implements Reference {

  private static final Pattern COMPLETE_SHA1_PATTERN = Pattern.compile("[a-f0-9]{40}");

  private final GitRepository repository;
  private final String reference;

  /**
   * Create a git reference from a complete (40 characters) git SHA-1 string.
   *
   * @param repository git repository that should contain the {@code reference}
   * @param reference a 40 characters SHA-1
   */
  GitReference(GitRepository repository, String reference) {
    Preconditions.checkArgument(COMPLETE_SHA1_PATTERN.matcher(reference).matches(),
        "Reference '%s' is not a 40 characters SHA-1", reference);

    this.repository = repository;
    this.reference = reference;
  }

  @Override
  public Instant readTimestamp() throws RepoException {
    // -s suppresses diff output
    // --format=%at indicates show the author timestamp as the number of seconds from UNIX epoch
    String stdout = repository.simpleCommand("show", "-s", "--format=%at", reference).getStdout();
    try {
      return Instant.ofEpochSecond(Long.parseLong(stdout.trim()));
    } catch (NumberFormatException e) {
      throw new RepoException("Output of git show not a valid long", e);
    }
  }

  @Override
  public String asString() {
    return reference;
  }

  @Override
  public String getLabelName() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  @Override
  public String toString() {
    return reference;
  }
}
