// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.copybara.Origin.Reference;

/**
 * Exceptions that happen when {@link Origin#changes(Reference, Reference, Authoring)} cannot compute the
 * changes between two references.
 */
public class CannotComputeChangesException extends RepoException {

  public CannotComputeChangesException(String message) {
    super(message);
  }
}
