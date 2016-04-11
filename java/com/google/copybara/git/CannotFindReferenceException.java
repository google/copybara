package com.google.copybara.git;

import com.google.copybara.RepoException;

/**
 * Indicates that a Git reference could not be found when performing a {@code git} operation.
 */
public class CannotFindReferenceException extends RepoException {
  public CannotFindReferenceException(String message) {
    super(message);
  }

  public CannotFindReferenceException(String message, Throwable cause) {
    super(message, cause);
  }
}
