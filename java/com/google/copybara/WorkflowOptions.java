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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.copybara.util.console.Console;
import java.util.Objects;

/**
 * Arguments for {@link Workflow} components.
 */
@Parameters(separators = "=")
public class WorkflowOptions implements Option {

  static final String CHANGE_REQUEST_PARENT_FLAG = "--change_request_parent";
  public static final String LAST_REV_FLAG = "--last-rev";
  public static final String IGNORE_NOOP_FLAG = "--ignore-noop";
  public static final String ITERATIVE_LIMIT_CHANGES_FLAG = "--iterative-limit-changes";

  @Parameter(names = CHANGE_REQUEST_PARENT_FLAG,
      description = "Commit revision to be used as parent when importing a commit using"
          + " CHANGE_REQUEST workflow mode. this shouldn't be needed in general as Copybara is able"
          + " to detect the parent commit message.")
  String changeBaseline = "";

  @Parameter(names = LAST_REV_FLAG,
      description = "Last revision that was migrated to the destination")
  String lastRevision;

  @Parameter(names = ITERATIVE_LIMIT_CHANGES_FLAG,
      description = "Import just a number of changes instead of all the pending ones")
  int iterativeLimitChanges = Integer.MAX_VALUE;

  @Parameter(names = IGNORE_NOOP_FLAG,
      description = "Only warn about operations/transforms that didn't have any effect."
          + " For example: A transform that didn't modify any file, non-existent origin"
          + " directories, etc.")
  public boolean ignoreNoop = false;

  @Parameter(
    names = "--squash-skip-history",
    description =
        "Avoid exposing the history of changes that are being migrated. This is"
            + " useful when we want to migrate a new repository but we don't want to expose all"
            + " the change history to metadata.squash_notes."
  )
  public boolean squashSkipHistory = false;

  @Parameter(names = "--iterative-all-changes",
      description = "By default Copybara will only try to migrate changes that could affect the"
          + " destination. Ignoring changes that only affect excluded files in origin_files. This"
          + " flag disables that behavior and runs for all the changes.")
  public boolean iterativeAllChanges = false;


  /**
   * Reports that some operation is a no-op. This will either throw an exception or report the
   * incident to the console, depending on the options.
   */
  public void reportNoop(Console console, String message) throws VoidOperationException {
    if (ignoreNoop) {
      console.warn(message);
    } else {
      throw new VoidOperationException(message);
    }
  }

  public WorkflowOptions() {}

  @VisibleForTesting
  public WorkflowOptions(String changeBaseline, String lastRevision) {
    this.changeBaseline = changeBaseline;
    this.lastRevision = lastRevision;
  }

  public String getLastRevision() {
    return lastRevision;
  }

  public String getChangeBaseline() {
    return changeBaseline;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    WorkflowOptions that = (WorkflowOptions) o;
    return Objects.equals(changeBaseline, that.changeBaseline)
        && Objects.equals(lastRevision, that.lastRevision);
  }

  @Override
  public int hashCode() {
    return Objects.hash(changeBaseline, lastRevision);
  }
}
