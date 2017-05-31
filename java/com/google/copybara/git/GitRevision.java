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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.copybara.RepoException;
import com.google.copybara.Revision;
import com.google.copybara.git.GitRepository.GitLogEntry;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** A Git repository reference */
public final class GitRevision implements Revision {

  static final Pattern COMPLETE_SHA1_PATTERN = Pattern.compile("[a-f0-9]{40}");

  private final GitRepository repository;
  private final String sha1;
  @Nullable private final String reference;
  private ImmutableMap<String, String> associatedLabels;
  @Nullable private final String reviewReference;

  /**
   * Create a git revision from a complete (40 characters) git SHA-1 string.
   *
   * @param repository git repository that should contain the {@code sha1}
   * @param sha1 a 40 characters SHA-1
   */
  GitRevision(GitRepository repository, String sha1) {
    this(repository, sha1, /*reviewReference=*/ null, /*reference=*/ null, ImmutableMap.of());
  }

  /**
   * Create a git revision from a complete (40 characters) git SHA-1 string.
   *
   * @param repository git repository that should contain the {@code sha1}
   * @param sha1 a 40 characters SHA-1
   * @param reviewReference an arbitrary string that allows to keep track of the revision of the
   *     code review being migrated. SHA-1 is not enough because code reviews are mutable and can go
   *     back and forth in to the same revision:
   *     <ul>
   *       <li>V1: sha1: abc
   *       <li>V2: sha1: cdb
   *       <li>V3: sha1: abc. Goes back to the original SHA-1. But we could have already migrated
   *           V2.
   *     </ul>
   *
   * @param reference a stable name that describes where this is coming from. Could be a git
   *     reference like 'master'
   * @param associatedLabels labels associated with this reference
   */
  GitRevision(
      GitRepository repository,
      String sha1,
      @Nullable String reviewReference,
      @Nullable String reference,
      ImmutableMap<String, String> associatedLabels) {
    this.reviewReference = reviewReference;
    Preconditions.checkArgument(
        COMPLETE_SHA1_PATTERN.matcher(sha1).matches(),
        "Reference '%s' is not a 40 characters SHA-1",
        sha1);

    this.repository = Preconditions.checkNotNull(repository);
    this.sha1 = sha1;
    this.reference = reference;
    this.associatedLabels = associatedLabels;
  }

  @Nullable
  @Override
  public String contextReference() {
    return reference;
  }

  @Override
  public ZonedDateTime readTimestamp() throws RepoException {
    // TODO(malcon): We should be able to skip this for revisions coming from 'git log'.
    ImmutableList<GitLogEntry> entry = repository.log(sha1).withLimit(1).run();
    if (entry.isEmpty()) {
      throw new RepoException(String.format("Cannot find '%s' in the git repository", sha1));
    }
    return Iterables.getOnlyElement(entry).getAuthorDate();
  }

  @Override
  public String asString() {
    return sha1 + (reviewReference == null ? "" : " " + reviewReference);
  }

  public String getSha1() {
    return sha1;
  }

  @Nullable
  public String getReviewReference() {
    return reviewReference;
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

  @Override
  public ImmutableMap<String, String> associatedLabels() {
    return associatedLabels;
  }
}
