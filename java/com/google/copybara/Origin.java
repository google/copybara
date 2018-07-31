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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.Glob;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A {@code Origin} represents a source control repository from which source is copied.
 *
 * @param <R> the origin type of the references/revisions this origin handles
 */
@SkylarkModule(
    name = "origin",
    doc = "A Origin represents a source control repository from which source is copied.",
    category = SkylarkModuleCategory.TOP_LEVEL_TYPE,
    documented = false)
public interface Origin<R extends Revision> extends ConfigItemDescription {

  /**
   * Resolves a migration reference into a revision. For example for git it would resolve 'master'
   * to the SHA-1.
   *
   * @throws RepoException if any error happens during the resolve.
   */
  R resolve(String reference) throws RepoException, ValidationException;

  /**
   * An object which is capable of checking out code from the origin at particular paths. This can
   * also enumerate changes in the history and transform authorship information.
   */
  interface Reader<R extends Revision> extends ChangeVisitable<R> {

    /**
     * Checks out the revision {@code ref} from the repository into {@code workdir} directory. This
     * method is not on {@link Revision} in order to prevent {@link Destination} implementations
     * from getting access to the code pre-transformation.
     *
     * @throws RepoException if any error happens during the checkout or workdir preparation.
     */
    void checkout(R ref, Path workdir) throws RepoException, ValidationException;

    /**
     * Returns the changes that happen in the interval (fromRef, toRef].
     *
     * <p>If {@code fromRef} is null, returns all the changes from the first commit of the parent
     * branch to {@code toRef}, both included.
     *
     * @param fromRef the revision used in the latest invocation. If null it means that no previous
     *     ref could be found or that the destination didn't store the ref.
     * @param toRef current revision to transform.
     * @throws RepoException if any error happens during the computation of the diff.
     */
    ChangesResponse<R> changes(@Nullable R fromRef, R toRef) throws RepoException;

    class ChangesResponse<R extends Revision> {

      @Nullable
      private final ChangeGraph<Change<R>> changes;
      @Nullable private final EmptyReason emptyReason;

      private ChangesResponse(@Nullable ChangeGraph<Change<R>> changes,
          @Nullable EmptyReason emptyReason) {
        Preconditions.checkArgument(changes == null ^ emptyReason == null, "Either we have"
            + " changes or we have an empty reason");
        this.changes = changes;
        this.emptyReason = emptyReason;
        if (changes != null) {
          Preconditions.checkArgument(!changes.nodes().isEmpty(), "Non-null empty graphs are not"
              + "allowed. Use emptyReason instead");
        }
      }

      public static <T extends Revision> ChangesResponse<T> forChanges(
          ChangeGraph<Change<T>> changes) {
        Preconditions.checkArgument(!changes.nodes().isEmpty(), "Empty changes not allowed");
        if (changes.nodes().size() > 1) {
          for (Change<T> node : changes.nodes()) {
            Preconditions.checkState(
                !changes.predecessors(node).isEmpty() || !changes.successors(node).isEmpty(),
                "Unconnected node: %s", node);
          }
        }
        return new ChangesResponse<>(changes, /*emptyReason*/ null);
      }

      public static <T extends Revision> ChangesResponse<T> noChanges(EmptyReason emptyReason) {
        Preconditions.checkNotNull(emptyReason);
        return new ChangesResponse<>(null, emptyReason);
      }

     /**
      * Returns true if there are no changes.
      */
      public boolean isEmpty() {
        return changes == null;
      }

      public EmptyReason getEmptyReason() {
        Preconditions.checkNotNull(emptyReason, "Use isEmpty() first");
        return emptyReason;
      }

      /**
       * The changes that happen in the interval (fromRef, toRef] as a flatten list
       */
      @VisibleForTesting
      public ImmutableList<Change<R>> getChangesAsListForTest() {
        Preconditions.checkNotNull(changes, "Use isEmpty() first");
        return ImmutableList.copyOf(changes.nodes());
      }

      /**
       * The changes that happen in the interval (fromRef, toRef].
       *
       * <p>The graph nodes are in order from oldest change to newest change.
       * <code>graph.successors()</code> returns the parents of each change.
       */
      public ChangeGraph<Change<R>> getChanges() {
        Preconditions.checkNotNull(changes, "Use isEmpty() first");
        return changes;
      }

