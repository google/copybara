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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Endpoint;
import com.google.copybara.LazyResourceLoader;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
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
import com.google.copybara.git.github.api.PullRequest;
import com.google.copybara.git.github.api.PullRequestComment;
import com.google.copybara.git.github.api.Ref;
import com.google.copybara.git.github.api.Status;
import com.google.copybara.git.github.api.Status.State;
import com.google.copybara.git.github.api.UpdatePullRequest;
import com.google.copybara.git.github.api.UpdateReferenceRequest;
import com.google.copybara.git.github.api.User;
import com.google.copybara.git.github.util.GitHubUtil;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.re2j.Pattern;
import javax.annotation.Nullable;

/** GitHub specific class used in feedback mechanism and migration event hooks to access GitHub */
@SuppressWarnings("unused")
@SkylarkModule(
  name = "github_api_obj",
  category = SkylarkModuleCategory.BUILTIN,
  doc = "GitHub API endpoint implementation for feedback migrations and after migration hooks."
)
public class GitHubEndPoint implements Endpoint {

  private final LazyResourceLoader<GitHubApi> apiSupplier;
  private final String url;
  private final Console console;
  // This might not be complete but it is only used for filtering get_pull_requests. We can
  // add more chars on demand.
  private static final Pattern SAFE_BRANCH_NAME_PREFIX = Pattern.compile("[\\w_.-][\\w/_.-]*");


  GitHubEndPoint(LazyResourceLoader<GitHubApi> apiSupplier, String url, Console console) {
    this.apiSupplier = Preconditions.checkNotNull(apiSupplier);
    this.url = Preconditions.checkNotNull(url);
    this.console = Preconditions.checkNotNull(console);
  }

  @SkylarkCallable(name = "create_status",
      doc = "Create or update a status for a commit. Returns the status created.",
      parameters = {
          @Param(name = "sha", type = String.class, named =  true,
              doc = "The SHA-1 for which we want to create or update the status"),
          @Param(name = "state", type = String.class, named =  true,
              doc = "The state of the commit status: 'success', 'error', 'pending' or 'failure'"),
          @Param(name = "context", type = String.class, doc = "The context for the commit"
              + " status. Use a value like 'copybara/import_successful' or similar",
              named =  true),
          @Param(name = "description", type = String.class, named = true,
              doc = "Description about what happened"),
          @Param(name = "target_url", type = String.class, named =  true,
              doc = "Url with expanded information about the event", noneable = true,
          defaultValue = "None"),
      },
      useLocation = true
  )
  public Status createStatus(
      String sha,
      String state,
      String context,
      String description,
      Object targetUrl,
      Location location) throws EvalException {
    try {
      checkCondition(State.VALID_VALUES.contains(state),
                     "Invalid value for state. Valid values: %s", State.VALID_VALUES);
      checkCondition(GitRevision.COMPLETE_SHA1_PATTERN.matcher(sha).matches(),
                     "Not a valid complete SHA-1: %s", sha);
      checkCondition(!Strings.isNullOrEmpty(description), "description cannot be empty");
      checkCondition(!Strings.isNullOrEmpty(context), "context cannot be empty");

      String project = GitHubUtil.getProjectNameFromUrl(url);
      return apiSupplier.load(console).createStatus(
          project, sha, new CreateStatusRequest(State.valueOf(state.toUpperCase()),
                                                 convertFromNoneable(targetUrl, null),
                                                 description, context));
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException(location, "Error calling create_status", e);
    }
  }

  @SkylarkCallable(name = "get_combined_status",
      doc = "Get the combined status for a commit. Returns None if not found.",
      parameters = {
          @Param(name = "ref", type = String.class, named = true,
              doc = "The SHA-1 or ref for which we want to get the combined status"),
      },
      useLocation = true, allowReturnNones = true)
  @Nullable
  public CombinedStatus getCombinedStatus(String ref, Location location) throws EvalException {
    try {
      checkCondition(!Strings.isNullOrEmpty(ref), "Empty reference not allowed");
      String project = GitHubUtil.getProjectNameFromUrl(url);
      return apiSupplier.load(console).getCombinedStatus(project, ref);
    } catch (GitHubApiException e) {
      return returnNullOnNotFound(location, e);
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException(location, "Error calling get_combined_status", e);
    }
  }

