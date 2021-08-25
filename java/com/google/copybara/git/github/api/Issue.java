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
import net.starlark.java.eval.StarlarkValue;

/**
 * Represents an issue returned by https://api.github.com/repos/REPO_ID/issues/NUMBER
 */

public class Issue extends PullRequestOrIssue implements StarlarkValue {

  @Key private List<Label> labels;


  public List<Label> getLabels() {
    return labels;
  }

  public void setLabels(List<Label> labels) {
    this.labels = labels;
  }

  @Override
  public String toString() {
    return super.toString() + getToStringHelper();
  }

  /** Represents https://docs.github.com/en/rest/reference/issues#create-an-issue--parameters */
  public static class CreateIssueRequest {
    @Key private String title;
    @Key private String body;
    @Key private List<String> assignees;

    public CreateIssueRequest(String title, String body, List<String> assignees) {
      this.title = title;
      this.body = body;
      this.assignees = ImmutableList.copyOf(assignees);
    }

    public String getTitle() {
      return title;
    }

    public String getBody() {
      return body;
    }

    public ImmutableList<String> getAssignees() {
      return ImmutableList.copyOf(assignees);
    }
  }

}
