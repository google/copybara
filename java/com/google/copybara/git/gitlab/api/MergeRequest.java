/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.git.gitlab.api;

import com.google.api.client.util.Key;

public class MergeRequest {
  @Key
  private String title;
  @Key
  private String description;
  @Key("target_branch")
   String targetBranch;
  @Key("source_branch")
  private String sourceBranch;
  @Key
  private String state;
  @Key
  private long iid;
  @Key
  private String sha;

  public String getTargetBranch() {
    return targetBranch;
  }

  public String getSourceBranch() {
    return sourceBranch;
  }

  public boolean isOpen() {
    return state.equals("opened");
  }

  public long getNumber() {
    return iid;
  }

  public String getHtmlUrl() {
    return "";
  }

  public String getSha() {
    return sha;
  }

}
