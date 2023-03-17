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

package com.google.copybara.git;

import static com.google.copybara.config.SkylarkUtil.convertFromNoneable;
import static com.google.copybara.config.SkylarkUtil.stringToEnum;
import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.Endpoint;
import com.google.copybara.LazyResourceLoader;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.github.api.CheckRun;
import com.google.copybara.git.github.api.CombinedStatus;
import com.google.copybara.git.github.api.CreateStatusRequest;
import com.google.copybara.git.github.api.GitHubApi;
import com.google.copybara.git.github.api.GitHubApi.PullRequestListParams;
import com.google.copybara.git.github.api.GitHubApi.PullRequestListParams.DirectionFilter;
import com.google.copybara.git.github.api.GitHubApi.PullRequestListParams.SortFilter;
import com.google.copybara.git.github.api.GitHubApi.PullRequestListParams.StateFilter;
import com.google.copybara.git.github.api.GitHubApiException;
import com.google.copybara.git.github.api.GitHubApiException.ResponseCode;
import com.google.copybara.git.github.api.GitHubCommit;
import com.google.copybara.git.github.api.Issue;
import com.google.copybara.git.github.api.Issue.CreateIssueRequest;
import com.google.copybara.git.github.api.IssueComment;
import com.google.copybara.git.github.api.PullRequest;
import com.google.copybara.git.github.api.PullRequestComment;
import com.google.copybara.git.github.api.Ref;
import com.google.copybara.git.github.api.Status;
import com.google.copybara.git.github.api.Status.State;
import com.google.copybara.git.github.api.UpdatePullRequest;
import com.google.copybara.git.github.api.UpdateReferenceRequest;
import com.google.copybara.git.github.api.User;
import com.google.copybara.git.github.util.GitHubHost;
import com.google.copybara.util.console.Console;
import com.google.re2j.Pattern;
import javax.annotation.Nullable;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

/** GitHub specific class used in feedback mechanism and migration event hooks to access GitHub */
@SuppressWarnings("unused")
@StarlarkBuiltin(
    name = "github_api_obj",
    doc = "GitHub API endpoint implementation for feedback migrations and after migration hooks.")
public class GitHubEndPoint implements Endpoint, StarlarkValue {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LazyResourceLoader<GitHubApi> apiSupplier;
  private final String url;
  private final Console console;
  private GitHubHost ghHost;
  // This might not be complete but it is only used for filtering get_pull_requests. We can
  // add more chars on demand.
  private static final Pattern SAFE_BRANCH_NAME_PREFIX = Pattern.compile("[\\w_.-][\\w/_.-]*");

  GitHubEndPoint(
      LazyResourceLoader<GitHubApi> apiSupplier, String url, Console console, GitHubHost ghHost) {
    this.apiSupplier = Preconditions.checkNotNull(apiSupplier);
    this.url = Preconditions.checkNotNull(url);
    this.console = Preconditions.checkNotNull(console);
    this.ghHost = ghHost;
  }

  @StarlarkMethod(
      name = "create_status",
      doc = "Create or update a status for a commit. Returns the status created.",
      parameters = {
        @Param(
            name = "sha",
            named = true,
            doc = "The SHA-1 for which we want to create or update the status"),
        @Param(
            name = "state",
            named = true,
            doc = "The state of the commit status: 'success', 'error', 'pending' or 'failure'"),
        @Param(
            name = "context",
            doc =
                "The context for the commit status. Use a value like 'copybara/import_successful'"
                    + " or similar",
            named = true),
        @Param(name = "description", named = true, doc = "Description about what happened"),
        @Param(
            name = "target_url",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            doc = "Url with expanded information about the event",
            defaultValue = "None"),
      }
  )
  public Status createStatus(
      String sha, String state, String context, String description, Object targetUrl)
      throws EvalException, RepoException, ValidationException {
    try {
      checkCondition(State.VALID_VALUES.contains(state),
          "Invalid value for state. Valid values: %s", State.VALID_VALUES);
      checkCondition(GitRevision.COMPLETE_SHA1_PATTERN.matcher(sha).matches(),
          "Not a valid complete SHA-1: %s", sha);
      checkCondition(!Strings.isNullOrEmpty(description), "description cannot be empty");
      checkCondition(!Strings.isNullOrEmpty(context), "context cannot be empty");

      String project = ghHost.getProjectNameFromUrl(url);
      return apiSupplier.load(console).createStatus(
          project, sha, new CreateStatusRequest(State.valueOf(state.toUpperCase()),
              convertFromNoneable(targetUrl, null),
              description, context));
    } catch (GitHubApiException gae) {
      if (gae.getResponseCode() == ResponseCode.UNPROCESSABLE_ENTITY) {
        throw new ValidationException(
            "GitHub was unable to process the request " + gae.getError(), gae);
      }
      throw gae;
    } catch (ValidationException | RuntimeException e) {
      throw Starlark.errorf("Error calling create_status: %s", e.getMessage());
    }
  }

