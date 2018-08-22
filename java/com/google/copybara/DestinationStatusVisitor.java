package com.google.copybara;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.copybara.ChangeVisitable.ChangesVisitor;
import com.google.copybara.ChangeVisitable.VisitResult;
import com.google.copybara.Destination.DestinationStatus;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import javax.annotation.Nullable;

/**
 * A visitor that computes the {@link DestinationStatus} matching the actual files affected by
 * the changes with the destination files glob.
 */
public class DestinationStatusVisitor implements ChangesVisitor {

  private final PathMatcher pathMatcher;
  private final String labelName;

  private DestinationStatus destinationStatus = null;

  public DestinationStatusVisitor(PathMatcher pathMatcher, String labelName) {
    this.pathMatcher = pathMatcher;
    this.labelName = labelName;
  }

  @Override
  public VisitResult visit(Change<? extends Revision> change) {
    ImmutableSet<String> changeFiles = change.getChangeFiles();
    if (changeFiles != null) {
      if (change.getLabels().containsKey(labelName)) {
        for (String file : changeFiles) {
          if (pathMatcher.matches(Paths.get('/' + file))) {
            String lastRev = Iterables.getLast(change.getLabels().get(labelName));
            destinationStatus = new DestinationStatus(lastRev, ImmutableList.of());
            return VisitResult.TERMINATE;
          }
        }
      }
    }
    return VisitResult.CONTINUE;
  }

  @Nullable
  public DestinationStatus getDestinationStatus() {
    return destinationStatus;
  }
}
