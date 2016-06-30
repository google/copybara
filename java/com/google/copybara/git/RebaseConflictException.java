package com.google.copybara.git;

import com.google.copybara.RepoException;

/**
 * Indicates that the rebase failed because of a conflict.
 */
class RebaseConflictException extends RepoException {

  RebaseConflictException(String message) {
    super(message);
  }
}