  @StarlarkMethod(
      name = "get_check_runs",
      doc =
          "Get the list of check runs for a sha. "
              + "https://developer.github.com/v3/checks/runs/#check-runs",
      parameters = {
        @Param(
            name = "sha",
            named = true,
            doc = "The SHA-1 for which we want to get the check runs"),
      })
  public ImmutableList<CheckRun> getCheckRuns(String sha) throws EvalException, RepoException {
    try {
      checkCondition(GitRevision.COMPLETE_SHA1_PATTERN.matcher(sha).matches(),
          "Not a valid complete SHA-1: %s", sha);
      String project = ghHost.getProjectNameFromUrl(url);
      return apiSupplier.load(console).getCheckRuns(project, sha);
    } catch (ValidationException | RuntimeException e) {
      throw Starlark.errorf("Error calling get_check_runs: %s", e.getMessage());
    }
  }

  @StarlarkMethod(
      name = "get_combined_status",
      doc = "Get the combined status for a commit. Returns None if not found.",
      parameters = {
        @Param(
            name = "ref",
            named = true,
            doc = "The SHA-1 or ref for which we want to get the combined status"),
      },
      allowReturnNones = true)
  @Nullable
  public CombinedStatus getCombinedStatus(String ref) throws EvalException, RepoException {
    try {
      checkCondition(!Strings.isNullOrEmpty(ref), "Empty reference not allowed");
      String project = ghHost.getProjectNameFromUrl(url);
      return apiSupplier.load(console).getCombinedStatus(project, ref);
    } catch (GitHubApiException e) {
      return returnNullOnNotFound(e);
    } catch (ValidationException | RuntimeException e) {
      throw Starlark.errorf("Error calling get_combined_status: %s", e);
    }
  }

  @StarlarkMethod(
      name = "get_commit",
      doc = "Get information for a commit in GitHub. Returns None if not found.",
      parameters = {
        @Param(
            name = "ref",
            named = true,
            // Works for refs too but we don't want to publicize since GH API docs refers to sha
            doc = "The SHA-1 for which we want to get the combined status"),
      },
      allowReturnNones = true)
  @Nullable
  public GitHubCommit getCommit(String ref) throws EvalException, RepoException {
    try {
      checkCondition(!Strings.isNullOrEmpty(ref), "Empty reference not allowed");
      String project = ghHost.getProjectNameFromUrl(url);
      return apiSupplier.load(console).getCommit(project, ref);
    } catch (GitHubApiException e) {
      return returnNullOnNotFound(e);
    } catch (ValidationException | RuntimeException e) {
      throw Starlark.errorf("Error calling get_commit: %s", e);
    }
  }

  @StarlarkMethod(
      name = "update_reference",
      doc = "Update a reference to point to a new commit. Returns the info of the reference.",
      parameters = {
        @Param(name = "ref", named = true, doc = "The name of the reference."),
        @Param(name = "sha", doc = "The id for the commit" + " status.", named = true),
        @Param(
            name = "force",
            named = true,
            doc =
                "Indicates whether to force the update or to make sure the update is a"
                    + " fast-forward update. Leaving this out or setting it to false will make"
                    + " sure you're not overwriting work. Default: false")
      })
  public Ref updateReference(String sha, String ref, boolean force)
      throws EvalException, RepoException {
    try {
      checkCondition(GitRevision.COMPLETE_SHA1_PATTERN.matcher(sha).matches(),
          "Not a valid complete SHA-1: %s", sha);
      checkCondition(!Strings.isNullOrEmpty(ref), "ref cannot be empty");

      if (!ref.startsWith("refs/")) {
        // TODO(malcon): Remove this functionality and use a check once library migrated.
        console.warnFmt(
            "Non-complete ref passed to update_reference '%s'. Assuming refs/heads/%s", ref, ref);
        ref = "refs/heads/" + ref;
      }
      String project = ghHost.getProjectNameFromUrl(url);
      return apiSupplier.load(console).updateReference(
          project, ref, new UpdateReferenceRequest(sha, force));
    } catch (ValidationException | RuntimeException e) {
      throw Starlark.errorf("Error calling update_reference: %s", e.getMessage());
    }
  }

