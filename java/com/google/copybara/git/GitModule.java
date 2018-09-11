/*
 * Copyright (C) 2016 Google Inc.
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

import static com.google.copybara.config.SkylarkUtil.checkNotEmpty;
import static com.google.copybara.config.SkylarkUtil.convertFromNoneable;
import static com.google.copybara.config.SkylarkUtil.stringToEnum;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_BASE_BRANCH;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_BASE_BRANCH_SHA1;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_PR_ASSIGNEE;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_PR_BODY;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_PR_HEAD_SHA;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_PR_REVIEWER_APPROVER;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_PR_REVIEWER_OTHER;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_PR_TITLE;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_PR_USER;
import static com.google.copybara.git.GitRepoType.GERRIT_CHANGE_DESCRIPTION_LABEL;
import static com.google.copybara.git.GitRepoType.GERRIT_CHANGE_ID_LABEL;
import static com.google.copybara.git.GitRepoType.GERRIT_CHANGE_NUMBER_LABEL;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Transformation;
import com.google.copybara.checks.Checker;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.GlobalMigrations;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.doc.annotations.DocDefault;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GerritDestination.ChangeIdPolicy;
import com.google.copybara.git.GitDestination.WriterImpl.DefaultWriteHook;
import com.google.copybara.git.GitHubPROrigin.ReviewState;
import com.google.copybara.git.GitHubPROrigin.StateFilter;
import com.google.copybara.git.GitIntegrateChanges.Strategy;
import com.google.copybara.git.GitOrigin.SubmoduleStrategy;
import com.google.copybara.git.gerritapi.SetReviewInput;
import com.google.copybara.git.github.api.AuthorAssociation;
import com.google.copybara.git.github.util.GitHubUtil;
import com.google.copybara.transform.patch.PatchTransformation;
import com.google.copybara.util.RepositoryUtil;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.EvalUtils;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.Runtime.NoneType;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Main module that groups all the functions that create Git origins and destinations.
 */
@SkylarkModule(
    name = "git",
    doc = "Set of functions to define Git origins and destinations.",
    category = SkylarkModuleCategory.BUILTIN)
@UsesFlags(GitOptions.class)
public class GitModule implements LabelsAwareModule {

  static final String DEFAULT_INTEGRATE_LABEL = "COPYBARA_INTEGRATE_REVIEW";
  static final SkylarkList<GitIntegrateChanges> DEFAULT_GIT_INTEGRATES =
      SkylarkList.createImmutable(ImmutableList.of(
          new GitIntegrateChanges(DEFAULT_INTEGRATE_LABEL,
              Strategy.FAKE_MERGE_AND_INCLUDE_FILES,
              /*ignoreErrors=*/true)));
  private static final String GERRIT_TRIGGER = "gerrit_trigger";
  private static final String GERRIT_API = "gerrit_api";
  private static final String GITHUB_TRIGGER = "github_trigger";
  private static final String GITHUB_API = "github_api";
  private static final String PATCH_FIELD = "patch";
  public static final String PATCH_FIELD_DESC =
      "Patch the checkout dir. The difference with `patch.apply` transformation is"
          + " that here we can apply it using three-way";

  protected final Options options;
  private ConfigFile<?> mainConfigFile;

