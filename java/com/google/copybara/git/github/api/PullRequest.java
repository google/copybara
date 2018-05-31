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

package com.google.copybara.git.github.api;

import com.google.api.client.util.Key;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * Represents a pull request returned by
 * https://api.github.com/repos/REPO_ID/pulls/NUMBER
 */
public class PullRequest extends PullRequestOrIssue {

  @Key private Revision head;
  @Key private Revision base;
  @Key("requested_reviewers") private List<User> requestedReviewers;

  public Revision getHead() {
    return head;
  }

  public Revision getBase() {
    return base;
  }

  public ImmutableList<User> getRequestedReviewers() {
    return requestedReviewers == null
        ? ImmutableList.of()
        : ImmutableList.copyOf(requestedReviewers);
  }

  @Override
  public String toString() {
    return getToStringHelper()
        .add("head", head)
        .add("base", base)
        .toString();
  }
}