  @SkylarkCallable(name = "get_commit",
      doc = "Get information for a commit in GitHub. Returns None if not found.",
      parameters = {
          @Param(name = "ref", type = String.class, named = true,
              // Works for refs too but we don't want to publicize since GH API docs refers to sha
              doc = "The SHA-1 for which we want to get the combined status"),
      },
      useLocation = true, allowReturnNones = true
  )
  @Nullable
  public GitHubCommit getCommit(String ref, Location location) throws EvalException {
    try {
      checkCondition(!Strings.isNullOrEmpty(ref), "Empty reference not allowed");
      String project = GitHubUtil.getProjectNameFromUrl(url);
      return apiSupplier.load(console).getCommit(project, ref);
    } catch (GitHubApiException e) {
      return returnNullOnNotFound(location, e);
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException(location, "Error calling get_commit", e);
    }
  }

  @SkylarkCallable(
      name = "update_reference",
      doc = "Update a reference to point to a new commit. Returns the info of the reference.",
      parameters = {
        @Param(name = "ref", type = String.class, named = true, doc = "The name of the reference."),
        @Param(
            name = "sha",
            type = String.class,
            doc = "The id for the commit" + " status.",
            named = true),
        @Param(
            name = "force",
            type = Boolean.class,
            named = true,
            doc =
                "Indicates whether to force the update or to make sure the update is a"
                    + " fast-forward update. Leaving this out or setting it to false will make"
                    + " sure you're not overwriting work. Default: false")
      },
      useLocation = true)
  public Ref updateReference(String sha, String ref, boolean force, Location location)
      throws EvalException {
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
      String project = GitHubUtil.getProjectNameFromUrl(url);
      return apiSupplier.load(console).updateReference(
          project, ref, new UpdateReferenceRequest(sha, force));
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException(location, "Error calling update_reference", e);
    }
  }

  @SkylarkCallable(name = "delete_reference",
      doc = "Delete a reference.",
      parameters = {
          @Param(name = "ref", type = String.class, named = true,
              doc = "The name of the reference."),
      },
      useLocation = true
  )
  public void deleteReference(String ref, Location location)
      throws EvalException {
    try {
      checkCondition(!Strings.isNullOrEmpty(ref), "ref cannot be empty");
      checkCondition(ref.startsWith("refs/"), "ref needs to be a complete reference."
          + " Example: refs/heads/foo");

      String project = GitHubUtil.getProjectNameFromUrl(url);
      apiSupplier.load(console).deleteReference(project, ref);
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException(location, "Error calling delete_reference", e);
    }
  }

  @SkylarkCallable(name = "get_reference",
      doc = "Get a reference SHA-1 from GitHub. Returns None if not found.",
      parameters = {
          @Param(name = "ref", type = String.class, named =  true,
              doc = "The name of the reference. For example: \"refs/heads/branchName\".")
      },
      useLocation = true, allowReturnNones = true)
  @Nullable
  public Ref getReference(String ref, Location location) throws EvalException {
    try {
      checkCondition(!Strings.isNullOrEmpty(ref), "Ref cannot be empty");

      String project = GitHubUtil.getProjectNameFromUrl(url);
      return apiSupplier.load(console).getReference(project, ref);
    } catch (GitHubApiException e) {
      return returnNullOnNotFound(location, e);
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException(location, "Error calling get_reference", e);
    }
  }

  @SkylarkCallable(name = "get_pull_requests",
      doc = "Get Pull Requests for a repo",
      parameters = {
          @Param(name = "head_prefix", type = String.class, named = true,
              doc = "Only return PRs wher the branch name has head_prefix",
              defaultValue = "None", noneable = true),
          @Param(name = "base_prefix", type = String.class, named = true,
              doc = "Only return PRs where the destination branch name has base_prefix",
              defaultValue = "None", noneable = true),
          @Param(name = "state", type = String.class,
              doc = "State of the Pull Request. Can be `\"OPEN\"`, `\"CLOSED\"` or `\"ALL\"`",
              defaultValue = "\"OPEN\"", named = true),
          @Param(name = "sort", type = String.class,
              doc = "Sort filter for retrieving the Pull Requests. Can be `\"CREATED\"`,"
                  + " `\"UPDATED\"` or `\"POPULARITY\"`", named = true,
              defaultValue = "\"CREATED\""),
          @Param(name = "direction", type = String.class,
              doc = "Direction of the filter. Can be `\"ASC\"` or `\"DESC\"`",
              defaultValue = "\"ASC\"", named = true)
      },
      useLocation = true, allowReturnNones = true)
  @Nullable
  public ImmutableList<PullRequest> getPullRequests(Object headPrefixParam, Object basePrefixParam,
      String state, String sort, String direction, Location location) throws EvalException {
    try {
      String project = GitHubUtil.getProjectNameFromUrl(url);
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

      return apiSupplier.load(console).getPullRequests(project,
          request.withState(stringToEnum(location, "state", state, StateFilter.class))
              .withDirection(stringToEnum(location, "direction", direction, DirectionFilter.class))
              .withSort(stringToEnum(location, "sort", sort, SortFilter.class)));
    } catch (GitHubApiException e) {
      return returnNullOnNotFound(location, e);
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException(location, "Error calling get_pull_requests", e);
    }
  }

  @SkylarkCallable(name = "update_pull_request",
      doc = "Update Pull Requests for a repo. Returns None if not found",
      parameters = {
          @Param(name = "number", type = Integer.class, named = true,
              doc = "Pull Request number"),
          @Param(name = "title", type = String.class, named = true,
              doc = "New Pull Request title",
              defaultValue = "None", noneable = true),
          @Param(name = "body", type = String.class, named = true,
              doc = "New Pull Request body",
              defaultValue = "None", noneable = true),
          @Param(name = "state", type = String.class,
              doc = "State of the Pull Request. Can be `\"OPEN\"`, `\"CLOSED\"`", named = true,
              noneable = true, defaultValue = "None"),
      },
      useLocation = true, allowReturnNones = true)
  @Nullable
  public PullRequest updatePullRequest(
      Integer number, Object title, Object body, Object state, Location location)
      throws EvalException {
    try {
      String project = GitHubUtil.getProjectNameFromUrl(url);

      return apiSupplier.load(console).updatePullRequest(project, number,
          new UpdatePullRequest(
              convertFromNoneable(title, null),
              convertFromNoneable(body, null),
              stringToEnum(location, "state",
                  convertFromNoneable(state, null), UpdatePullRequest.State.class)));
    } catch (GitHubApiException e) {
      return returnNullOnNotFound(location, e);
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException(location, "Error calling update_pull_request", e);
    }
  }

  @SkylarkCallable(name = "get_authenticated_user",
      doc = "Get autenticated user info, return null if not found",
      useLocation = true, allowReturnNones = true)
  @Nullable
  public User getAuthenticatedUser(Location location)
      throws EvalException {
    try {
      return apiSupplier.load(console).getAuthenticatedUser();
    } catch (GitHubApiException e) {
      return returnNullOnNotFound(location, e);
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException(location, "Error calling get_authenticated_user", e);
    }
  }

  @Nullable
  private <T> T returnNullOnNotFound(Location location, GitHubApiException e)
      throws EvalException {
    SkylarkUtil.check(location, e.getResponseCode() == ResponseCode.NOT_FOUND, e.getMessage());
    return null;
  }

  @SkylarkCallable(
      name = "get_references",
      doc = "Get all the reference SHA-1s from GitHub. Note that Copybara only returns a maximum "
          + "number of 500.",
      useLocation = true
  )
  public SkylarkList<Ref> getReferences(Location location) throws EvalException {
    try {
      String project = GitHubUtil.getProjectNameFromUrl(url);
      return SkylarkList.createImmutable(apiSupplier.load(console).getReferences(project));
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException(location, "Error calling get_references", e);
    }
  }

  @SkylarkCallable(
      name = "get_pull_request_comment",
      doc = "Get a pull request comment",
      parameters = {
        @Param(name = "comment_id", type = String.class, named = true, doc = "Comment identifier"),
      },
      useLocation = true)
  public PullRequestComment getPullRequestComment(String commentId, Location location)
      throws EvalException {
    try {
      long commentIdLong;
      try {
        commentIdLong = Long.parseLong(commentId);
      } catch (NumberFormatException e) {
        throw new EvalException(location, "Invalid comment id " + commentId, e);
      }
      String project = GitHubUtil.getProjectNameFromUrl(url);
      return apiSupplier.load(console).getPullRequestComment(project, commentIdLong);
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException(location, "Error calling get_pull_request_comment", e);
    }
  }

  @SkylarkCallable(
      name = "get_pull_request_comments",
      doc = "Get all pull request comments",
      parameters = {
        @Param(name = "number", type = Integer.class, named = true, doc = "Pull Request number"),
      },
      useLocation = true)
  public SkylarkList<PullRequestComment> getPullRequestComments(Integer prNumber, Location location)
      throws EvalException {
    try {
      String project = GitHubUtil.getProjectNameFromUrl(url);
      return SkylarkList.createImmutable(
          apiSupplier.load(console).getPullRequestComments(project, prNumber));
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException(location, "Error calling get_pull_request_comments", e);
    }
  }

  @SkylarkCallable(
      name = "url",
      doc = "Return the URL of this endpoint.",
      structField = true)
  public String getUrl() {
    return url;
  }

  @Override
  public GitHubEndPoint withConsole(Console console) {
    return new GitHubEndPoint(this.apiSupplier, this.url, console);
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
