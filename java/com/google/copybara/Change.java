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

package com.google.copybara;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.copybara.DestinationEffect.OriginRef;
import com.google.copybara.authoring.Author;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

/** Represents a change in a Repository */
@StarlarkBuiltin(
    name = "change",
    doc = "A change metadata. Contains information like author, change message or detected labels")
public final class Change<R extends Revision> extends OriginRef implements StarlarkValue {

  private final R revision;
  private final Author author;
  private final String message;
  private final ZonedDateTime dateTime;
  private final ImmutableListMultimap<String, String> labels;
  private Author mappedAuthor;
  private final boolean merge;
  @Nullable
  private final ImmutableList<R> parents;

  @Nullable
  private final ImmutableSet<String> changeFiles;

  public Change(R revision, Author author, String message, ZonedDateTime dateTime,
      ImmutableListMultimap<String, String> labels) {
    this(revision, author, message, dateTime, labels, /*changeFiles=*/null);
  }

  public Change(R revision, Author author, String message, ZonedDateTime dateTime,
      ImmutableListMultimap<String, String> labels, @Nullable Set<String> changeFiles) {
    this(revision, author, message, dateTime, labels, changeFiles, /*merge=*/false,
        /*parents=*/ null);
  }

  public Change(R revision, Author author, String message, ZonedDateTime dateTime,
      ImmutableListMultimap<String, String> labels, @Nullable Set<String> changeFiles,
      boolean merge, @Nullable ImmutableList<R> parents) {
    super(revision.asString());
    this.revision = Preconditions.checkNotNull(revision);
    this.author = Preconditions.checkNotNull(author);
    this.message = Preconditions.checkNotNull(message);
    this.dateTime = dateTime;
    this.labels = labels;
    this.changeFiles = changeFiles == null ? null : ImmutableSet.copyOf(changeFiles);
    this.merge = merge;
    this.parents = parents;
  }

  /**
   * Reference of the change. For example a SHA-1 reference in git.
   */
  public R getRevision() {
    return revision;
  }

  /**
   * Return the parent revisions if the origin provides that information. Currently only for Git and
   * Hg. Otherwise null.
   */
  @Nullable
  public ImmutableList<R> getParents() {
    return parents;
  }

  @StarlarkMethod(name = "original_author", doc = "The author of the change before any"
      + " mapping", structField = true)
  public Author getAuthor() {
    return author;
  }

  /**
   * The author of the change. Can already be mapped using metadata.map_author
   */
  @StarlarkMethod(name = "author", doc = "The author of the change", structField = true)
  public Author getMappedAuthor() {
    return Preconditions.checkNotNull(mappedAuthor == null ? author : mappedAuthor);
  }

  public void setMappedAuthor(Author mappedAuthor) {
    this.mappedAuthor = mappedAuthor;
  }

  @StarlarkMethod(name = "message", doc = "The message of the change", structField = true)
  public String getMessage() {
    return message;
  }

  @StarlarkMethod(
      name = "labels",
      doc =
          "A dictionary with the labels detected for the change. If the label is present multiple"
              + " times it returns the last value. Note that this is a heuristic and it could"
              + " include things that are not labels.",
      structField = true)
  public Dict<String, String> getLabelsForSkylark() {
    return Dict.immutableCopyOf(Maps.transformValues(labels.asMap(), Iterables::getLast));
  }

  @StarlarkMethod(
      name = "labels_all_values",
      doc =
          "A dictionary with the labels detected for the change. Note that the value is a"
              + " collection of the values for each time the label was found. Use 'labels' instead"
              + " if you are only interested in the last value. Note that this is a heuristic and"
              + " it could include things that are not labels.",
      structField = true)
  public Dict<String, Sequence<String>> getLabelsAllForSkylark() {
    return Dict.immutableCopyOf(
        Maps.transformValues(labels.asMap(), StarlarkList::immutableCopyOf));
  }

  /**
   * If not null, the files that were affected in this change.
   */
  @Nullable
  public ImmutableSet<String> getChangeFiles() {
    return changeFiles;
  }

  public ZonedDateTime getDateTime() {
    return dateTime;
  }

  @StarlarkMethod(name = "date_time_iso_offset",
      doc = "Return a ISO offset date time. Example:  2011-12-03T10:15:30+01:00'",
      structField = true)
  public String dateTimeFmt() {
    return ISO_OFFSET_DATE_TIME.format(getDateTime());
  }

  public ImmutableListMultimap<String, String> getLabels() {
    return labels;
  }

  /**
   * Returns the first line of the change. Usually a summary.
   */
  @StarlarkMethod(name = "first_line_message", doc = "The message of the change"
      , structField = true)
  public String firstLineMessage() {
    return extractFirstLine(message);
  }

  static String extractFirstLine(String message) {
    int idx = message.indexOf('\n');
    return idx == -1 ? message : message.substring(0, idx);
  }

  /**
   * Returns true if the change represents a merge.
   */
  @StarlarkMethod(name = "merge", doc = "Returns true if the change represents a merge"
      , structField = true)
  public boolean isMerge() {
    return merge;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("revision", revision.asString())
        .add("author", author)
        .add("dateTime", dateTime)
        .add("message", message)
        .add("merge", merge)
        .add("parents", parents)
        .toString();
  }

  public Change<R> withLabels(ImmutableListMultimap<String, String> newLabels) {
    return new Change<>(revision, author, message, dateTime,
        Revision.addNewLabels(labels, newLabels), changeFiles, merge, parents);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Change<?> change = (Change<?>) o;
    return Objects.equals(revision, change.revision)
        && Objects.equals(author, change.author)
        && Objects.equals(message, change.message)
        && Objects.equals(dateTime, change.dateTime)
        && Objects.equals(labels, change.labels);
  }

  @Override
  public int hashCode() {
    return Objects.hash(revision, author, message, dateTime, labels);
  }
}
