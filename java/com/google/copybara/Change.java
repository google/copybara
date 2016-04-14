package com.google.copybara;

import com.google.common.base.Preconditions;
import com.google.copybara.Origin.Reference;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Represents a change in a Repository
 */
public final class Change<T extends Origin<T>> {

  private final Reference<T> reference;
  private final String author;
  private final String message;
  private final long date;

  public Change(Reference<T> reference, String author, String message, long date) {
    this.reference = Preconditions.checkNotNull(reference);
    this.author = Preconditions.checkNotNull(author);
    this.message = Preconditions.checkNotNull(message);
    this.date = date;
  }

  /**
   * Reference of the change. For example a SHA-1 reference in git.
   */
  public Reference<T> getReference() {
    return reference;
  }

  public String getAuthor() {
    return author;
  }

  public String getMessage() {
    return message;
  }

  public long getDate() {
    return date;
  }

  @Override
  public String toString() {
    DateFormat format = SimpleDateFormat.getDateTimeInstance();
    return "Reference: " + reference.asString()
        + "\nAuthor: " + author
        + "\nDate: " + format.format(new Date(date))
        + "\n" + message;
  }
}