  @StarlarkMethod(
      name = "delete_reference",
      doc = "Delete a reference.",
      parameters = {
        @Param(name = "ref", named = true, doc = "The name of the reference."),
      })
  public void deleteReference(String ref) throws EvalException, RepoException {
    try {
      checkCondition(!Strings.isNullOrEmpty(ref), "ref cannot be empty");
      checkCondition(ref.startsWith("refs/"), "ref needs to be a complete reference."
          + " Example: refs/heads/foo");

      String project = ghHost.getProjectNameFromUrl(url);
      apiSupplier.load(console).deleteReference(project, ref);
    } catch (ValidationException | RuntimeException e) {
      throw Starlark.errorf("Error calling delete_reference: %s", e.getMessage());
    }
  }

  @StarlarkMethod(
      name = "get_reference",
      doc = "Get a reference SHA-1 from GitHub. Returns None if not found.",
      parameters = {
        @Param(
            name = "ref",
            named = true,
            doc = "The name of the reference. For example: \"refs/heads/branchName\".")
      },
      allowReturnNones = true)
  @Nullable
  public Ref getReference(String ref) throws EvalException, RepoException {
    try {
      checkCondition(!Strings.isNullOrEmpty(ref), "Ref cannot be empty");

      String project = ghHost.getProjectNameFromUrl(url);
      return apiSupplier.load(console).getReference(project, ref);
    } catch (GitHubApiException e) {
      return returnNullOnNotFound(e);
    } catch (ValidationException | RuntimeException e) {
      throw Starlark.errorf("Error calling get_reference: %s", e.getMessage());
    }
  }

  @StarlarkMethod(
      name = "get_pull_requests",
      doc = "Get Pull Requests for a repo",
      parameters = {
        @Param(
            name = "head_prefix",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            doc = "Only return PRs wher the branch name has head_prefix",
            defaultValue = "None"),
        @Param(
            name = "base_prefix",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            doc = "Only return PRs where the destination branch name has base_prefix",
            defaultValue = "None"),
        @Param(
            name = "state",
            doc = "State of the Pull Request. Can be `\"OPEN\"`, `\"CLOSED\"` or `\"ALL\"`",
            defaultValue = "\"OPEN\"",
            named = true),
        @Param(
            name = "sort",
            doc =
                "Sort filter for retrieving the Pull Requests. Can be `\"CREATED\"`,"
                    + " `\"UPDATED\"` or `\"POPULARITY\"`",
            named = true,
            defaultValue = "\"CREATED\""),
        @Param(
            name = "direction",
            doc = "Direction of the filter. Can be `\"ASC\"` or `\"DESC\"`",
            defaultValue = "\"ASC\"",
            named = true)
      },
      allowReturnNones = true)
  @Nullable
  public ImmutableList<PullRequest> getPullRequests(
      Object headPrefixParam, Object basePrefixParam, String state, String sort, String direction)
      throws EvalException, RepoException {
    try {
      String project = ghHost.getProjectNameFromUrl(url);
      PullRequestListParams request = PullRequestListParams.DEFAULT;
      String headPrefix = convertFromNoneable(headPrefixParam, null);
      String basePrefix = convertFromNoneable(basePrefixParam, null);
      if (!Strings.isNullOrEmpty(headPrefix)) {
        checkCondition(SAFE_BRANCH_NAME_PREFIX.matches(headPrefix),
            "'%s' is not a valid head_prefix (%s is used for validation)",
            headPrefix, SAFE_BRANCH_NAME_PREFIX.pattern());
        request = request.withHead(headPrefix);
      }
      if (!Strings.isNullOrEmpty(basePrefix)) {
        checkCondition(SAFE_BRANCH_NAME_PREFIX.matches(basePrefix),
            "'%s' is not a valid base_prefix (%s is used for validation)",
            basePrefix, SAFE_BRANCH_NAME_PREFIX.pattern());
        request = request.withHead(basePrefix);
      }

      return apiSupplier
          .load(console)
          .getPullRequests(
              project,
              request
                  .withState(stringToEnum("state", state, StateFilter.class))
                  .withDirection(stringToEnum("direction", direction, DirectionFilter.class))
                  .withSort(stringToEnum("sort", sort, SortFilter.class)));
    } catch (GitHubApiException e) {
      return returnNullOnNotFound(e);
    } catch (ValidationException | RuntimeException e) {
      throw Starlark.errorf("Error calling get_pull_requests: %s", e.getMessage());
    }
  }

