/*
 * Copyright (C) 2017 Google Inc.
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

/**
 * Represents a pull request review element returned by
 * https://api.github.com/repos/REPO_ID/pulls/NUMBER/reviews
 */
public class Review {

  @Key
  private long id;

  @Key
  private User user;

  @Key
  private String body;

  @Key("commit_id")
  private String commitId;

  @Key
  private String state;

  @Key("author_association")
  private String authorAssociation;

  public long getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getBody() {
    return body;
  }

  public String getCommitId() {
    return commitId;
  }

  public String getState() {
    return state;
  }

  public boolean isApproved() {
    return "APPROVED".equals(getState());
  }

  public AuthorAssociation getAuthorAssociation() {
    return authorAssociation == null
        ? AuthorAssociation.NONE
        : AuthorAssociation.valueOf(authorAssociation);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", id)
        .add("user", user)
        .add("body", body)
        .add("commitId", commitId)
        .add("state", state)
        .toString();
  }
}
