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

import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import java.io.IOException;
import javax.annotation.Nullable;


/** A repository which a source of truth can be copied to. */
@SkylarkModule(
  name = "destination",
  doc = "A repository which a source of truth can be copied to",
  category = SkylarkModuleCategory.TOP_LEVEL_TYPE
)
public interface Destination<R extends Reference> extends ConfigItemDescription {

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
   * An object which is capable of introspecting the destination repository. This can also enumerate
   * changes in the history.
   **/
  interface Reader<R extends Reference> extends ChangeVisitable<R> { }

  /**
   * Creates a new reader of this destination.
   *
   * @param destinationFiles indicates which files in the destination repository need to be read.
   * @throws ValidationException if the reader could not be created because of a user error.
   */
  @Nullable
  default public Reader<R> newReader(Glob destinationFiles)
      throws ValidationException, RepoException {
   return null;
  };

  /**
   * An object which is capable of writing multiple revisions to the destination. This object is
   * allowed to maintain state between the writing of revisions if applicable (for instance, to
   * create multiple changes which are dependent on one another that require review before
   * submission).
   *
   * <p>A single instance of this class is used to import either a single change, or a sequence of
   * changes where each change is the following change's parent.
   */
  interface Writer {

    /**
     * Returns the latest origin ref that was pushed to this destination.
     *
     * <p>Returns null if the last origin ref cannot be identified or the destination doesn't
     * support this feature. This requires that the {@code Destination} stores information about
     * the origin ref.
     *
     * <p>This method may have undefined behavior if called after
     * {@link #write(TransformResult, Console)}.
     */
    @Nullable
    String getPreviousRef(String labelName) throws RepoException;

    /**
     * Returns true if this destination stores references in the repository so that
     * {@code getPreviousRef} can be used for discovering previous imported revisions.
     */
    default boolean supportsPreviousRef() {
      return true;
    }

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
   *     deleted if they don't exist in the source.
   * @throws ValidationException if the writer could not be created because of a user error. For
   *     instance, the destination cannot be used with the given {@code destinationFiles}.
   */
  Writer newWriter(Glob destinationFiles) throws ValidationException;

  /**
   * Given a reverse workflow with an {@code Origin} than is of the same type as this destination,
   * the label that that {@link Origin#getLabelName()} would return.
   *
   * <p>This label name is used by the origin in the reverse workflow to stamp it's original
   * revision id. Destinations return the origin label so that a baseline label can be found when
   * using {@link WorkflowMode#CHANGE_REQUEST}.
   */
  String getLabelNameWhenOrigin();

}
