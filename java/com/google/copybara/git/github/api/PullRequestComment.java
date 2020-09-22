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
import com.google.common.base.MoreObjects;
import java.time.ZonedDateTime;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/**
 * Represents pull request comments returned by:
 *
 * <ul>
 *   <li>https://api.github.com/repos/REPO_ID/pulls/PR_NUMBER/comments
 *   <li>https://api.github.com/repos/REPO_ID/pulls/comments/COMMENT_ID
 * </ul>
 */
@StarlarkBuiltin(
    name = "github_api_pull_request_comment_obj",
    doc =
        "Information about a pull request comment as defined in"
            + " https://developer.github.com/v3/pulls/comments/. This is a subset of the available"
            + " fields in GitHub")
public class PullRequestComment implements StarlarkValue {
  @Key private Long id;

  @Key("diff_hunk")
  private String diffHunk;

  @Key private String path;
  @Key private Integer position;

  @Key("original_position")
  private Integer originalPosition;

  @Key("commit_id")
  private String commitId;

  @Key("original_commit_id")
  private String originalCommitId;

  @Key private User user;
  @Key private String body;

  @Key("created_at")
  private String createdAt;

  @Key("updated_at")
  private String updatedAt;

  @StarlarkMethod(name = "id", doc = "Comment identifier", structField = true)
  public String getIdAsStr() {
    return Long.toString(id);
  }

  public long getId() {
    return id;
  }

  @StarlarkMethod(name = "user", doc = "The user who posted the comment", structField = true)
  public User getUser() {
    return user;
  }

  @StarlarkMethod(name = "body", doc = "Body of the comment", structField = true)
  public String getBody() {
    return body;
  }

  @StarlarkMethod(name = "position", doc = "Position of the comment", structField = true)
  public int getPosition() {
    return position;
  }

  @StarlarkMethod(
      name = "original_position",
      doc = "Original position of the comment",
      structField = true)
  public int getOriginalPosition() {
    return originalPosition;
  }

  public String getCommitId() {
    return commitId;
  }

  public String getOriginalCommitId() {
    return originalCommitId;
  }

  @StarlarkMethod(
      name = "diff_hunk",
      doc = "The diff hunk where the comment was posted",
      structField = true)
  public String getDiffHunk() {
    return diffHunk;
  }

  @StarlarkMethod(name = "path", doc = "The file path", structField = true)
  public String getPath() {
    return path;
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
        .add("createdAt", createdAt)
        .add("updatedAt", updatedAt)
        .toString();
  }
}
