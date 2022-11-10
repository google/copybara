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

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;
import java.time.ZonedDateTime;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/** Represents issue comments returned by https://api.github.com/repos/REPO_ID/issues/comments */
@StarlarkBuiltin(
    name = "github_api_issue_comment_obj",
    doc =
        "Information about an issue comment as defined in"
            + " https://docs.github.com/en/rest/issues/comments. This is a subset of the available"
            + " fields in GitHub")
public class IssueComment implements StarlarkValue {
  @Key private long id;
  @Key private User user;
  @Key private String body;
  @Key("author_association") private String authorAssociation;
  @Key("created_at") private String createdAt;
  @Key("updated_at") private String updatedAt;

  public AuthorAssociation getAuthorAssociation() {
    return authorAssociation == null
        ? AuthorAssociation.NONE
        : AuthorAssociation.valueOf(authorAssociation);
  }

  @StarlarkMethod(name = "id", doc = "Comment identifier", structField = true)
  public long getId() {
    return id;
  }

  @StarlarkMethod(name = "user", doc = "Comment user", structField = true)
  public User getUser() {
    return user;
  }

  @StarlarkMethod(name = "body", doc = "Body of the comment", structField = true)
  public String getBody() {
    return body;
  }

  public ZonedDateTime getCreatedAt() {
    return ZonedDateTime.parse(createdAt);
  }

  public ZonedDateTime getUpdatedAt() {
    return ZonedDateTime.parse(updatedAt);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", id)
        .add("user", user)
        .add("body", body)
        .add("authorAssociation", authorAssociation)
        .add("createdAt", createdAt)
        .add("updatedAt", updatedAt)
        .toString();
  }
}
