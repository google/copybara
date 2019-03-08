/*
 * Copyright (C) 2019 Google Inc.
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
import com.google.api.client.util.Preconditions;
import com.google.api.client.util.Value;
import com.google.common.annotations.VisibleForTesting;
import javax.annotation.Nullable;

public class UpdatePullRequest {

  @Key @Nullable private String title;
  @Key @Nullable private String body;
  @Key @Nullable private State state;

  @VisibleForTesting
  public UpdatePullRequest() {
  }

  public UpdatePullRequest(@Nullable String title, @Nullable String body,
      @Nullable State state) {
    this.title = title;
    this.body = body;
    this.state = state;
    Preconditions.checkArgument(title != null || body != null || state != null,
        "No state change provided. At least one field needs to be not-null");
  }

  public enum State {
    @Value("open") OPEN,
    @Value("closed") CLOSED
  }

  @Nullable
  public String getTitle() {
    return title;
  }

  @Nullable
  public String getBody() {
    return body;
  }

  @Nullable
  public State getState() {
    return state;
  }
}
