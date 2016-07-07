package com.google.copybara;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import com.beust.jcommander.Parameter;

/**
 * Arguments for {@link Workflow} components.
 */
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
}
