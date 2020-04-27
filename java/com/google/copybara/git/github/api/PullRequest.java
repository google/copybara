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
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.StarlarkBuiltin;
import com.google.devtools.build.lib.skylarkinterface.StarlarkDocumentationCategory;
import com.google.devtools.build.lib.syntax.StarlarkValue;
import java.util.List;

/** Represents a pull request returned by https://api.github.com/repos/REPO_ID/pulls/NUMBER */
@StarlarkBuiltin(
    name = "github_api_pull_request_obj",
    category = StarlarkDocumentationCategory.BUILTIN,
    doc =
        "Information about a pull request as defined in"
            + " https://developer.github.com/v3/repos/pulls. This is a subset of the available"
            + " fields in GitHub")
public class PullRequest extends PullRequestOrIssue implements StarlarkValue {

  @Key private Revision head;
  @Key private Revision base;
  @Key("requested_reviewers") private List<User> requestedReviewers;

  @SkylarkCallable(name = "head", doc = "Information about head", structField = true)
  public Revision getHead() {
    return head;
  }

  @SkylarkCallable(name = "base", doc = "Information about base", structField = true)
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
