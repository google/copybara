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

/** Represents the current status of a ref, as returned by the git/refs API call */
@StarlarkBuiltin(
    name = "github_api_commit_obj",
    doc =
        "Commit field for GitHub commit information"
            + " https://developer.github.com/v3/git/commits/#get-a-commit."
            + " This is a subset of the available fields in GitHub")
public class Commit implements StarlarkValue {
  @Key private String message;
  @Key private CommitAuthor author;
  @Key private CommitAuthor committer;

  @StarlarkMethod(name = "message", doc = "Message of the commit", structField = true)
  public String getMessage() {
    return message;
  }

  @StarlarkMethod(name = "author", doc = "Author of the commit", structField = true)
  public CommitAuthor getAuthor() {
    return author;
  }

  @StarlarkMethod(name = "committer", doc = "Committer of the commit", structField = true)
  public CommitAuthor getCommitter() {
    return committer;
  }

  @StarlarkBuiltin(
      name = "github_api_commit_author_obj",
      doc =
          "Author/Committer for commit field for GitHub commit information"
              + " https://developer.github.com/v3/git/commits/#get-a-commit."
              + " This is a subset of the available fields in GitHub")
  public static class CommitAuthor implements StarlarkValue {
    @Key String name;
    @Key String email;
    @Key String date;

    public ZonedDateTime getDate() {
      return ZonedDateTime.parse(date);
    }

    @StarlarkMethod(name = "date", doc = "Date of the commit", structField = true)
    public String getDateForSkylark() {
      return date;
    }

    @StarlarkMethod(name = "name", doc = "Name of the author/committer", structField = true)
    public String getName() {
      return name;
    }

    @StarlarkMethod(name = "email", doc = "Email of the author/committer", structField = true)
    public String getEmail() {
      return email;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("name", name)
          .add("email", email)
          .add("date", date)
          .toString();
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("message", message)
        .add("author", author)
        .add("committer", committer)
        .toString();
  }
}
