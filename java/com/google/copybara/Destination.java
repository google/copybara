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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import java.io.IOException;
import java.util.Objects;
import javax.annotation.Nullable;


/** A repository which a source of truth can be copied to. */
@SkylarkModule(
  name = "destination",
  doc = "A repository which a source of truth can be copied to",
  category = SkylarkModuleCategory.TOP_LEVEL_TYPE
)
public interface Destination<R extends Revision> extends ConfigItemDescription {

  /**
   * The result of invoking {@link Writer#write(TransformResult, Console)}.
   */
  enum WriterResult {
    /**
     * The execution of {@link Writer#write(TransformResult, Console)} was successful. The caller
     * should proceed with the execution.
     */
    OK,
    /**
     * The execution of {@link Writer#write(TransformResult, Console)} had errors or warnings that
     * were logged into the console. The caller should prompt confirmation to the user to continue.
     */
    PROMPT_TO_CONTINUE,
  }

  /**
   * An object which is capable of writing multiple revisions to the destination. This object is
   * allowed to maintain state between the writing of revisions if applicable (for instance, to
   * create multiple changes which are dependent on one another that require review before
   * submission).
   *
   * <p>A single instance of this class is used to import either a single change, or a sequence of
   * changes where each change is the following change's parent.
   */
  interface Writer<R extends Revision> extends ChangeVisitable<R>{

    /**
     * Returns the status of the import at the destination.
     *
     * <p>This method may have undefined behavior if called after {@link #write(TransformResult,
     * Console)}.
     *
     * @param labelName the label used in the destination for storing the last migrated ref
     */
    @Nullable
    DestinationStatus getDestinationStatus(String labelName)
        throws RepoException, ValidationException;
    /**
     * Returns true if this destination stores revisions in the repository so that
     * {@link #getDestinationStatus(String)}  can be used for discovering the state of the
     * destination and we can use the methods in {@link ChangeVisitable}.
     */
    boolean supportsHistory();

    /**
     * Writes the fully-transformed repository stored at {@code workdir} to this destination.
     * @param transformResult what to write to the destination
     * @param console console to be used for printing messages
     *
     * @throws ValidationException if an user attributable error happens during the write
     * @throws RepoException if there was an issue with the destination repository
     * @throws IOException if a file access error happens during the write
     */
    WriterResult write(TransformResult transformResult, Console console)
        throws ValidationException, RepoException, IOException;
  }

  /**
   * Creates a writer which is capable of writing to this destination. This writer may maintain
   * state between writing of revisions.
   *
   * <p>This method should only do trivial initialization of the writer, since it does not have
   * access to a {@link Console}.
   *
   * @param destinationFiles A path matcher which matches files in the destination that should be
   * deleted if they don't exist in the source.
   * @param dryRun if the writer should be created in dry-run mode. Dry-run modes might vary
   * between implementations. Some implementations might chose to create a side effect as far as
   * it is not in the main branch.
   * @param groupId an optional identifier for the group of changes that will be migrated
   * (For example if the all the changes are from a Github pull request).
   * @param oldWriter workflows might create several writers for the same invocation so that
   * they can run with different config per change migrated. This allows the writer to maintain
   * state for the whole workflow execution scope.
   * @throws ValidationException if the writer could not be created because of a user error. For
   * instance, the destination cannot be used with the given {@code destinationFiles}.
   */
  Writer<R> newWriter(Glob destinationFiles, boolean dryRun, @Nullable String groupId,
      @Nullable Writer<R> oldWriter) throws ValidationException;

  /**
   * Given a reverse workflow with an {@code Origin} than is of the same type as this destination,
   * the label that that {@link Origin#getLabelName()} would return.
   *
   * <p>This label name is used by the origin in the reverse workflow to stamp it's original
   * revision id. Destinations return the origin label so that a baseline label can be found when
   * using {@link WorkflowMode#CHANGE_REQUEST}.
   */
  String getLabelNameWhenOrigin() throws ValidationException;

  /**
   * This class represents the status of the destination. It includes the baseline revision
   * and if it is a code review destination, the list of pending changes that have been already
   * migrated. In order: First change is the oldest one.
   */
  final class DestinationStatus {

    private final String baseline;
    private final ImmutableList<String> pendingChanges;

    public DestinationStatus(String baseline, ImmutableList<String> pendingChanges) {
      this.baseline = Preconditions.checkNotNull(baseline);
      this.pendingChanges = Preconditions.checkNotNull(pendingChanges);
    }

    /**
     * String representation of the latest migrated revision in the baseline.
     */
    public String getBaseline() {
      return Preconditions.checkNotNull(baseline, "Trying to get baseline for NO_STATUS");
    }

    /**
     * String representation of the migrated revisions that are in pending state in the destination.
     * First element is the oldest one. Last element the newest one.
     */
    public ImmutableList<String> getPendingChanges() {
      return Preconditions.checkNotNull(pendingChanges,
                                        "Trying to get pendingChanges for NO_STATUS");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      DestinationStatus that = (DestinationStatus) o;
      return Objects.equals(baseline, that.baseline)
          && Objects.equals(pendingChanges, that.pendingChanges);
    }

    @Override
    public int hashCode() {
      return Objects.hash(baseline, pendingChanges);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("baseline", baseline)
          .add("pendingChanges", pendingChanges)
          .toString();
    }
  }
}
