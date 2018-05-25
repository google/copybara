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
import com.google.common.collect.ImmutableSet;
import com.google.copybara.Option;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Options related to GitHub destination
 *
 * <p>Intentionally empty so that we have the necessary infrastructure when
 * we add github options.
 */
public class GitHubPrOriginOptions implements Option {

  @Parameter(names = "--github-required-label",
      description = "Required labels in the Pull Request to be imported by "
          + GitModule.GITHUB_PR_ORIGIN_NAME)
  public List<String> requiredLabels= new ArrayList<>();

  @Parameter(names = "--github-retryable-label",
      description = "Required labels in the Pull Request that should be retryed to be imported by "
          + GitModule.GITHUB_PR_ORIGIN_NAME)
  public List<String> retryableLabels= new ArrayList<>();

  @Parameter(names = "--github-skip-required-labels", description = "Skip checking labels for"
      + " importing Pull Requests. Note that this is dangerous as it might import an unsafe PR.")
  public boolean skipRequiredLabels = false;

  /**
   * Compute the labels that should be required by git.github_pr_origin for importing a
   * Pull Request.
   *
   * <p>This method might be overwritten to provide custom behavior.
   */
  public Set<String> getRequiredLabels(Iterable<String> configLabels) {
    if (skipRequiredLabels) {
      return ImmutableSet.of();
    } else if (requiredLabels.isEmpty()) {
      return ImmutableSet.copyOf(configLabels);
    }
    return ImmutableSet.copyOf(requiredLabels);
  }

  /**
   * Compute the labels that should be retried by git.github_pr_origin for importing a
   * Pull Request.
   *
   * <p>This method might be overwritten to provide custom behavior.
   */
  public Set<String> getRetryableLabels(Iterable<String> configLabels) {
    if (skipRequiredLabels) {
      return ImmutableSet.of();
    } else if (retryableLabels.isEmpty()) {
      return ImmutableSet.copyOf(configLabels);
    }
    return ImmutableSet.copyOf(retryableLabels);
  }

}
