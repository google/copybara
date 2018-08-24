/*
 * Copyright (C) 2018 Google Inc.
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
