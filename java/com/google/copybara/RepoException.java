package com.google.copybara;

/**
 * Exceptions that happen during repository manipulation.
 */
public class RepoException extends Exception {

  public RepoException(String message) {
    super(message);
  }

  public RepoException(String message, Throwable cause) {
    super(message, cause);
  }
}
