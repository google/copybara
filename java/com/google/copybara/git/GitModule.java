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

import static com.google.copybara.config.SkylarkUtil.check;
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
import static com.google.copybara.git.GitHubPROrigin.GITHUB_PR_URL;
import static com.google.copybara.git.GitHubPROrigin.GITHUB_PR_USER;
import static com.google.copybara.git.LatestVersionSelector.VersionElementType.ALPHABETIC;
import static com.google.copybara.git.LatestVersionSelector.VersionElementType.NUMERIC;
import static com.google.copybara.git.github.api.GitHubEventType.WATCHABLE_EVENTS;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Transformation;
import com.google.copybara.WorkflowOptions;
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
import com.google.copybara.git.GerritDestination.NotifyOption;
import com.google.copybara.git.GitDestination.WriterImpl.DefaultWriteHook;
import com.google.copybara.git.GitHubPROrigin.ReviewState;
import com.google.copybara.git.GitHubPROrigin.StateFilter;
import com.google.copybara.git.GitIntegrateChanges.Strategy;
import com.google.copybara.git.GitOrigin.SubmoduleStrategy;
import com.google.copybara.git.LatestVersionSelector.VersionElementType;
import com.google.copybara.git.gerritapi.SetReviewInput;
import com.google.copybara.git.github.api.AuthorAssociation;
import com.google.copybara.git.github.api.GitHubEventType;
import com.google.copybara.git.github.util.GitHubUtil;
import com.google.copybara.transform.Replace;
import com.google.copybara.transform.patch.PatchTransformation;
import com.google.copybara.util.RepositoryUtil;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.EvalUtils;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.Runtime.NoneType;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.StarlarkThread;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeMap;
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
  final SkylarkList<GitIntegrateChanges> defaultGitIntegrate;
  private static final String GERRIT_TRIGGER = "gerrit_trigger";
  private static final String GERRIT_API = "gerrit_api";
  private static final String GITHUB_TRIGGER = "github_trigger";
  private static final String GITHUB_API = "github_api";
  private static final String PATCH_FIELD = "patch";
  private static final String PATCH_FIELD_DESC =
      "Patch the checkout dir. The difference with `patch.apply` transformation is"
          + " that here we can apply it using three-way";
  private static final String DESCRIBE_VERSION_FIELD_DOC =
      "Download tags and use 'git describe' to create two labels with a meaningful version:<br><br>"
          + "   - `GIT_DESCRIBE_CHANGE_VERSION`: The version for the change or changes being"
          + " migrated. The value changes per change in `ITERATIVE` mode and will be the latest"
          + " migrated change in `SQUASH` (In other words, doesn't include excluded changes). this"
          + " is normally what users want to use.<br>"
          + "   - `GIT_DESCRIBE_REQUESTED_VERSION`: `git describe` for the requested/head version."
          + " Constant in `ITERATIVE` mode and includes filtered changes.<br>";

  protected final Options options;
  private ConfigFile mainConfigFile;

  public GitModule(Options options) {
    this.options = Preconditions.checkNotNull(options);
    this.defaultGitIntegrate = SkylarkList.createImmutable(ImmutableList.of(
        new GitIntegrateChanges(DEFAULT_INTEGRATE_LABEL,
            Strategy.FAKE_MERGE_AND_INCLUDE_FILES,
            /*ignoreErrors=*/true, useNewIntegrate())));

  }

  private boolean useNewIntegrate() {
    // TODO(malcon): Remove after 2019/09/20 when new integrate is proven to work
    return options.get(GeneralOptions.class).isTemporaryFeature("NEW_GIT_INTEGRATE", true);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "origin",
      doc =
          "Defines a standard Git origin. For Git specific origins use: `github_origin` or "
              + "`gerrit_origin`.<br><br>All the origins in this module accept several string"
              + " formats as reference (When copybara is called in the form of `copybara config"
              + " workflow reference`):<br><ul><li>**Branch name:** For example"
              + " `master`</li><li>**An arbitrary reference:**"
              + " `refs/changes/20/50820/1`</li><li>**A SHA-1:** Note that it has to be reachable"
              + " from the default refspec</li><li>**A Git repository URL and reference:**"
              + " `http://github.com/foo master`</li><li>**A GitHub pull request URL:**"
              + " `https://github.com/some_project/pull/1784`</li></ul><br>So for example,"
              + " Copybara can be invoked for a `git.origin` in the CLI as:<br>`copybara"
              + " copy.bara.sky my_workflow https://github.com/some_project/pull/1784`<br>This"
              + " will use the pull request as the origin URL and reference.",
      parameters = {
        @Param(
            name = "url",
            type = String.class,
            named = true,
            doc = "Indicates the URL of the git repository"),
        @Param(
            name = "ref",
            type = String.class,
            noneable = true,
            defaultValue = "None",
            named = true,
            doc =
                "Represents the default reference that will be used for reading the revision "
                    + "from the git repository. For example: 'master'"),
        @Param(
            name = "submodules",
            type = String.class,
            defaultValue = "'NO'",
            named = true,
            positional = false,
            doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
        @Param(
            name = "include_branch_commit_logs",
            type = Boolean.class,
            defaultValue = "False",
            named = true,
            positional = false,
            doc =
                "Whether to include raw logs of branch commits in the migrated change message."
                    + "WARNING: This field is deprecated in favor of 'first_parent' one."
                    + " This setting *only* affects merge commits."),
        @Param(
            name = "first_parent",
            type = Boolean.class,
            defaultValue = "True",
            named = true,
            positional = false,
            doc =
                "If true, it only uses the first parent when looking for changes. Note that"
                    + " when disabled in ITERATIVE mode, it will try to do a migration for each"
                    + " change of the merged branch."),
        @Param(
            name = PATCH_FIELD,
            type = Transformation.class,
            defaultValue = "None",
            named = true,
            positional = false,
            noneable = true,
            doc = PATCH_FIELD_DESC),
        @Param(
            name = "describe_version",
            type = Boolean.class,
            defaultValue = "None",
            named = true,
            positional = false,
            doc = DESCRIBE_VERSION_FIELD_DOC,
            noneable = true),
        @Param(
            name = "version_selector",
            type = LatestVersionSelector.class,
            defaultValue = "None",
            named = true,
            positional = false,
            doc = "Select a custom version (tag)to migrate" + " instead of 'ref'",
            noneable = true),
      },
      useLocation = true)
  public GitOrigin origin(
      String url,
      Object ref,
      String submodules,
      Boolean includeBranchCommitLogs,
      Boolean firstParent,
      Object patch,
      Object describeVersion,
      Object versionSelector,
      Location location)
      throws EvalException {
    checkNotEmpty(url, "url", location);
    PatchTransformation patchTransformation = maybeGetPatchTransformation(patch, location);

    if (versionSelector != Runtime.NONE) {
      check(location, ref == Runtime.NONE,
          "Cannot use ref field and version_selector. Version selector will decide the ref"
              + " to migrate");
    }

    return GitOrigin.newGitOrigin(
        options,
        fixHttp(url, location),
        SkylarkUtil.convertOptionalString(ref),
        GitRepoType.GIT,
        stringToEnum(location, "submodules", submodules, GitOrigin.SubmoduleStrategy.class),
        includeBranchCommitLogs,
        firstParent,
        patchTransformation,
        convertDescribeVersion(describeVersion),
        convertFromNoneable(versionSelector, null));
  }

  @Nullable
  private PatchTransformation maybeGetPatchTransformation(Object patch, Location location)
      throws EvalException {
    if (EvalUtils.isNullOrNone(patch)) {
      return null;
    }
    check(location, patch instanceof PatchTransformation,
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
        ignoreErrors, useNewIntegrate());
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "mirror",
      doc = "Mirror git references between repositories",
      parameters = {
        @Param(name = "name", type = String.class, named = true, doc = "Migration name"),
        @Param(
            name = "origin",
            type = String.class,
            named = true,
            doc = "Indicates the URL of the origin git repository"),
        @Param(
            name = "destination",
            type = String.class,
            named = true,
            doc = "Indicates the URL of the destination git repository"),
        @Param(
            name = "refspecs",
            type = SkylarkList.class,
            generic1 = String.class,
            named = true,
            defaultValue = "['refs/heads/*']",
            doc =
                "Represents a list of git refspecs to mirror between origin and destination."
                    + " For example 'refs/heads/*:refs/remotes/origin/*' will mirror any reference"
                    + " inside refs/heads to refs/remotes/origin."),
        @Param(
            name = "prune",
            type = Boolean.class,
            named = true,
            doc = "Remove remote refs that don't have a origin counterpart",
            defaultValue = "False"),
        @Param(
            name = "description",
            type = String.class,
            named = true,
            noneable = true,
            positional = false,
            doc = "A description of what this workflow achieves",
            defaultValue = "None"),
      },
      useLocation = true,
      useStarlarkThread = true)
  @UsesFlags(GitMirrorOptions.class)
  public NoneType mirror(
      String name,
      String origin,
      String destination,
      SkylarkList<String> strRefSpecs,
      Boolean prune,
      Object description,
      Location location,
      StarlarkThread thread)
      throws EvalException {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    GitOptions gitOptions = options.get(GitOptions.class);
    List<Refspec> refspecs = new ArrayList<>();

    for (String refspec : SkylarkList.castList(strRefSpecs, String.class, "refspecs")) {
      try {
        refspecs.add(
            Refspec.create(
                gitOptions.getGitEnvironment(generalOptions.getEnvironment()),
                generalOptions.getCwd(),
                refspec));
      } catch (InvalidRefspecException e) {
        throw new EvalException(location, e);
      }
    }
    GlobalMigrations.getGlobalMigrations(thread)
        .addMigration(
            location,
            name,
            new Mirror(
                generalOptions,
                gitOptions,
                name,
                fixHttp(origin, location),
                fixHttp(destination, location),
                refspecs,
                options.get(GitMirrorOptions.class),
                prune,
                mainConfigFile,
                convertFromNoneable(description, null)));
    return Runtime.NONE;
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "gerrit_origin",
      doc =
          "Defines a Git origin for Gerrit reviews.\n"
              + "\n"
              + "Implicit labels that can be used/exposed:\n"
              + "\n"
              + "  - "
              + GerritChange.GERRIT_CHANGE_NUMBER_LABEL
              + ": The change number for the Gerrit review.\n"
              + "  - "
              + GerritChange.GERRIT_CHANGE_ID_LABEL
              + ": The change id for the Gerrit review.\n"
              + "  - "
              + GerritChange.GERRIT_CHANGE_DESCRIPTION_LABEL
              + ": The description of the Gerrit review.\n"
              + "  - "
              + DEFAULT_INTEGRATE_LABEL
              + ": A label that when exposed, can be used to"
              + " integrate automatically in the reverse workflow.\n"
              + "  - "
              + GerritChange.GERRIT_CHANGE_BRANCH
              + ": The destination branch for thechange\n"
              + "  - "
              + GerritChange.GERRIT_CHANGE_TOPIC
              + ": The change topic\n"
              + "  - "
              + GerritChange.GERRIT_COMPLETE_CHANGE_ID_LABEL
              + ": Complete Change-Id with project, branch and Change-Id\n"
              + "  - "
              + GerritChange.GERRIT_OWNER_EMAIL_LABEL
              + ": Owner email\n"
              + "  - GERRIT_REVIEWER_EMAIL: Multiple value field with the email of the reviewers\n"
              + "  - GERRIT_CC_EMAIL: Multiple value field with the email of the people/groups in"
              + " cc\n",
      parameters = {
        @Param(
            name = "url",
            type = String.class,
            named = true,
            doc = "Indicates the URL of the git repository"),
        @Param(
            name = "ref",
            type = String.class,
            noneable = true,
            defaultValue = "None",
            named = true,
            doc = "DEPRECATED. Use git.origin for submitted branches."),
        @Param(
            name = "submodules",
            type = String.class,
            defaultValue = "'NO'",
            named = true,
            doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
        @Param(
            name = "first_parent",
            type = Boolean.class,
            defaultValue = "True",
            named = true,
            doc =
                "If true, it only uses the first parent when looking for changes. Note that"
                    + " when disabled in ITERATIVE mode, it will try to do a migration for each"
                    + " change of the merged branch.",
            positional = false),
        @Param(
            name = "api_checker",
            type = Checker.class,
            defaultValue = "None",
            doc =
                "A checker for the Gerrit API endpoint provided for after_migration hooks. "
                    + "This field is not required if the workflow hooks don't use the "
                    + "origin/destination endpoints.",
            named = true,
            positional = false,
            noneable = true),
        @Param(
            name = PATCH_FIELD,
            type = Transformation.class,
            defaultValue = "None",
            named = true,
            positional = false,
            noneable = true,
            doc = PATCH_FIELD_DESC),
        @Param(
            name = "branch",
            type = String.class,
            defaultValue = "None",
            named = true,
            positional = false,
            noneable = true,
            doc =
                "Limit the import to"
                    + " changes that are for this branch. By default imports everything."),
        @Param(
            name = "describe_version",
            type = Boolean.class,
            defaultValue = "None",
            named = true,
            positional = false,
            doc = DESCRIBE_VERSION_FIELD_DOC,
            noneable = true)
      },
      useLocation = true)
  public GitOrigin gerritOrigin(
      String url,
      Object ref,
      String submodules,
      Boolean firstParent,
      Object checkerObj,
      Object patch,
      Object branch,
      Object describeVersion,
      Location location)
      throws EvalException {
    checkNotEmpty(url, "url", location);
    url = fixHttp(url, location);
    String refField = SkylarkUtil.convertOptionalString(ref);

    PatchTransformation patchTransformation = maybeGetPatchTransformation(patch, location);

    if (!Strings.isNullOrEmpty(refField)) {
      getGeneralConsole().warn(
          "'ref' field detected in configuration. git.gerrit_origin"
              + " is deprecating its usage for submitted changes. Use git.origin instead.");
      return GitOrigin.newGitOrigin(
          options, url, refField, GitRepoType.GERRIT,
          stringToEnum(location, "submodules",
              submodules, GitOrigin.SubmoduleStrategy.class),
          /*includeBranchCommitLogs=*/false, firstParent, patchTransformation,
          convertDescribeVersion(describeVersion), /*versionSelector=*/null);
    }
    return GerritOrigin.newGerritOrigin(
        options, url, stringToEnum(location, "submodules",
            submodules, GitOrigin.SubmoduleStrategy.class), firstParent,
        convertFromNoneable(checkerObj, null), patchTransformation,
        convertFromNoneable(branch, null),
        convertDescribeVersion(describeVersion));
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
          + "  - " + GITHUB_PR_URL + ": GitHub url of the Pull Request.\n"
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
                  + " https://developer.github.com/v4/enum/commentauthorassociation/"),
          @Param(name = "api_checker", type = Checker.class,  defaultValue = "None",
              doc = "A checker for the GitHub API endpoint provided for after_migration hooks. "
                  + "This field is not required if the workflow hooks don't use the "
                  + "origin/destination endpoints.",
              named = true, positional = false,
              noneable = true),
          @Param(name = PATCH_FIELD, type = Transformation.class, defaultValue = "None",
              named = true, positional = false, noneable = true, doc = PATCH_FIELD_DESC),
          @Param(name = "branch", type = String.class, named = true, positional = false,
              defaultValue = "None", noneable = true,
              doc = "If set, it will only migrate pull requests for this base branch"),
          @Param(name = "describe_version", type = Boolean.class, defaultValue = "None",
              named = true, positional = false, doc = DESCRIBE_VERSION_FIELD_DOC, noneable = true)},
      useLocation = true)
  @UsesFlags(GitHubPrOriginOptions.class)
  @DocDefault(field = "review_approvers", value = "[\"COLLABORATOR\", \"MEMBER\", \"OWNER\"]")
  public GitHubPROrigin githubPrOrigin(String url, Boolean merge,
      SkylarkList<String> requiredLabels, SkylarkList<String> retryableLabels, String submodules,
      Boolean baselineFromBranch, Boolean firstParent, String state,
      Object reviewStateParam, Object reviewApproversParam, Object checkerObj, Object patch,
      Object branch, Object describeVersion, Location location) throws EvalException {
    checkNotEmpty(url, "url", location);
    check(location, url.contains("github.com"), "Invalid Github URL: %s", url);
    PatchTransformation patchTransformation = maybeGetPatchTransformation(patch, location);

    String reviewStateString = convertFromNoneable(reviewStateParam, null);
    SkylarkList<String> reviewApproversStrings =
        convertFromNoneable(reviewApproversParam, null);
    ReviewState reviewState;
    ImmutableSet<AuthorAssociation> reviewApprovers;
    if (reviewStateString == null) {
      reviewState = null;
      check(location, reviewApproversStrings == null,
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
        boolean added =
            approvers.add(stringToEnum(location, "review_approvers", r, AuthorAssociation.class));
        check(location, added, "Repeated element %s", r);
      }
      reviewApprovers = ImmutableSet.copyOf(approvers);
    }

    return new GitHubPROrigin(
        fixHttp(url, location),
        merge,
        options.get(GeneralOptions.class),
        options.get(GitOptions.class),
        options.get(GitOriginOptions.class),
        options.get(GitHubOptions.class),
        options.get(GitHubPrOriginOptions.class),
        ImmutableSet.copyOf(requiredLabels),
        ImmutableSet.copyOf(retryableLabels),
        stringToEnum(location, "submodules", submodules, SubmoduleStrategy.class),
        baselineFromBranch, firstParent,
        stringToEnum(location, "state", state, StateFilter.class),
        reviewState,
        reviewApprovers,
        convertFromNoneable(checkerObj, null),
        patchTransformation,
        convertFromNoneable(branch, null),
        convertDescribeVersion(describeVersion));
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "github_origin",
      doc =
          "Defines a Git origin for a Github repository. This origin should be used for public"
              + " branches. Use "
              + GITHUB_PR_ORIGIN_NAME
              + " for importing Pull Requests.",
      parameters = {
        @Param(
            name = "url",
            type = String.class,
            named = true,
            doc = "Indicates the URL of the git repository"),
        @Param(
            name = "ref",
            type = String.class,
            noneable = true,
            defaultValue = "None",
            named = true,
            doc =
                "Represents the default reference that will be used for reading the revision "
                    + "from the git repository. For example: 'master'"),
        @Param(
            name = "submodules",
            type = String.class,
            defaultValue = "'NO'",
            named = true,
            doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
        @Param(
            name = "first_parent",
            type = Boolean.class,
            defaultValue = "True",
            named = true,
            doc =
                "If true, it only uses the first parent when looking for changes. Note that"
                    + " when disabled in ITERATIVE mode, it will try to do a migration for each"
                    + " change of the merged branch.",
            positional = false),
        @Param(
            name = PATCH_FIELD,
            type = Transformation.class,
            defaultValue = "None",
            named = true,
            positional = false,
            noneable = true,
            doc = PATCH_FIELD_DESC),
        @Param(
            name = "describe_version",
            type = Boolean.class,
            defaultValue = "None",
            named = true,
            positional = false,
            doc = DESCRIBE_VERSION_FIELD_DOC,
            noneable = true),
        @Param(
            name = "version_selector",
            type = LatestVersionSelector.class,
            defaultValue = "None",
            named = true,
            positional = false,
            doc = "Select a custom version (tag)to migrate" + " instead of 'ref'",
            noneable = true),
      },
      useLocation = true)
  public GitOrigin githubOrigin(
      String url,
      Object ref,
      String submodules,
      Boolean firstParent,
      Object patch,
      Object describeVersion,
      Object versionSelector,
      Location location)
      throws EvalException {
    check(
        location, GitHubUtil.isGitHubUrl(checkNotEmpty(url, "url", location)),
        "Invalid Github URL: %s", url);

    if (versionSelector != Runtime.NONE) {
      check(location, ref == Runtime.NONE,
          "Cannot use ref field and version_selector. Version selector will decide the ref"
              + " to migrate");
    }

    PatchTransformation patchTransformation = maybeGetPatchTransformation(patch, location);

    // TODO(copybara-team): See if we want to support includeBranchCommitLogs for GitHub repos.
    return GitOrigin.newGitOrigin(
        options,
        fixHttp(url, location),
        SkylarkUtil.convertOptionalString(ref),
        GitRepoType.GITHUB,
        stringToEnum(location, "submodules", submodules, GitOrigin.SubmoduleStrategy.class),
        /*includeBranchCommitLogs=*/ false,
        firstParent,
        patchTransformation,
        convertDescribeVersion(describeVersion),
        convertFromNoneable(versionSelector, null));
  }

  private boolean convertDescribeVersion(Object describeVersion) {
    return convertFromNoneable(describeVersion,
        options.get(GitOriginOptions.class).gitDescribeDefault);
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
          @Param(name = "tag_name", type = String.class, named = true,
              doc = "A template string that refers to a tag name. If tag_name exists, overwrite "
                  + "this tag only if flag git-tag-overwrite is set. Note that tag creation is "
                  + "best-effort and migration will succeed even if the tag cannot be created. "
                  + "Usage: Users can use a string or a string with a label. "
                  + "For instance ${label}_tag_name. And the value of label must be "
                  + "in changes' label list. Otherwise, tag won't be created.",
              defaultValue = "None", noneable = true),
          @Param(name = "tag_msg", type = String.class, named = true,
              doc = "A template string that refers to the commit msg of a tag. If set, we will "
                  + "create an annotated tag when tag_name is set. Usage: Users can use a string "
                  + "or a string with a label. For instance ${label}_message. And the value of "
                  + "label must be in changes' label list. Otherwise, tag will be created with "
                  + "sha1's commit msg.",
              defaultValue = "None", noneable = true),
          @Param(name = "fetch", type = String.class, named = true,
              doc = "Indicates the ref from which to get the parent commit. Defaults to push value"
                  + " if None",
              defaultValue = "None", noneable = true),
          @Param(name = "integrates", type = SkylarkList.class, named = true,
              generic1 = GitIntegrateChanges.class, defaultValue = "None",
              doc = "Integrate changes from a url present in the migrated change"
                  + " label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is"
                  + " present in the message", positional = false, noneable = true),
      },
      useLocation = true)
  @UsesFlags(GitDestinationOptions.class)
  public GitDestination destination(
      String url, String push, Object tagName, Object tagMsg, Object fetch, Object integrates,
      Location location)
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
        convertFromNoneable(tagName, null),
        convertFromNoneable(tagMsg, null),
        destinationOptions,
        options.get(GitOptions.class),
        generalOptions,
        new DefaultWriteHook(),
        SkylarkList.castList(convertFromNoneable(integrates, defaultGitIntegrate),
            GitIntegrateChanges.class, "integrates"));
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(name = "github_destination",
      doc = "Creates a commit in a GitHub repository branch (for example master). For creating Pull"
          + "Request use git.github_pr_destination.",
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
          @Param(name = "pr_branch_to_update", type = String.class, named = true,
              doc = "A template string that refers to a pull request branch in the same repository "
                  + "will be updated to current commit of this push branch only if "
                  + "pr_branch_to_update exists. The reason behind this field is that presubmiting "
                  + "changes creates and leaves a pull request open. By using this, we can "
                  + "automerge/close this type of pull requests. As a result, users will see this "
                  + "pr_branch_to_update as merged to this push branch. Usage: "
                  + "Users can use a string or a string with a label. For instance "
                  + "${label}_pr_branch_name. And the value of label must be in changes' label"
                  + " list. Otherwise, nothing will happen.",
              defaultValue = "None", noneable = true),
          @Param(name = "delete_pr_branch", type = Boolean.class, named = true,
              doc = "When `pr_branch_to_update` is enabled, it will delete the branch reference"
                  + " after the push to the branch and main branch (i.e master) happens. This"
                  + " allows to cleanup temporary branches created for testing.",
              noneable = true, defaultValue = "None"),
          @Param(name = "integrates", type = SkylarkList.class, named = true,
              generic1 = GitIntegrateChanges.class, defaultValue = "None",
              doc = "Integrate changes from a url present in the migrated change"
                  + " label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is"
                  + " present in the message", positional = false, noneable = true),
          @Param(name = "api_checker", type = Checker.class,  defaultValue = "None",
              doc = "A checker for the Gerrit API endpoint provided for after_migration hooks. "
                  + "This field is not required if the workflow hooks don't use the "
                  + "origin/destination endpoints.",
              named = true, positional = false,
              noneable = true),
      },
      useLocation = true)
  @UsesFlags(GitDestinationOptions.class)
  // Used to detect in the future users that don't set it and change the default
  @DocDefault(field = "delete_pr_branch", value = "False")
  public GitDestination gitHubDestination(String url, String push, Object fetch,
      Object prBranchToUpdate, Object deletePrBranchParam, Object integrates, Object checker,
      Location location) throws EvalException {
    GitDestinationOptions destinationOptions = options.get(GitDestinationOptions.class);
    String resolvedPush = checkNotEmpty(firstNotNull(destinationOptions.push, push),
        "push", location);
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    String repoUrl = fixHttp(checkNotEmpty(
        firstNotNull(destinationOptions.url, url), "url", location), location);
    String branchToUpdate = convertFromNoneable(prBranchToUpdate, null);
    Boolean deletePrBranch = convertFromNoneable(deletePrBranchParam, null);
    check(location, branchToUpdate != null || deletePrBranch == null,
        "'delete_pr_branch' can only be set if 'pr_branch_to_update' is used");
    GitHubOptions gitHubOptions = options.get(GitHubOptions.class);
    WorkflowOptions workflowOptions = options.get(WorkflowOptions.class);

    String effectivePrBranchToUpdate = branchToUpdate;
    if (options.get(WorkflowOptions.class).isInitHistory()) {
      generalOptions
          .console()
          .infoFmt("Ignoring field 'pr_branch_to_update' as '--init-history' is set.");
      effectivePrBranchToUpdate = null;
    }
    // First flag has priority, then field, and then (for now) we set it to false.
    // TODO(malcon): Once this is stable the default will be 'branchToUpdate != null'
    boolean effectiveDeletePrBranch =
        gitHubOptions.gitHubDeletePrBranch != null
            ? gitHubOptions.gitHubDeletePrBranch
            : deletePrBranch != null ? deletePrBranch : false;
    return new GitDestination(
        repoUrl,
        checkNotEmpty(
            firstNotNull(destinationOptions.fetch,
                convertFromNoneable(fetch, null),
                resolvedPush),
            "fetch", location),
        resolvedPush,
        /*tagName*/null,
        /*tagMsg*/null,
        destinationOptions,
        options.get(GitOptions.class),
        generalOptions,
        new GitHubWriteHook(
            generalOptions,
            repoUrl,
            gitHubOptions,
            effectivePrBranchToUpdate,
            effectiveDeletePrBranch,
            getGeneralConsole(),
            convertFromNoneable(checker, null)),
        SkylarkList.castList(convertFromNoneable(integrates, defaultGitIntegrate),
            GitIntegrateChanges.class, "integrates"));
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "github_pr_destination",
      doc = "Creates changes in a new pull request in the destination.",
      parameters = {
        @Param(
            name = "url",
            type = String.class,
            named = true,
            doc =
                "Url of the GitHub project. For example"
                    + " \"https://github.com/google/copybara'\""),
        @Param(
            name = "destination_ref",
            type = String.class,
            named = true,
            doc = "Destination reference for the change. By default 'master'",
            defaultValue = "\"master\""),
        @Param(
            name = "pr_branch",
            type = String.class,
            defaultValue = "None",
            noneable = true,
            named = true,
            positional = false,
            doc =
                "Customize the pull request branch. Any variable present in the message in the "
                    + "form of ${CONTEXT_REFERENCE} will be replaced by the corresponding stable "
                    + "reference (head, PR number, Gerrit change number, etc.)."),
        @Param(
            name = "title",
            type = String.class,
            defaultValue = "None",
            noneable = true,
            named = true,
            positional = false,
            doc =
                "When creating (or updating if `update_description` is set) a pull request, use"
                    + " this title. By default it uses the change first line. This field accepts"
                    + " a template with labels. For example: `\"Change ${CONTEXT_REFERENCE}\"`"),
        @Param(
            name = "body",
            type = String.class,
            defaultValue = "None",
            noneable = true,
            named = true,
            positional = false,
            doc =
                "When creating (or updating if `update_description` is set) a pull request, use"
                    + " this body. By default it uses the change summary. This field accepts"
                    + " a template with labels. For example: `\"Change ${CONTEXT_REFERENCE}\"`"),
        @Param(
            name = "integrates",
            type = SkylarkList.class,
            named = true,
            generic1 = GitIntegrateChanges.class,
            defaultValue = "None",
            doc =
                "Integrate changes from a url present in the migrated change"
                    + " label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is"
                    + " present in the message",
            positional = false,
            noneable = true),
        @Param(
            name = "api_checker",
            type = Checker.class,
            defaultValue = "None",
            doc =
                "A checker for the GitHub API endpoint provided for after_migration hooks. "
                    + "This field is not required if the workflow hooks don't use the "
                    + "origin/destination endpoints.",
            named = true,
            positional = false,
            noneable = true),
          @Param(
              name = "update_description",
              type = Boolean.class,
              defaultValue = "False",
              named = true,
              positional = false,
              doc = "By default, Copybara only set the title and body of the PR when creating"
                  + " the PR. If this field is set to true, it will update those fields for"
                  + " every update."),
      },
      useLocation = true)
  @UsesFlags({GitDestinationOptions.class, GitHubDestinationOptions.class})
  @Example(
      title = "Common usage",
      before = "Create a branch by using copybara's computerIdentity algorithm:",
      code =
          "git.github_pr_destination(\n"
              + "        url = \"https://github.com/google/copybara\",\n"
              + "        destination_ref = \"master\",\n"
              + "    )")
  @Example(
      title = "Using pr_branch with label",
      before = "Customize pr_branch with context reference:",
      code =
          "git.github_pr_destination(\n"
              + "        url = \"https://github.com/google/copybara\",\n"
              + "         destination_ref = \"master\",\n"
              + "         pr_branch = 'test_${CONTEXT_REFERENCE}',\n"
              + "    )")
  @Example(
      title = "Using pr_branch with constant string",
      before = "Customize pr_branch with a constant string:",
      code =
          "git.github_pr_destination(\n"
              + "        url = \"https://github.com/google/copybara\",\n"
              + "        destination_ref = \"master\",\n"
              + "        pr_branch = 'test_my_branch',\n"
              + "    )")
  public GitHubPrDestination githubPrDestination(
      String url,
      String destinationRef,
      Object prBranch,
      Object title,
      Object body,
      Object integrates,
      Object checkerObj,
      Boolean updateDescription,
      Location location)
      throws EvalException {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    // This restricts to github.com, we will have to revisit this to support setups like GitHub
    // Enterprise.
    check(location, GitHubUtil.isGitHubUrl(url), "'%s' is not a valid GitHub url", url);
    GitDestinationOptions destinationOptions = options.get(GitDestinationOptions.class);
    return new GitHubPrDestination(
        fixHttp(url, location),
        destinationRef,
        convertFromNoneable(prBranch, null),
        generalOptions,
        options.get(GitHubOptions.class),
        destinationOptions,
        options.get(GitHubDestinationOptions.class),
        options.get(GitOptions.class),
        new DefaultWriteHook(),
        SkylarkList.castList(
            convertFromNoneable(integrates, defaultGitIntegrate),
            GitIntegrateChanges.class,
            "integrates"),
        convertFromNoneable(title, null),
        convertFromNoneable(body, null),
        mainConfigFile,
        convertFromNoneable(checkerObj, null),
        updateDescription);
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
          @Param(name = "notify", type = String.class,
              named = true,
              doc =
                  ""
                      + "Type of Gerrit notify option (https://gerrit-review.googlesource.com/Docum"
                      + "entation/user-upload.html#notify). Sends notifications by default.",
              defaultValue = "None",
              noneable = true
          ),
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
                  + "The element in the list is: an email, for example: \"foo@example.com\" or "
                  + "label for example: ${SOME_GERRIT_REVIEWER}. These are under the condition of "
                  + "assuming that users have registered to gerrit repos"),
          @Param(name = "cc", type = SkylarkList.class, named = true,
              defaultValue = "[]",
              doc = "The list of the email addresses or users that will be CCed in the review. Can"
                  + " use labels as the `reviewers` field."),
          @Param(name = "labels", type = SkylarkList.class, named = true,
              defaultValue = "[]",
              doc = "The list of labels to be pushed with the change. The format is the label "
                  + "along with the associated value. For example: Run-Presubmit+1"),
          @Param(name = "api_checker", type = Checker.class,  defaultValue = "None",
              doc = "A checker for the Gerrit API endpoint provided for after_migration hooks. "
                  + "This field is not required if the workflow hooks don't use the "
                  + "origin/destination endpoints.",
              named = true, positional = false,
              noneable = true),
          @Param(name = "integrates", type = SkylarkList.class, named = true,
              generic1 = GitIntegrateChanges.class, defaultValue = "None",
              doc = "Integrate changes from a url present in the migrated change"
                  + " label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is"
                  + " present in the message", positional = false, noneable = true),
          @Param(
              name = "topic",
              type = String.class,
              defaultValue = "None",
              noneable = true,
              named = true,
              positional = false,
              doc =
                  ""
                      + "Sets the topic of the Gerrit change created.<br><br>"
                      + "By default it sets no topic. This field accepts a template with labels. "
                      + "For example: `\"topic_${CONTEXT_REFERENCE}\"`"),
      },
      useLocation = true)
  @UsesFlags(GitDestinationOptions.class)
  @DocDefault(field = "push_to_refs_for", value = "fetch value")
  public GerritDestination gerritDestination(
      String url, String fetch, Object pushToRefsFor, Boolean submit, Object notifyOptionObj,
      String changeIdPolicy, Boolean allowEmptyPatchSet, SkylarkList<String> reviewers,
      SkylarkList<String> ccParam, SkylarkList<String> labelsParam,
      Object checkerObj, Object integrates, Object topicObj, Location location) throws EvalException {
    checkNotEmpty(url, "url", location);

    List<String> newReviewers = SkylarkUtil.convertStringList(reviewers, "reviewers");
    List<String> cc = SkylarkUtil.convertStringList(ccParam, "cc");
    List<String> labels = SkylarkUtil.convertStringList(labelsParam, "labels");

    String notifyOptionStr = convertFromNoneable(notifyOptionObj, null);
    check(location, !(submit && notifyOptionStr != null),
        "Cannot set 'notify' with 'submit = True' in git.gerrit_destination().");

    String topicStr = convertFromNoneable(topicObj, null);
    check(location, !(submit && topicStr != null),
        "Cannot set 'topic' with 'submit = True' in git.gerrit_destination().");
    NotifyOption notifyOption =
        notifyOptionStr == null
            ? null
            : stringToEnum(location, "notify", notifyOptionStr, NotifyOption.class);
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
        notifyOption,
        stringToEnum(location, "change_id_policy", changeIdPolicy, ChangeIdPolicy.class),
        allowEmptyPatchSet,
        newReviewers,
        cc,
        labels,
        convertFromNoneable(checkerObj, null),
        SkylarkList.castList(convertFromNoneable(integrates, defaultGitIntegrate),
            GitIntegrateChanges.class, "integrates"),
        topicStr);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
    name = GITHUB_API,
    doc =
        "Defines a feedback API endpoint for GitHub, that exposes relevant GitHub API operations.",
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
              + "Defines a feedback API endpoint for Gerrit, that exposes relevant Gerrit API "
              + "operations.",
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
      doc = "Defines a feedback trigger based on updates on a Gerrit change.",
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
      doc = "Defines a feedback trigger based on updates on a GitHub PR.",
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
        @Param(
            name = "events",
            type = SkylarkList.class,
            generic1 = String.class,
            named = true,
            defaultValue = "[]",
            doc =
                "Type of events to subscribe. Valid values are: `'ISSUES'`, `'ISSUE_COMMENT'`,"
                    + " `'PULL_REQUEST'`,  `'PULL_REQUEST_REVIEW_COMMENT'`, `'PUSH'`,"
                    + " `'STATUS'`, "),
      },
      useLocation = true)
  @UsesFlags(GitHubOptions.class)
  public GitHubTrigger gitHubTrigger(
      String url, Object checkerObj, SkylarkList<String> events, Location location)
      throws EvalException {
    checkNotEmpty(url, "url", location);
    url = fixHttp(url, location);
    Checker checker = convertFromNoneable(checkerObj, null);
    LinkedHashSet<GitHubEventType> eventBuilder = new LinkedHashSet<>();
    for (String e : events) {
      GitHubEventType event = stringToEnum(location, "events", e, GitHubEventType.class);
      check(location, eventBuilder.add(event), "Repeated element %s", e);
      check(location, WATCHABLE_EVENTS.contains(event),
          "%s is not a valid value. Values: %s", event, WATCHABLE_EVENTS);
    }
    check(location, !eventBuilder.isEmpty(), "events cannot be empty");

    ImmutableSet<GitHubEventType> parsedEvents = ImmutableSet.copyOf(eventBuilder);
    validateEndpointChecker(location, checker, GITHUB_TRIGGER);
    GitHubOptions gitHubOptions = options.get(GitHubOptions.class);
    return new GitHubTrigger(gitHubOptions.newGitHubApiSupplier(url, checker), url,
        parsedEvents, getGeneralConsole());
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
    return SetReviewInput.create(convertFromNoneable(message, null),
        SkylarkDict.castSkylarkDictOrNoneToDict(
            labels, String.class, Integer.class, "Gerrit review labels"));
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "latest_version",
      doc = "Customize what version of the available branches and tags to pick."
          + " By default it ignores the reference passed as parameter. Using `force:reference`"
          + " in the CLI will force to use that reference instead.",
      parameters = {
          @Param(
              name = "refspec_format",
              type = String.class,
              doc = "The format of of the branch/tag",
              named = true,
              defaultValue = "\"refs/tags/${n0}.${n1}.${n2}\"", noneable = true),
          @Param(name = "refspec_groups", named = true, type = SkylarkDict.class,
              doc = "A set of named regexes that can be used to match part of the versions."
                  + "Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax."
                  + " Use the following nomenclature n0, n1, n2 for the version part (will use"
                  + " numeric sorting) or s0, s1, s2 (alphabetic sorting). Note that there can"
                  + " be mixed but the numbers cannot be repeated. In other words n0, s1, n2 is"
                  + " valid but not n0, s0, n1. n0 has more priority than n1. If there are fields"
                  + " where order is not important, use s(N+1) where N ist he latest sorted field."
                  + " Example {\"n0\": \"[0-9]+\", \"s1\": \"[a-z]+\"}",
              defaultValue = "{'n0' : '[0-9]+', 'n1' : '[0-9]+', 'n2' : '[0-9]+'}"),
      },
      useLocation = true)
  public LatestVersionSelector versionSelector(String refspec,SkylarkDict<String, String> groups,
      Location location) throws EvalException {
    check(location, refspec.startsWith("refs/"), "Wrong value '%s'. Refspec has to"
        + " start with 'refs/'. For example 'refs/tags/${v0}.${v1}.${v2}'");

    TreeMap<Integer, VersionElementType> elements = new TreeMap<>();
    Pattern regexKey = Pattern.compile("([sn])([0-9])");
    for (String s : groups.keySet()) {
      Matcher matcher = regexKey.matcher(s);
      check(location, matcher.matches(), "Incorrect key for refspec_group. Should be in the "
          + "format of n0, n1, etc. or s0, s1, etc. Value: %s", s);
      VersionElementType type = matcher.group(1).equals("s") ? ALPHABETIC : NUMERIC;
      int num = Integer.parseInt(matcher.group(2));
      check(location, !elements.containsKey(num) || elements.get(num) == type,
          "Cannot use same n in both s%s and n%s: %s", num, num, s);
      elements.put(num, type);
    }
    for (Integer num : elements.keySet()) {
      if (num > 0 ) {
        check(location, elements.containsKey(num -1), "Cannot have s%s or n%s if s%s or n%s"
            + " doesn't exist", num, num, num -1 , num -1);
      }
    }

    return new LatestVersionSelector(
        refspec, Replace.parsePatterns(location, groups), elements, location);
  }

  @Override
  public void setConfigFile(ConfigFile mainConfigFile, ConfigFile currentConfigFile) {
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