  @StarlarkMethod(
      name = "update_pull_request",
      doc = "Update Pull Requests for a repo. Returns None if not found",
      parameters = {
        @Param(name = "number", named = true, doc = "Pull Request number"),
        @Param(
            name = "title",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            doc = "New Pull Request title",
            defaultValue = "None"),
        @Param(
            name = "body",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            doc = "New Pull Request body",
            defaultValue = "None"),
        @Param(
            name = "state",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            doc = "State of the Pull Request. Can be `\"OPEN\"`, `\"CLOSED\"`",
            named = true,
            defaultValue = "None"),
      },
      allowReturnNones = true)
  @Nullable
  public PullRequest updatePullRequest(StarlarkInt number, Object title, Object body, Object state)
      throws EvalException, RepoException {
    try {
      String project = ghHost.getProjectNameFromUrl(url);

      return apiSupplier
          .load(console)
          .updatePullRequest(
              project,
              number.toInt("number"),
              new UpdatePullRequest(
                  convertFromNoneable(title, null),
                  convertFromNoneable(body, null),
                  stringToEnum(
                      "state", convertFromNoneable(state, null), UpdatePullRequest.State.class)));
    } catch (GitHubApiException e) {
      return returnNullOnNotFound(e);
    } catch (ValidationException | RuntimeException e) {
      throw Starlark.errorf("Error calling update_pull_request: %s", e.getMessage());
    }
  }

  @StarlarkMethod(
      name = "get_authenticated_user",
      doc = "Get autenticated user info, return null if not found",
      allowReturnNones = true)
  @Nullable
  public User getAuthenticatedUser() throws EvalException, RepoException {
    try {
      return apiSupplier.load(console).getAuthenticatedUser();
    } catch (GitHubApiException e) {
      return returnNullOnNotFoundOrUnauthorized(e);
    } catch (ValidationException | RuntimeException e) {
      throw Starlark.errorf("Error calling get_authenticated_user: %s", e.getMessage());
    }
  }

  @Nullable
  private <T> T returnNullOnNotFound(GitHubApiException e) throws EvalException {
    SkylarkUtil.check(e.getResponseCode() == ResponseCode.NOT_FOUND, "%s", e.getMessage());
    return null;
  }

  @Nullable
  private <T> T returnNullOnNotFoundOrUnauthorized(GitHubApiException e) throws EvalException {
    SkylarkUtil.check(e.getResponseCode() == ResponseCode.NOT_FOUND
            || e.getResponseCode() == ResponseCode.UNAUTHORIZED,
        "%s", e.getMessage());
    return null;
  }

  @StarlarkMethod(
      name = "get_references",
      doc =
          "Get all the reference SHA-1s from GitHub. Note that Copybara only returns a maximum "
              + "number of 500.")
  public Sequence<Ref> getReferences() throws EvalException, RepoException {
    try {
      String project = ghHost.getProjectNameFromUrl(url);
      return StarlarkList.immutableCopyOf(apiSupplier.load(console).getReferences(project));
    } catch (ValidationException | RuntimeException e) {
      throw Starlark.errorf("Error calling get_references: %s", e.getMessage());
    }
  }

  @StarlarkMethod(
      name = "get_pull_request_comment",
      doc = "Get a pull request comment",
      parameters = {
        @Param(name = "comment_id", named = true, doc = "Comment identifier"),
      })
  public PullRequestComment getPullRequestComment(String commentId)
      throws EvalException, RepoException {
    try {
      long commentIdLong;
      try {
        commentIdLong = Long.parseLong(commentId);
      } catch (NumberFormatException e) {
        throw Starlark.errorf("Invalid comment id %s: %s", commentId, e.getMessage());
      }
      String project = ghHost.getProjectNameFromUrl(url);
      return apiSupplier.load(console).getPullRequestComment(project, commentIdLong);
    } catch (ValidationException | RuntimeException e) {
      throw Starlark.errorf("Error calling get_pull_request_comment: %s", e.getMessage());
    }
  }

