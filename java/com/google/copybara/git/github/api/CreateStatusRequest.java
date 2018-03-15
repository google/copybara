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

package com.google.copybara.git.github.api;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;
import com.google.common.base.Preconditions;
import com.google.copybara.git.github.api.Status.State;

/**
 * Request for creating commit statuses:
 *
 * <p>https://developer.github.com/v3/repos/statuses/#create-a-status
 */
public class CreateStatusRequest extends GenericJson {

  @Key
  private State state;

  @Key("target_url")
  private String targetUrl;

  @Key
  private String description;

  @Key
  private String context;

  public CreateStatusRequest(State state, String targetUrl, String description,
      String context) {
    this.state = Preconditions.checkNotNull(state);
    this.targetUrl = targetUrl;
    this.description = description;
    this.context = Preconditions.checkNotNull(context);
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

}
