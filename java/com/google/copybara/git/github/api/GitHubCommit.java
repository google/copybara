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
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/** Represents GitHub information for a commit */
@StarlarkBuiltin(
    name = "github_api_github_commit_obj",
    doc =
        "Information about a commit as defined in"
            + " https://developer.github.com/v3/git/commits/#get-a-commit."
            + " This is a subset of the available fields in GitHub")
public class GitHubCommit implements StarlarkValue {

  @Key private String sha;
  @Key private String url;
  @Key("html_url") private String htmlUrl;
  @Key private User author;
  @Key private User committer;
  @Key private Commit commit;

  @StarlarkMethod(name = "sha", doc = "SHA of the commit", structField = true)
  public String getSha() {
    return sha;
  }

  public String getUrl() {
    return url;
  }

  @StarlarkMethod(name = "html_url", doc = "GitHub url for the commit", structField = true)
  public String getHtmlUrl() {
    return htmlUrl;
  }

  @StarlarkMethod(name = "author", doc = "GitHub information about the author of the change",
      structField = true)
  public User getAuthor() {
    return author;
  }

  @StarlarkMethod(name = "committer",
      doc = "GitHub information about the committer of the change", structField = true)
  public User getCommitter() {
    return committer;
  }

  @StarlarkMethod(name = "commit", doc = "Information about the commit, like the message or"
      + " git commit author/committer",
      structField = true)
  public Commit getCommit() {
    return commit;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("sha", sha)
        .add("url", url)
        .add("html_url", htmlUrl)
        .add("author", author)
        .add("committer", committer)
        .add("commit", commit)
        .toString();
  }
}
