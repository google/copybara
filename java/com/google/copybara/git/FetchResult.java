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

package com.google.copybara.git;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The result of executing git fetch command
 * ({@link GitRepository#fetch(String, boolean, boolean, Iterable)}.
 */
public class FetchResult {

  private final ImmutableMap<String, GitRevision> deleted;
  private final ImmutableMap<String, GitRevision> inserted;
  private final ImmutableMap<String, RefUpdate> updated;

  FetchResult(ImmutableMap<String, GitRevision> before,
      ImmutableMap<String, GitRevision> after) {
    MapDifference<String, GitRevision> diff = Maps.difference(before, after);
    deleted = ImmutableMap.copyOf(diff.entriesOnlyOnLeft());
    inserted = ImmutableMap.copyOf(diff.entriesOnlyOnRight());
    updated = ImmutableMap.copyOf(diff.entriesDiffering().entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            v -> new RefUpdate(v.getValue().leftValue(), v.getValue().rightValue()))));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("deleted", deleted)
        .add("inserted", inserted)
        .add("updated", updated)
        .toString();
  }

  public ImmutableMap<String, GitRevision> getDeleted() {
    return deleted;
  }

  public ImmutableMap<String, GitRevision> getInserted() {
    return inserted;
  }

  public ImmutableMap<String, RefUpdate> getUpdated() {
    return updated;
  }

  /**
   * A reference update for a fetch command. Contains before and after SHA-1.
   */
  public static final class RefUpdate {

    private final GitRevision before;
    private final GitRevision after;

    RefUpdate(GitRevision before, GitRevision after) {
      this.before = before;
      this.after = after;
    }

    public GitRevision getBefore() {
      return before;
    }

    public GitRevision getAfter() {
      return after;
    }

    @Override
    public String toString() {
      return before.getSha1() + " -> " + after.getSha1();
    }
  }
}
