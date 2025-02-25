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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.copybara.git.GitRepository.COPYBARA_FETCH_NAMESPACE;
import static com.google.copybara.git.GitRepository.FULL_REF_NAMESPACE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.exception.RepoException;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.revision.Revision;
import com.google.copybara.util.CommandOutput;
import java.time.ZonedDateTime;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** A Git repository reference */
public final class GitRevision implements Revision {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final Pattern COMPLETE_SHA1_PATTERN = Pattern.compile("[a-f0-9]{40}");

  private final GitRepository repository;
  private final String sha1;
  @Nullable private final String reference;
  private final ListMultimap<String, String> associatedLabels;
  @Nullable private final String reviewReference;
  @Nullable private final String url;
  private String describe;
  private String describeAbbrev;
  private String revisionNumber;
  private Optional<String> fullReference = Optional.empty();

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
   * @param repository Git repository that should contain the {@code sha1}
   * @param sha1 A 40 character SHA-1.
   * @param url The url of the repository that the revision comes from.
   */
  GitRevision(GitRepository repository, String sha1, String url) {
    this(repository, sha1, null, null, ImmutableListMultimap.of(), url);
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
    ArrayListMultimap<String, String> labels = ArrayListMultimap.create();
    labels.putAll(associatedLabels);
    if (!associatedLabels.containsKey("GIT_SHA1")) {
      labels.put("GIT_SHA1", sha1);
      labels.put("GIT_SHORT_SHA1", sha1.substring(0, 7));
    }
    this.associatedLabels = labels;
    this.url = url;
  }

  @Nullable
  @Override
  public String contextReference() {
    return reference;
  }

  @Override
  public String fixedReference() {
    return sha1;
  }

  @Override
  public Optional<String> fullReference() {
    if (fullReference.isPresent()) {
      return fullReference;
    }

    if (reference == null || reference.startsWith("refs/")) {
      fullReference = Optional.ofNullable(reference);
    } else {
      try {
        ImmutableList<String> matchingRefs =
            repository
                .showRef(ImmutableList.of(reference, reference + FULL_REF_NAMESPACE))
                .entrySet()
                .stream()
                .filter(e -> e.getKey().startsWith(COPYBARA_FETCH_NAMESPACE + "/refs/"))
                .filter(e -> e.getValue().getSha1().equals(sha1))
                .map(Entry::getKey)
                .map(GitRevision::getCleanedFullReference)
                .collect(toImmutableList());

        if (matchingRefs.isEmpty()) {
          logger.atInfo().log("No full reference for ref: %s", reference);
        } else {
          // Git allows having branches and tags with the same name. Prioritize tags over branches.
          fullReference =
              matchingRefs.stream()
                  .filter(e -> e.startsWith("refs/tags/"))
                  .findFirst()
                  .or(() -> matchingRefs.stream().findFirst());
        }
      } catch (RepoException e) {
        logger.atWarning().withCause(e).log(
            "Could not determine full reference for ref: %s. Cause: %s", reference, e.getMessage());
        return Optional.empty();
      }
    }
    return fullReference;
  }

  private static String getCleanedFullReference(String originalRef) {
    String fullRef = originalRef;
    String prefix = "refs/copybara_fetch/";
    if (fullRef.startsWith(prefix)) {
      fullRef = fullRef.substring(prefix.length());
    }
    if (fullRef.endsWith(FULL_REF_NAMESPACE)) {
      fullRef = fullRef.substring(0, fullRef.length() - FULL_REF_NAMESPACE.length());
    }

    return fullRef;
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
        .add("labels", this.associatedLabels)
        .toString();
  }

  @Override
  @Nullable
  public String getUrl() {
    return url;
  }

  @Override
  public Optional<String> getRevisionType() {
    return Optional.of("Git");
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
    return ImmutableListMultimap.copyOf(associatedLabels);
  }

  @Override
  public ImmutableList<String> associatedLabel(String label) {
    // We only return git describe if specifically ask for this label
    if (label.equals(GitRepository.GIT_DESCRIBE_CHANGE_VERSION)) {
      return populateDescribe();
    }
    if (label.equals(GitRepository.GIT_SEQUENTIAL_REVISION_NUMBER)) {
      return populateRevisionNumber();
    }
    if (label.equals(GitRepository.GIT_DESCRIBE_ABBREV)) {
      return populateDescribeAbbrev();
    }
    if (label.equals(GitRepository.GIT_TAG_POINTS_AT)) {
      return populateTagPointsAt();
    }
    return ImmutableList.copyOf(associatedLabels.get(label));
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

  private synchronized ImmutableList<String> populateTagPointsAt() {
    if (associatedLabels.containsKey(GitRepository.GIT_TAG_POINTS_AT)) {
      return ImmutableList.copyOf(associatedLabels.get(GitRepository.GIT_TAG_POINTS_AT));
    }

    try {
      ImmutableList<String> tags = repository.tagPointsAt(this);
      associatedLabels.putAll(GitRepository.GIT_TAG_POINTS_AT, tags);
      return tags;
    } catch (RepoException e) {
      logger.atWarning().withCause(e).log("Cannot get 'tag --points-to' output for %s", sha1);
    }

    return ImmutableList.of();
  }

  private synchronized ImmutableList<String> populateDescribeAbbrev() {
    if (associatedLabels.containsKey(GitRepository.GIT_DESCRIBE_ABBREV)) {
      return ImmutableList.copyOf(associatedLabels.get(GitRepository.GIT_DESCRIBE_ABBREV));
    }

    if (describeAbbrev == null) {
      try {
        describeAbbrev = repository.describeAbbrev(this);
      } catch (RepoException e) {
        logger.atWarning().withCause(e).log(
            "Cannot get closest tag for %s.", sha1);
      }
    }
    if (describeAbbrev != null) {
      associatedLabels.put(GitRepository.GIT_DESCRIBE_ABBREV, describeAbbrev);
    }
    return ImmutableList.of(Strings.nullToEmpty(describeAbbrev));
  }

  /** Lazily compute rev number. */
  private synchronized ImmutableList<String> populateRevisionNumber() {
    if (revisionNumber == null) {
      try {
        CommandOutput cmdout = repository.simpleCommand("rev-list", "--count", sha1);
        revisionNumber = cmdout.getStdout().trim();
      } catch (RepoException e) {
        logger.atWarning().withCause(e).log(
            "Cannot get revision number for %s. Using short sha", sha1);
        revisionNumber = "";
      }
    }
    return ImmutableList.of(revisionNumber);
  }

  GitRevision withUrl(String url) {
    return new GitRevision(repository, sha1, reviewReference, reference,
        ImmutableListMultimap.copyOf(associatedLabels), url);
  }

  GitRevision withContextReference(String tag) {
    return new GitRevision(
        repository,
        sha1,
        reviewReference,
        tag,
        ImmutableListMultimap.copyOf(associatedLabels),
        url);
  }

  GitRevision withLabels(ImmutableListMultimap<String, String> labels) {
    return new GitRevision(repository, sha1, reviewReference, reference,
        Revision.addNewLabels(ImmutableListMultimap.copyOf(associatedLabels), labels), url);
  }
}
