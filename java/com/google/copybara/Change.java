// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import org.joda.time.DateTime;

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
  private final DateTime date;
  private final ImmutableMap<String, String> labels;

  public Change(R reference, Author author, String message, DateTime date,
      ImmutableMap<String, String> labels) {
    this.reference = Preconditions.checkNotNull(reference);
    this.author = Preconditions.checkNotNull(author);
    this.message = Preconditions.checkNotNull(message);
    this.date = date;
    this.labels = labels;
  }

  /**
   * Reference of the change. For example a SHA-1 reference in git.
   */
  public R getReference() {
    return reference;
  }

  @SkylarkCallable(name = "ref", doc = "A string identifier of the change.")
  public String refAsString() {
    return reference.asString();
  }

  @SkylarkCallable(name = "author", doc = "The author of the change")
  public Author getAuthor() {
    return author;
  }

  @SkylarkCallable(name = "message", doc = "The message of the change")
  public String getMessage() {
    return message;
  }

  public DateTime getDate() {
    return date;
  }

  public ImmutableMap<String, String> getLabels() {
    return labels;
  }

  /**
   * Returns the first line of the change. Usually a summary.
   */
  @SkylarkCallable(name = "first_line_message", doc = "The message of the change")
  public String firstLineMessage() {
    int idx = message.indexOf('\n');
    return idx == -1 ? message : message.substring(0, idx);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("reference", reference.asString())
        .add("author", author)
        .add("date", date)
        .add("message", message)
        .toString();
  }
}
