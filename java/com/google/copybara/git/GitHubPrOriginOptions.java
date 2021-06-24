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
  public List<String> requiredLabels = new ArrayList<>();

  @Parameter(
      names = "--github-required-status-context-name",
      description =
          "Required status context names in the Pull Request to be imported by "
              + GitModule.GITHUB_PR_ORIGIN_NAME)
  public List<String> requiredStatusContextNames = new ArrayList<>();

  @Parameter(
      names = "--github-required-check-run",
      description =
          "Required check runs in the Pull Request to be imported by "
              + GitModule.GITHUB_PR_ORIGIN_NAME)
  public List<String> requiredCheckRuns = new ArrayList<>();

  @Parameter(names = "--github-retryable-label",
      description = "Required labels in the Pull Request that should be retryed to be imported by "
          + GitModule.GITHUB_PR_ORIGIN_NAME)
  public List<String> retryableLabels = new ArrayList<>();

  @Parameter(names = "--github-skip-required-labels", description = "Skip checking labels for"
      + " importing Pull Requests. Note that this is dangerous as it might import an unsafe PR.")
  public boolean skipRequiredLabels = false;

  @Parameter(
      names = "--github-skip-required-status-context-names",
      description =
          "Skip checking status context names for importing Pull Requests. Note that this is"
              + " dangerous as it might import an unsafe PR.")
  public boolean skipRequiredStatusContextNames = false;

  @Parameter(
      names = "--github-skip-required-check-runs",
      description =
          "Skip checking check runs for importing Pull Requests. Note that this is dangerous as it"
              + " might import an unsafe PR.")
  public boolean skipRequiredCheckRuns = false;

  @Parameter(names = "--github-force-import", description = "Force import regardless of the state"
      + " of the PR")
  public boolean forceImport = false;

  @Parameter(names = "--github-pr-merge", description = "Override merge bit from config", arity = 1)
  public Boolean overrideMerge = null;

  /**
   * Compute the labels that should be required by git.github_pr_origin for importing a Pull
   * Request.
   *
   * <p>This method might be overridden to provide custom behavior.
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
   * Compute the status context names that should be required by git.github_pr_origin for importing
   * a Pull Request.
   *
   * <p>This method might be overridden to provide custom behavior.
   */
  public Set<String> getRequiredStatusContextNames(Iterable<String> configStatusContextNames) {
    if (skipRequiredStatusContextNames) {
      return ImmutableSet.of();
    } else if (requiredStatusContextNames.isEmpty()) {
      return ImmutableSet.copyOf(configStatusContextNames);
    }
    return ImmutableSet.copyOf(requiredStatusContextNames);
  }

  /**
   * Compute the check runs that should be required by git.github_pr_origin for importing a Pull
   * Request.
   *
   * <p>This method might be overridden to provide custom behavior.
   */
  public Set<String> getRequiredCheckRuns(Iterable<String> configCheckRuns) {
    if (skipRequiredCheckRuns) {
      return ImmutableSet.of();
    } else if (requiredCheckRuns.isEmpty()) {
      return ImmutableSet.copyOf(configCheckRuns);
    }
    return ImmutableSet.copyOf(requiredCheckRuns);
  }

  /**
   * Compute the labels that should be retried by git.github_pr_origin for importing a Pull Request.
   *
   * <p>This method might be overridden to provide custom behavior.
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
