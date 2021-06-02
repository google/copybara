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

package com.google.copybara.transform.metadata;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Iterables;
import com.google.copybara.Change;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.ValidationException;
import java.io.IOException;
import net.starlark.java.eval.EvalException;
import net.starlark.java.syntax.Location;

/**
 * Restores an original author stored in a label.
 */
public class RestoreOriginalAuthor implements Transformation {

  private final String label;
  private final boolean searchAllChanges;
  private final Location location;

  RestoreOriginalAuthor(String label, boolean searchAllChanges,
      Location location) {
    this.label = label;
    this.searchAllChanges = searchAllChanges;
    this.location = Preconditions.checkNotNull(location);
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException {
    Author author = null;
    // If multiple commits are included (for example on a squash for skipping a bad change),
    // last author wins.
    for (Change<?> change : work.getChanges().getCurrent()) {
      ImmutableCollection<String> labelValue = change.getLabels().get(label);
      if (!labelValue.isEmpty()) {
        try {
          author = Author.parse(Iterables.getLast(labelValue));
        } catch (EvalException e) {
          // Don't fail the migration because the label is wrong since it is very
          // difficult for a user to recover from this.
          work.getConsole().warn("Cannot restore original author: " + e.getMessage());
        }
      }
      if (!searchAllChanges) {
        break;
      }
    }
    if (author != null) {
      work.setAuthor(author);
      work.removeLabel(label, /*wholeMessage=*/true);
    }
    return TransformationStatus.success();
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    return new SaveOriginalAuthor(label, location);
  }

  @Override
  public String describe() {
    return "Restoring original author";
  }

  @Override
  public Location location() {
    return location;
  }
}
