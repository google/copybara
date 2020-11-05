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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.Revision;
import com.google.copybara.exception.RepoException;
import com.google.copybara.git.GitRepository.GitLogEntry;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** A Git repository reference */
public final class GitRevision implements Revision {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final Pattern COMPLETE_SHA1_PATTERN = Pattern.compile("[a-f0-9]{40}");

  private final GitRepository repository;
  private final String sha1;
  @Nullable private final String reference;
  private final ImmutableListMultimap<String, String> associatedLabels;
  @Nullable private final String reviewReference;
  @Nullable private final String url;
  private String describe;
  /**
   * Create a git revision from a complete (40 characters) git SHA-1 string.
   *
   * @param repository git repository that should contain the {@code sha1}
   * @param sha1 a 40 characters SHA-1
   */
  GitRevision(GitRepository repository, String sha1) {
    this(repository, sha1, /*reviewReference=*/ null, /*reference=*/ null,
        ImmutableListMultimap.of(), /*url=*/null);
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
   * @param url if present, the url of the repository that the revision comes from
   */
  @VisibleForTesting
  public GitRevision(
      GitRepository repository,
      String sha1,
      @Nullable String reviewReference,
      @Nullable String reference,
      ImmutableListMultimap<String, String> associatedLabels,
      @Nullable String url) {
    this.reviewReference = reviewReference;
    Preconditions.checkArgument(
        COMPLETE_SHA1_PATTERN.matcher(sha1).matches(),
        "Reference '%s' is not a 40 characters SHA-1",
        sha1);

    this.repository = Preconditions.checkNotNull(repository);
    this.sha1 = sha1;
    this.reference = reference;
    ImmutableListMultimap.Builder<String, String> labelBuilder =
        ImmutableListMultimap.<String, String>builder()
        .putAll(associatedLabels);
    if (!associatedLabels.containsKey("GIT_SHA1")) {
      labelBuilder
         .put("GIT_SHA1", sha1)
         .put("GIT_SHORT_SHA1", sha1.substring(0, 7));
    }
    this.associatedLabels = labelBuilder.build();
    this.url = url;
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
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .omitNullValues()
        .add("url", url)
        .add("reference", reference)
        .add("sha1", this.sha1)
        .toString();
  }

  @Nullable
  public String getUrl() {
    return url;
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
  public ImmutableListMultimap<String, String> associatedLabels() {
    return associatedLabels;
  }

  @Override
  public ImmutableList<String> associatedLabel(String label) {
    // We only return git describe if specifically ask for this label
    if (label.equals(GitRepository.GIT_DESCRIBE_CHANGE_VERSION)) {
      return populateDescribe();
    }
    return associatedLabels.get(label);
  }

  /**
   * Lazily compute describe. In general we could compute this in git.origin, but sometimes
   * there are thousands of changes to be migrated and we end up calling 'git describe' lot of
   * times. Then in the workflow we generally only need one per migration or one per iteration.
   * This is a waste of time (and a real performance issue). Instead we compute here on demand.
   *
   * <p>Synchronized to make sure we do this only once in case it is being called from parallel
   * transformations.
   */
  private synchronized ImmutableList<String> populateDescribe() {
    if (describe == null) {
      try {
        describe = repository.describe(this, false);
      } catch (RepoException e) {
        logger.atWarning().withCause(e).log(
            "Cannot describe version for %s. Using short sha", sha1);
        describe = sha1.substring(0, 7);
      }
    }
    return ImmutableList.of(describe);
  }

  GitRevision withUrl(String url) {
    return new GitRevision(repository, sha1, reviewReference, reference, associatedLabels, url);
  }

  GitRevision withLabels(ImmutableListMultimap<String, String> labels) {
    return new GitRevision(repository, sha1, reviewReference, reference,
        Revision.addNewLabels(associatedLabels, labels), url);
  }
}
