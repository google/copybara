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

import com.google.common.collect.Lists;
import com.google.copybara.Change;
import com.google.copybara.NonReversibleValidationException;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.ValidationException;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.transform.IntentionalNoop;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates a message that includes a constant prefix text and a list of changes
 * included in the squash change.
 */
public class MetadataSquashNotes implements Transformation {

  private final String prefix;
  private final int max;
  private final boolean compact;
  private final boolean showAuthor;
  private final boolean showDescription;
  private final boolean showRef;
  private final boolean oldestFirst;

  public MetadataSquashNotes(String prefix, int max, boolean compact, boolean showRef,
      boolean showAuthor, boolean showDescription, boolean oldestFirst) {
    this.prefix = prefix;
    this.max = max;
    this.compact = compact;
    this.showRef = showRef;
    this.showAuthor = showAuthor;
    this.showDescription = showDescription;
    this.oldestFirst = oldestFirst;
  }

  @Override
  public void transform(TransformWork work)
      throws IOException, ValidationException {
    StringBuilder sb = new StringBuilder(prefix);
    if (max == 0) {
      // Don't force changes to be computed if we don't want any change back.
      work.setMessage(sb.toString());
      return;
    }
    int counter = 0;
    List<? extends Change<?>> changes = work.getChanges().getCurrent();
    if (oldestFirst) {
      changes = Lists.reverse(changes);
    }
    for (int i = 0; i < changes.size(); i++) {
      Change c = changes.get(i);
      if (counter == max) {
        break;
      }
      ArrayList<String> summary = new ArrayList<>();
      if (compact) {
        sb.append("  - ");
        if (showRef) {
          summary.add(c.refAsString());
        }
        if (showDescription) {
          summary.add(cutIfLong(c.firstLineMessage()));
        }
        if (showAuthor) {
          summary.add("by " + c.getAuthor().toString());
        }
        sb.append(summary.stream()
                  .collect(Collectors.joining(" ")));
        sb.append("\n");
      } else {
        sb.append("--\n");
        if (showRef) {
          summary.add(c.refAsString());
        } else {
          summary.add(String.format("Change %s of %s", i + 1, changes.size()));
        }
        if (showAuthor) {
          summary.add("by " + c.getAuthor().toString());
        }
        sb.append(summary.stream()
            .collect(Collectors.joining(" ")));
        if (showDescription) {
          sb.append(":\n\n");
          sb.append(c.getMessage());
        }
        sb.append("\n");
      }
      counter++;
    }
    if (changes.size() > max) {
      sb.append("  (And ").append(changes.size() - max).append(" more changes)\n");
    }
    work.setMessage(sb.toString());
  }

  private String cutIfLong(String msg) {
    return msg.length() < 60 ? msg : msg.substring(0, 57) + "...";
  }


  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    return new ExplicitReversal(IntentionalNoop.INSTANCE, this);
  }

  @Override
  public String describe() {
    return "squash_notes";
  }
}
