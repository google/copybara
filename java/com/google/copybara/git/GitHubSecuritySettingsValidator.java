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

package com.google.copybara.git;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.copybara.approval.ChangeWithApprovals;
import com.google.copybara.approval.StatementPredicate;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.github.api.GitHubApi;
import com.google.copybara.git.github.api.GitHubApiException;
import com.google.copybara.git.github.api.GitHubApiException.ResponseCode;
import com.google.copybara.util.console.Console;

/** Provides Statement Predicates for GitHub Security related predicates */
public class GitHubSecuritySettingsValidator {
  private static final int ALL_STAR_APP_ID = 119816;
  public static final String ALL_STAR_PREDICATE_TYPE = "github.organization.all_star_installed";
  public static final String TWO_FACTOR_PREDICATE_TYPE =
      "github.organization.2FA_requirement_enabled";
  private final GitHubApi api;
  private final Console console;

  public GitHubSecuritySettingsValidator(GitHubApi api, Console console) {
    this.api = api;
    this.console = console;
  }

  /**
   * Provisions {@code StatementPredicate} that describes whether the origin GitHub repository has
   * two factor authentication enabled to {@code changes}. PreConditions: {@code changes} all
   * originate from the same GitHub Project
   *
   * @param changes the list of changes to apply {@code StatementPredicates} to
   * @param organization the github organization to check two factor authentication enforcement
   *     policy on
   */
  public ImmutableList<ChangeWithApprovals> mapTwoFactorAuth(
      ImmutableList<ChangeWithApprovals> changes, String organization)
      throws ValidationException, RepoException {
    if (changes.isEmpty()) {
      return ImmutableList.of();
    }
    if (!hasTwoFactorEnabled(organization, console)) {
      return changes;
    }
    return appendPredicateToAll(
        changes,
        new StatementPredicate(
            TWO_FACTOR_PREDICATE_TYPE,
            "Whether the organization that the change originated from has two factor authentication"
                + " requirement enabled.",
            Iterables.getLast(changes).getChange().getRevision().getUrl()));
  }

  /**
   * Provisions {@code StatementPredicate} that describes whether the origin GitHub repository has
   * AllStar installed to {@code changes}. PreConditions: {@code changes} all originate from the
   * same GitHub Project
   *
   * @param changes the list of changes to apply {@code StatementPredicates} to
   * @param organization the github organization to check AllStar installation presence for
   */
  public ImmutableList<ChangeWithApprovals> mapAllStar(
      ImmutableList<ChangeWithApprovals> changes, String organization)
      throws ValidationException, RepoException {
    if (changes.isEmpty()) {
      return ImmutableList.of();
    }
    if (!hasAllStar(organization)) {
      return changes;
    }
    return appendPredicateToAll(
        changes,
        new StatementPredicate(
            ALL_STAR_PREDICATE_TYPE,
            "Whether the organization that the change originated from has allstar" + " installed",
            Iterables.getLast(changes).getChange().getRevision().getUrl()));
  }

  private ImmutableList<ChangeWithApprovals> appendPredicateToAll(
      ImmutableList<ChangeWithApprovals> changes, StatementPredicate predicate) {
    ImmutableList.Builder<ChangeWithApprovals> builder = ImmutableList.builder();
    for (ChangeWithApprovals change : changes) {
      // predicates are aliased but that is okay because they are global descriptors of these
      // changes
      ChangeWithApprovals newChangeWithApprovals = change.addApprovals(ImmutableList.of(predicate));
      builder.add(newChangeWithApprovals);
    }
    return builder.build();
  }

  private boolean hasAllStar(String organization) throws ValidationException, RepoException {
    try {
      return api.getInstallations(organization).stream()
          .anyMatch(installation -> installation.getAppId() == ALL_STAR_APP_ID);
    } catch (GitHubApiException e) {
      throw handleGitHubException(
          e,
          "Confirming AllStar app installation",
          "Please review your copybara app permissions, this request requires admin:read"
              + " permissions.");
    }
  }

  private boolean hasTwoFactorEnabled(String organization, Console console)
      throws ValidationException, RepoException {
    try {
      // Note: twoFactorRequirementEnabled can also be null in the payload, this will not throw an
      // error, but is a sign the credential used lacks sufficient priviledges.
      Boolean twoFactorEnabled = api.getOrganization(organization).getTwoFactorRequirementEnabled();
      if (twoFactorEnabled == null) {
        console.warnFmt(
            "Copybara could not confirm that 2FA requirement is being enforced in the '%s' GitHub"
                + " organization, so it will be assumed as being not enforced."
                + " Please confirm Copybara is given admin:org permissions with your GitHub org"
                + " admins and try again.",
            organization);
        return false;
      }
      return twoFactorEnabled;
    } catch (GitHubApiException e) {
      throw handleGitHubException(e, "Confirm organizational enforcement of 2FA", "");
    }
  }

  /**
   * Wraps GitHubException {@code e} as a user error if it turns out the GitHub response code is a
   * user issue. Otherwise, throw as is.
   */
  private RepoException handleGitHubException(
      GitHubApiException e, String operationAttempted, String userRecourse)
      throws ValidationException, RepoException {
    if (ImmutableList.of(ResponseCode.NOT_FOUND, ResponseCode.FORBIDDEN, ResponseCode.UNAUTHORIZED)
        .contains(e.getResponseCode())) {
      String userRecourseIfAny =
          !Strings.isNullOrEmpty(userRecourse)
              ? String.format("Possible user recourse: '%s'.", userRecourse)
              : "";
      throw new ValidationException(
          String.format(
              "Encountered user error while attempting to '%s'."
                  + " With Github HTTP response code '%s'. %s",
              operationAttempted, e.getResponseCode(), userRecourseIfAny),
          e);
    }
    throw e;
  }
}
