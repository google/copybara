// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.copybara.util.console.Console;
import java.util.Objects;

/**
 * Arguments for {@link Workflow} components.
 */
@Parameters(separators = "=")
public class WorkflowOptions implements Option {

  static final String CHANGE_REQUEST_PARENT_FLAG = "--change_request_parent";
  @Parameter(names = CHANGE_REQUEST_PARENT_FLAG,
      description = "Commit reference to be used as parent when importing a commit using"
          + " CHANGE_REQUEST workflow mode. this shouldn't be needed in general as Copybara is able"
          + " to detect the parent commit message.")
  String changeBaseline = "";

  @Parameter(names = "--last-rev",
      description = "Last revision that was migrated to the destination")
  String lastRevision;

  @Parameter(names = "--ignore-noop",
      description = "Only warn about operations/transforms that didn't have any effect."
          + " For example: A transform that didn't modify any file, non-existent origin"
          + " directories, etc.")
  public boolean ignoreNoop = false;

  @Parameter(names = "--ignore-empty-changes",
      description = "If the resulted change is an empty one, skip it instead of throwing an error")
  public boolean ignoreEmptyChanges = false;

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

  private String workflowName;

  public WorkflowOptions() {
  }

  @VisibleForTesting
  public WorkflowOptions(String changeBaseline, String lastRevision, String workflowName) {
    this.changeBaseline = changeBaseline;
    this.lastRevision = lastRevision;
    this.workflowName = workflowName;
  }

  void setWorkflowName(String workflowName) {
    Preconditions.checkNotNull(workflowName);
    this.workflowName = workflowName;
  }

  public String getLastRevision() {
    return lastRevision;
  }

  public String getWorkflowName() {
    return workflowName;
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
    return Objects.equals(changeBaseline, that.changeBaseline) &&
        Objects.equals(lastRevision, that.lastRevision) &&
        Objects.equals(workflowName, that.workflowName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(changeBaseline, lastRevision, workflowName);
  }
}
