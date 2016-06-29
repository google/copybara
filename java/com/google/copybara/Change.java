package com.google.copybara;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.copybara.Origin.ReferenceFiles;

import org.joda.time.DateTime;

/**
 * Represents a change in a Repository
 */
public final class Change<T extends Origin<T>> {

  private final ReferenceFiles<T> reference;
  private final Author author;
  private final String message;
  private final DateTime date;

  public Change(ReferenceFiles<T> reference, Author author, String message, DateTime date) {
    this.reference = Preconditions.checkNotNull(reference);
    this.author = Preconditions.checkNotNull(author);
    this.message = Preconditions.checkNotNull(message);
    this.date = date;
  }

  /**
   * Reference of the change. For example a SHA-1 reference in git.
   */
  public ReferenceFiles<T> getReference() {
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
