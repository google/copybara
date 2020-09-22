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

import static com.google.common.collect.Queues.newArrayDeque;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.StarlarkValue;

/**
 * A {@code Origin} represents a source control repository from which source is copied.
 *
 * @param <R> the origin type of the references/revisions this origin handles
 */
@StarlarkBuiltin(
    name = "origin",
    doc = "A Origin represents a source control repository from which source is copied.",
    documented = false)
public interface Origin<R extends Revision> extends ConfigItemDescription, StarlarkValue {

  /**
   * Resolves a migration reference into a revision. For example for git it would resolve 'main'
   * to the SHA-1.
   *
   * @throws RepoException if any error happens during the resolve.
   */
  R resolve(String reference) throws RepoException, ValidationException;

  /**
   * Resolves a migration last migrated reference into a revision.
   * For example for git it would resolve 'main' to the SHA-1.
   *
   * @throws RepoException if any error happens during the resolve.
   */
  default R resolveLastRev(String reference) throws RepoException, ValidationException {
    return resolve(reference);
  }

  /**
   * Show different changes between two references. Returns null if the origin doesn't
   * support generating differences.
   *
   * @throws RepoException
   */
  @Nullable
  default String showDiff(R revisionFrom, R revisionTo) throws RepoException {
    return null;
  }

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
    ChangesResponse<R> changes(@Nullable R fromRef, R toRef)
        throws RepoException, ValidationException;

    class ChangesResponse<R extends Revision> {

      private final ImmutableList<Change<R>> changes;
      @Nullable private final EmptyReason emptyReason;

      /**
       * Changes in key will only be included if the value is included. The usage is for non-linear
       * histories like git where including a change depends if we end up including the merge
       * commit.
       */
      private final ImmutableMap<Change<R>, Change<R>> conditionalChanges;

      private ChangesResponse(ImmutableList<Change<R>> changes,
          ImmutableMap<Change<R>, Change<R>> conditionalChanges,
          @Nullable EmptyReason emptyReason) {
        this.changes = Preconditions.checkNotNull(changes);
        this.conditionalChanges = Preconditions.checkNotNull(conditionalChanges);
        this.emptyReason = emptyReason;
        Preconditions.checkArgument(changes.isEmpty() ^ emptyReason == null, "Either we have"
            + " changes or we have an empty reason");
      }

      public static <T extends Revision> ChangesResponse<T> forChanges(
          Iterable<Change<T>> changes) {
        Preconditions.checkArgument(!Iterables.isEmpty(changes), "Empty changes not allowed");
        return new ChangesResponse<>(ImmutableList.copyOf(changes),
            ImmutableMap.copyOf(ImmutableMap.of()),
            /*emptyReason=*/ null);
      }

      /**
       * Build a ChangeResponse object with changes where some of them are conditional to their
       * closest first-parent root being included (merge commit).
       */
      public static <R extends Revision> ChangesResponse<R> forChangesWithMerges(
          Iterable<Change<R>> changes) {
        Preconditions.checkArgument(!Iterables.isEmpty(changes),
            "Shouldn't be called for empty changes");

        Map<R, Change<R>> byRevision = new HashMap<>();
        List<Change<R>> all = new ArrayList<>();
        for (Change<R> e : changes) {
          all.add(e);
          byRevision.put(e.getRevision(), e);
        }

        List<Change<R>> firstParents = new ArrayList<>();
        Set<R> toSkip = new HashSet<>();
        Change<R> latest = Iterables.getLast(changes);

        // Compute first parents and add them to toSkip so that they are not counted as conditional
        // changes later.
        while (true) {
          firstParents.add(latest);
          // We don't want to add first parents as conditional changes
          toSkip.add(latest.getRevision());
          if (parents(latest).isEmpty()) {
            break;
          }
          R firstParent = parents(latest).get(0);
          Change<R> firstParentChange = byRevision.get(firstParent);
          if (firstParentChange == null) {
            break;
          }
          latest = firstParentChange;
        }

        Map<Change<R>, Change<R>> conditionalChanges = new HashMap<>();

        // Traverse from old to new so that we use oldest first-parent as the conditional change.
        for (Change<R> firstParent : Lists.reverse(firstParents)) {
          // Skip non-merges
          if (parents(firstParent).size() < 2) {
            continue;
          }
          Deque<R> toVisit = newArrayDeque(Iterables.skip(parents(firstParent), 1));
          while (!toVisit.isEmpty()) {
            R revision = toVisit.poll();
            // Don't traverse again non-first parents already visited: This is for performance and
            // correctness, the conditional changes is the oldest merge first-parent.
            if (!toSkip.add(revision)) {
              continue;
            }
            Change<R> change = byRevision.get(revision);
            if (change == null) {
              continue;
            }
            conditionalChanges.put(change, firstParent);
            toVisit.addAll(parents(change));
          }
        }
        return new ChangesResponse<>(ImmutableList.copyOf((Iterable<Change<R>>) all),
            ImmutableMap.copyOf(conditionalChanges),
            /*emptyReason=*/ null);
      }

      private static <R extends Revision> ImmutableList<R> parents(Change<R> change) {
        return Preconditions.checkNotNull(change.getParents(),
            "Don't use forChangesWithParents for changes that don't support parents: ",
            change);
      }

      /**
       * Create a {@code ChangeResponse} that doesn't contain any change
       */
      public static <T extends Revision> ChangesResponse<T> noChanges(EmptyReason emptyReason) {
        Preconditions.checkNotNull(emptyReason);
        return new ChangesResponse<>(ImmutableList.of(), ImmutableMap.of(), emptyReason);
      }

      /**
      * Returns true if there are no changes.
      */
      public boolean isEmpty() {
        return changes.isEmpty();
      }

      public EmptyReason getEmptyReason() {
        Preconditions.checkNotNull(emptyReason, "Use isEmpty() first");
        return emptyReason;
      }

      /**
       * The changes that happen in the interval (fromRef, toRef].
       *
       * <p>The list might include changes that shouldn't be included in the final list of changes.
       * Check conditionalChanges for changes that might not be included.
       */
      public ImmutableList<Change<R>> getChanges() {
        Preconditions.checkNotNull(changes, "Use isEmpty() first");
        return changes;
      }

      /**
       * Changes that should only be included if the change in the value is also included.
       */
      ImmutableMap<Change<R>, Change<R>> getConditionalChanges() {
        return conditionalChanges;
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
    Change<R> change(R ref) throws RepoException, ValidationException;

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
     * @param console
     */
    default Endpoint getFeedbackEndPoint(Console console) throws ValidationException {
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
