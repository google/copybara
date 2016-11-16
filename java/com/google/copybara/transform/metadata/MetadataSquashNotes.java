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
import java.util.List;

/**
 * Generates a message that includes a constant prefix text and a list of changes
 * included in the squash change.
 */
public class MetadataSquashNotes implements Transformation {

  private final String prefix;
  private final int max;
  private final boolean compact;
  private final boolean showRef;
  private final boolean showAuthor;
  private final boolean oldestFirst;

  public MetadataSquashNotes(String prefix, int max, boolean compact, boolean showRef,
      boolean showAuthor, boolean oldestFirst) {
    this.prefix = prefix;
    this.max = max;
    this.compact = compact;
    this.showRef = showRef;
    this.showAuthor = showAuthor;
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
      if (compact) {
        sb.append("  - ");
        if (showRef) {
          sb.append(c.refAsString());
          sb.append(" ");
        }
        sb.append(cutIfLong(c.firstLineMessage()));
        if (showAuthor) {
          sb.append(" by ");
          sb.append(c.getAuthor());
        }
        sb.append("\n");
      } else {
        sb.append("--\n");
        if (showRef) {
          sb.append(c.refAsString());
        } else {
          sb.append("Change ").append(i + 1).append(" of ").append(changes.size());
        }
        if (showAuthor) {
          sb.append(" by ");
          sb.append(c.getAuthor());
        }
        sb.append(":\n\n");
        sb.append(c.getMessage());
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
