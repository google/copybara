/*
 * Copyright (C) 2026 Google Inc.
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

package com.google.copybara.perforce;

import com.google.common.base.MoreObjects;
import com.google.copybara.revision.Revision;
import java.time.ZonedDateTime;
import javax.annotation.Nullable;

/**
 * A Perforce revision, identified by a submitted changelist number.
 *
 * <p>Changelist numbers are monotonically increasing across the whole depot, so a single integer
 * fully pins the state of any stream — it is both the {@link #asString()} identifier and the
 * {@link #fixedReference()}.
 */
public class PerforceRevision implements Revision {

  private final int changelist;
  // The stream this revision was resolved against, e.g. "//stream/main". Acts as the context
  // reference, the equivalent of a git branch name.
  @Nullable private final String stream;
  @Nullable private final ZonedDateTime timestamp;

  public PerforceRevision(int changelist) {
    this(changelist, /* stream= */ null, /* timestamp= */ null);
  }

  public PerforceRevision(int changelist, @Nullable String stream,
      @Nullable ZonedDateTime timestamp) {
    this.changelist = changelist;
    this.stream = stream;
    this.timestamp = timestamp;
  }

  int getChangelist() {
    return changelist;
  }

  @Override
  public String asString() {
    return Integer.toString(changelist);
  }

  @Nullable
  @Override
  public String contextReference() {
    return stream;
  }

  @Override
  public String fixedReference() {
    return asString();
  }

  @Nullable
  @Override
  public ZonedDateTime readTimestamp() {
    return timestamp;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .omitNullValues()
        .add("changelist", changelist)
        .add("stream", stream)
        .toString();
  }
}
