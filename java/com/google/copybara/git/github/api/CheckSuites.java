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
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.StarlarkValue;

/**
 * Represents a GitHub App's checkSuites response detail.
 * https://docs.github.com/en/rest/checks/suites?apiVersion=2022-11-28#list-check-suites-for-a-git-reference
 */
@StarlarkBuiltin(
    name = "github_check_suites_response_obj",
    doc =
        "Detail about a check run as defined in "
            + "https://docs.github.com/en/rest/checks/suites?apiVersion=2022-11-28#list-check-suites-for-a-git-reference")
public class CheckSuites implements StarlarkValue, PaginatedPayload<CheckSuite> {

  @SuppressWarnings("unused")
  public CheckSuites() {
  }

  private CheckSuites(int totalCount, PaginatedList<CheckSuite> checkSuites) {
    this.checkSuites = checkSuites;
    this.totalCount = totalCount;
  }

  @Key("total_count")
  private int totalCount;

  @Key("check_suites")
  private PaginatedList<CheckSuite> checkSuites;

  @Override
  public PaginatedList<CheckSuite> getPayload() {
    return checkSuites;
  }

  @Override
  public PaginatedPayload<CheckSuite> annotatePayload(String apiPrefix,
      @Nullable String linkHeader) {
    return new CheckSuites(totalCount, checkSuites.withPaginationInfo(apiPrefix, linkHeader));
  }
}