  public GitModule(Options options) {
    this.options = Preconditions.checkNotNull(options);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(name = "origin",
      doc = "Defines a standard Git origin. For Git specific origins use: `github_origin` or "
          + "`gerrit_origin`.<br><br>"
          + "All the origins in this module accept several string formats as reference (When"
          + " copybara is called in the form of `copybara config workflow reference`):<br>"
          + "<ul>"
          + "<li>**Branch name:** For example `master`</li>"
          + "<li>**An arbitrary reference:** `refs/changes/20/50820/1`</li>"
          + "<li>**A SHA-1:** Note that it has to be reachable from the default refspec</li>"
          + "<li>**A Git repository URL and reference:** `http://github.com/foo master`</li>"
          + "<li>**A GitHub pull request URL:** `https://github.com/some_project/pull/1784`</li>"
          + "</ul><br>"
          + "So for example, Copybara can be invoked for a `git.origin` in the CLI as:<br>"
          + "`copybara copy.bara.sky my_workflow https://github.com/some_project/pull/1784`<br>"
          + "This will use the pull request as the origin URL and reference.",
      parameters = {
          @Param(name = "url", type = String.class, named = true,
              doc = "Indicates the URL of the git repository"),
          @Param(name = "ref", type = String.class, noneable = true, defaultValue = "None",
              named = true,
              doc = "Represents the default reference that will be used for reading the revision "
                  + "from the git repository. For example: 'master'"),
          @Param(name = "submodules", type = String.class, defaultValue = "'NO'", named = true,
              positional = false,
              doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
          @Param(name = "include_branch_commit_logs", type = Boolean.class, defaultValue = "False",
              named = true, positional = false,
              doc = "Whether to include raw logs of branch commits in the migrated change message."
                  + "WARNING: This field is deprecated in favor of 'first_parent' one."
                  + " This setting *only* affects merge commits."),
          @Param(name = "first_parent", type = Boolean.class, defaultValue = "True", named = true,
              positional = false,
              doc = "If true, it only uses the first parent when looking for changes. Note that"
                  + " when disabled in ITERATIVE mode, it will try to do a migration for each"
                  + " change of the merged branch."),
          @Param(name = PATCH_FIELD, type = Transformation.class, defaultValue = "None",
              named = true, positional = false, noneable = true, doc = PATCH_FIELD_DESC)
      }, useLocation = true)
  public GitOrigin origin(String url, Object ref, String submodules,
      Boolean includeBranchCommitLogs, Boolean firstParent, Object patch,
      Location location)
      throws EvalException {
    checkNotEmpty(url, "url", location);
    PatchTransformation patchTransformation = maybeGetPatchTransformation(patch, location);

    return GitOrigin.newGitOrigin(
        options, fixHttp(url, location), Type.STRING.convertOptional(ref, "ref"),
        GitRepoType.GIT, stringToEnum(location, "submodules",
            submodules, GitOrigin.SubmoduleStrategy.class),
        includeBranchCommitLogs, firstParent, patchTransformation);
  }

  @Nullable
  private PatchTransformation maybeGetPatchTransformation(Object patch, Location location)
      throws EvalException {
    if (EvalUtils.isNullOrNone(patch)) {
      return null;
    }
    SkylarkUtil.check(location, patch instanceof PatchTransformation,
        "'%s' is not a patch.apply(...) transformation", PATCH_FIELD);
    return  (PatchTransformation) patch;
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(name = "integrate",
      doc = "Integrate changes from a url present in the migrated change label.",
      parameters = {
          @Param(name = "label", type = String.class, named = true,
              doc = "The migration label that will contain the url to the change to integrate.",
              defaultValue = "\"" + DEFAULT_INTEGRATE_LABEL + "\""),
          @Param(name = "strategy", type = String.class, named = true,
              defaultValue = "\"FAKE_MERGE_AND_INCLUDE_FILES\"",
              doc = "How to integrate the change:<br>"
                  + "<ul>"
                  + " <li><b>'FAKE_MERGE'</b>: Add the url revision/reference as parent of the"
                  + " migration change but ignore all the files from the url. The commit message"
                  + " will be a standard merge one but will include the corresponding RevId label"
                  + "</li>"
                  + " <li><b>'FAKE_MERGE_AND_INCLUDE_FILES'</b>: Same as 'FAKE_MERGE' but any"
                  + " change to files that doesn't match destination_files will be included as part"
                  + " of the merge commit. So it will be a semi fake merge: Fake for"
                  + " destination_files but merge for non destination files.</li>"
                  + " <li><b>'INCLUDE_FILES'</b>: Same as 'FAKE_MERGE_AND_INCLUDE_FILES' but it"
                  + " it doesn't create a merge but only include changes not matching"
                  + " destination_files</li>"
                  + "</ul>"),
          @Param(name = "ignore_errors", type = Boolean.class, named = true,
              doc = "If we should ignore integrate errors and continue the migration without the"
                  + " integrate", defaultValue = "True"),
      },
      useLocation = true)
  @Example(title = "Integrate changes from a review url",
      before = "Assuming we have a git.destination defined like this:",
      code = "git.destination(\n"
          + "        url = \"https://example.com/some_git_repo\",\n"
          + "        integrates = [git.integrate()],\n"
          + "\n"
          + ")",
      after =
          "It will look for `" + DEFAULT_INTEGRATE_LABEL
              + "` label during the worklow migration. If the label"
              + " is found, it will fetch the git url and add that change as an additional parent"
              + " to the migration commit (merge). It will fake-merge any change from the url that"
              + " matches destination_files but it will include changes not matching it.")
  public GitIntegrateChanges integrate(String label, String strategy, Boolean ignoreErrors,
      Location location) throws EvalException {
    return new GitIntegrateChanges(
        label,
        stringToEnum(location, "strategy", strategy, Strategy.class),
        ignoreErrors);
  }


  @SuppressWarnings("unused")
  @SkylarkCallable(name = "mirror",
      doc = "Mirror git references between repositories",
      parameters = {
          @Param(name = "name", type = String.class, named = true,
              doc = "Migration name"),
          @Param(name = "origin", type = String.class, named = true,
              doc = "Indicates the URL of the origin git repository"),
          @Param(name = "destination", type = String.class, named = true,
              doc = "Indicates the URL of the destination git repository"),
          @Param(name = "refspecs", type = SkylarkList.class, generic1 = String.class, named = true,
              defaultValue = "['refs/heads/*']",
              doc = "Represents a list of git refspecs to mirror between origin and destination."
                  + "For example 'refs/heads/*:refs/remotes/origin/*' will mirror any reference"
                  + "inside refs/heads to refs/remotes/origin."),
          @Param(name = "prune", type = Boolean.class, named = true,
              doc = "Remove remote refs that don't have a origin counterpart",
              defaultValue = "False"),

      },
      useLocation = true, useEnvironment = true)
  @UsesFlags(GitMirrorOptions.class)
  public NoneType mirror(String name, String origin, String destination,
      SkylarkList<String> strRefSpecs, Boolean prune, Location location, Environment env)
      throws EvalException {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    List<Refspec> refspecs = new ArrayList<>();

    for (String refspec : SkylarkList.castList(strRefSpecs, String.class, "refspecs")) {
      try {
        refspecs.add(Refspec.create(
            generalOptions.getEnvironment(), generalOptions.getCwd(), refspec));
      } catch (InvalidRefspecException e) {
        throw new EvalException(location, e);
      }
    }
    GlobalMigrations.getGlobalMigrations(env).addMigration(
        location,
        name,
        new Mirror(
            generalOptions,
            options.get(GitOptions.class),
            name,
            fixHttp(origin, location),
            fixHttp(destination, location),
            refspecs,
            options.get(GitMirrorOptions.class),
            prune,
            mainConfigFile));
    return Runtime.NONE;
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(name = "gerrit_origin",
      doc = "Defines a Git origin for Gerrit reviews.\n"
          + "\n"
          + "Implicit labels that can be used/exposed:\n"
          + "\n"
          + "  - " + GERRIT_CHANGE_NUMBER_LABEL + ": The change number for the Gerrit review.\n"
          + "  - " + GERRIT_CHANGE_ID_LABEL + ": The change id for the Gerrit review.\n"
          + "  - " + GERRIT_CHANGE_DESCRIPTION_LABEL + ": The description of the Gerrit review.\n"
          + "  - " + DEFAULT_INTEGRATE_LABEL + ": A label that when exposed, can be used to"
          + " integrate automatically in the reverse workflow.\n",
      parameters = {
          @Param(name = "url", type = String.class, named = true,
              doc = "Indicates the URL of the git repository"),
          @Param(name = "ref", type = String.class, noneable = true, defaultValue = "None",
              named = true,
              doc = "DEPRECATED. Use git.origin for submitted branches."),
          @Param(name = "submodules", type = String.class, defaultValue = "'NO'", named = true,
              doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
          @Param(name = "first_parent", type = Boolean.class, defaultValue = "True", named = true,
              doc = "If true, it only uses the first parent when looking for changes. Note that"
                  + " when disabled in ITERATIVE mode, it will try to do a migration for each"
                  + " change of the merged branch.", positional = false),
          @Param(name = "api_checker", type = Checker.class,  defaultValue = "None",
              doc = "A checker for the Gerrit API endpoint provided for after_migration hooks. "
                  + "This field is not used if the workflow doesn't have hooks.",
              named = true, positional = false,
              noneable = true),
          @Param(name = PATCH_FIELD, type = Transformation.class, defaultValue = "None",
              named = true, positional = false, noneable = true, doc = PATCH_FIELD_DESC)
      },
      useLocation = true)
  public GitOrigin gerritOrigin(String url, Object ref, String submodules,
      Boolean firstParent, Object checkerObj, Object patch, Location location)
      throws EvalException {
    checkNotEmpty(url, "url", location);
    url = fixHttp(url, location);
    String refField = Type.STRING.convertOptional(ref, "ref");

    PatchTransformation patchTransformation = maybeGetPatchTransformation(patch, location);

    if (!Strings.isNullOrEmpty(refField)) {
      getGeneralConsole().warn(
          "'ref' field detected in configuration. git.gerrit_origin"
              + " is deprecating its usage for submitted changes. Use git.origin instead.");
      return GitOrigin.newGitOrigin(
          options, url, refField, GitRepoType.GERRIT,
          stringToEnum(location, "submodules",
              submodules, GitOrigin.SubmoduleStrategy.class),
          /*includeBranchCommitLogs=*/false, firstParent, patchTransformation);
    }
    return GerritOrigin.newGerritOrigin(
        options, url, stringToEnum(location, "submodules",
            submodules, GitOrigin.SubmoduleStrategy.class), firstParent,
        convertFromNoneable(checkerObj, null), patchTransformation);
  }

  static final String GITHUB_PR_ORIGIN_NAME = "github_pr_origin";

  @SuppressWarnings("unused")
  @SkylarkCallable(name = GITHUB_PR_ORIGIN_NAME,
      doc = "Defines a Git origin for Github pull requests.\n"
          + "\n"
          + "Implicit labels that can be used/exposed:\n"
          + "\n"
          + "  - " + GitHubPROrigin.GITHUB_PR_NUMBER_LABEL + ": The pull request number if the"
          + " reference passed was in the form of `https://github.com/project/pull/123`, "
          + " `refs/pull/123/head` or `refs/pull/123/master`.\n"
          + "  - " + DEFAULT_INTEGRATE_LABEL + ": A label that when exposed, can be used to"
          + " integrate automatically in the reverse workflow.\n"
          + "  - " + GITHUB_BASE_BRANCH + ": The base branch name used for the Pull Request.\n"
          + "  - " + GITHUB_BASE_BRANCH_SHA1 + ": The base branch SHA-1 used as baseline.\n"
          + "  - " + GITHUB_PR_TITLE + ": Title of the Pull Request.\n"
          + "  - " + GITHUB_PR_BODY + ": Body of the Pull Request.\n"
          + "  - " + GITHUB_PR_HEAD_SHA + ": The SHA-1 of the head commit of the pull request.\n"
          + "  - " + GITHUB_PR_USER + ": The login of the author the pull request.\n"
          + "  - " + GITHUB_PR_ASSIGNEE + ": A repeated label with the login of the assigned"
          + " users.\n"
          + "  - " + GITHUB_PR_REVIEWER_APPROVER + ": A repeated label with the login of users"
          + " that have participated in the review and that can approve the import. Only"
          + " populated if `review_state` field is set. Every reviewers type matching"
          + " `review_approvers` will be added to this list.\n"
          + "  - " + GITHUB_PR_REVIEWER_OTHER + ": A repeated label with the login of users"
          + " that have participated in the review but cannot approve the import. Only"
          + " populated if `review_state` field is set.\n",
      parameters = {
          @Param(name = "url", type = String.class, named = true,
              doc = "Indicates the URL of the GitHub repository"),
          @Param(name = "use_merge", type = Boolean.class, defaultValue = "False", named = true,
              positional = false,
              doc = "If the content for refs/pull/<ID>/merge should be used instead of the PR"
                  + " head. The GitOrigin-RevId still will be the one from refs/pull/<ID>/head"
                  + " revision."),
          @Param(name = "required_labels", type = SkylarkList.class, named = true,
              generic1 = String.class, defaultValue = "[]",
              doc = "Required labels to import the PR. All the labels need to be present in order"
                  + " to migrate the Pull Request.", positional = false),
          @Param(name = "retryable_labels", type = SkylarkList.class, named = true,
              generic1 = String.class, defaultValue = "[]",
              doc = "Required labels to import the PR that should be retried. This parameter must"
                  + " be a subset of required_labels.", positional = false),
          @Param(name = "submodules", type = String.class, defaultValue = "'NO'", named = true,
              positional = false,
              doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
          @Param(name = "baseline_from_branch", type = Boolean.class, named = true,
              doc = "WARNING: Use this field only for github -> git CHANGE_REQUEST workflows.<br>"
                  + "When the field is set to true for CHANGE_REQUEST workflows it will find the"
                  + " baseline comparing the Pull Request with the base branch instead of looking"
                  + " for the *-RevId label in the commit message.", defaultValue = "False",
              positional = false),
          @Param(name = "first_parent", type = Boolean.class, defaultValue = "True", named = true,
              doc = "If true, it only uses the first parent when looking for changes. Note that"
                  + " when disabled in ITERATIVE mode, it will try to do a migration for each"
                  + " change of the merged branch.", positional = false),
          @Param(name = "state", type = String.class, defaultValue = "'OPEN'", named = true,
              positional = false,
              doc = "Only migrate Pull Request with that state."
                  + " Possible values: `'OPEN'`, `'CLOSED'` or `'ALL'`. Default 'OPEN'"),
          @Param(name = "review_state", type = String.class, defaultValue = "None",
              named = true, positional = false, noneable = true,
              doc = "Required state of the reviews associated with the Pull Request"
                  + " Possible values: `'HEAD_COMMIT_APPROVED'`, `'ANY_COMMIT_APPROVED'`,"
                  + " `'HAS_REVIEWERS'` or `'ANY'`. Default `None`. This field is required if"
                  + " the user wants `" + GITHUB_PR_REVIEWER_APPROVER + "` and `"
                  + GITHUB_PR_REVIEWER_OTHER + "` labels populated"),
          @Param(name = "review_approvers", type = SkylarkList.class, generic1 = String.class,
              defaultValue = "None",
              named = true, positional = false, noneable = true,
              doc = "The set of reviewer types that are considered for approvals. In order to"
                  + " have any effect, `review_state` needs to be set. "
                  + GITHUB_PR_REVIEWER_APPROVER + "` will be populated for these types."
                  + " See the valid types here:"
                  + " https://developer.github.com/v4/reference/enum/commentauthorassociation/"),
          @Param(name = "api_checker", type = Checker.class,  defaultValue = "None",
              doc = "A checker for the GitHub API endpoint provided for after_migration hooks. "
                  + "This field is not used if the workflow doesn't have hooks.",
              named = true, positional = false,
              noneable = true),
          @Param(name = PATCH_FIELD, type = Transformation.class, defaultValue = "None",
              named = true, positional = false, noneable = true, doc = PATCH_FIELD_DESC)
      },
      useLocation = true)
  @UsesFlags(GitHubPrOriginOptions.class)
  @DocDefault(field = "review_approvers", value = "[\"COLLABORATOR\", \"MEMBER\", \"OWNER\"]")
  public GitHubPROrigin githubPrOrigin(String url, Boolean merge,
      SkylarkList<String> requiredLabels, SkylarkList<String> retryableLabels, String submodules,
      Boolean baselineFromBranch, Boolean firstParent, String state,
      Object reviewStateParam, Object reviewApproversParam, Object checkerObj, Object patch,
      Location location) throws EvalException {
    checkNotEmpty(url, "url", location);
    if (!url.contains("github.com")) {
      throw new EvalException(location, "Invalid Github URL: " + url);
    }
    PatchTransformation patchTransformation = maybeGetPatchTransformation(patch, location);

    String reviewStateString = SkylarkUtil.convertFromNoneable(reviewStateParam, null);
    SkylarkList<String> reviewApproversStrings =
        SkylarkUtil.convertFromNoneable(reviewApproversParam, null);
    ReviewState reviewState;
    ImmutableSet<AuthorAssociation> reviewApprovers;
    if (reviewStateString == null) {
      reviewState = null;
      SkylarkUtil.check(location, reviewApproversStrings == null,
          "'review_approvers' cannot be set if `review_state` is not set");
      reviewApprovers = ImmutableSet.of();
    } else {
      reviewState = ReviewState.valueOf(reviewStateString);
      if (reviewApproversStrings == null) {
        reviewApproversStrings = SkylarkList.createImmutable(
            ImmutableList.of("COLLABORATOR", "MEMBER", "OWNER"));
      }
      HashSet<AuthorAssociation> approvers = new HashSet<>();
      for (String r : reviewApproversStrings) {
        if (!approvers.add(
            stringToEnum(location, "review_approvers", r, AuthorAssociation.class))) {
          throw new EvalException(location, "Repeated element " + r);
        }
      }
      reviewApprovers = ImmutableSet.copyOf(approvers);
    }

    GitHubPrOriginOptions gitHubPrOriginOptions = options.get(GitHubPrOriginOptions.class);
    return new GitHubPROrigin(
        fixHttp(url, location),
        merge,
        options.get(GeneralOptions.class),
        options.get(GitOptions.class),
        options.get(GitOriginOptions.class),
        options.get(GitHubOptions.class),
        gitHubPrOriginOptions.getRequiredLabels(requiredLabels),
        gitHubPrOriginOptions.getRetryableLabels(retryableLabels),
        stringToEnum(location, "submodules", submodules, SubmoduleStrategy.class),
        baselineFromBranch, firstParent,
        stringToEnum(location, "state", state, StateFilter.class),
        reviewState,
        reviewApprovers,
        convertFromNoneable(checkerObj, null),
        patchTransformation);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(name = "github_origin",
      doc = "Defines a Git origin for a Github repository. This origin should be used for public"
          + " branches. Use " + GITHUB_PR_ORIGIN_NAME + " for importing Pull Requests.",
      parameters = {
          @Param(name = "url", type = String.class, named = true,
              doc = "Indicates the URL of the git repository"),
          @Param(name = "ref", type = String.class, noneable = true, defaultValue = "None",
              named = true,
              doc = "Represents the default reference that will be used for reading the revision "
                  + "from the git repository. For example: 'master'"),
          @Param(name = "submodules", type = String.class, defaultValue = "'NO'", named = true,
              doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
          @Param(name = "first_parent", type = Boolean.class, defaultValue = "True", named = true,
              doc = "If true, it only uses the first parent when looking for changes. Note that"
                  + " when disabled in ITERATIVE mode, it will try to do a migration for each"
                  + " change of the merged branch.", positional = false),
          @Param(name = PATCH_FIELD, type = Transformation.class, defaultValue = "None",
              named = true, positional = false, noneable = true, doc = PATCH_FIELD_DESC)
      },
      useLocation = true)
  public GitOrigin githubOrigin(String url, Object ref, String submodules,
      Boolean firstParent, Object patch, Location location) throws EvalException {
    if (!GitHubUtil.isGitHubUrl(checkNotEmpty(url, "url", location))) {
      throw new EvalException(location, "Invalid Github URL: " + url);
    }

    PatchTransformation patchTransformation = maybeGetPatchTransformation(patch, location);

    // TODO(copybara-team): See if we want to support includeBranchCommitLogs for GitHub repos.
    return GitOrigin.newGitOrigin(
        options, fixHttp(url, location), Type.STRING.convertOptional(ref, "ref"),
        GitRepoType.GITHUB, stringToEnum(location, "submodules",
            submodules, GitOrigin.SubmoduleStrategy.class),
        /*includeBranchCommitLogs=*/false, firstParent, patchTransformation);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(name = "destination",
      doc = "Creates a commit in a git repository using the transformed worktree."
          + "<br><br>Given that Copybara doesn't ask for user/password in the console when"
          + " doing the push to remote repos, you have to use ssh protocol, have the credentials"
          + " cached or use a credential manager.",
      parameters = {
          @Param(name = "url", type = String.class, named = true,
              doc = "Indicates the URL to push to as well as the URL from which to get the parent "
                  + "commit"),
          @Param(name = "push", type = String.class, named = true,
              doc = "Reference to use for pushing the change, for example 'master'",
              defaultValue = "'master'"),
          @Param(name = "fetch", type = String.class, named = true,
              doc = "Indicates the ref from which to get the parent commit. Defaults to push value"
                  + " if None",
              defaultValue = "None", noneable = true),
          @Param(name = "skip_push", type = Boolean.class, defaultValue = "False", named = true,
              doc = "If set, copybara will not actually push the result to the destination. This is"
                  + " meant for testing workflows and dry runs."),
          @Param(name = "integrates", type = SkylarkList.class, named = true,
              generic1 = GitIntegrateChanges.class, defaultValue = "None",
              doc = "Integrate changes from a url present in the migrated change"
                  + " label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is"
                  + " present in the message", positional = false, noneable = true),
      },
      useLocation = true)
  @UsesFlags(GitDestinationOptions.class)
  public GitDestination destination(String url, String push, Object fetch,
      Boolean skipPush, Object integrates, Location location)
      throws EvalException {
    GitDestinationOptions destinationOptions = options.get(GitDestinationOptions.class);
    String resolvedPush = checkNotEmpty(firstNotNull(destinationOptions.push, push),
        "push", location);
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    return new GitDestination(
        fixHttp(checkNotEmpty(
            firstNotNull(destinationOptions.url, url), "url", location), location),
        checkNotEmpty(
            firstNotNull(destinationOptions.fetch,
                convertFromNoneable(fetch, null),
                resolvedPush),
            "fetch", location),
        resolvedPush,
        destinationOptions,
        options.get(GitOptions.class),
        generalOptions,
        skipPush,
        new DefaultWriteHook(),
        SkylarkList.castList(SkylarkUtil.convertFromNoneable(integrates, DEFAULT_GIT_INTEGRATES),
            GitIntegrateChanges.class, "integrates"));
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(name = "github_pr_destination",
      doc = "Creates changes in a new pull request in the destination.",
      parameters = {
          @Param(name = "url", type = String.class, named = true,
              doc = "Url of the GitHub project. For example"
                  + " \"https://github.com/google/copybara'\""),
          @Param(name = "destination_ref", type = String.class, named = true,
              doc = "Destination reference for the change. By default 'master'",
              defaultValue = "\"master\""),
          @Param(name = "skip_push", type = Boolean.class, defaultValue = "False", named = true,
              positional = false,
              doc = "If set, copybara will not actually push the result to the destination. This is"
                  + " meant for testing workflows and dry runs."),
          @Param(name = "title", type = String.class, defaultValue = "None", noneable = true,
              named = true, positional = false,
              doc = "When creating a pull request, use this title. By default it uses the change"
                  + " first line."),
          @Param(name = "body", type = String.class, defaultValue = "None", noneable = true,
              named = true, positional = false,
              doc = "When creating a pull request, use this body. By default it uses the change"
                  + " summary."),
          @Param(name = "integrates", type = SkylarkList.class, named = true,
              generic1 = GitIntegrateChanges.class, defaultValue = "None",
              doc = "Integrate changes from a url present in the migrated change"
                  + " label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is"
                  + " present in the message", positional = false, noneable = true),
      },
      useLocation = true)
  @UsesFlags({GitDestinationOptions.class, GitHubDestinationOptions.class})
  public GitHubPrDestination githubPrDestination(String url, String destinationRef,
      Boolean skipPush, Object title, Object body, Object integrates, Location location)
      throws EvalException {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    // This restricts to github.com, we will have to revisit this to support setups like GitHub
    // Enterprise.
    SkylarkUtil.check(location, GitHubUtil.isGitHubUrl(url), "'%s' is not a valid GitHub url", url);
    return new GitHubPrDestination(
        fixHttp(url, location),
        destinationRef,
        generalOptions,
        options.get(GitHubOptions.class),
        options.get(GitDestinationOptions.class),
        options.get(GitHubDestinationOptions.class),
        options.get(GitOptions.class),
        skipPush,
        new DefaultWriteHook(),
        SkylarkList.castList(
            SkylarkUtil.convertFromNoneable(integrates, DEFAULT_GIT_INTEGRATES),
            GitIntegrateChanges.class,
            "integrates"),
        SkylarkUtil.convertFromNoneable(title, null),
        SkylarkUtil.convertFromNoneable(body, null),
        mainConfigFile);
  }

  private static String firstNotNull(String... values) {
    for (String value : values) {
      if (!Strings.isNullOrEmpty(value)) {
        return value;
      }
    }
    return null;
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(name = "gerrit_destination",
      doc = "Creates a change in Gerrit using the transformed worktree. If this is used in "
          + "iterative mode, then each commit pushed in a single Copybara invocation will have the "
          + "correct commit parent. The reviews generated can then be easily done in the correct "
          + "order without rebasing.",
      parameters = {
          @Param(name = "url", type = String.class, named = true,
              doc = "Indicates the URL to push to as well as the URL from which to get the parent "
                  + "commit"),
          @Param(name = "fetch", type = String.class, named = true,
              doc = "Indicates the ref from which to get the parent commit"),
          @Param(
              name = "push_to_refs_for", type = String.class, defaultValue = "None",
              named = true, noneable = true,
              doc = "Review branch to push the change to, for example setting this to 'feature_x'"
                  + " causes the destination to push to 'refs/for/feature_x'. It defaults to "
                  + "'fetch' value."),
          @Param(name = "submit", type = Boolean.class,
              named = true,
              doc =
                  "If true, skip the push thru Gerrit refs/for/branch and directly push to branch."
                      + " This is effectively a git.destination that sets a Change-Id",
              defaultValue = "False"),
          @Param(
              name = "change_id_policy", type = String.class, defaultValue = "'FAIL_IF_PRESENT'",
              named = true,
              doc = "What to do in the presence or absent of Change-Id in message:"
                  + "<ul>"
                  + "  <li>`'REQUIRE'`: Require that the change_id is present in the message as a"
                  + " valid label</li>"
                  + "  <li>`'FAIL_IF_PRESENT'`: Fail if found in message</li>"
                  + "  <li>`'REUSE'`: Reuse if present. Otherwise generate a new one</li>"
                  + "  <li>`'REPLACE'`: Replace with a new one if found</li>"
                  + "</ul>"),
          @Param(name = "allow_empty_diff_patchset", type = Boolean.class,
              named = true,
              doc = "By default Copybara will upload a new PatchSet to Gerrit without checking the"
                  + " previous one. If this set to false, Copybara will download current PatchSet"
                  + " and check the diff against the new diff.",
              defaultValue = "True"),
          @Param(name = "reviewers", type = SkylarkList.class, named = true,
              defaultValue = "[]",
              doc = "The list of the reviewers will be added to gerrit change reviewer list"
                  + "The element in the list is: an email, for example: \"foo@example.com\" or label "
                  + "for example: ${SOME_GERRIT_REVIEWER}. These are under the condition of "
                  + "assuming that users have registered to gerrit repos"),
      },
      useLocation = true)
  @UsesFlags(GitDestinationOptions.class)
  @DocDefault(field = "push_to_refs_for", value = "fetch value")
  public GerritDestination gerritDestination(
      String url, String fetch, Object pushToRefsFor, Boolean submit, String changeIdPolicy,
      Boolean allowEmptyPatchSet, SkylarkList<String> reviewers, Location location) throws EvalException {
    checkNotEmpty(url, "url", location);
    List<String> newReviewers =
            Type.STRING_LIST.convert(reviewers, "reviewers");
    return GerritDestination.newGerritDestination(
        options,
        fixHttp(url, location),
        checkNotEmpty(firstNotNull(
            options.get(GitDestinationOptions.class).fetch,
            fetch), "fetch", location),
        checkNotEmpty(
            firstNotNull(convertFromNoneable(pushToRefsFor, null),
                options.get(GitDestinationOptions.class).fetch,
                fetch),
            "push_to_refs_for", location),
        submit,
        stringToEnum(location, "change_id_policy", changeIdPolicy, ChangeIdPolicy.class),
        allowEmptyPatchSet,
        newReviewers);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
    name = GITHUB_API,
    doc =
        ""
            + "[EXPERIMENTAL] Defines a feedback API endpoint for GitHub, that exposes relevant "
            + "GitHub API operations.",
    parameters = {
        @Param(name = "url", type = String.class, doc = "Indicates the GitHub repo URL.",
            named = true),
        @Param(name = "checker", type = Checker.class,  defaultValue = "None",
            doc = "A checker for the GitHub API transport.", named = true, noneable = true),
    },
    useLocation = true
  )
  @UsesFlags(GitHubOptions.class)
  public GitHubEndPoint githubApi(String url, Object checkerObj, Location location)
      throws EvalException {
    checkNotEmpty(url, "url", location);
    url = fixHttp(url, location);
    Checker checker = convertFromNoneable(checkerObj, null);
    validateEndpointChecker(location, checker, GITHUB_API);
    GitHubOptions gitHubOptions = options.get(GitHubOptions.class);
    return new GitHubEndPoint(gitHubOptions.newGitHubApiSupplier(url, checker), url,
        getGeneralConsole());
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = GERRIT_API,
      doc =
          ""
              + "[EXPERIMENTAL] Defines a feedback API endpoint for Gerrit, that exposes relevant "
              + "Gerrit API operations.",
      parameters = {
        @Param(
            name = "url",
            type = String.class,
            doc = "Indicates the Gerrit repo URL.",
            named = true),
        @Param(
            name = "checker",
            type = Checker.class,
            defaultValue = "None",
            doc = "A checker for the Gerrit API transport.",
            named = true,
            noneable = true),
      },
      useLocation = true)
  @UsesFlags(GerritOptions.class)
  public GerritEndpoint gerritApi(String url, Object checkerObj, Location location)
      throws EvalException {
    checkNotEmpty(url, "url", location);
    url = fixHttp(url, location);
    Checker checker = convertFromNoneable(checkerObj, null);
    validateEndpointChecker(location, checker, GERRIT_API);
    GerritOptions gerritOptions = options.get(GerritOptions.class);
    return new GerritEndpoint(gerritOptions.newGerritApiSupplier(url, checker), url,
        getGeneralConsole());
  }

  private Console getGeneralConsole() {
    return options.get(GeneralOptions.class).console();
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = GERRIT_TRIGGER,
      doc = "[EXPERIMENTAL] Defines a feedback trigger based on updates on a Gerrit change.",
      parameters = {
          @Param(name = "url", type = String.class, doc = "Indicates the Gerrit repo URL.",
              named = true),
          @Param(name = "checker", type = Checker.class,  defaultValue = "None",
              doc = "A checker for the Gerrit API transport provided by this trigger.",
              named = true, noneable = true),
      },
      useLocation = true)
  @UsesFlags(GerritOptions.class)
  public GerritTrigger gerritTrigger(String url, Object checkerObj, Location location)
      throws EvalException {
    checkNotEmpty(url, "url", location);
    url = fixHttp(url, location);
    Checker checker = convertFromNoneable(checkerObj, null);
    validateEndpointChecker(location, checker, GERRIT_TRIGGER);
    GerritOptions gerritOptions = options.get(GerritOptions.class);
    return new GerritTrigger(gerritOptions.newGerritApiSupplier(url, checker), url,
        getGeneralConsole());
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = GITHUB_TRIGGER,
      doc = "[EXPERIMENTAL] Defines a feedback trigger based on updates on a GitHub PR.",
      parameters = {
        @Param(
            name = "url",
            type = String.class,
            doc = "Indicates the GitHub repo URL.",
            named = true),
        @Param(
            name = "checker",
            type = Checker.class,
            defaultValue = "None",
            doc = "A checker for the GitHub API transport provided by this trigger.",
            named = true,
            noneable = true),
      },
      useLocation = true, documented = false)
  @UsesFlags(GitHubOptions.class)
  public GitHubTrigger gitHubTrigger(String url, Object checkerObj, Location location)
      throws EvalException {
    checkNotEmpty(url, "url", location);
    url = fixHttp(url, location);
    Checker checker = convertFromNoneable(checkerObj, null);
    validateEndpointChecker(location, checker, GITHUB_TRIGGER);
    GitHubOptions gitHubOptions = options.get(GitHubOptions.class);
    return new GitHubTrigger(gitHubOptions.newGitHubApiSupplier(url, checker), url,
        getGeneralConsole());
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "review_input",
      doc = "Creates a review to be posted on Gerrit.",
      parameters = {
        @Param(
            name = "labels",
            type = SkylarkDict.class,
            doc = "The labels to post.",
            named = true,
            defaultValue = "{}"),
          @Param(
              name = "message",
              type = String.class,
              doc = "The message to be added as review comment.",
              named = true,
              defaultValue = "None", noneable = true),
      },
      useLocation = true)
  @UsesFlags(GerritOptions.class)
  public SetReviewInput reviewInput(SkylarkDict<String, Integer> labels, Object message,
      Location location) throws EvalException {
    return SetReviewInput.create(SkylarkUtil.convertFromNoneable(message, null),
        SkylarkDict.castSkylarkDictOrNoneToDict(
            labels, String.class, Integer.class, "Gerrit review labels"));
  }

  @Override
  public void setConfigFile(ConfigFile<?> mainConfigFile, ConfigFile<?> currentConfigFile) {
    this.mainConfigFile = mainConfigFile;
  }

  @CheckReturnValue
  private String fixHttp(String url, Location location) {
    try {
      RepositoryUtil.validateNotHttp(url);
    } catch (ValidationException e) {
      String fixed = "https" + url.substring("http".length());
      getGeneralConsole().warnFmt(
          "%s: Url '%s' does not use https - please change the URL. Proceeding with '%s'.",
          location.print(), url, fixed);
      return fixed;
    }
    return url;
  }

  /**
   * Validates the {@link Checker} provided to a feedback endpoint.
   */
  @SuppressWarnings({"unused", "RedundantThrows"})
  protected void validateEndpointChecker(
      Location location, Checker checker, String functionName) throws EvalException {}
}
