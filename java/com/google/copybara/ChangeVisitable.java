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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * An interface stating that the implementing class accepts child visitors to explore repository
 * state beyond the changes being migrated.
 */
public interface ChangeVisitable <R extends Revision> {

  /**
   * Visit the parents of the {@code start} revision and call the visitor for each
   * change. The visitor can stop the stream of changes at any moment by returning {@see
   * VisitResult#TERMINATE}.
   *
   * <p>It is up to the Origin how and what changes it provides to the function.
   */
  void visitChanges(@Nullable R start, ChangesVisitor visitor)
      throws RepoException, ValidationException;

  /**
   * Visit only changes that contain any of the labels in {@code labels}.
   */
  default void visitChangesWithAnyLabel(
      @Nullable R start, ImmutableCollection<String> labels, ChangesLabelVisitor visitor)
      throws RepoException, ValidationException {
    visitChanges(start, input -> {
      // We could return all the label values, but this is really only used for
      // RevId like ones and last is good enough for now.
      Map<String, String> copy = Maps.newHashMap(Maps.transformValues(input.getLabels().asMap(),
          Iterables::getLast));
      copy.keySet().retainAll(labels);
      if (copy.isEmpty()) {
        return VisitResult.CONTINUE;
      }
      return visitor.visit(input, ImmutableMap.copyOf(copy));
    });
  }
  
  /**
   * A visitor of changes. An implementation of this interface is provided to {@see
   * visitChanges} methods to visit changes in Origin or
   * Destination history.
   */
  interface ChangesVisitor  {

    /**
     * Invoked for each change found. The implementation can chose to cancel the visitation by
     * returning {@link VisitResult#TERMINATE}.
     */
    VisitResult visit(Change<? extends Revision> input);
  }

  /**
   * A visitor of changes that only receives changes that match any of the passed labels.
   */
  interface ChangesLabelVisitor  {

    /**
     * Invoked for each change found that matches the labels.
     *
     * <p>Note that the {@code matchedLabels} can be disjoint with the labels in {@code input},
     * since labels might be stored with a different string format.
     */
    VisitResult visit(Change<? extends Revision> input, ImmutableMap<String, String> matchedLabels);
  }

  /**
   * The result type for the function passed to
   * {@see visitChanges}.
   */
  enum VisitResult {
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

