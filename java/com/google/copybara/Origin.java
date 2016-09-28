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

import com.google.common.collect.ImmutableList;
import com.google.copybara.util.Glob;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import java.nio.file.Path;
import java.time.Instant;
import javax.annotation.Nullable;

/**
 * A {@code Origin} represents a source control repository from which source is copied.
 *
 * @param <R> the origin type of the reference this origin handles
 */
@SkylarkModule(
    name = "origin",
    doc = "A Origin represents a source control repository from which source is copied.",
    category = SkylarkModuleCategory.TOP_LEVEL_TYPE)
public interface Origin<R extends Origin.Reference> {

  /**
   * A reference of {@link Origin}.
   *
   * <p>For example, in Git it would be a reference to a commit SHA-1.
   */
  interface Reference {

    /**
     * Reads the timestamp of this reference from the repository, or {@code null} if this repo type
     * does not support it. This is the {@link Instant} from the UNIX epoch when the reference was
     * submitted to the source repository.
     */
    @Nullable
    Instant readTimestamp() throws RepoException;

    /**
     * String representation of the reference that can be parsed by {@link Origin#resolve(String)}.
     *
     * <p> Unlike {@link #toString()} method, this method is guaranteed to be stable.
     */
    String asString();

    /**
     * Label name to be used in when creating a commit message in the destination to refer to a
     * reference. For example "Git-RevId".
     */
    String getLabelName();
  }

  /**
   * Resolves a reference using the {@code Origin} configuration and flags

   * <p> If reference is null or empty then it will try to use the configured default for the
   * Origin.
   * @throws RepoException if any error happens during the resolve.
   */
  R resolve(@Nullable String reference) throws RepoException;

  /**
   * An object which is capable of checking out code from the origin at particular paths. This can
   * also enumerate changes in the history and transform authorship information.
   */
  interface Reader<R extends Origin.Reference> {

    /**
     * Checks out the reference {@code ref} from the repository into {@code workdir} directory. This
     * method is not on {@link Reference} in order to prevent {@link Destination} implementations
     * from getting access to the code pre-transformation.
     *
     * @throws RepoException if any error happens during the checkout or workdir preparation.
     */
    void checkout(R ref, Path workdir) throws RepoException;

    /**
     * Returns the changes that happen in the interval (fromRef, toRef].
     *
     * <p>If {@code fromRef} is null, returns all the changes from the first commit of the parent
     * branch to {@code toRef}, both included.
     *
     * @param fromRef the reference used in the latest invocation. If null it means that no
     * previous ref could be found or that the destination didn't store the ref.
     * @param toRef current reference to transform.
     * @throws RepoException if any error happens during the computation of the diff.
     */
    ImmutableList<Change<R>> changes(@Nullable R fromRef, R toRef) throws RepoException;

    /**
     * Returns a change identified by {@code ref}.
     *
     * @param ref current reference to transform.
     * @throws RepoException if any error happens during the computation of the diff.
     */
    Change<R> change(R ref) throws RepoException;

    /**
     * Visit the parents of {@code start} reference recursively and call the visitor for each
     * change. The visitor can stop the stream of changes at any moment by returning {@link
     * VisitResult#TERMINATE}.
     *
     * <p>It is up to the Origin how and what changes it provides to the function.
     */
    void visitChanges(R start, ChangesVisitor visitor) throws RepoException;
  }

  /**
   * Creates a new reader of this origin.
   *
   * @param originFiles indicates which files in the origin repository need to be read. Note that
   *     the reader does not necessarily need to remove files after checking them out according to
   *     the glob - that is actually done automatically by the {@link Workflow}. However, some
   *     {@link Origin} implementations may choose to optimize operations on the repo based on the
   *     glob.
   * @param authoring the authoring object used for constructing the Author objects.
   * @throws ValidationException if the reader could not be created because of a user error. For
   *     instance, the origin cannot be used with the given {@code originFiles}.
   */
  Reader<R> newReader(Glob originFiles, Authoring authoring) throws ValidationException;

  /**
   * A visitor of changes. An implementation of this interface is provided to the {@link
   * Reader#visitChanges(Reference, ChangesVisitor)} methods to visit changes in Origin
   * history.
   */
  interface ChangesVisitor {

    /**
     * Invoked for each change found. The implementation can chose to cancel the visitation by
     * returning {@link VisitResult#TERMINATE}.
     */
    VisitResult visit(Change<?> input);
  }

  /**
   * The result type for the function passed to
   * {@link Reader#visitChanges(Reference, ChangesVisitor)}.
   */
  enum VisitResult {
    /**
     * Continue. If more changes are available for visiting, the origin will call again the function
     * with the next changes.
     */
    CONTINUE,
    /**
     * Stop. Origin will not pass more changes to the visitor function. Usually used because the
     * function found what it was looking for (For example a commit with a label).
     */
    TERMINATE
  }

  /**
   * Label name to be used in when creating a commit message in the destination to refer to a
   * reference. For example "Git-RevId".
   */
  String getLabelName();
}
