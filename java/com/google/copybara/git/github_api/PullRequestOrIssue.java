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

package com.google.copybara.git.github_api;

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.time.ZonedDateTime;

/**
 * Common fields for issues and pull requests.
 *
 * <p>There are more fields but they are ignored for now.
 */
public class PullRequestOrIssue {
  @Key
  private long number;

  @Key
  private String state;

  @Key
  private String title;

  @Key
  private String body;

  @Key
  private String created_at;

  @Key
  private String updated_at;

  public long getNumber() {
    return number;
  }

  public String getState() {
    return state;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public ZonedDateTime getCreatedAt() {
    return ZonedDateTime.parse(created_at);
  }

  public ZonedDateTime getModifiedAt() {
    return ZonedDateTime.parse(created_at);
  }

  @Override
  public String toString() {
    return getToStringHelper().toString();
  }

  protected ToStringHelper getToStringHelper() {
    return MoreObjects.toStringHelper(this)
        .add("number", number)
        .add("state", state)
        .add("title", title)
        .add("body", body)
        .add("created_at", created_at)
        .add("updated_at", updated_at);
  }
}
