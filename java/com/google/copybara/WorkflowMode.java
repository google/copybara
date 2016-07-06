package com.google.copybara;

import com.google.common.collect.ImmutableList;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.transform.ValidationException;
import com.google.copybara.util.console.ProgressPrefixConsole;

import java.io.IOException;

/**
 * Workflow type to run between origin an destination
 */
public enum WorkflowMode {
  /**
   * Create a single commit in the destination with new tree state.
   */
  @DocField(description = "Create a single commit in the destination with new tree state.")
  SQUASH {
    @Override
    <O extends Origin<O>> void run(Workflow<O>.RunHelper runHelper)
        throws RepoException, IOException, EnvironmentException, ValidationException {
      runHelper.migrate(
          runHelper.getResolvedRef(),
          // SQUASH workflows always use the default author
          runHelper.getAuthoring().getDefaultAuthor(),
          runHelper.getConsole(),
          runHelper.changesSummaryMessage());
    }
  },

  /**
   * Import each origin change individually.
   */
  @DocField(description = "Import each origin change individually.")
  ITERATIVE {
    @Override
    <O extends Origin<O>> void run(Workflow<O>.RunHelper runHelper)
        throws RepoException, IOException, EnvironmentException, ValidationException {
      ImmutableList<Change<O>> changes = runHelper.changesSinceLastImport();
      for (int i = 0; i < changes.size(); i++) {
        Change<O> change = changes.get(i);
        String prefix = String.format(
            "[%2d/%d] Migrating change %s: ", i + 1, changes.size(),
            change.getReference().asString());

        runHelper.migrate(
            change.getReference(),
            change.getAuthor(),
            new ProgressPrefixConsole(prefix, runHelper.getConsole()),
            change.getMessage());
      }
    }
  };

  abstract <O extends Origin<O>> void run(Workflow<O>.RunHelper runHelper)
      throws RepoException, IOException, EnvironmentException, ValidationException;

  // TODO(copybara): Import an origin tree state diffed by a common parent
  // in destination. This could be a GH Pull Request, a Gerrit Change, etc.
  //CHANGE_REQUEST,
  // TODO(copybara): Mirror individual changes from origin to destination. Requires a
  // that origin and destination are of the same time and that they support mirroring
  //MIRROR
}
