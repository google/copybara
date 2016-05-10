// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

/**
 * Options which indicate which workflow to run.
 */
public final class WorkflowNameOptions implements Option {
  private final String workflowName;

  public WorkflowNameOptions(String workflowName) {
    this.workflowName = workflowName;
  }

  public String get() {
    return workflowName;
  }
}