  @StarlarkMethod(
      name = "get_pull_request_comments",
      doc = "Get all pull request comments",
      parameters = {
        @Param(name = "number", named = true, doc = "Pull Request number"),
      })
  public Sequence<PullRequestComment> getPullRequestComments(StarlarkInt prNumber)
      throws EvalException, RepoException {
    try {
      String project = ghHost.getProjectNameFromUrl(url);
      return StarlarkList.immutableCopyOf(
          apiSupplier.load(console).getPullRequestComments(project, prNumber.toInt("number")));
    } catch (ValidationException | RuntimeException e) {
      throw Starlark.errorf("Error calling get_pull_request_comments: %s", e.getMessage());
    }
  }

  @StarlarkMethod(
      name = "url",
      doc = "Return the URL of this endpoint.",
      structField = true)
  @Override
  public String getUrl() {
    return url;
  }

  @StarlarkMethod(
      name = "add_label",
      doc = "Add labels to a PR/issue",
      parameters = {
        @Param(name = "number", named = true, doc = "Pull Request number"),
        @Param(
            name = "labels",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
            },
            named = true,
            doc = "List of labels to add."),
      })
  public void addLabels(StarlarkInt prNumber, Sequence<?> labels)
      throws EvalException, RepoException {
    try {
      String project = ghHost.getProjectNameFromUrl(url);
      // Swallow response, until a use-case for returning it surfaces.
      apiSupplier
          .load(console)
          .addLabels(
              project,
              prNumber.toInt("number"),
              SkylarkUtil.convertStringList(labels, "Expected list of GitHub label names."));
    } catch (ValidationException | RuntimeException e) {
      throw Starlark.errorf("Error calling add_label: %s", e.getMessage());
    }
  }

  @StarlarkMethod(
      name = "create_issue",
      doc = "Create a new issue.",
      parameters = {
          @Param(name = "title", named = true, doc = "Title of the issue"),
          @Param(name = "body", named = true, doc = "Body of the issue."),
          @Param(name = "assignees", named = true,
              doc = "GitHub users to whom the issue will be assigned."),
      })
  public Issue createIssue(String title, String body, StarlarkList<?> assignees)
      throws EvalException, RepoException {
    try {
      String project = ghHost.getProjectNameFromUrl(url);
      return apiSupplier.load(console)
          .createIssue(project, new CreateIssueRequest(title, body,
              SkylarkUtil.convertStringList(assignees,
                  "Expected assignees to be a list of string.")));
    } catch (ValidationException | RuntimeException e) {
      throw Starlark.errorf(
          "Error calling create_issue: %s - %s",
          e.getMessage(), Throwables.getRootCause(e).getMessage());
    }
  }

  @StarlarkMethod(
      name = "post_issue_comment",
      doc = "Post a comment on a issue.",
      parameters = {
        @Param(name = "number", named = true, doc = "Issue or Pull Request number"),
        @Param(name = "comment", named = true, doc = "Comment body to post."),
      })
  public void postIssueComment(StarlarkInt prNumber, String comment)
      throws EvalException, RepoException {
    try {
      String project = ghHost.getProjectNameFromUrl(url);
      apiSupplier.load(console).postComment(project, prNumber.toInt("number"), comment);
    } catch (ValidationException | RuntimeException e) {
      throw Starlark.errorf("Error calling post_issue_comment: %s", e.getMessage());
    }
  }

  @StarlarkMethod(
      name = "list_issue_comments",
      doc = "Lists comments for an issue",
      parameters = {
        @Param(name = "number", named = true, doc = "Issue or Pull Request number"),
      })
  public Sequence<IssueComment> listIssueComments(StarlarkInt issueNumber)
      throws EvalException, RepoException {
    try {
      String project = ghHost.getProjectNameFromUrl(url);
      return StarlarkList.immutableCopyOf(
          apiSupplier.load(console).listIssueComments(project, issueNumber.toInt("number")));
    } catch (ValidationException | RuntimeException e) {
      throw Starlark.errorf("Error calling list_issue_comments: %s", e.getMessage());
    }
  }

  @Override
  public GitHubEndPoint withConsole(Console console) {
    return new GitHubEndPoint(this.apiSupplier, this.url, console, ghHost);
  }

  @Override
  public ImmutableSetMultimap<String, String> describe() {
    return ImmutableSetMultimap.of("type", "github_api", "url", url);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("url", url)
        .toString();
  }
}
