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

package com.google.copybara.git.github.util;

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.copybara.exception.ValidationException;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.Optional;

/**
 * General utilities for manipulating GitHub urls and data
 */
public class GitHubUtil {

  private GitHubUtil() {}

  /**
   * Returns a valid branch name by replacing invalid character with "_"
   * throw ValidationException when branchName starts with "/" or "refs/"
   */
  public static String getValidBranchName(String branchName)
      throws ValidationException {
    checkCondition(!branchName.startsWith("/") && !branchName.startsWith("refs/"),
        "Branch name has invalid prefix: \"/\" or \"refs/\"");
    return branchName.replaceAll("[^A-Za-z0-9/_-]", "_");
  }

  /**
   * The variable name of the list of the required status context names.
   */
  public static final String REQUIRED_STATUS_CONTEXT_NAMES = "required_status_context_names";

  /**
   * The variable name of the list of the required check runs.
   */
  public static final String REQUIRED_CHECK_RUNS = "required_check_runs";

  /**
   * The variable name of the list of the required labels.
   */
  public static final String REQUIRED_LABELS = "required_labels";

  /**
   * The variable name of the list of the retryable labels.
   */
  public static final String RETRYABLE_LABELS = "retryable_labels";

  private static final Pattern GITHUB_PULL_REQUEST_REF =
      Pattern.compile("refs/pull/([0-9]+)/(head|merge)");

  /**
   * Given a ref like 'refs/pull/12345/head' returns 12345 or null it not a GitHub PR ref
   */
  public static Optional<Integer> maybeParseGithubPrFromHeadRef(String ref) {
    Matcher matcher = GITHUB_PULL_REQUEST_REF.matcher(ref);
    return (matcher.matches() && "head".equals(matcher.group(2)))
           ? Optional.of(Integer.parseInt(matcher.group(1)))
           : Optional.empty();
  }

  /**
   * Given a ref like 'refs/pull/12345/merge' returns 12345 or null it not a GitHub PR ref
   */
  public static Optional<Integer> maybeParseGithubPrFromMergeOrHeadRef(String ref) {
    Matcher matcher = GITHUB_PULL_REQUEST_REF.matcher(ref);
    return matcher.matches() ? Optional.of(Integer.parseInt(matcher.group(1))) : Optional.empty();
  }

  /**
   * Given a prNumber return a git reference like 'refs/pull/12345/head'
   */
  public static String asHeadRef(int prNumber) {
    return "refs/pull/" + prNumber + "/head";
  }

  /**
   * Given a prNumber return a git reference like 'refs/pull/12345/merge'
   */
  public static String asMergeRef(int prNumber) {
    return "refs/pull/" + prNumber + "/merge";
  }

}
