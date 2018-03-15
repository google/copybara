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
import com.google.common.base.MoreObjects;
import java.util.List;

/**
 * Represents an issue returned by https://api.github.com/repos/REPO_ID/issues/NUMBER
 */
public class Issue extends PullRequestOrIssue {

  @Key
  private List<Label> labels;

  public List<Label> getLabels() {
    return labels;
  }

  public void setLabels(List<Label> labels) {
    this.labels = labels;
  }

  @Override
  public String toString() {
    return getToStringHelper()
        .add("labels", labels)
        .toString();
  }

  public static class Label {

    @Key
    private long id;
    @Key
    private String name;

    public long getId() {
      return id;
    }

    public String getName() {
      return name;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("id", id)
          .add("name", name)
          .toString();
    }
  }
}
