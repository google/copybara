package com.google.copybara.git.github.api;

/*
 * Copyright (C) 2022 Google Inc.
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

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;
import java.util.List;

/**
 * Corresponds to JSON schema response for getting a GitHub organization detailed in
 * https://docs.github.com/en/rest/search?apiVersion=2022-11-28#search-issues-and-pull-requests
 *
 * <p>Not all property keys are included here. Add them as needed.
 */
public class IssuesAndPullRequestsSearchResults {

  @Key private List<IssuesAndPullRequestsSearchResult> items;

  public IssuesAndPullRequestsSearchResults() {}

  public List<IssuesAndPullRequestsSearchResult> getItems() {
    return items;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("items", items.toString()).toString();
  }

  /** A single result entity from fetching issues. */
  public static class IssuesAndPullRequestsSearchResult {
    @Key private long number;

    public IssuesAndPullRequestsSearchResult() {}

    public long getNumber() {
      return number;
    }
  }
}
