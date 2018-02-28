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

package com.google.copybara.git.githubapi;

import com.google.api.client.util.Key;
import com.google.api.client.util.Value;
import java.time.ZonedDateTime;
import javax.annotation.Nullable;

/**
 * A commit status object
 *
 * <p>https://developer.github.com/v3/repos/statuses
 */
public class Status {

  @Key("target_url") private String targetUrl;
  @Key("description") private String description;
  @Key("context") private String context;
  @Key private State state;
  @Key("created_at") private String createdAt;
  @Key("updated_at") private String updatedAt;
  @Nullable @Key private User creator;

  public ZonedDateTime getCreatedAt() {
    return ZonedDateTime.parse(createdAt);
  }

  public ZonedDateTime getModifiedAt() {
    return ZonedDateTime.parse(createdAt);
  }

  public State getState() {
    return state;
  }

  public String getTargetUrl() {
    return targetUrl;
  }

  public String getDescription() {
    return description;
  }

  public String getContext() {
    return context;
  }

  /**
   * Not set in combined status
   */
  @Nullable
  public User getCreator() {
    return creator;
  }

  /**
   * State of the commit status
   */
  public enum State {
    @Value("error") ERROR,
    @Value("failure") FAILURE,
    @Value("pending") PENDING,
    @Value("success") SUCCESS
  }
}
