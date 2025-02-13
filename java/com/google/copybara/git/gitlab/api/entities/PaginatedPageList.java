/*
 * Copyright (C) 2025 Google LLC
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

package com.google.copybara.git.gitlab.api.entities;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.api.client.http.HttpHeaders;
import com.google.common.base.Splitter;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A list that can contain information on retrieving the next page of a paginated response.
 *
 * <p>This class is intended to represent a partial response set (i.e. a page), where the next URL
 * can be followed to obtain further elements in all pages.
 *
 * @param <T> the type of elements in the list
 */
public class PaginatedPageList<T> extends ArrayList<T> implements GitLabApiEntity {
  private static final Pattern LINK_HEADER_PATTERN = Pattern.compile("<([^>]+)>; rel=\"([a-z]+)\"");

  private final Optional<String> nextUrl;

  public PaginatedPageList() {
    this(ImmutableList.of(), Optional.empty());
  }

  private PaginatedPageList(List<T> elements, Optional<String> nextUrl) {
    super(elements);
    this.nextUrl = nextUrl;
  }

  /**
   * Returns the "next" URL that this object is annotated with, if any.
   *
   * @return the "next" URL
   */
  public Optional<String> getNextUrl() {
    return nextUrl;
  }

  /**
   * Annotates this {@link PaginatedPageList} instance with the "next" link from the provided
   * header.
   *
   * @param apiUrl the URL of the API endpoint, used to verify that the "next" link points to the
   *     correct endpoint
   * @param httpHeaders the headers from the {@link com.google.api.client.http.HttpResponse} object
   * @return the new list, with the "next" link set
   */
  public PaginatedPageList<T> withPaginatedInfo(String apiUrl, HttpHeaders httpHeaders) {
    if (!httpHeaders.containsKey("link")) {
      return this;
    }

    @SuppressWarnings("unchecked") // String header values are returned within a list of strings.
    String linkHeader = Iterables.getOnlyElement((List<String>) httpHeaders.get("link"));
    if (linkHeader == null) {
      return this;
    }

    ImmutableMap<String, String> links =
        Splitter.on(',')
            .trimResults()
            .splitToStream(linkHeader)
            .filter(link -> !link.trim().isEmpty())
            .map(
                link -> {
                  Matcher matcher = LINK_HEADER_PATTERN.matcher(link);
                  Verify.verify(matcher.matches(), "'%s' does not match link header regex.", link);
                  return matcher;
                })
            .peek(
                m ->
                    Verify.verify(
                        m.group(1).startsWith(apiUrl),
                        "%s doesn't start with %s",
                        m.group(1),
                        apiUrl))
            // key is the "rel" value (e.g. next, prev). Value is the URL.
            .collect(toImmutableMap(m -> m.group(2), m -> m.group(1).substring(apiUrl.length())));
    return new PaginatedPageList<>(this, Optional.ofNullable(links.get("next")));
  }
}
