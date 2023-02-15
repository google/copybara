/*
 * Copyright (C) 2023 Google Inc.
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
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkValue;

/**
 * Represents a GitHub App's checkRun detail.
 * https://docs.github.com/en/rest/checks/suites?apiVersion=2022-11-28#list-check-suites-for-a-git-reference
 */
@StarlarkBuiltin(
    name = "github_check_suite_obj",
    doc =
        "Detail about a check run as defined in "
            + "https://developer.github.com/v3/checks/runs/#create-a-check-run")
public class CheckSuite implements StarlarkValue {


  @Key("status")
  private CheckRun.Status status;

  @Key
  @Nullable
  private CheckRun.Conclusion conclusion;

  @Key("head_sha")
  private String sha;

  @Key private long id;
  @Key private GitHubApp app;

  @StarlarkMethod(
      name = "id",
      doc = "Check suite identifier",
      structField = true
  )
  public StarlarkInt getId() {
    return StarlarkInt.of(id);
  }

  @StarlarkMethod(
      name = "status",
      doc = "The current status of the check run. Can be one of queued, in_progress, pending,"
          + " or completed.",
      structField = true
  )
  public String getStatus() {
    return status.toString().toLowerCase();
  }

  @StarlarkMethod(
      name = "conclusion",
      doc = "The final conclusion of the check. Can be one of success, failure, neutral, "
          + "cancelled, timed_out, or action_required.",
      structField = true,
      allowReturnNones = true
  )
  @Nullable
  public String getConclusion() {
    return conclusion == null ? null : conclusion.toString().toLowerCase();
  }

  @StarlarkMethod(
      name = "sha",
      doc = "The SHA-1 the check run is based on",
      structField = true
  )
  public String getSha() {
    return sha;
  }

  @StarlarkMethod(
      name = "app",
      doc = "The detail of a GitHub App, such as id, slug, and name",
      structField = true
  )
  public GitHubApp getApp() {
    return app;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", id)
        .add("status", status)
        .add("conclusion", conclusion)
        .add("sha", sha)
        .add("app", app)
        .toString();
  }
}
