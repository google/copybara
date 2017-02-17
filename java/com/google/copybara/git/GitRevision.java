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
import com.google.copybara.RepoException;
import com.google.copybara.Revision;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * A Git repository reference
 */
public final class GitRevision implements Revision {

  static final Pattern COMPLETE_SHA1_PATTERN = Pattern.compile("[a-f0-9]{40}");

  private final GitRepository repository;
  private final String sha1;
  @Nullable
  private final String reference;

  /**
   * Create a git revision from a complete (40 characters) git SHA-1 string.
   *
   * @param repository git repository that should contain the {@code sha1}
   * @param sha1 a 40 characters SHA-1
   */
  GitRevision(GitRepository repository, String sha1) {
    this(repository, sha1, /*reference=*/null);
  }

  /**
   * Create a git revision from a complete (40 characters) git SHA-1 string.
   *
   * @param repository git repository that should contain the {@code sha1}
   * @param sha1 a 40 characters SHA-1
   * @param reference a stable name that describes where this is coming from. Could be a git
   * reference like 'master'
   */
  GitRevision(GitRepository repository, String sha1, @Nullable String reference) {
    Preconditions.checkArgument(COMPLETE_SHA1_PATTERN.matcher(sha1).matches(),
                                "Reference '%s' is not a 40 characters SHA-1", sha1);

    this.repository = Preconditions.checkNotNull(repository);
    this.sha1 = sha1;
    this.reference = reference;
  }

  @Nullable
  @Override
  public String contextReference() {
    return reference;
  }

  @Override
  public Instant readTimestamp() throws RepoException {
    // -s suppresses diff output
    // --format=%at indicates show the author timestamp as the number of seconds from UNIX epoch
    String stdout = repository.simpleCommand("log", "-1", "-s", "--format=%at", sha1)
        .getStdout();
    try {
      return Instant.ofEpochSecond(Long.parseLong(stdout.trim()));
    } catch (NumberFormatException e) {
      throw new RepoException("Output of git show not a valid long", e);
    }
  }

  @Override
  public String asString() {
    return sha1;
  }

  @Override
  public String getLabelName() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  @Override
  public String toString() {
    return sha1;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GitRevision that = (GitRevision) o;
    return Objects.equals(sha1, that.sha1);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(sha1);
  }
}
