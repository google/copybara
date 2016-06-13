package com.google.copybara;

import com.google.copybara.doc.annotations.DocField;

/**
 * Workflow type to run between origin an destination
 */
public enum WorkflowMode {
  /**
   * Create a single commit in the destination with new tree state.
   */
  @DocField(description = "Create a single commit in the destination with new tree state.")
  SQUASH,
  /**
   * Import each origin change individually
   */
  @DocField(description = "Import each origin change individually.")
  ITERATIVE,
  // TODO(copybara): Import an origin tree state diffed by a common parent
  // in destination. This could be a GH Pull Request, a Gerrit Change, etc.
  //CHANGE_REQUEST,
  // TODO(copybara): Mirror individual changes from origin to destination. Requires a
  // that origin and destination are of the same time and that they support mirroring
  //MIRROR
}
