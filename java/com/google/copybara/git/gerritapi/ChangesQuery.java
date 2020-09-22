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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.StarlarkValue;

/**
 * An object that represents the input parameters for a changes query:
 *
 * <p>https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#list-changes
 */
@StarlarkBuiltin(
    name = "gerritapi.ChangesQuery",
    doc =
        "Input for listing Gerrit changes. See "
            + "https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#list-changes")
public class ChangesQuery implements StarlarkValue {

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

  public ChangesQuery withStart(int start) {
    return new ChangesQuery(query, include, limit, start);
  }

  public ChangesQuery withLimit(int limit) {
    return new ChangesQuery(query, include, limit, start);
  }

  public ChangesQuery withInclude(Iterable<IncludeResult> include) {
    return new ChangesQuery(query, ImmutableSet.copyOf(include), limit, start);
  }

  @VisibleForTesting
  public String asUrlParams() {
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

  @Override
  public void repr(Printer printer) {
    printer.append(toString());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("query", query)
        .add("include", include)
        .add("limit", limit)
        .add("start", start)
        .toString();
  }
}
