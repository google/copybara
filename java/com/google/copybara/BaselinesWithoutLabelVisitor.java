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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.ChangeVisitable.ChangesVisitor;
import com.google.copybara.ChangeVisitable.VisitResult;
import com.google.copybara.util.Glob;
import java.util.ArrayList;
import java.util.List;

/** A visitor that finds all the parents that match the origin glob. */
public class BaselinesWithoutLabelVisitor<T> implements ChangesVisitor {

  private final List<T> result = new ArrayList<>();
  private final int limit;
  private final Glob originFiles;
  private boolean skipFirst;

  public BaselinesWithoutLabelVisitor(Glob originFiles, int limit, boolean skipFirst) {
    this.originFiles = Preconditions.checkNotNull(originFiles);
    Preconditions.checkArgument(limit > 0);
    this.limit = limit;
    this.skipFirst = skipFirst;
  }

  public ImmutableList<T> getResult() {
    return ImmutableList.copyOf(result);
  }

  @SuppressWarnings("unchecked")
  @Override
  public VisitResult visit(Change<? extends Revision> change) {
    if (skipFirst) {
      skipFirst = false;
      return VisitResult.CONTINUE;
    }
    ImmutableSet<String> files = change.getChangeFiles();
    if (Glob.affectsRoots(originFiles.roots(), files)) {
      result.add((T) change.getRevision());
      return result.size() < limit ? VisitResult.CONTINUE : VisitResult.TERMINATE;
    }
    // This change only contains files that are not exported
    return VisitResult.CONTINUE;
  }
}
