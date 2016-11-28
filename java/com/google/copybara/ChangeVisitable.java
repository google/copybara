/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara;

/**
 * An interface stating that the implementing class accepts child visitors to explore repository
 * state beyond the changes being migrated.
 */
public interface ChangeVisitable <R extends Reference> {

  /**
   * Visit the parents of the {@code start} reference and call the visitor for each
   * change. The visitor can stop the stream of changes at any moment by returning {@see
   * VisitResult#TERMINATE}.
   *
   * <p>It is up to the Origin how and what changes it provides to the function.
   */
  void visitChanges(R start, ChangesVisitor visitor)
      throws RepoException, CannotResolveReferenceException;

  /**
   * A visitor of changes. An implementation of this interface is provided to {@see
   * visitChanges} methods to visit changes in Origin or
   * Destination history.
   */
  public interface ChangesVisitor  {

    /**
     * Invoked for each change found. The implementation can chose to cancel the visitation by
     * returning {@link VisitResult#TERMINATE}.
     */
    VisitResult visit(Change<? extends Reference> input);
  }

  /**
   * The result type for the function passed to
   * {@see visitChanges}.
   */
  public enum VisitResult {
    /**
     * Continue. If more changes are available for visiting, the origin will call again the
     * function with the next changes.
     */
    CONTINUE,
    /**
     * Stop. Origin will not pass more changes to the visitor function. Usually used because the
     * function found what it was looking for (For example a commit with a label).
     */
    TERMINATE
  }
}

