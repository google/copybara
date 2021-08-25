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
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import java.time.ZonedDateTime;
import java.util.List;
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkValue;

/**
 * Common fields for issues and pull requests.
 *
 * <p>There are more fields but they are ignored for now.
 */
public class PullRequestOrIssue implements StarlarkValue {
  @Key private long number;
  @Key private String state;
  @Key private String title;
  @Key private String body;
  @Key("created_at") private String createdAt;
  @Key("updated_at") private String updatedAt;
  @Key("html_url") private String htmlUrl;
  @Key private User user;
  @Key private User assignee;
  @Key private List<User> assignees;


  public long getNumber() {
    return number;
  }

  @StarlarkMethod(name = "number", doc = "Pull Request number", structField = true)
  public StarlarkInt getNumberForSkylark() {
    return StarlarkInt.of(number);
  }
  public String getState() {
    return state;
  }
  @StarlarkMethod(name = "state", doc = "Pull Request state", structField = true)
  public String getStateForSkylark() {
    return state.toUpperCase();
  }

  @StarlarkMethod(name = "title", doc = "Pull Request title", structField = true)
  public String getTitle() {
    return title;
  }

  @StarlarkMethod(name = "body", doc = "Pull Request body", structField = true)
  public String getBody() {
    return body;
  }

  public ZonedDateTime getCreatedAt() {
    return ZonedDateTime.parse(createdAt);
  }

  public ZonedDateTime getModifiedAt() {
    return ZonedDateTime.parse(createdAt);
  }

  public String getHtmlUrl() {
    return htmlUrl;
  }

  @Override
  public String toString() {
    return getToStringHelper().toString();
  }

  public boolean isOpen() {
    return "open".equals(state);
  }

  @StarlarkMethod(name = "assignee", doc = "Pull Request assignee", structField = true,
      allowReturnNones = true)
  @Nullable
  public User getAssignee() {
    return assignee;
  }

  @StarlarkMethod(name = "user", doc = "Pull Request owner", structField = true)
  public User getUser() {
    return user;
  }

  public ImmutableList<User> getAssignees() {
    return assignees == null ? ImmutableList.of() : ImmutableList.copyOf(assignees);
  }

  protected ToStringHelper getToStringHelper() {
    return MoreObjects.toStringHelper(this)
        .add("number", number)
        .add("state", state)
        .add("title", title)
        .add("body", body)
        .add("created_at", createdAt)
        .add("updated_at", updatedAt);
  }
}
