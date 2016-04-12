package com.google.copybara;

/**
 * Exceptions that happen when {@link Origin#changes(String, String)} cannot compute the changes
 * between two references.
 */
public class CannotComputeChangesException extends RepoException {

  public CannotComputeChangesException(String message) {
    super(message);
  }
}
