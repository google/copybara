// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import static com.google.copybara.WorkflowOptions.CHANGE_REQUEST_PARENT_FLAG;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import com.google.copybara.Destination.WriterResult;
import com.google.copybara.Origin.ChangesVisitor;
import com.google.copybara.Origin.VisitResult;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.util.console.ProgressPrefixConsole;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

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
    <R extends Origin.Reference> void run(Workflow<R>.RunHelper runHelper)
        throws RepoException, IOException, ValidationException {
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
    <R extends Origin.Reference> void run(Workflow<R>.RunHelper runHelper)
        throws RepoException, IOException, ValidationException {
      ImmutableList<Change<R>> changes = runHelper.changesSinceLastImport();
      int changeNumber = 1;
      UnmodifiableIterator<Change<R>> changesIterator = changes.iterator();
      while (changesIterator.hasNext()) {
        Change<R> change = changesIterator.next();
        String prefix = String.format(
            "Change %d of %d (%s): ",
            changeNumber, changes.size(), change.getReference().asString());
        WriterResult result;
        try {
          result = runHelper.migrate(
              change.getReference(),
              runHelper.getAuthoring().resolve(change.getOriginalAuthor()),
              new ProgressPrefixConsole(prefix, runHelper.getConsole()),
              change.getMessage());
        } catch (EmptyChangeException e) {
          if (runHelper.workflowOptions().ignoreEmptyChanges) {
            runHelper.getConsole().warn(e.getMessage());
            result = WriterResult.OK;
          } else {
            throw e;
          }
        }

        if (result == WriterResult.PROMPT_TO_CONTINUE && changesIterator.hasNext()) {
          // Use the regular console to log prompt and final message, it will be easier to spot
          if (!runHelper.getConsole()
              .promptConfirmation("Continue importing next change?")) {
            String message = String.format("Iterative workflow aborted by user after: %s", prefix);
            runHelper.getConsole().warn(message);
            throw new ChangeRejectedException(message);
          }
        }
        changeNumber++;
      }
    }
  },
  @DocField(description = "Import an origin tree state diffed by a common parent"
      + " in destination. This could be a GH Pull Request, a Gerrit Change, etc.")
  CHANGE_REQUEST {
    @Override
    <R extends Origin.Reference> void run(Workflow<R>.RunHelper runHelper)
        throws RepoException, IOException, ValidationException {
      final AtomicReference<String> requestParent = new AtomicReference<>(
          runHelper.workflowOptions().changeBaseline);
      final String originLabelName = runHelper.getDestination().getLabelNameWhenOrigin();
      if (Strings.isNullOrEmpty(requestParent.get())) {
        runHelper.getOrigin().visitChanges(runHelper.getResolvedRef(), new ChangesVisitor() {
          @Override
          public VisitResult visit(Change<?> change) {
            if (change.getLabels().containsKey(originLabelName)) {
              requestParent.set(change.getLabels().get(originLabelName));
              return VisitResult.TERMINATE;
            }
            return VisitResult.CONTINUE;
          }
        });
      }

      if (Strings.isNullOrEmpty(requestParent.get())) {
        throw new ValidationException(
            "Cannot find matching parent commit in in the destination. Use '"
                + CHANGE_REQUEST_PARENT_FLAG
                + "' flag to force a parent commit to use as baseline in the destination.");
      }
      Change<R> change = runHelper.getOrigin().change(runHelper.getResolvedRef());
      runHelper.migrate(
          runHelper.getResolvedRef(),
          runHelper.getAuthoring().resolve(change.getOriginalAuthor()),
          runHelper.getConsole(),
          change.getMessage(), requestParent.get());
    }
  },

  // TODO(copybara): Implement
  @DocField(description = "Mirror individual changes from origin to destination. Requires that "
      + "origin and destination are of the same type and that they support mirroring.",
      undocumented = true)
  MIRROR {
    @Override
    <R extends Origin.Reference> void run(Workflow<R>.RunHelper helper)
        throws RepoException, IOException, ValidationException {
      throw new UnsupportedOperationException("WorkflowMode 'MIRROR' not implemented.");
    }
  };

  abstract <R extends Origin.Reference> void run(Workflow<R>.RunHelper runHelper)
      throws RepoException, IOException, ValidationException;
}
