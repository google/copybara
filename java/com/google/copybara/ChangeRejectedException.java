// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

/**
 * User rejected the change and aborted execution.
 */
public class ChangeRejectedException extends RepoException {

  public ChangeRejectedException(String message) {
    super(message);
  }
}
