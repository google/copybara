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
import com.google.common.collect.ImmutableList;
import com.google.copybara.git.githubapi.Status.State;
import java.util.List;

public class CombinedStatus {

  @Key private State state;
  @Key private String sha;

  @Key("total_count")
  private int totalCount;

  @Key private List<Status> statuses;

  public State getState() {
    return state;
  }

  public String getSha() {
    return sha;
  }

  public int getTotalCount() {
    return totalCount;
  }

  public ImmutableList<Status> getStatuses() {
    return ImmutableList.copyOf(statuses);
  }
}
