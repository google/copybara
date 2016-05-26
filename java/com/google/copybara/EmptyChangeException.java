package com.google.copybara;

/**
 * An exception thrown by destinations when they detect that there is no change to submit. Usually
 * this means that the change was already applied.
 */
public class EmptyChangeException extends RepoException {

  public EmptyChangeException(String message) {
    super(message);
  }
}
