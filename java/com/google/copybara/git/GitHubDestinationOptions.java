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

package com.google.copybara.git;

import com.beust.jcommander.Parameter;
import com.google.copybara.Option;

/**
 * Options related to GitHub destination
 *
 * <p>Intentionally empty so that we have the necessary infrastructure when
 * we add github options.
 */
public final class GitHubDestinationOptions implements Option {

  static final String GITHUB_DESTINATION_PR_BRANCH = "--github-destination-pr-branch";

  @Parameter(names = GITHUB_DESTINATION_PR_BRANCH,
      description = "If set, uses this branch for creating the pull request instead of using a"
          + " generated one")
  public String destinationPrBranch = null;

  @Parameter(names = "--github-destination-pr-create",
      description = "If the pull request should be created", arity = 1)
  public boolean createPullRequest = true;

}
