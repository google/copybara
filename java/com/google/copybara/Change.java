package com.google.copybara;

import com.google.common.base.Preconditions;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Represents a change in a Repository
 */
public final class Change {

  private final Origin origin;
  private final String id;
  private final String author;
  private final String message;
  private final long date;

  public Change(Origin origin, String id, String author, String message, long date) {
    this.origin = Preconditions.checkNotNull(origin);
    this.id = Preconditions.checkNotNull(id);
    this.author = Preconditions.checkNotNull(author);
    this.message = Preconditions.checkNotNull(message);
    this.date = date;
  }

  /**
   * Id of the change. For example a SHA-1 reference in git.
   */
  public String getId() {
    return id;
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

  public Origin getOrigin() {
    return origin;
  }

  @Override
  public String toString() {
    DateFormat format = SimpleDateFormat.getDateTimeInstance();
    return "Commit: " + id
        + "\nAuthor: " + author
        + "\nDate" + format.format(new Date(date))
        + "\n" + message
        + "\nRepository: " + origin;
  }
}
