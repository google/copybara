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
import com.google.copybara.git.github.api.Status.State;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.StarlarkBuiltin;
import com.google.devtools.build.lib.skylarkinterface.StarlarkDocumentationCategory;
import com.google.devtools.build.lib.syntax.Sequence;
import com.google.devtools.build.lib.syntax.StarlarkList;
import com.google.devtools.build.lib.syntax.StarlarkValue;
import java.util.List;

/**
 * A combined commit status object
 *
 * <p>https://developer.github.com/v3/repos/statuses
 */
@StarlarkBuiltin(
    name = "github_api_combined_status_obj",
    category = StarlarkDocumentationCategory.BUILTIN,
    doc =
        "Combined Information about a commit status as defined in"
            + " https://developer.github.com/v3/repos/statuses. This is a subset of the available"
            + " fields in GitHub")
public class CombinedStatus implements StarlarkValue {

  @Key private State state;
  @Key private String sha;

  @Key("total_count")
  private int totalCount;

  @Key private List<Status> statuses;

  public State getState() {
    return state;
  }

  @SkylarkCallable(
      name = "state",
      doc = "The overall state of all statuses for a commit: success, failure, pending or error",
      structField = true
  )
  public String getStateForSkylark() {
    return state.toString().toLowerCase();
  }

  @SkylarkCallable(name = "sha", doc = "The SHA-1 of the commit", structField = true)
  public String getSha() {
    return sha;
  }

  @SkylarkCallable(name = "total_count", doc = "Total number of statuses", structField = true)
  public int getTotalCount() {
    return totalCount;
  }

  @SkylarkCallable(name = "statuses", doc = "List of statuses for the commit", structField = true)
  public Sequence<Status> getStatuses() {
    return StarlarkList.immutableCopyOf(statuses);
  }
}
