// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import org.joda.time.DateTime;

/**
 * Represents a change in a Repository
 */
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

  public Author getAuthor() {
    return author;
  }

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
  public String firstLineMessage() {
    int idx = message.indexOf('\n');
    return idx == -1 ? message : message.substring(0, idx);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("reference", reference.asString())
        .add("author", author)
        .add("date", author)
        .add("message", message)
        .toString();
  }
}
