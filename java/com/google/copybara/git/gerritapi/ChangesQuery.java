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

package com.google.copybara.git.gerritapi;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import javax.annotation.Nullable;

/**
 * An object that represents the input parameters for a changes query:
 *
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#list-changes
 */
public class ChangesQuery {

  private final String query;
  private final ImmutableSet<IncludeResult> include;
  @Nullable private final Integer limit;
  @Nullable private final Integer start;

  public ChangesQuery(String query) {
    this.query = query;
    include = ImmutableSet.of();
    limit = null;
    start = null;
  }

  private ChangesQuery(String query, ImmutableSet<IncludeResult> include, Integer limit,
      Integer start) {
    this.query = Preconditions.checkNotNull(query);
    this.include = Preconditions.checkNotNull(include);
    this.limit = limit;
    this.start = start;
  }

  ChangesQuery withStart(int start) {
    return new ChangesQuery(query, include, limit, start);
  }

  ChangesQuery withLimit(int limit) {
    return new ChangesQuery(query, include, limit, start);
  }

  ChangesQuery withInclude(Iterable<IncludeResult> include) {
    return new ChangesQuery(query, ImmutableSet.copyOf(include), limit, start);
  }

  String asUrlParams() {
    StringBuilder sb = new StringBuilder("q=").append(escape(query));
    for (IncludeResult includeResult : include) {
      sb.append("&o=").append(includeResult);
    }
    if (limit != null) {
      sb.append("&n=").append(limit);
    }
    if (start != null) {
      sb.append("&S=").append(start);
    }
    return sb.toString();
  }

  private static String escape(String query) {
    try {
      return URLEncoder.encode(query, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Shouldn't fail", e);
    }
  }

  /**
   * Fields to include in the response
   */
  public enum IncludeResult {
    LABELS,
    DETAILED_LABELS,
    CURRENT_REVISION,
    ALL_REVISIONS,
    DOWNLOAD_COMMANDS,
    CURRENT_COMMIT,
    ALL_COMMITS,
    CURRENT_FILES,
    ALL_FILES,
    DETAILED_ACCOUNTS,
    REVIEWER_UPDATES,
    MESSAGES,
    CURRENT_ACTIONS,
    CHANGE_ACTIONS,
    REVIEWED,
    SUBMITTABLE,
    WEB_LINKS,
    CHECK,
    COMMIT_FOOTERS,
    PUSH_CERTIFICATES
  }
}
