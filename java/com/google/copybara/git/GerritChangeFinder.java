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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.util.console.Console;
import java.util.List;

/** Generic interface for querying Gerrit. */
public interface GerritChangeFinder {

  /** Each ResponsePart contains information about a single change. */
  public static class GerritChange {
    private final String changeId;

    public GerritChange(String changeId) {
      this.changeId = Preconditions.checkNotNull(changeId);
    }
    public String getChangeId() {
      return changeId;
    }

    @Override
    public String toString() {
      return "changeId=" + changeId;
    }
  }

  /** Response to a query. */
  public static class Response {
    private final List<GerritChange> changes;

    public Response(ImmutableList<GerritChange> changes) {
      this.changes = Preconditions.checkNotNull(changes);
    }

    public List<GerritChange> getParts() {
      return changes;
    }
  }

  /** Default no op implementation. */
  // TODO(copybara-team) - provide a proper implementation.
  public static class Default implements GerritChangeFinder {

    @Override
    public Response find(String url, String query, Console console) {
      return new Response(ImmutableList.of());
    }
  }

  /**
   * Queries a repo in Gerrit for changes. @see <a
   * href="https://gerrit-review.googlesource.com/Documentation/user-search.html"/>for how to query
   * Gerrit.
   */
  public Response find(String repoUrl, String query, Console console);
}
