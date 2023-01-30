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

package com.google.copybara.git.github.api;

import com.google.common.base.Splitter;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.ArrayList;
import java.util.Collection;
import javax.annotation.Nullable;

/**
 * A List that contains additional information on how to fetch the next/prev page.
 */
public class PaginatedList<T> extends ArrayList<T> implements PaginatedPayload<T> {

  private static final Pattern LINK_HEADER_PATTERN = Pattern.compile("<([^>]+)>; rel=\"([a-z]+)\"");

  @Nullable
  private final String nextUrl;
  @Nullable
  private final String prevUrl;
  @Nullable
  private final String lastUrl;
  @Nullable
  private final String firstUrl;

  @SuppressWarnings("unused")
  public PaginatedList() {
    this(ImmutableList.of(), null, null, null, null);
  }
  
  private PaginatedList(Collection<T> elements, @Nullable String firstUrl, @Nullable String prevUrl,
      @Nullable String nextUrl, @Nullable String lastUrl) {
    super(elements);
    this.firstUrl = firstUrl;
    this.prevUrl = prevUrl;
    this.nextUrl = nextUrl;
    this.lastUrl = lastUrl;
  }

  @Nullable
  public String getNextUrl() {
    return nextUrl;
  }

  @Nullable
  public String getPrevUrl() {
    return prevUrl;
  }

  @Nullable
  public String getLastUrl() {
    return lastUrl;
  }

  @Nullable
  public String getFirstUrl() {
    return firstUrl;
  }

  /** Return a PaginatedList with the next/last/etc. fields populated if linkHeader is not null. */
  public PaginatedList<T> withPaginationInfo(String apiPrefix, @Nullable String linkHeader) {
    if (linkHeader == null) {
      return this;
    }
    String next = null;
    String prev = null;
    String last = null;
    String first = null;
    for (String e : Splitter.on(',').trimResults().split(linkHeader)) {
      Matcher matcher = LINK_HEADER_PATTERN.matcher(e);
      Verify.verify(matcher.matches(), "'%s' doesn't match Link regex. Header: %s", e, linkHeader);
      String url = matcher.group(1);
      String rel = matcher.group(2);
      Verify.verify(url.startsWith(apiPrefix));
      url = url.substring(apiPrefix.length());
      switch (rel) {
        case "first":
          first = url;
          break;
        case "prev":
          prev = url;
          break;
        case "next":
          next = url;
          break;
        case "last":
          last = url;
          break;
        default: // fall out
      }
    }
    return new PaginatedList<>(this, first, prev, next, last);
  }

  @Override
  public PaginatedList<T> getPayload() {
    return this;
  }

  @Override
  public PaginatedPayload<T> annotatePayload(String apiPrefix, @Nullable String linkHeader) {
    return withPaginationInfo(apiPrefix, linkHeader);
  }
}