      /** Reason why {@link Origin.Reader#changes(Revision, Revision)} didn't return any change */
      public enum EmptyReason {
        /** 'from' is ancestor of 'to' but all changes are for irrelevant files */
        NO_CHANGES,
        /** There is no parent/child relationship between 'from' and 'to' */
        UNRELATED_REVISIONS,
        /* 'to' is equal or ancestor of 'from' */
        TO_IS_ANCESTOR,
      }
    }
    /**
     * Returns true if the origin repository supports maintaining a history of changes. Generally
     * this should be true
     */
    default boolean supportsHistory() {
      return true;
    }

    /**
     * Returns a change identified by {@code ref}.
     *
     * @param ref current revision to transform.
     * @throws RepoException if any error happens during the computation of the diff.
     */
    Change<R> change(R ref) throws RepoException, EmptyChangeException;

    /**
     * Given a revision, compute if possible an identity for the group of changes to be migrated.
     * For example for Github this would be the pull request number, for Gerrit it could be a
     * topic.
     */
    @Nullable
    default String getGroupIdentity(R rev) throws RepoException{
      return null;
    }

    /**
     * Finds the baseline of startRevision. Most of the implementations will use the label to
     * look for the closest parent with that label, but there might be other kind of implementations
     * that ignore it.
     *
     * <p>If the the label is present in a change multiple times it generally uses the last
     * appearance.
     */
    default Optional<Baseline<R>> findBaseline(R startRevision, String label)
        throws RepoException, ValidationException {
      FindLatestWithLabel<R> visitor = new FindLatestWithLabel<>(startRevision, label);
      visitChanges(startRevision, visitor);
      return visitor.getBaseline();
    }

    /**
     * Find the baseline of the change without using a label. That means that it will use the
     * specific system information to compute the parent. For example for GH PR, it will return the
     * baseline submitted SHA-1.
     *
     * <p>The order is chronologically reversed. First element is the most recent one. In other
     * words, the best suitable baseline should be element 0, then 1, etc.
     */
    default ImmutableList<R> findBaselinesWithoutLabel(R startRevision, int limit)
        throws RepoException, ValidationException {
      throw new ValidationException("Origin does't support this workflow mode");
    }

    class FindLatestWithLabel<R extends Revision> implements ChangesVisitor {

      private final R startRevision;
      private final String label;
      @Nullable
      private Baseline<R> baseline;

      public FindLatestWithLabel(R startRevision, String label) {
        this.startRevision = Preconditions.checkNotNull(startRevision);
        this.label = Preconditions.checkNotNull(label);
      }

      public Optional<Baseline<R>> getBaseline() {
        return Optional.ofNullable(baseline);
      }

      @SuppressWarnings("unchecked")
      @Override
      public VisitResult visit(Change<? extends Revision> input) {
        if (input.getRevision().asString().equals(startRevision.asString())) {
          return VisitResult.CONTINUE;
        }
        ImmutableMap<String, Collection<String>> labels = input.getLabels().asMap();
        if (!labels.containsKey(label)) {
          return VisitResult.CONTINUE;
        }
        baseline = new Baseline<>(Iterables.getLast(labels.get(label)),
            (R) input.getRevision());
        return VisitResult.TERMINATE;
      }
    }

    /**
     * Utility endpoint for accessing and adding feedback data.
     */
    default Endpoint getFeedbackEndPoint() throws ValidationException {
      return Endpoint.NOOP_ENDPOINT;
    }
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
   * Label name to be used in when creating a commit message in the destination to refer to a
   * revision. For example "Git-RevId".
   */
  String getLabelName();

  /**
   * Represents a baseline pointer in the origin
   * @param <R>
   */
  class Baseline<R extends Revision> {

    private final String baseline;
    private final R originRevision;

    public Baseline(String baseline, @Nullable R originRevision) {
      this.baseline = Preconditions.checkNotNull(baseline);
      this.originRevision = originRevision;
    }

    /**
     * The baseline reference that will be used in the destination.
     */
    public String getBaseline() {
      return baseline;
    }

    /**
     * A reference to the origin revision where the baseline was found.
     */
    @Nullable
    public R getOriginRevision() {
      return originRevision;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("baseline", baseline)
          .add("originRevision", originRevision)
          .toString();
    }
  }
}
