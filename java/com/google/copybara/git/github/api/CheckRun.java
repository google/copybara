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
import com.google.api.client.util.Value;
import com.google.common.base.MoreObjects;
import javax.annotation.Nullable;

/**
 * Represents a GitHub App's checkRun detail.
 * https://developer.github.com/v3/checks/runs/#create-a-check-run
 * https://developer.github.com/v3/checks/runs/#response
 */
public class CheckRun {

  @Key("details_url")
  @Nullable
  private String detailUrl;

  @Key("status")
  private Status status;

  @Key("conclusion")
  @Nullable
  private Conclusion conclusion;

  @Key
  private GitHubApp app;

  @Nullable
  public String getDetailUrl() {
    return detailUrl;
  }

  public Status getStatus() {
    return status;
  }

  @Nullable
  public Conclusion getConclusion() {
    return conclusion;
  }

  public GitHubApp getApp() {
    return app;
  }

  /**
   * Status of a check run
   */
  public enum Status {
    @Value("queued") QUEUED,
    @Value("in_progress") INPROGRESS,
    @Value("completed") COMPLETED;
  }

  /**
   * Conclusion of a check run status
   */
  public enum Conclusion {
    @Value("success") SUCCESS,
    @Value("failure") FAILURE,
    @Value("neutral") NEUTRAL,
    @Value("timed_out") TIMEDOUT,
    @Value("cancelled") CANCELLED,
    @Value("action_required") ACTIONREQUIRED;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("details_url", detailUrl)
        .add("status", status)
        .add("conclusion", conclusion)
        .add("app", app)
        .toString();
  }

}
