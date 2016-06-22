// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

/**
 * Represents a contributor in an origin or destination repository.
 *
 * <p>A contributor can be either an individual or a team.
 */
public interface Author {

  /**
   * Returns the unique identifier of the author in the repository.
   */
  String getId();
}
