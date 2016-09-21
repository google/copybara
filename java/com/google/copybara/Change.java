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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Represents a change in a Repository
 */
@SkylarkModule(name = "change",
    category = SkylarkModuleCategory.BUILTIN,
    doc = "A change metadata. Contains information like author, change message or detected labels")
public final class Change<R extends Origin.Reference> {

  private final R reference;
  private final Author author;
  private final String message;
  private final ZonedDateTime dateTime;
  private final ImmutableMap<String, String> labels;

  public Change(R reference, Author author, String message, ZonedDateTime dateTime,
      ImmutableMap<String, String> labels) {
    this.reference = Preconditions.checkNotNull(reference);
    this.author = Preconditions.checkNotNull(author);
    this.message = Preconditions.checkNotNull(message);
    this.dateTime = dateTime;
    this.labels = labels;
  }

  /**
   * Reference of the change. For example a SHA-1 reference in git.
   */
  public R getReference() {
    return reference;
  }

  @SkylarkCallable(name = "ref", doc = "A string identifier of the change.", structField = true)
  public String refAsString() {
    return reference.asString();
  }

  @SkylarkCallable(name = "author", doc = "The author of the change", structField = true)
  public Author getAuthor() {
    return author;
  }

  @SkylarkCallable(name = "message", doc = "The message of the change", structField = true)
  public String getMessage() {
    return message;
  }

  @SkylarkCallable(name = "labels", doc = "A dictionary with the labels detected for the change."
      + " Note that this is an heuristic and it could include things that are not labels.",
      structField = true)
  public SkylarkDict<String, String> getLabelsForSkylark() {
    return SkylarkDict.copyOf(/*environment=*/null, labels);
  }

  public ZonedDateTime getDateTime() {
    return dateTime;
  }

  public ImmutableMap<String, String> getLabels() {
    return labels;
  }

  /**
   * Returns the first line of the change. Usually a summary.
   */
  @SkylarkCallable(name = "first_line_message", doc = "The message of the change"
      , structField = true)
  public String firstLineMessage() {
    int idx = message.indexOf('\n');
    return idx == -1 ? message : message.substring(0, idx);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("reference", reference.asString())
        .add("author", author)
        .add("dateTime", dateTime)
        .add("message", message)
        .toString();
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
    return Objects.equals(reference, change.reference) &&
        Objects.equals(author, change.author) &&
        Objects.equals(message, change.message) &&
        Objects.equals(dateTime, change.dateTime) &&
        Objects.equals(labels, change.labels);
  }

  @Override
  public int hashCode() {
    return Objects.hash(reference, author, message, dateTime, labels);
  }
}
