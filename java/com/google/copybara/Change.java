package com.google.copybara;

import com.google.common.base.Preconditions;
import com.google.copybara.Origin.Reference;
import com.google.copybara.git.GitOrigin;

import org.joda.time.DateTime;

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
  private final DateTime date;

  public Change(Reference<T> reference, String author, String message, DateTime date) {
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

  public DateTime getDate() {
    return date;
  }

  @Override
  public String toString() {
    return "Reference: " + reference.asString()
        + "\nAuthor: " + author
        + "\nDate: " + date
        + "\n" + message;
  }
}
