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

package com.google.copybara.git;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.copybara.Option;

/**
 * Options related to GitLab destination
 *
 * <p>Intentionally empty so that we have the necessary infrastructure when
 * we add gitlab options.
 */
@Parameters(separators = "=")
public final class GitLabDestinationOptions implements Option {

  static final String GITLAB_DESTINATION_MR_BRANCH = "--gitlab-destination-mr-branch";

  @Parameter(names = GITLAB_DESTINATION_MR_BRANCH,
      description = "If set, uses this branch for creating the merge request instead of using a"
          + " generated one")
  public String destinationMrBranch = null;

  @Parameter(names = "--gitlab-destination-mr-create",
      description = "If the merge request should be created", arity = 1)
  public boolean createMergeRequest = true;

}
