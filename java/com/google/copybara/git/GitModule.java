/*
 * Copyright (C) 2016 Google LLC
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.copybara.config.SkylarkUtil.check;
import static com.google.copybara.config.SkylarkUtil.checkNotEmpty;
import static com.google.copybara.config.SkylarkUtil.convertFromNoneable;
import static com.google.copybara.config.SkylarkUtil.convertToOptional;
import static com.google.copybara.config.SkylarkUtil.stringToEnum;
import static com.google.copybara.git.GitHubPrOrigin.GITHUB_BASE_BRANCH;
import static com.google.copybara.git.GitHubPrOrigin.GITHUB_BASE_BRANCH_SHA1;
import static com.google.copybara.git.GitHubPrOrigin.GITHUB_PR_ASSIGNEE;
import static com.google.copybara.git.GitHubPrOrigin.GITHUB_PR_BODY;
import static com.google.copybara.git.GitHubPrOrigin.GITHUB_PR_HEAD_SHA;
import static com.google.copybara.git.GitHubPrOrigin.GITHUB_PR_REVIEWER_APPROVER;
import static com.google.copybara.git.GitHubPrOrigin.GITHUB_PR_REVIEWER_OTHER;
import static com.google.copybara.git.GitHubPrOrigin.GITHUB_PR_TITLE;
import static com.google.copybara.git.GitHubPrOrigin.GITHUB_PR_URL;
import static com.google.copybara.git.GitHubPrOrigin.GITHUB_PR_USER;
import static com.google.copybara.git.GitHubPrOrigin.GITHUB_PR_USE_MERGE;
import static com.google.copybara.git.GitOptions.USE_CREDENTIALS_FROM_CONFIG;
import static com.google.copybara.git.github.api.GitHubEventType.WATCHABLE_EVENTS;
import static com.google.copybara.git.github.util.GitHubHost.GITHUB_COM;
import static com.google.copybara.version.LatestVersionSelector.VersionElementType.ALPHABETIC;
import static com.google.copybara.version.LatestVersionSelector.VersionElementType.NUMERIC;
import static java.util.Arrays.stream;

import com.google.common.base.Enums;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSetMultimap.Builder;
import com.google.copybara.EndpointProvider;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LazyResourceLoader;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.Transformation;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.action.Action;
import com.google.copybara.action.StarlarkAction;
import com.google.copybara.approval.ApprovalsProvider;
import com.google.copybara.approval.NoneApprovedProvider;
import com.google.copybara.checks.Checker;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.GlobalMigrations;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.credentials.CredentialModule.UsernamePasswordIssuer;
import com.google.copybara.doc.annotations.DocDefault;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GerritDestination.ChangeIdPolicy;
import com.google.copybara.git.GerritDestination.NotifyOption;
import com.google.copybara.git.GitDestination.WriterImpl.DefaultWriteHook;
import com.google.copybara.git.GitHubPrOrigin.ReviewState;
import com.google.copybara.git.GitHubPrOrigin.StateFilter;
import com.google.copybara.git.GitIntegrateChanges.Strategy;
import com.google.copybara.git.GitLabMrDestination.GitLabMrDestinationParams;
import com.google.copybara.git.GitOrigin.SubmoduleStrategy;
import com.google.copybara.git.gerritapi.GerritEventType;
import com.google.copybara.git.gerritapi.NotifyType;
import com.google.copybara.git.gerritapi.SetReviewInput;
import com.google.copybara.git.github.api.AuthorAssociation;
import com.google.copybara.git.github.api.CheckRun.Conclusion;
import com.google.copybara.git.github.api.GitHubEventType;
import com.google.copybara.git.github.api.GitHubGraphQLApi.GetCommitHistoryParams;
import com.google.copybara.git.github.util.GitHubUtil;
import com.google.copybara.git.gitlab.GitLabOptions;
import com.google.copybara.git.gitlab.api.entities.MergeRequest.DetailedMergeStatus;
import com.google.copybara.transform.Replace;
import com.google.copybara.transform.patch.PatchTransformation;
import com.google.copybara.util.RepositoryUtil;
import com.google.copybara.util.console.Console;
import com.google.copybara.version.LatestVersionSelector;
import com.google.copybara.version.LatestVersionSelector.VersionElementType;
import com.google.copybara.version.OrderedVersionSelector;
import com.google.copybara.version.RequestedVersionSelector;
import com.google.copybara.version.VersionSelector;
import com.google.copybara.version.VersionSelector.SearchPattern;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkThread.PrintHandler;
import net.starlark.java.eval.StarlarkValue;
import net.starlark.java.syntax.Location;

/** Main module that groups all the functions that create Git origins and destinations. */
@StarlarkBuiltin(name = "git", doc = "Set of functions to define Git origins and destinations.")
@UsesFlags({
    GitOptions.class,
})
public class GitModule implements LabelsAwareModule, StarlarkValue {

  static final String DEFAULT_INTEGRATE_LABEL = "COPYBARA_INTEGRATE_REVIEW";
  public static final String CREDENTIAL_DOC =
      "EXPERIMENTAL: Read credentials from config file to access the Git Repo. This expects a"
          + " 'credentials.username_password' specifying the username to use for the remote git"
          + " host and a password or token. This is gated by the '"
          + USE_CREDENTIALS_FROM_CONFIG
          + "' flag";

  public static final String GITLAB_CREDENTIAL_DOC =
      "Read credentials from config file to access the GitLab Repo. This expects a"
          + " `credentials.username_password` specifying the username to use for the remote GitLab"
          + " host and a password or token.";
  private final Sequence<GitIntegrateChanges> defaultGitIntegrate;
  private static final String GERRIT_TRIGGER = "gerrit_trigger";
  private static final String GERRIT_API = "gerrit_api";
  private static final String GITHUB_TRIGGER = "github_trigger";
  private static final String GITHUB_API = "github_api";
  private static final String PATCH_FIELD = "patch";
  private static final String PATCH_FIELD_DESC =
      "Patch the checkout dir. The difference with `patch.apply` transformation is"
          + " that here we can apply it using three-way";
  private static final String DESCRIBE_VERSION_FIELD_DOC =
      "Download tags and use 'git describe' to create four labels with a meaningful version"
          + " identifier:<br><br>  - `GIT_DESCRIBE_CHANGE_VERSION`: The version for the change or"
          + " changes being migrated. The value changes per change in `ITERATIVE` mode and will be"
          + " the latest migrated change in `SQUASH` (In other words, doesn't include excluded"
          + " changes). this is normally what users want to use.<br> -"
          + " `GIT_DESCRIBE_REQUESTED_VERSION`: `git describe` for the requested/head version."
          + " Constant in `ITERATIVE` mode and includes filtered changes.<br> "
          + " -`GIT_DESCRIBE_FIRST_PARENT`: `git describe` for the first parent version.<br> "
          + " -`GIT_SEQUENTIAL_REVISION_NUMBER`: The sequential number of the commit. Falls back to"
          + " the SHA1 if not applicable.<br>";

  /**
   * Primary branch name that will be ignored if autodetect is enabled.
   */
  public static final ImmutableSet<String> PRIMARY_BRANCHES = ImmutableSet.of("master", "main");

  protected final Options options;
  protected ConfigFile mainConfigFile;
  private String workflowName;
  private StarlarkThread.PrintHandler printHandler;

  public GitModule(Options options) {
    this.options = Preconditions.checkNotNull(options);
    this.defaultGitIntegrate =
        StarlarkList.of(
            /*mutability=*/ null,
            new GitIntegrateChanges(
                DEFAULT_INTEGRATE_LABEL,
                Strategy.FAKE_MERGE_AND_INCLUDE_FILES,
                /*ignoreErrors=*/ true));
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
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
        @Param(name = "url", named = true, doc = "Indicates the URL of the git repository"),
        @Param(
            name = "ref",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            doc =
                "Represents the default reference that will be used for reading the revision "
                    + "from the git repository. For example: 'master'"),
        @Param(
            name = "submodules",
            defaultValue = "'NO'",
            named = true,
            positional = false,
            doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
        @Param(
            name = "excluded_submodules",
            defaultValue = "[]",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
            },
            named = true,
            positional = false,
            doc =
                "A list of names (not paths, e.g. \"foo\" is the submodule name if [submodule"
                    + " \"foo\"] appears in the .gitmodules file) of submodules that will not be"
                    + " download even if 'submodules' is set to YES or RECURSIVE. "),
        @Param(
            name = "include_branch_commit_logs",
            defaultValue = "False",
            named = true,
            positional = false,
            doc =
                "Whether to include raw logs of branch commits in the migrated change message."
                    + "WARNING: This field is deprecated in favor of 'first_parent' one."
                    + " This setting *only* affects merge commits."),
        @Param(
            name = "first_parent",
            defaultValue = "True",
            named = true,
            positional = false,
            doc =
                "If true, it only uses the first parent when looking for changes. Note that"
                    + " when disabled in ITERATIVE mode, it will try to do a migration for each"
                    + " change of the merged branch."),
        @Param(
            name = "partial_fetch",
            defaultValue = "False",
            named = true,
            positional = false,
            doc = "If true, partially fetch git repository by only fetching affected files."),
        @Param(
            name = PATCH_FIELD,
            allowedTypes = {
              @ParamType(type = Transformation.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = PATCH_FIELD_DESC),
        @Param(
            name = "describe_version",
            allowedTypes = {
              @ParamType(type = Boolean.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = DESCRIBE_VERSION_FIELD_DOC),
        @Param(
            name = "version_selector",
            allowedTypes = {
              @ParamType(type = VersionSelector.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc =
                "Select a custom version (tag)to migrate"
                    + " instead of 'ref'. Version"
                    + " selector is expected to match the whole refspec (e.g. 'refs/heads/${n1}')"),
        @Param(
            name = "primary_branch_migration",
            allowedTypes = {
              @ParamType(type = Boolean.class),
            },
            defaultValue = "False",
            named = true,
            positional = false,
            doc =
                "When enabled, copybara will ignore the 'ref' param if it is 'master' or 'main' and"
                    + " instead try to establish the default git branch. If this fails, it will"
                    + " fall back to the 'ref' param.\n"
                    + "This is intended to help migrating to the new standard of using 'main'"
                    + " without breaking users relying on the legacy default."),
        @Param(
            name = "credentials",
            allowedTypes = {
              @ParamType(type = UsernamePasswordIssuer.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = CREDENTIAL_DOC)
      },
      useStarlarkThread = true)
  @UsesFlags(GitOriginOptions.class)
  public GitOrigin origin(
      String url,
      Object ref,
      String submodules,
      Object excludedSubmodules,
      Boolean includeBranchCommitLogs,
      Boolean firstParent,
      Boolean partialFetch,
      Object patch,
      Object describeVersion,
      Object versionSelector,
      Boolean primaryBranchMigration,
      @Nullable Object credentials,
      StarlarkThread thread)
      throws EvalException {
    checkNotEmpty(url, "url");
    PatchTransformation patchTransformation = maybeGetPatchTransformation(patch);

    if (versionSelector != Starlark.NONE) {
      check(
          ref == Starlark.NONE,
          "Cannot use ref field and version_selector. Version selector will decide the ref"
              + " to migrate");
    }

    List<String> excludedSubmoduleList =
        Sequence.cast(excludedSubmodules, String.class, "excluded_submodules");
    checkSubmoduleConfig(submodules, excludedSubmoduleList);
    String fixedUrl = fixHttp(url, thread.getCallerLocation());
    CredentialFileHandler credentialHandler = getCredentialHandler(fixedUrl, credentials);
    return GitOrigin.newGitOrigin(
        options,
        fixedUrl,
        SkylarkUtil.convertOptionalString(ref),
        GitRepoType.GIT,
        stringToEnum("submodules", submodules, GitOrigin.SubmoduleStrategy.class),
        excludedSubmoduleList,
        includeBranchCommitLogs,
        firstParent,
        partialFetch,
        primaryBranchMigration,
        patchTransformation,
        convertDescribeVersion(describeVersion),
        validateVersionSelector(versionSelector),
        mainConfigFile.path(),
        workflowName,
        GITHUB_COM.isGitHubUrl(url)
            ? githubPostSubmitApprovalsProvider(
                fixedUrl, SkylarkUtil.convertOptionalString(ref), credentialHandler)
            : approvalsProvider(url),
        /* enableLfs= */ false,
        credentialHandler
    );
  }

  @Nullable
  private VersionSelector validateVersionSelector(Object versionSelector) throws EvalException {
    VersionSelector selector = convertFromNoneable(versionSelector, null);

    if (selector == null) {
      return  null;
    }

    for (SearchPattern searchPattern : selector.searchPatterns()) {
      if (searchPattern.isNone() || searchPattern.isAll()) {
        continue;
      }
      check(searchPattern.tokens().get(0).getValue().startsWith("refs/"),
          "Git version selector matches complete references (e.g. 'refs/tags/${n})'. The"
              + " version selector provided doesn't start with the 'refs/' prefix: %s", selector);
    }
    return selector;
  }

  @Nullable
  private PatchTransformation maybeGetPatchTransformation(Object patch) throws EvalException {
    if (Starlark.isNullOrNone(patch)) {
      return null;
    }
    check(
        patch instanceof PatchTransformation,
        "'%s' is not a patch.apply(...) transformation",
        PATCH_FIELD);
    return  (PatchTransformation) patch;
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "integrate",
      doc = "Integrate changes from a url present in the migrated change label.",
      parameters = {
        @Param(
            name = "label",
            named = true,
            doc = "The migration label that will contain the url to the change to integrate.",
            defaultValue = "\"" + DEFAULT_INTEGRATE_LABEL + "\""),
        @Param(
            name = "strategy",
            named = true,
            defaultValue = "\"FAKE_MERGE_AND_INCLUDE_FILES\"",
            doc =
                "How to integrate the change:<br><ul> <li><b>'FAKE_MERGE'</b>: Add the url"
                    + " revision/reference as parent of the migration change but ignore all the"
                    + " files from the url. The commit message will be a standard merge one but"
                    + " will include the corresponding RevId label</li>"
                    + " <li><b>'FAKE_MERGE_AND_INCLUDE_FILES'</b>: Same as 'FAKE_MERGE' but any"
                    + " change to files that doesn't match destination_files will be included as"
                    + " part of the merge commit. So it will be a semi fake merge: Fake for"
                    + " destination_files but merge for non destination files.</li>"
                    + " <li><b>'INCLUDE_FILES'</b>: Same as 'FAKE_MERGE_AND_INCLUDE_FILES' but it"
                    + " it doesn't create a merge but only include changes not matching"
                    + " destination_files</li></ul>"),
        @Param(
            name = "ignore_errors",
            named = true,
            doc =
                "If we should ignore integrate errors and continue the migration without the"
                    + " integrate",
            defaultValue = "True"),
      })
  @Example(
      title = "Integrate changes from a review url",
      before = "Assuming we have a git.destination defined like this:",
      code =
          "git.destination(\n"
              + "        url = \"https://example.com/some_git_repo\",\n"
              + "        integrates = [git.integrate()],\n"
              + "\n"
              + ")",
      after =
          "It will look for `"
              + DEFAULT_INTEGRATE_LABEL
              + "` label during the worklow migration. If the label"
              + " is found, it will fetch the git url and add that change as an additional parent"
              + " to the migration commit (merge). It will fake-merge any change from the url that"
              + " matches destination_files but it will include changes not matching it.")
  public GitIntegrateChanges integrate(String label, String strategy, Boolean ignoreErrors)
      throws EvalException {
    return new GitIntegrateChanges(
        label, stringToEnum("strategy", strategy, Strategy.class), ignoreErrors);
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "mirror",
      doc = "Mirror git references between repositories",
      parameters = {
        @Param(name = "name", named = true, doc = "Migration name"),
        @Param(
            name = "origin",
            named = true,
            doc = "Indicates the URL of the origin git repository"),
        @Param(
            name = "destination",
            named = true,
            doc = "Indicates the URL of the destination git repository"),
        @Param(
            name = "refspecs",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
            },
            named = true,
            defaultValue = "['refs/heads/*']",
            doc =
                "Represents a list of git refspecs to mirror between origin and destination."
                    + " For example 'refs/heads/*:refs/remotes/origin/*' will mirror any reference"
                    + " inside refs/heads to refs/remotes/origin."),
        @Param(
            name = "prune",
            named = true,
            doc =
                "Remove remote refs that don't have a origin counterpart. Prune is ignored if"
                    + " actions are used (Action is in charge of doing the pruning)",
            defaultValue = "False"),
        @Param(
            name = "partial_fetch",
            defaultValue = "False",
            named = true,
            positional = false,
            doc = "This is an experimental feature that only works for certain origin globs."),
        @Param(
            name = "description",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            positional = false,
            doc = "A description of what this migration achieves",
            defaultValue = "None"),
        @Param(
            name = "action",
            doc =
                "An action to execute when the migration is triggered. Actions can fetch,"
                    + " push, rebase, merge, etc. Only fetches/pushes for the declared refspec"
                    + " are allowed.",
            defaultValue = "None",
            positional = false,
            named = true),
        @Param(
            name = "origin_checker",
            doc =
                "Checker for applicable gerrit or github apis that can be inferred from the"
                    + " origin url. You can omit this if there no intention to use"
                    + " aforementioned APIs.",
            defaultValue = "None",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            positional = false,
            named = true),
        @Param(
            name = "destination_checker",
            doc =
                "Checker for applicable gerrit or github apis that can be inferred from the"
                    + " destination url. You can omit this if there no intention to use"
                    + " aforementioned APIs.",
            defaultValue = "None",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            positional = false,
            named = true),
        @Param(
            name = "origin_credentials",
            allowedTypes = {
              @ParamType(type = UsernamePasswordIssuer.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = CREDENTIAL_DOC),
        @Param(
            name = "destination_credentials",
            allowedTypes = {
              @ParamType(type = UsernamePasswordIssuer.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = CREDENTIAL_DOC)
      },
      useStarlarkThread = true)
  @UsesFlags(GitMirrorOptions.class)
  public NoneType mirror(
      String name,
      String origin,
      String destination,
      Sequence<?> strRefSpecs, // <String>
      Boolean prune,
      Boolean partialFetch,
      Object description,
      Object rawAction,
      Object rawOriginChecker,
      Object rawDestinationChecker,
      @Nullable Object originCreds,
      @Nullable Object destinationCreds,
      StarlarkThread thread)
      throws EvalException {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    GitOptions gitOptions = options.get(GitOptions.class);
    List<Refspec> refspecs = new ArrayList<>();

    for (String refspec : Sequence.cast(strRefSpecs, String.class, "refspecs")) {
      try {
        refspecs.add(
            Refspec.create(
                gitOptions.getGitEnvironment(generalOptions.getEnvironment()),
                generalOptions.getCwd(),
                refspec));
      } catch (InvalidRefspecException e) {
        throw Starlark.errorf("%s", e.getMessage());
      }
    }
    String fixedOriginHttp = fixHttp(origin, thread.getCallerLocation());
    String fixedDestinationHttp = fixHttp(destination, thread.getCallerLocation());

    CredentialFileHandler originCredential = getCredentialHandler(fixedOriginHttp, originCreds);
    CredentialFileHandler destinationCredential =
        getCredentialHandler(fixedDestinationHttp, destinationCreds);

    ImmutableList<CredentialFileHandler> creds =
        stream(new CredentialFileHandler[] {originCredential, destinationCredential})
            .filter(c -> c != null)
            .collect(toImmutableList());
    Checker originChecker = convertFromNoneable(rawOriginChecker, null);
    Checker destinationChecker = convertFromNoneable(rawDestinationChecker, null);
    Action action = rawAction != Starlark.NONE ? maybeWrapAction(printHandler, rawAction) : null;
    Module module = Module.ofInnermostEnclosingStarlarkFunction(thread);
    GlobalMigrations.getGlobalMigrations(module)
        .addMigration(
            name,
            new Mirror(
                generalOptions,
                gitOptions,
                name,
                fixedOriginHttp,
                fixedDestinationHttp,
                refspecs,
                options.get(GitDestinationOptions.class),
                prune,
                partialFetch,
                mainConfigFile,
                convertFromNoneable(description, null),
                action,
                getEndpointProvider(
                    fixedOriginHttp, originChecker, originCredential, false, thread),
                getEndpointProvider(
                    fixedDestinationHttp, destinationChecker, destinationCredential, false, thread),
                creds));
    return Starlark.NONE;
  }

  private static Action maybeWrapAction(PrintHandler printHandler, Object action)
      throws EvalException {
    if (action instanceof StarlarkCallable) {
      return new StarlarkAction(((StarlarkCallable) action).getName(),
          (StarlarkCallable) action, Dict.empty(), printHandler);
    } else if (action instanceof Action) {
      return (Action) action;
    } else {
      throw Starlark.errorf(
          "Invalid feedback action '%s 'of type: %s", action, action.getClass());
    }
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
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
        @Param(name = "url", named = true, doc = "Indicates the URL of the git repository"),
        @Param(
            name = "ref",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            doc = "DEPRECATED. Use git.origin for submitted branches."),
        @Param(
            name = "submodules",
            defaultValue = "'NO'",
            named = true,
            doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
        @Param(
            name = "excluded_submodules",
            defaultValue = "[]",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
            },
            named = true,
            positional = false,
            doc =
                "A list of names (not paths, e.g. \"foo\" is the submodule name if [submodule"
                    + " \"foo\"] appears in the .gitmodules file) of submodules that will not be"
                    + " download even if 'submodules' is set to YES or RECURSIVE. "),
        @Param(
            name = "first_parent",
            defaultValue = "True",
            named = true,
            doc =
                "If true, it only uses the first parent when looking for changes. Note that"
                    + " when disabled in ITERATIVE mode, it will try to do a migration for each"
                    + " change of the merged branch.",
            positional = false),
        @Param(
            name = "partial_fetch",
            defaultValue = "False",
            named = true,
            positional = false,
            doc = "If true, partially fetch git repository by only fetching affected files."),
        @Param(
            name = "api_checker",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc =
                "A checker for the Gerrit API endpoint provided for after_migration hooks. "
                    + "This field is not required if the workflow hooks don't use the "
                    + "origin/destination endpoints.",
            named = true,
            positional = false),
        @Param(
            name = PATCH_FIELD,
            allowedTypes = {
              @ParamType(type = Transformation.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = PATCH_FIELD_DESC),
        @Param(
            name = "branch",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc =
                "Limit the import to"
                    + " changes that are for this branch. By default imports everything."),
        @Param(
            name = "describe_version",
            allowedTypes = {
              @ParamType(type = Boolean.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = DESCRIBE_VERSION_FIELD_DOC),
        @Param(
            name = "ignore_gerrit_noop",
            defaultValue = "False",
            named = true,
            positional = false,
            doc = "Option to not migrate Gerrit changes that do not change origin_files"),
        @Param(
            name = "primary_branch_migration",
            allowedTypes = {
              @ParamType(type = Boolean.class),
            },
            defaultValue = "False",
            named = true,
            positional = false,
            doc =
                "When enabled, copybara will ignore the 'ref' param if it is 'master' or 'main' and"
                    + " instead try to establish the default git branch. If this fails, it will"
                    + " fall back to the 'ref' param.\n"
                    + "This is intended to help migrating to the new standard of using 'main'"
                    + " without breaking users relying on the legacy default."),
        @Param(
            name = "import_wip_changes",
            allowedTypes = {
              @ParamType(type = Boolean.class),
            },
            defaultValue = "True",
            named = true,
            positional = false,
            doc =
                "When set to true, Copybara will migrate changes marked as Work in Progress"
                    + " (WIP)."),
      },
      useStarlarkThread = true)
  @UsesFlags({GitOriginOptions.class, GerritOptions.class})
  public GitOrigin gerritOrigin(
      String url,
      Object ref,
      String submodules,
      Object excludedSubmodules,
      Boolean firstParent,
      Boolean partialFetch,
      Object checkerObj,
      Object patch,
      Object branch,
      Object describeVersion,
      Boolean ignoreGerritNoop,
      Boolean primaryBranchMigration,
      boolean importWipChanges,
      StarlarkThread thread)
      throws EvalException {
    checkNotEmpty(url, "url");
    url = fixHttp(url, thread.getCallerLocation());
    String refField = SkylarkUtil.convertOptionalString(ref);

    PatchTransformation patchTransformation = maybeGetPatchTransformation(patch);

    List<String> excludedSubmoduleList =
        Sequence.cast(excludedSubmodules, String.class, "excluded_submodules");
    checkSubmoduleConfig(submodules, excludedSubmoduleList);

    if (!Strings.isNullOrEmpty(refField)) {
      getGeneralConsole().warn(
          "'ref' field detected in configuration. git.gerrit_origin"
              + " is deprecating its usage for submitted changes. Use git.origin instead.");
      return GitOrigin.newGitOrigin(
          options,
          url,
          refField,
          GitRepoType.GERRIT,
          stringToEnum("submodules", submodules, GitOrigin.SubmoduleStrategy.class),
          excludedSubmoduleList,
          /* includeBranchCommitLogs= */ false,
          firstParent,
          partialFetch,
          primaryBranchMigration,
          patchTransformation,
          convertDescribeVersion(describeVersion),
          /* versionSelector= */ null,
          mainConfigFile.path(),
          workflowName,
          approvalsProvider(url),
          /* enableLfs= */ false,
          /* credentials= */ null);
    }
    return GerritOrigin.newGerritOrigin(
        options,
        url,
        stringToEnum("submodules", submodules, GitOrigin.SubmoduleStrategy.class),
        excludedSubmoduleList,
        firstParent,
        partialFetch,
        convertFromNoneable(checkerObj, null),
        patchTransformation,
        convertFromNoneable(branch, null),
        convertDescribeVersion(describeVersion),
        ignoreGerritNoop,
        primaryBranchMigration,
        approvalsProvider(url),
        importWipChanges);
  }

  static final String GITHUB_PR_ORIGIN_NAME = "github_pr_origin";

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = GITHUB_PR_ORIGIN_NAME,
      doc =
          "Defines a Git origin for Github pull requests.\n"
              + "\n"
              + "Implicit labels that can be used/exposed:\n"
              + "\n"
              + "  - "
              + GitHubPrOrigin.GITHUB_PR_NUMBER_LABEL
              + ": The pull request number if the"
              + " reference passed was in the form of `https://github.com/project/pull/123`, "
              + " `refs/pull/123/head` or `refs/pull/123/master`.\n"
              + "  - "
              + DEFAULT_INTEGRATE_LABEL
              + ": A label that when exposed, can be used to"
              + " integrate automatically in the reverse workflow.\n"
              + "  - "
              + GITHUB_BASE_BRANCH
              + ": The name of the branch which serves as the base for the Pull Request.\n"
              + "  - "
              + GITHUB_BASE_BRANCH_SHA1
              + ": The SHA-1 of the commit used as baseline. Generally, the baseline commit is the"
              + " point of divergence between the PR's 'base' and 'head' branches. When `use_merge"
              + " = True` is specified, the baseline is instead the tip of the PR's base branch.\n"
              + "  - "
              + GITHUB_PR_USE_MERGE
              + ": Equal to 'true' if the workflow is importing a GitHub PR 'merge' commit and"
              + " 'false' when importing a GitHub PR 'head' commit.\n"
              + "  - "
              + GITHUB_PR_TITLE
              + ": Title of the Pull Request.\n"
              + "  - "
              + GITHUB_PR_BODY
              + ": Body of the Pull Request.\n"
              + "  - "
              + GITHUB_PR_URL
              + ": GitHub url of the Pull Request.\n"
              + "  - "
              + GITHUB_PR_HEAD_SHA
              + ": The SHA-1 of the head commit of the pull request.\n"
              + "  - "
              + GITHUB_PR_USER
              + ": The login of the author the pull request.\n"
              + "  - "
              + GITHUB_PR_ASSIGNEE
              + ": A repeated label with the login of the assigned"
              + " users.\n"
              + "  - "
              + GITHUB_PR_REVIEWER_APPROVER
              + ": A repeated label with the login of users"
              + " that have participated in the review and that can approve the import. Only"
              + " populated if `review_state` field is set. Every reviewers type matching"
              + " `review_approvers` will be added to this list.\n"
              + "  - "
              + GITHUB_PR_REVIEWER_OTHER
              + ": A repeated label with the login of users"
              + " that have participated in the review but cannot approve the import. Only"
              + " populated if `review_state` field is set.\n",
      parameters = {
        @Param(name = "url", named = true, doc = "Indicates the URL of the GitHub repository"),
        @Param(
            name = "use_merge",
            defaultValue = "False",
            named = true,
            positional = false,
            doc =
                "If the content for refs/pull/&lt;ID&gt;/merge should be used instead of the PR"
                    + " head. The GitOrigin-RevId still will be the one from"
                    + " refs/pull/&lt;ID&gt;/head revision."),
        @Param(
            name = GitHubUtil.REQUIRED_LABELS,
            allowedTypes = {@ParamType(type = Sequence.class, generic1 = String.class)},
            named = true,
            defaultValue = "[]",
            doc =
                "Required labels to import the PR. All the labels need to be present in order to"
                    + " migrate the Pull Request.",
            positional = false),
        @Param(
            name = GitHubUtil.REQUIRED_STATUS_CONTEXT_NAMES,
            allowedTypes = {@ParamType(type = Sequence.class, generic1 = String.class)},
            named = true,
            defaultValue = "[]",
            doc =
                "A list of names of services which must all mark the PR with 'success' before it"
                    + " can be imported.<br><br>See"
                    + " https://docs.github.com/en/rest/reference/repos#statuses",
            positional = false),
        @Param(
            name = GitHubUtil.REQUIRED_CHECK_RUNS,
            allowedTypes = {@ParamType(type = Sequence.class, generic1 = String.class)},
            named = true,
            defaultValue = "[]",
            doc =
                "A list of check runs which must all have a value of 'success' in order to import"
                    + " the PR.<br><br>See"
                    + " https://docs.github.com/en/rest/guides/getting-started-with-the-checks-api",
            positional = false),
        @Param(
            name = GitHubUtil.RETRYABLE_LABELS,
            allowedTypes = {@ParamType(type = Sequence.class, generic1 = String.class)},
            named = true,
            defaultValue = "[]",
            doc =
                "Required labels to import the PR that should be retried. This parameter must"
                    + " be a subset of required_labels.",
            positional = false),
        @Param(
            name = "submodules",
            defaultValue = "'NO'",
            named = true,
            positional = false,
            doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
        @Param(
            name = "excluded_submodules",
            defaultValue = "[]",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
            },
            named = true,
            positional = false,
            doc =
                "A list of names (not paths, e.g. \"foo\" is the submodule name if [submodule"
                    + " \"foo\"] appears in the .gitmodules file) of submodules that will not be"
                    + " download even if 'submodules' is set to YES or RECURSIVE. "),
        @Param(
            name = "baseline_from_branch",
            named = true,
            doc =
                "WARNING: Use this field only for github -> git CHANGE_REQUEST workflows.<br>"
                    + "When the field is set to true for CHANGE_REQUEST workflows it will find the"
                    + " baseline comparing the Pull Request with the base branch instead of looking"
                    + " for the *-RevId label in the commit message.",
            defaultValue = "False",
            positional = false),
        @Param(
            name = "first_parent",
            defaultValue = "True",
            named = true,
            doc =
                "If true, it only uses the first parent when looking for changes. Note that"
                    + " when disabled in ITERATIVE mode, it will try to do a migration for each"
                    + " change of the merged branch.",
            positional = false),
        @Param(
            name = "partial_fetch",
            defaultValue = "False",
            named = true,
            positional = false,
            doc = "This is an experimental feature that only works for certain origin globs."),
        @Param(
            name = "state",
            defaultValue = "'OPEN'",
            named = true,
            positional = false,
            doc =
                "Only migrate Pull Request with that state."
                    + " Possible values: `'OPEN'`, `'CLOSED'` or `'ALL'`. Default 'OPEN'"),
        @Param(
            name = "review_state",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc =
                "Required state of the reviews associated with the Pull Request"
                    + " Possible values:\n\n"
                    + "-  `ANY`: No review or approval required.\n"
                    + "-  `HAS_REVIEWERS`: A reviewer interacted with the change, e.g. commented.\n"
                    + "-  `ANY_COMMIT_APPROVED`: At least one commit in the PR was approved.\n"
                    + "-  `HEAD_COMMIT_APPROVED`: The current head commit was approved.\n\n"
                    + " Default is `None` which has no requirement.\nThis field is required if"
                    + " the user wants `"
                    + GITHUB_PR_REVIEWER_APPROVER
                    + "` and `"
                    + GITHUB_PR_REVIEWER_OTHER
                    + "` labels populated"),
        @Param(
            name = "review_approvers",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc =
                "The set of reviewer types that are considered for approvals. In order to"
                    + " have any effect, `review_state` needs to be set. "
                    + GITHUB_PR_REVIEWER_APPROVER
                    + "` will be populated for these types."
                    + " See the valid types here:"
                    + " https://developer.github.com/v4/enum/commentauthorassociation/"),
        @Param(
            name = "api_checker",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc =
                "A checker for the GitHub API endpoint provided for after_migration hooks. "
                    + "This field is not required if the workflow hooks don't use the "
                    + "origin/destination endpoints.",
            named = true,
            positional = false),
        @Param(
            name = PATCH_FIELD,
            allowedTypes = {
              @ParamType(type = Transformation.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = PATCH_FIELD_DESC),
        @Param(
            name = "branch",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            positional = false,
            defaultValue = "None",
            doc = "If set, it will only migrate pull requests for this base branch"),
        @Param(
            name = "describe_version",
            allowedTypes = {
              @ParamType(type = Boolean.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = DESCRIBE_VERSION_FIELD_DOC),
        @Param(
            name = "credentials",
            allowedTypes = {
              @ParamType(type = UsernamePasswordIssuer.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = CREDENTIAL_DOC)
      },
      useStarlarkThread = true)
  @UsesFlags({GitHubPrOriginOptions.class, GitOriginOptions.class, GitHubOptions.class})
  @DocDefault(field = "review_approvers", value = "[\"COLLABORATOR\", \"MEMBER\", \"OWNER\"]")
  public Origin<GitRevision> githubPrOrigin(
      String url,
      Boolean merge,
      Sequence<?> requiredLabels, // <String>
      Sequence<?> requiredStatusContextNames, // <String>
      Sequence<?> requiredCheckRuns, // <String>
      Sequence<?> retryableLabels, // <String>
      String submodules,
      Object excludedSubmodules,
      Boolean baselineFromBranch,
      Boolean firstParent,
      Boolean partialClone,
      String state,
      Object reviewStateParam,
      Object reviewApproversParam,
      Object checkerObj,
      Object patch,
      Object branch,
      Object describeVersion,
      @Nullable Object credentials,
      StarlarkThread thread)
      throws EvalException {
    checkNotEmpty(url, "url");
    check(GITHUB_COM.isGitHubUrl(url), "Invalid Github URL: %s", url);
    PatchTransformation patchTransformation = maybeGetPatchTransformation(patch);

    List<String> excludedSubmoduleList =
        Sequence.cast(excludedSubmodules, String.class, "excluded_submodules");
    checkSubmoduleConfig(submodules, excludedSubmoduleList);

    String reviewStateString = convertFromNoneable(reviewStateParam, null);
    Sequence<String> reviewApproversStrings = convertFromNoneable(reviewApproversParam, null);
    ReviewState reviewState;
    ImmutableSet<AuthorAssociation> reviewApprovers;
    if (reviewStateString == null) {
      reviewState = null;
      check(
          reviewApproversStrings == null,
          "'review_approvers' cannot be set if `review_state` is not set");
      reviewApprovers = ImmutableSet.of();
    } else {
      reviewState = ReviewState.valueOf(reviewStateString);
      if (reviewApproversStrings == null) {
        reviewApproversStrings =
            StarlarkList.of(/*mutability=*/ null, "COLLABORATOR", "MEMBER", "OWNER");
      }
      HashSet<AuthorAssociation> approvers = new HashSet<>();
      for (String r : reviewApproversStrings) {
        boolean added = approvers.add(stringToEnum("review_approvers", r, AuthorAssociation.class));
        check(added, "Repeated element %s", r);
      }
      reviewApprovers = ImmutableSet.copyOf(approvers);
    }
    String fixedUrl = fixHttp(url, thread.getCallerLocation());
    GitHubPrOriginOptions prOpts = options.get(GitHubPrOriginOptions.class);
    CredentialFileHandler credHandler = getCredentialHandler(fixedUrl, credentials);
    if (prOpts.repo != null) {
      Iterator<String> split = Splitter.on(" ").split(prOpts.repo).iterator();
      String repo = split.next();
      String ref = split.hasNext() ? split.next() : "main";
      return new GitOrigin(
          options.get(GeneralOptions.class),
          repo,
          ref,
          GitRepoType.GIT,
          options.get(GitOptions.class),
          options.get(GitOriginOptions.class),
          stringToEnum("submodules", submodules, SubmoduleStrategy.class),
          excludedSubmoduleList,
          false,
          firstParent,
          partialClone,
          patchTransformation,
          convertDescribeVersion(describeVersion),
          null,
          mainConfigFile.path(),
          workflowName,
          false,
          githubPostSubmitApprovalsProvider(fixedUrl, ref, credHandler),
          /* enableLfs= */ false,
          credHandler
      );
    }
    return new GitHubPrOrigin(
        fixedUrl,
        prOpts.overrideMerge != null ? prOpts.overrideMerge : merge,
        options.get(GeneralOptions.class),
        options.get(GitOptions.class),
        options.get(GitOriginOptions.class),
        options.get(GitHubOptions.class),
        prOpts,
        ImmutableSet.copyOf(
            Sequence.cast(requiredLabels, String.class, GitHubUtil.REQUIRED_LABELS)),
        ImmutableSet.copyOf(
            Sequence.cast(
                requiredStatusContextNames,
                String.class,
                GitHubUtil.REQUIRED_STATUS_CONTEXT_NAMES)),
        ImmutableSet.copyOf(
            Sequence.cast(requiredCheckRuns, String.class, GitHubUtil.REQUIRED_CHECK_RUNS)),
        ImmutableSet.copyOf(
            Sequence.cast(retryableLabels, String.class, GitHubUtil.RETRYABLE_LABELS)),
        stringToEnum("submodules", submodules, SubmoduleStrategy.class),
        excludedSubmoduleList,
        baselineFromBranch,
        firstParent,
        partialClone,
        stringToEnum("state", state, StateFilter.class),
        reviewState,
        reviewApprovers,
        convertFromNoneable(checkerObj, null),
        patchTransformation,
        convertFromNoneable(branch, null),
        convertDescribeVersion(describeVersion),
        GITHUB_COM,
        githubPreSubmitApprovalsProvider(fixedUrl, credHandler),
        credHandler);
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "github_origin",
      doc =
          "Defines a Git origin for a Github repository. This origin should be used for public"
              + " branches. Use "
              + GITHUB_PR_ORIGIN_NAME
              + " for importing Pull Requests.",
      parameters = {
        @Param(name = "url", named = true, doc = "Indicates the URL of the git repository"),
        @Param(
            name = "ref",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            doc =
                "Represents the default reference that will be used for reading the revision "
                    + "from the git repository. For example: 'master'"),
        @Param(
            name = "submodules",
            defaultValue = "'NO'",
            named = true,
            doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
        @Param(
            name = "excluded_submodules",
            defaultValue = "[]",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
            },
            named = true,
            positional = false,
            doc =
                "A list of names (not paths, e.g. \"foo\" is the submodule name if [submodule"
                    + " \"foo\"] appears in the .gitmodules file) of submodules that will not be"
                    + " download even if 'submodules' is set to YES or RECURSIVE. "),
        @Param(
            name = "first_parent",
            defaultValue = "True",
            named = true,
            doc =
                "If true, it only uses the first parent when looking for changes. Note that"
                    + " when disabled in ITERATIVE mode, it will try to do a migration for each"
                    + " change of the merged branch.",
            positional = false),
        @Param(
            name = "partial_fetch",
            defaultValue = "False",
            named = true,
            positional = false,
            doc = "If true, partially fetch git repository by only fetching affected files."),
        @Param(
            name = PATCH_FIELD,
            allowedTypes = {
              @ParamType(type = Transformation.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = PATCH_FIELD_DESC),
        @Param(
            name = "describe_version",
            allowedTypes = {
              @ParamType(type = Boolean.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = DESCRIBE_VERSION_FIELD_DOC),
        @Param(
            name = "version_selector",
            allowedTypes = {
              @ParamType(type = VersionSelector.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc =
                "Select a custom version (tag)to migrate"
                    + " instead of 'ref'. Version"
                    + " selector is expected to match the whole refspec (e.g. 'refs/heads/${n1}')"),
        @Param(
            name = "primary_branch_migration",
            allowedTypes = {
              @ParamType(type = Boolean.class),
            },
            defaultValue = "False",
            named = true,
            positional = false,
            doc =
                "When enabled, copybara will ignore the 'ref' param if it is 'master' or 'main' and"
                    + " instead try to establish the default git branch. If this fails, it will"
                    + " fall back to the 'ref' param.\n"
                    + "This is intended to help migrating to the new standard of using 'main'"
                    + " without breaking users relying on the legacy default."),
        @Param(
            name = "enable_lfs",
            defaultValue = "False",
            named = true,
            positional = false,
            doc = "If true, Large File Storage support is enabled for the origin."),
        @Param(
            name = "credentials",
            allowedTypes = {
              @ParamType(type = UsernamePasswordIssuer.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = CREDENTIAL_DOC)
      },
      useStarlarkThread = true)
  @UsesFlags({GitOriginOptions.class, GitHubOptions.class})
  public GitOrigin githubOrigin(
      String url,
      Object ref,
      String submodules,
      Object excludedSubmodules,
      Boolean firstParent,
      Boolean partialFetch,
      Object patch,
      Object describeVersion,
      Object versionSelector,
      Boolean primaryBranchMigration,
      Boolean enableLfs,
      @Nullable Object credentials,
      StarlarkThread thread)
      throws EvalException {
    check(GITHUB_COM.isGitHubUrl(checkNotEmpty(url, "url")), "Invalid Github URL: %s", url);

    if (versionSelector != Starlark.NONE) {
      check(
          ref == Starlark.NONE,
          "Cannot use ref field and version_selector. Version selector will decide the ref"
              + " to migrate");
    }

    List<String> excludedSubmoduleList =
        Sequence.cast(excludedSubmodules, String.class, "excluded_submodules");
    checkSubmoduleConfig(submodules, excludedSubmoduleList);
    String fixedUrl = fixHttp(url, thread.getCallerLocation());
    PatchTransformation patchTransformation = maybeGetPatchTransformation(patch);

    CredentialFileHandler credentialHandler = getCredentialHandler(fixedUrl, credentials);
    // TODO(copybara-team): See if we want to support includeBranchCommitLogs for GitHub repos.
    return GitOrigin.newGitOrigin(
        options,
        fixedUrl,
        SkylarkUtil.convertOptionalString(ref),
        GitRepoType.GITHUB,
        stringToEnum("submodules", submodules, GitOrigin.SubmoduleStrategy.class),
        excludedSubmoduleList,
        /* includeBranchCommitLogs= */ false,
        firstParent,
        partialFetch,
        primaryBranchMigration,
        patchTransformation,
        convertDescribeVersion(describeVersion),
        convertFromNoneable(versionSelector, null),
        mainConfigFile.path(),
        workflowName,
        githubPostSubmitApprovalsProvider(
            fixedUrl, SkylarkUtil.convertOptionalString(ref), credentialHandler),
        enableLfs,
        credentialHandler);
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "gitlab_origin",
      doc =
          "Defines a Git origin for a GitLab hosted repository.",
      documented = false,
      parameters = {
        @Param(name = "url", named = true, doc = "Indicates the URL of the git repository"),
        @Param(
            name = "ref",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            doc =
                "Represents the default reference that will be used for reading the revision "
                    + "from the git repository. For example: 'master'"),
        @Param(
            name = "submodules",
            defaultValue = "'NO'",
            named = true,
            doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
        @Param(
            name = "excluded_submodules",
            defaultValue = "[]",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
            },
            named = true,
            positional = false,
            doc =
                "A list of names (not paths, e.g. \"foo\" is the submodule name if [submodule"
                    + " \"foo\"] appears in the .gitmodules file) of submodules that will not be"
                    + " download even if 'submodules' is set to YES or RECURSIVE. "),
        @Param(
            name = "first_parent",
            defaultValue = "True",
            named = true,
            doc =
                "If true, it only uses the first parent when looking for changes. Note that"
                    + " when disabled in ITERATIVE mode, it will try to do a migration for each"
                    + " change of the merged branch.",
            positional = false),
        @Param(
            name = "partial_fetch",
            defaultValue = "False",
            named = true,
            positional = false,
            doc = "If true, partially fetch git repository by only fetching affected files."),
        @Param(
            name = PATCH_FIELD,
            allowedTypes = {
              @ParamType(type = Transformation.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = PATCH_FIELD_DESC),
        @Param(
            name = "describe_version",
            allowedTypes = {
              @ParamType(type = Boolean.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = DESCRIBE_VERSION_FIELD_DOC),
        @Param(
            name = "version_selector",
            allowedTypes = {
              @ParamType(type = VersionSelector.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc =
                "Select a custom version (tag)to migrate"
                    + " instead of 'ref'. Version"
                    + " selector is expected to match the whole refspec (e.g. 'refs/heads/${n1}')"),
        @Param(
            name = "primary_branch_migration",
            allowedTypes = {
              @ParamType(type = Boolean.class),
            },
            defaultValue = "False",
            named = true,
            positional = false,
            doc =
                "When enabled, copybara will ignore the 'ref' param if it is 'master' or 'main' and"
                    + " instead try to establish the default git branch. If this fails, it will"
                    + " fall back to the 'ref' param.\n"
                    + "This is intended to help migrating to the new standard of using 'main'"
                    + " without breaking users relying on the legacy default."),
        @Param(
            name = "enable_lfs",
            defaultValue = "False",
            named = true,
            positional = false,
            doc = "If true, Large File Storage support is enabled for the origin."),
        @Param(
            name = "credentials",
            allowedTypes = {
              @ParamType(type = UsernamePasswordIssuer.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = CREDENTIAL_DOC)
      },
      useStarlarkThread = true)
  public GitOrigin gitlabOrigin(
      String url,
      Object ref,
      String submodules,
      Object excludedSubmodules,
      Boolean firstParent,
      Boolean partialFetch,
      Object patch,
      Object describeVersion,
      Object versionSelector,
      Boolean primaryBranchMigration,
      Boolean enableLfs,
      @Nullable Object credentials,
      StarlarkThread thread)
      throws EvalException {
    check(
        versionSelector == Starlark.NONE || ref == Starlark.NONE,
        "Cannot use ref field and version_selector. Version selector will decide the ref"
            + " to migrate");

    List<String> excludedSubmoduleList =
        Sequence.cast(excludedSubmodules, String.class, "excluded_submodules");
    checkSubmoduleConfig(submodules, excludedSubmoduleList);
    String fixedUrl = fixHttp(url, thread.getCallerLocation());
    PatchTransformation patchTransformation = maybeGetPatchTransformation(patch);
    CredentialFileHandler credentialHandler = getCredentialHandler(fixedUrl, credentials);

    return GitOrigin.newGitOrigin(
        options,
        fixedUrl,
        SkylarkUtil.convertOptionalString(ref),
        GitRepoType.GITLAB,
        stringToEnum("submodules", submodules, GitOrigin.SubmoduleStrategy.class),
        excludedSubmoduleList,
        /* includeBranchCommitLogs= */ false,
        firstParent,
        partialFetch,
        primaryBranchMigration,
        patchTransformation,
        convertDescribeVersion(describeVersion),
        convertFromNoneable(versionSelector, null),
        mainConfigFile.path(),
        workflowName,
        new NoneApprovedProvider(),
        enableLfs,
        credentialHandler);
  }

  private boolean convertDescribeVersion(Object describeVersion) {
    return convertFromNoneable(describeVersion,
        options.get(GitOriginOptions.class).gitDescribeDefault);
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "destination",
      doc =
          "Creates a commit in a git repository using the transformed worktree.<br><br>For"
              + " GitHub use git.github_destination. For creating Pull Requests in GitHub, use"
              + " git.github_pr_destination. For creating a Gerrit change use"
              + " git.gerrit_destination.<br><br>Given that Copybara doesn't ask"
              + " for user/password in the console when doing the push to remote repos, you have to"
              + " use ssh protocol, have the credentials cached or use a credential manager.",
      parameters = {
        @Param(
            name = "url",
            named = true,
            doc =
                "Indicates the URL to push to as well as the URL from which to get the parent "
                    + "commit"),
        @Param(
            name = "push",
            named = true,
            doc = "Reference to use for pushing the change, for example 'main'.",
            defaultValue = "'master'"),
        @Param(
            name = "tag_name",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            doc =
                "A template string that refers to a tag name. If tag_name exists, overwrite "
                    + "this tag only if flag git-tag-overwrite is set. Note that tag creation is "
                    + "best-effort and migration will succeed even if the tag cannot be created. "
                    + "Usage: Users can use a string or a string with a label. "
                    + "For instance ${label}_tag_name. And the value of label must be "
                    + "in changes' label list. Otherwise, tag won't be created.",
            defaultValue = "None"),
        @Param(
            name = "tag_msg",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            doc =
                "A template string that refers to the commit msg of a tag. If set, we will "
                    + "create an annotated tag when tag_name is set. Usage: Users can use a string "
                    + "or a string with a label. For instance ${label}_message. And the value of "
                    + "label must be in changes' label list. Otherwise, tag will be created with "
                    + "sha1's commit msg.",
            defaultValue = "None"),
        @Param(
            name = "fetch",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            doc =
                "Indicates the ref from which to get the parent commit. Defaults to push value"
                    + " if None",
            defaultValue = "None"),
        @Param(
            name = "partial_fetch",
            defaultValue = "False",
            named = true,
            positional = false,
            doc = "This is an experimental feature that only works for certain origin globs."),
        @Param(
            name = "integrates",
            named = true,
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = GitIntegrateChanges.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc =
                "Integrate changes from a url present in the migrated change"
                    + " label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is"
                    + " present in the message",
            positional = false),
        @Param(
            name = "primary_branch_migration",
            allowedTypes = {
              @ParamType(type = Boolean.class),
            },
            defaultValue = "False",
            named = true,
            positional = false,
            doc =
                "When enabled, copybara will ignore the 'push' and 'fetch' params if either is"
                    + " 'master' or 'main' and instead try to establish the default git branch. If"
                    + " this fails, it will fall back to the param's declared value.\n"
                    + "This is intended to help migrating to the new standard of using 'main'"
                    + " without breaking users relying on the legacy default."),
        @Param(
            name = "checker",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc = "A checker that can check leaks or other checks in the commit created. ",
            named = true,
            positional = false),
          @Param(
              name = "credentials",
              allowedTypes = {
                  @ParamType(type = UsernamePasswordIssuer.class),
                  @ParamType(type = NoneType.class),
              },
              defaultValue = "None",
              named = true,
              positional = false,
              doc = CREDENTIAL_DOC),
          @Param(
              name = "lfs_source",
              allowedTypes = {
                  @ParamType(type = String.class),
                  @ParamType(type = NoneType.class),
              },
              defaultValue = "None",
              named = true,
              positional = false,
              doc = "Git repository URL to use for pulling LFS files. This is required when "
                  + "GIT_LFS_SKIP_SMUDGE=1 is set in the environment, which causes git operations to "
                  + "work with LFS pointer files instead of actual file content. When specified, "
                  + "Copybara will pull the actual LFS file contents from this URL before pushing to "
                  + "the destination. This can be the same as the origin URL or a different URL if "
                  + "LFS files are stored elsewhere. Example: "
                  + "'git@github.com:AppliedNeuron/core-stack.git'")
      },
      useStarlarkThread = true)
  @UsesFlags(GitDestinationOptions.class)
  public GitDestination destination(
      String url,
      String push,
      Object tagName,
      Object tagMsg,
      Object fetch,
      boolean partialFetch,
      Object integrates,
      Boolean primaryBranchMigration,
      Object checker,
      @Nullable Object credentials,
      Object lfsSource,
      StarlarkThread thread)
      throws EvalException {
    GitDestinationOptions destinationOptions = options.get(GitDestinationOptions.class);
    String resolvedPush = checkNotEmpty(firstNotNull(destinationOptions.push, push), "push");
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    Checker maybeChecker = convertFromNoneable(checker, null);
    if (maybeChecker != null && options.get(GitDestinationOptions.class).skipGitChecker) {
      maybeChecker = null;
      getGeneralConsole()
          .warn(
              "Skipping git checker for git.destination. Note that this could"
                  + " cause leaks or other problems");
    }
    CredentialFileHandler credentialHandler = getCredentialHandler(url, credentials);
    return new GitDestination(
        fixHttp(
            checkNotEmpty(firstNotNull(destinationOptions.url, url), "url"),
            thread.getCallerLocation()),
        checkNotEmpty(
            firstNotNull(destinationOptions.fetch, convertFromNoneable(fetch, null), resolvedPush),
            "fetch"),
        resolvedPush,
        partialFetch,
        primaryBranchMigration,
        convertFromNoneable(tagName, null),
        convertFromNoneable(tagMsg, null),
        destinationOptions,
        options.get(GitOptions.class),
        generalOptions,
        new DefaultWriteHook(),
        Starlark.isNullOrNone(integrates)
            ? defaultGitIntegrate
            : Sequence.cast(integrates, GitIntegrateChanges.class, "integrates"),
        maybeChecker,
        credentialHandler,
        convertFromNoneable(lfsSource, null));
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "github_destination",
      doc =
          "Creates a commit in a GitHub repository branch (for example master). For creating Pull"
              + "Request use git.github_pr_destination.",
      parameters = {
        @Param(
            name = "url",
            named = true,
            doc =
                "Indicates the URL to push to as well as the URL from which to get the parent "
                    + "commit"),
        @Param(
            name = "push",
            named = true,
            doc = "Reference to use for pushing the change, for example 'main'.",
            defaultValue = "'master'"),
        @Param(
            name = "fetch",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            doc =
                "Indicates the ref from which to get the parent commit. Defaults to push value"
                    + " if None",
            defaultValue = "None"),
        @Param(
            name = "pr_branch_to_update",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            doc =
                "A template string that refers to a pull request branch in the same repository"
                    + " will be updated to current commit of this push branch only if"
                    + " pr_branch_to_update exists. The reason behind this field is that"
                    + " presubmiting changes creates and leaves a pull request open. By using"
                    + " this, we can automerge/close this type of pull requests. As a result,"
                    + " users will see this pr_branch_to_update as merged to this push branch."
                    + " Usage: Users can use a string or a string with a label. For instance"
                    + " ${label}_pr_branch_name. And the value of label must be in changes' label"
                    + " list. Otherwise, nothing will happen.",
            defaultValue = "None"),
        @Param(
            name = "partial_fetch",
            defaultValue = "False",
            named = true,
            doc = "This is an experimental feature that only works for certain origin globs."),
        @Param(
            name = "delete_pr_branch",
            allowedTypes = {
              @ParamType(type = Boolean.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            doc =
                "When `pr_branch_to_update` is enabled, it will delete the branch reference"
                    + " after the push to the branch and main branch (i.e master) happens. This"
                    + " allows to cleanup temporary branches created for testing.",
            defaultValue = "None"),
        @Param(
            name = "integrates",
            named = true,
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = GitIntegrateChanges.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc =
                "Integrate changes from a url present in the migrated change"
                    + " label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is"
                    + " present in the message",
            positional = false),
        @Param(
            name = "api_checker",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc =
                "A checker for the Gerrit API endpoint provided for after_migration hooks. "
                    + "This field is not required if the workflow hooks don't use the "
                    + "origin/destination endpoints.",
            named = true,
            positional = false),
        @Param(
            name = "primary_branch_migration",
            allowedTypes = {
              @ParamType(type = Boolean.class),
            },
            defaultValue = "False",
            named = true,
            positional = false,
            doc =
                "When enabled, copybara will ignore the 'push' and 'fetch' params if either is"
                    + " 'master' or 'main' and instead try to establish the default git branch. If"
                    + " this fails, it will fall back to the param's declared value.\n"
                    + "This is intended to help migrating to the new standard of using 'main'"
                    + " without breaking users relying on the legacy default."),
        @Param(
            name = "tag_name",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            positional = false,
            doc =
                "A template string that specifies to a tag name. If the tag already exists, "
                    + "copybara will only overwrite it if the --git-tag-overwrite flag is set."
                    + "\nNote that tag creation is "
                    + "best-effort and the migration will succeed even if the tag cannot be "
                    + "created. "
                    + "Usage: Users can use a string or a string with a label. "
                    + "For instance ${label}_tag_name. And the value of label must be "
                    + "in changes' label list. Otherwise, tag won't be created.",
            defaultValue = "None"),
        @Param(
            name = "tag_msg",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            positional = false,
            doc =
                "A template string that refers to the commit msg for a tag. If set, copybara will"
                    + "create an annotated tag with this custom message\n"
                    + "Usage: Labels in the string will be resolved. E.g. .${label}_message."
                    + "By default, the tag will be created with the labeled commit's message.",
            defaultValue = "None"),
        @Param(
            name = "checker",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc =
                "A checker that validates the commit files & message. If `api_checker` is not"
                    + " set, it will also be used for checking API calls. If only `api_checker`"
                    + "is used, that checker will only apply to API calls.",
            named = true,
            positional = false),
        @Param(
            name = "credentials",
            allowedTypes = {
              @ParamType(type = UsernamePasswordIssuer.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = CREDENTIAL_DOC),
       // Experimental flag to push to a fork instead of the main repo.
       // Do not use this flag.
        @Param(
            name = "push_to_fork",
            allowedTypes = {
              @ParamType(type = Boolean.class),
            },
            defaultValue = "False",
            named = true,
            positional = false,
            documented = false),
      },
      useStarlarkThread = true)
  @UsesFlags({GitDestinationOptions.class, GitHubOptions.class})
  // Used to detect in the future users that don't set it and change the default
  @DocDefault(field = "delete_pr_branch", value = "False")
  public GitDestination gitHubDestination(
      String url,
      String push,
      Object fetch,
      Object prBranchToUpdate,
      Boolean partialFetch,
      Object deletePrBranchParam,
      Object integrates,
      Object apiChecker,
      Boolean primaryBranchMigration,
      Object tagName,
      Object tagMsg,
      Object checker,
      @Nullable Object credentials,
      Boolean pushToFork,
      StarlarkThread thread)
      throws EvalException {
    GitDestinationOptions destinationOptions = options.get(GitDestinationOptions.class);
    String resolvedPush = checkNotEmpty(firstNotNull(destinationOptions.push, push), "push");
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    String repoUrl =
        fixHttp(
            checkNotEmpty(firstNotNull(destinationOptions.url, url), "url"),
            thread.getCallerLocation());
    String branchToUpdate = convertFromNoneable(prBranchToUpdate, null);
    Boolean deletePrBranch = convertFromNoneable(deletePrBranchParam, null);
    check(
        branchToUpdate != null || deletePrBranch == null,
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

    Checker apiCheckerObj = convertFromNoneable(apiChecker, null);
    Checker checkerObj = convertFromNoneable(checker, null);
    CredentialFileHandler credentialHandler;
    try {
      credentialHandler = getCredentialHandler(
          GITHUB_COM.getHost(), GITHUB_COM.getProjectNameFromUrl(url), credentials);
    } catch (ValidationException e) {
      throw new EvalException("Cannot parse url", e);
    }
    return new GitDestination(
        repoUrl,
        checkNotEmpty(
            firstNotNull(destinationOptions.fetch, convertFromNoneable(fetch, null), resolvedPush),
            "fetch"),
        resolvedPush,
        partialFetch,
        primaryBranchMigration,
        convertFromNoneable(tagName, null),
        convertFromNoneable(tagMsg, null),
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
            apiCheckerObj != null ? apiCheckerObj : checkerObj,
            GITHUB_COM,
            credentialHandler,
            pushToFork),
        Starlark.isNullOrNone(integrates)
            ? defaultGitIntegrate
            : Sequence.cast(integrates, GitIntegrateChanges.class, "integrates"),
        checkerObj,
        credentialHandler,
        /* lfsSource= */ null);
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "github_pr_destination",
      doc = "Creates changes in a new pull request in the destination.",
      parameters = {
        @Param(
            name = "url",
            named = true,
            doc =
                "Url of the GitHub project. For example"
                    + " \"https://github.com/google/copybara'\""),
        @Param(
            name = "destination_ref",
            named = true,
            doc = "Destination reference for the change.",
            defaultValue = "'master'"),
        @Param(
            name = "pr_branch",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc =
                "Customize the pull request branch. The token ${CONTEXT_REFERENCE} will be replaced"
                    + " with the corresponding stable "
                    + "reference (head, PR number, Gerrit change number, etc.)."),
        @Param(
            name = "partial_fetch",
            defaultValue = "False",
            named = true,
            positional = false,
            doc = "This is an experimental feature that only works for certain origin globs."),
        @Param(
            name = "allow_empty_diff",
            defaultValue = "True",
            named = true,
            positional = false,
            doc =
                "By default, copybara migrates changes without checking existing PRs. "
                    + "If set, copybara will skip pushing a change to an existing PR "
                    + "only if the git three of the pending migrating change is the same "
                    + "as the existing PR."),
        @Param(
            name = "allow_empty_diff_merge_statuses",
            allowedTypes = {@ParamType(type = Sequence.class, generic1 = String.class)},
            defaultValue = "[]",
            named = true,
            positional = false,
            doc =
                "**EXPERIMENTAL feature.** By default, if `allow_empty_diff = False` is set,"
                    + " Copybara skips uploading the change if the tree hasn't changed and it can"
                    + " be merged. When this list is set with values from"
                    + " https://docs.github.com/en/github-ae@latest/graphql/reference/enums#mergestatestatus,"
                    + " it will still upload for the configured statuses. For example, if a user"
                    + " sets it to `['DIRTY', 'UNSTABLE', 'UNKNOWN']` (the recommended set to use),"
                    + " it wouldn't skip upload if test failed in GitHub for previous export, or if"
                    + " the change cannot be merged. **Note that this field is experimental and is"
                    + " subject to change by GitHub without notice**. Please consult Copybara team"
                    + " before using this field."),
        @Param(
            name = "allow_empty_diff_check_suites_to_conclusion",
            allowedTypes = {@ParamType(type = Dict.class, generic1 = String.class)},
            defaultValue = "{}",
            named = true,
            positional = false,
            doc =
                "**EXPERIMENTAL feature.** By default, if `allow_empty_diff = False` is set,"
                    + " Copybara skips uploading the change if the tree hasn't changed and it can"
                    + " be merged.<br><br>This field allows to configure Check suit slugs and"
                    + " conclusions for those check suites where an upload needs to happen despite"
                    + " no code changes. For example this can be used to upload if tests are"
                    + " failing. A Very common usage would be `{\"github-actions\" :   [\"none\","
                    + " \"failure\", \"timed_out\", \"cancelled\"]}`: This would upload changes"
                    + " when Checks are in progress, has failed, timeout or being cancelled."
                    + " `github-actions` check suit slug name is the default name for checks run by"
                    + " GitHub actions where the suit is not given a name."),
        @Param(
            name = "title",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc =
                "When creating (or updating if `update_description` is set) a pull request, use"
                    + " this title. By default it uses the change first line. This field accepts"
                    + " a template with labels. For example: `\"Change ${CONTEXT_REFERENCE}\"`"),
        @Param(
            name = "body",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc =
                "When creating (or updating if `update_description` is set) a pull request, use"
                    + " this body. By default it uses the change summary. This field accepts"
                    + " a template with labels. For example: `\"Change ${CONTEXT_REFERENCE}\"`"),
        @Param(
            name = "assignees",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
            },
            defaultValue = "[]",
            named = true,
            positional = false,
            doc =
                "The assignees to set when creating a new pull request. The maximum number of"
                    + " assignees is 10 and the assignees must be GitHub usernames or a label that"
                    + " can be resolved to a GitHub username. For example: `assignees ="
                    + " [\"github-repo-owner1\", \"${YOUR_LABEL}\"]`"),
        @Param(
            name = "integrates",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = GitIntegrateChanges.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            defaultValue = "None",
            doc =
                "Integrate changes from a url present in the migrated change"
                    + " label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is"
                    + " present in the message",
            positional = false),
        @Param(
            name = "api_checker",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc =
                "A checker for the GitHub API endpoint provided for after_migration hooks. "
                    + "This field is not required if the workflow hooks don't use the "
                    + "origin/destination endpoints.",
            named = true,
            positional = false),
        @Param(
            name = "update_description",
            defaultValue = "False",
            named = true,
            positional = false,
            doc =
                "By default, Copybara only set the title and body of the PR when creating"
                    + " the PR. If this field is set to true, it will update those fields for"
                    + " every update."),
        @Param(
            name = "primary_branch_migration",
            defaultValue = "False",
            named = true,
            positional = false,
            doc =
                "When enabled, copybara will ignore the 'desination_ref' param if it is 'master' or"
                    + " 'main' and instead try to establish the default git branch. If this fails,"
                    + " it will fall back to the param's declared value.\n"
                    + "This is intended to help migrating to the new standard of using 'main'"
                    + " without breaking users relying on the legacy default."),
        @Param(
            name = "checker",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc =
                "A checker that validates the commit files & message. If `api_checker` is not"
                    + " set, it will also be used for checking API calls. If only `api_checker`"
                    + "is used, that checker will only apply to API calls.",
            named = true,
            positional = false),
        @Param(
            name = "draft",
            defaultValue = "False",
            named = true,
            positional = false,
            doc = "Flag create pull request as draft or not."),
        @Param(
            name = "credentials",
            allowedTypes = {
              @ParamType(type = UsernamePasswordIssuer.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = CREDENTIAL_DOC)
      },
      useStarlarkThread = true)
  @UsesFlags({GitDestinationOptions.class, GitHubDestinationOptions.class, GitHubOptions.class})
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
      Boolean partialFetch,
      Boolean allowEmptyDiff,
      Sequence<?> allowEmptyDiffMergeStatuses,
      Dict<?, ?> allowEmptyDiffCheckSuitesToConclusion,
      Object title,
      Object body,
      Sequence<? extends String> assignees,
      Object integrates,
      Object apiChecker,
      Boolean updateDescription,
      Boolean primaryBranchMigrationMode,
      Object checker,
      boolean isDraft,
      @Nullable Object credentials,
      StarlarkThread thread)
      throws EvalException {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    // This restricts to github.com, we will have to revisit this to support setups like GitHub
    // Enterprise.
    check(GITHUB_COM.isGitHubUrl(url), "'%s' is not a valid GitHub url", url);
    GitDestinationOptions destinationOptions = options.get(GitDestinationOptions.class);
    GitHubOptions gitHubOptions = options.get(GitHubOptions.class);
    String destinationPrBranch = convertFromNoneable(prBranch, null);
    Checker apiCheckerObj = convertFromNoneable(apiChecker, null);
    Checker checkerObj = convertFromNoneable(checker, null);
    CredentialFileHandler credentialHandler;
    try {
      credentialHandler = getCredentialHandler(
          GITHUB_COM.getHost(), GITHUB_COM.getProjectNameFromUrl(url), credentials);
    } catch (ValidationException e) {
      throw new EvalException("Cannot parse url", e);
    }
    return new GitHubPrDestination(
        fixHttp(
            checkNotEmpty(firstNotNull(destinationOptions.url, url), "url"),
            thread.getCallerLocation()),
        destinationRef,
        convertFromNoneable(prBranch, null),
        partialFetch,
        isDraft,
        generalOptions,
        options.get(GitHubOptions.class),
        destinationOptions,
        options.get(GitHubDestinationOptions.class),
        options.get(GitOptions.class),
        new GitHubPrWriteHook(
            generalOptions,
            url,
            gitHubOptions,
            destinationPrBranch,
            partialFetch,
            allowEmptyDiff,
            ImmutableSet.copyOf(
                SkylarkUtil.convertStringList(allowEmptyDiffMergeStatuses,
                    "empty_diff_merge_statuses")),
            convertSlugToConclusion(allowEmptyDiffCheckSuitesToConclusion),
            getGeneralConsole(),
            GITHUB_COM,
            credentialHandler),
        Starlark.isNullOrNone(integrates)
            ? defaultGitIntegrate
            : Sequence.cast(integrates, GitIntegrateChanges.class, "integrates"),
        convertFromNoneable(title, null),
        convertFromNoneable(body, null),
        ImmutableList.copyOf(assignees),
        mainConfigFile,
        apiCheckerObj != null ? apiCheckerObj : checkerObj,
        updateDescription,
        GITHUB_COM,
        primaryBranchMigrationMode,
        checkerObj,
        credentialHandler);
  }

  private ImmutableSetMultimap<String, Conclusion> convertSlugToConclusion(
      Dict<?, ?> allowEmptyDiffCheckSuitesToConclusion) throws EvalException {
    Builder<String, Conclusion> skipSuiteToConclusion =
        ImmutableSetMultimap.builder();
    for (Object k : allowEmptyDiffCheckSuitesToConclusion.keySet()) {
      if (!(k instanceof String)) {
        throw Starlark.errorf("Invalid key '%s' for allow_empty_diff_check_suites_to_conclusion."
            + " The value has to be an string with the slug name of the check suite."
            + " e.g. \"github-actions\"", k);
      }
      Sequence<String> conclusionStr = Sequence.cast(allowEmptyDiffCheckSuitesToConclusion.get(k),
          String.class, "allow_empty_diff_check_suites_to_conclusion[\"" + k + "\"]");
      for (String c : conclusionStr) {
        Optional<Conclusion> conclusion = Conclusion.fromValue(c);
        if (conclusion.isEmpty()) {
          throw Starlark.errorf(
              "Invalid conclusion value %s. Valid values: %s",
              c, stream(Conclusion.values()).map(Conclusion::getApiVal).collect(toImmutableList()));
        }
        skipSuiteToConclusion.put(k.toString(), conclusion.get());
      }
    }
    return skipSuiteToConclusion.build();
  }

  @Nullable private static String firstNotNull(String... values) {
    for (String value : values) {
      if (!Strings.isNullOrEmpty(value)) {
        return value;
      }
    }
    return null;
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "gerrit_destination",
      doc =
          "Creates a change in Gerrit using the transformed worktree. If this is used in iterative"
              + " mode, then each commit pushed in a single Copybara invocation will have the"
              + " correct commit parent. The reviews generated can then be easily done in the"
              + " correct order without rebasing.",
      parameters = {
        @Param(
            name = "url",
            named = true,
            doc =
                "Indicates the URL to push to as well as the URL from which to get the parent "
                    + "commit"),
        @Param(
            name = "fetch",
            named = true,
            doc = "Indicates the ref from which to get the parent commit"),
        @Param(
            name = "push_to_refs_for",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            doc =
                "Review branch to push the change to, for example setting this to 'feature_x'"
                    + " causes the destination to push to 'refs/for/feature_x'. It defaults to "
                    + "'fetch' value."),
        @Param(
            name = "submit",
            named = true,
            doc =
                "If true, skip the push thru Gerrit refs/for/branch and directly push to branch."
                    + " This is effectively a git.destination that sets a Change-Id",
            defaultValue = "False"),
        @Param(
            name = "partial_fetch",
            defaultValue = "False",
            named = true,
            doc = "This is an experimental feature that only works for certain origin globs."),
        @Param(
            name = "notify",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            doc =
                ""
                    + "Type of Gerrit notify option (https://gerrit-review.googlesource.com/Docum"
                    + "entation/user-upload.html#notify). Sends notifications by default.",
            defaultValue = "None"),
        @Param(
            name = "change_id_policy",
            defaultValue = "'FAIL_IF_PRESENT'",
            named = true,
            doc =
                "What to do in the presence or absent of Change-Id in message:"
                    + "<ul>"
                    + "  <li>`'REQUIRE'`: Require that the change_id is present in the message as a"
                    + " valid label</li>"
                    + "  <li>`'FAIL_IF_PRESENT'`: Fail if found in message</li>"
                    + "  <li>`'REUSE'`: Reuse if present. Otherwise generate a new one</li>"
                    + "  <li>`'REPLACE'`: Replace with a new one if found</li>"
                    + "</ul>"),
        @Param(
            name = "allow_empty_diff_patchset",
            named = true,
            doc =
                "By default Copybara will upload a new PatchSet to Gerrit without checking the"
                    + " previous one. If this set to false, Copybara will download current PatchSet"
                    + " and check the diff against the new diff.",
            defaultValue = "True"),
        @Param(
            name = "reviewers",
            named = true,
            defaultValue = "[]",
            doc =
                "The list of the reviewers to add. Each element in the list is:"
                    + " an email (e.g. `\"foo@example.com\"` or label (e.g. "
                    + " `\"${SOME_GERRIT_REVIEWER}`). These assume that users have already"
                    + " registered on the Gerrit host and has access to the repos."),
        @Param(
            name = "cc",
            named = true,
            defaultValue = "[]",
            doc =
                "The list of the email addresses or users that will be CCed in the review. Can"
                    + " use labels as the `reviewers` field."),
        @Param(
            name = "labels",
            named = true,
            defaultValue = "[]",
            doc =
                "The list of labels to be pushed with the change. The format is the label "
                    + "along with the associated value. For example: Run-Presubmit+1"),
        @Param(
            name = "api_checker",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc =
                "A checker for the Gerrit API endpoint provided for after_migration hooks. "
                    + "This field is not required if the workflow hooks don't use the "
                    + "origin/destination endpoints.",
            named = true,
            positional = false),
        @Param(
            name = "integrates",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = GitIntegrateChanges.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            defaultValue = "None",
            doc =
                "Integrate changes from a url present in the migrated change"
                    + " label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is"
                    + " present in the message",
            positional = false),
        @Param(
            name = "topic",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc =
                ""
                    + "Sets the topic of the Gerrit change created.<br><br>"
                    + "By default it sets no topic. This field accepts a template with labels. "
                    + "For example: `\"topic_${CONTEXT_REFERENCE}\"`"),
        @Param(
            name = "gerrit_submit",
            defaultValue = "False",
            named = true,
            positional = false,
            doc =
                "By default, Copybara uses git commit/push to the main branch when submit = True."
                    + "  If this flag is enabled, it will update the Gerrit change with the "
                    + "latest commit and submit using Gerrit."),
        @Param(
            name = "primary_branch_migration",
            allowedTypes = {
              @ParamType(type = Boolean.class),
            },
            defaultValue = "False",
            named = true,
            positional = false,
            doc =
                "When enabled, copybara will ignore the 'push_to_refs_for' and 'fetch' params if"
                    + " either is 'master' or 'main' and instead try to establish the default git"
                    + " branch. If this fails, it will fall back to the param's declared value.\n"
                    + "This is intended to help migrating to the new standard of using 'main'"
                    + " without breaking users relying on the legacy default."),
        @Param(
            name = "checker",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc =
                "A checker that validates the commit files & message. If `api_checker` is not"
                    + " set, it will also be used for checking API calls. If only `api_checker`"
                    + "is used, that checker will only apply to API calls.",
            named = true,
            positional = false),
        @Param(
            name = "credentials",
            allowedTypes = {
              @ParamType(type = UsernamePasswordIssuer.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = CREDENTIAL_DOC)
      },
      useStarlarkThread = true)
  @UsesFlags({GitDestinationOptions.class, GerritOptions.class})
  @DocDefault(field = "push_to_refs_for", value = "fetch value")
  public GerritDestination gerritDestination(
      String url,
      String fetch,
      Object pushToRefsFor,
      Boolean submit,
      Boolean partialFetch,
      Object notifyOptionObj,
      String changeIdPolicy,
      Boolean allowEmptyPatchSet,
      Sequence<?> reviewers, // <String>
      Sequence<?> ccParam, // <String>
      Sequence<?> labelsParam, // <String>
      Object apiChecker,
      Object integrates,
      Object topicObj,
      Boolean gerritSubmit,
      Boolean primaryBranchMigrationMode,
      Object checker,
      @Nullable Object credentials,
      StarlarkThread thread)
      throws EvalException {
    checkNotEmpty(url, "url");
    if (gerritSubmit) {
      Preconditions.checkArgument(submit, "Only set gerrit_submit if submit is true");
    }

    List<String> newReviewers = SkylarkUtil.convertStringList(reviewers, "reviewers");
    List<String> cc = SkylarkUtil.convertStringList(ccParam, "cc");
    List<String> labels = SkylarkUtil.convertStringList(labelsParam, "labels");

    String notifyOptionStr = convertFromNoneable(notifyOptionObj, null);
    check(
        !(submit && notifyOptionStr != null),
        "Cannot set 'notify' with 'submit = True' in git.gerrit_destination().");

    String topicStr = convertFromNoneable(topicObj, null);
    check(
        !(submit && topicStr != null),
        "Cannot set 'topic' with 'submit = True' in git.gerrit_destination().");
    NotifyOption notifyOption =
        notifyOptionStr == null
            ? null
            : stringToEnum("notify", notifyOptionStr, NotifyOption.class);

    Checker apiCheckerObj = convertFromNoneable(apiChecker, null);
    Checker checkerObj = convertFromNoneable(checker, null);
    CredentialFileHandler credentialHandler = getCredentialHandler(url, credentials);
    return GerritDestination.newGerritDestination(
        options,
        fixHttp(url, thread.getCallerLocation()),
        checkNotEmpty(firstNotNull(options.get(GitDestinationOptions.class).fetch, fetch), "fetch"),
        checkNotEmpty(
            firstNotNull(
                convertFromNoneable(pushToRefsFor, null),
                options.get(GitDestinationOptions.class).fetch,
                fetch),
            "push_to_refs_for"),
        submit,
        partialFetch,
        notifyOption,
        stringToEnum("change_id_policy", changeIdPolicy, ChangeIdPolicy.class),
        allowEmptyPatchSet,
        newReviewers,
        cc,
        labels,
        apiCheckerObj != null ? apiCheckerObj : checkerObj,
        Starlark.isNullOrNone(integrates)
            ? defaultGitIntegrate
            : Sequence.cast(integrates, GitIntegrateChanges.class, "integrates"),
        topicStr,
        gerritSubmit,
        primaryBranchMigrationMode,
        checkerObj,
        credentialHandler);
  }

  @StarlarkMethod(
      name = "gitlab_mr_origin",
      doc =
          "Creates a GitLab Merge Request origin. WARNING: This is still in an experimental state;"
              + " please do not use.",
      documented = false,
      parameters = {
        @Param(
            name = "url",
            named = true,
            positional = false,
            doc = "The URL of the GitLab repository."),
        @Param(
            name = "credentials",
            allowedTypes = {
              @ParamType(type = UsernamePasswordIssuer.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = GITLAB_CREDENTIAL_DOC),
        @Param(
            name = "partial_fetch",
            named = true,
            positional = false,
            doc = "If true, partially fetch the Git repository by only fetching affected files.",
            defaultValue = "False"),
        @Param(
            name = "use_merge_commit",
            named = true,
            positional = false,
            doc =
                "If the content for GitLab's generated merge commit"
                    + " (refs/merge-request/&lt;ID&gt;/merge) should be used instead of the MR"
                    + " head.",
            defaultValue = "False"),
        @Param(
            name = "describe_version",
            named = true,
            positional = false,
            doc = DESCRIBE_VERSION_FIELD_DOC,
            defaultValue = "True"),
        @Param(
            name = "first_parent",
            defaultValue = "True",
            named = true,
            positional = false,
            doc =
                "If true, it only uses the first parent when looking for changes. Note that"
                    + " when disabled in ITERATIVE mode, it will try to do a migration for each"
                    + " change of the merged branch."),
        @Param(
            name = "submodules",
            defaultValue = "'NO'",
            named = true,
            positional = false,
            doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
        @Param(
            name = "excluded_submodules",
            defaultValue = "[]",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
            },
            named = true,
            positional = false,
            doc =
                "A list of names (not paths, e.g. \"foo\" is the submodule name if [submodule"
                    + " \"foo\"] appears in the .gitmodules file) of submodules that will not be"
                    + " download even if 'submodules' is set to YES or RECURSIVE. "),
        @Param(
            name = PATCH_FIELD,
            allowedTypes = {
              @ParamType(type = Transformation.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = PATCH_FIELD_DESC),
      })
  @SuppressWarnings("unused")
  public GitLabMrOrigin gitLabMrOrigin(
      String url,
      Object usernamePasswordIssuer,
      boolean partialFetch,
      boolean useMergeCommit,
      boolean describeVersion,
      boolean firstParent,
      String submodules,
      Sequence<?> excludedSubmodules,
      Object patch)
      throws EvalException {
    checkNotEmpty(url, "url");

    GitLabOptions gitLabOptions = options.get(GitLabOptions.class);
    Console console = getGeneralConsole();
    PatchTransformation patchTransformation = maybeGetPatchTransformation(patch);

    GitLabMrOrigin.Builder originBuilder =
        GitLabMrOrigin.builder()
            .setConsole(console)
            .setUsernamePasswordIssuer(convertToOptional(usernamePasswordIssuer))
            .setRepoUrl(URI.create(url))
            .setGitOptions(options.get(GitOptions.class))
            .setGitOriginOptions(options.get(GitOriginOptions.class))
            .setGitLabOptions(options.get(GitLabOptions.class))
            .setGeneralOptions(options.get(GeneralOptions.class))
            .setSubmoduleStrategy(stringToEnum("submodules", submodules, SubmoduleStrategy.class))
            .setExcludedSubmodules(
                ImmutableList.copyOf(
                    Sequence.cast(excludedSubmodules, String.class, "excluded_submodules")))
            .setPartialFetch(convertFromNoneable(partialFetch, false))
            .setDescribeVersion(describeVersion)
            .setFirstParent(firstParent)
            .setUseMergeCommit(useMergeCommit);

    if (patchTransformation != null) {
      originBuilder.setPatchTransformation(patchTransformation);
    }

    return originBuilder.build();
  }

  @StarlarkMethod(
      name = "gitlab_mr_destination",
      doc =
          "Creates a GitLab Merge Request destination. WARNING: This is still in an experimental"
              + " state; please do not use.",
      documented = false,
      parameters = {
        @Param(
            name = "url",
            named = true,
            positional = false,
            doc = "The URL of the GitLab repository."),
        @Param(name = "credentials", named = true, positional = false, doc = GITLAB_CREDENTIAL_DOC),
        @Param(
            name = "source_branch",
            named = true,
            positional = false,
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            doc = "The source branch to use for creating the merge request.",
            defaultValue = "None"),
        @Param(
            name = "target_branch",
            named = true,
            positional = false,
            doc = "The target branch to use for creating the merge request."),
        @Param(
            name = "title",
            named = true,
            positional = false,
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "The title to use for creating the merge request's title. This field also accepts a"
                    + " template with labels.\n"
                    + "Example: `title = Merge Request ${CONTEXT_REFERENCE}`",
            defaultValue = "None"),
        @Param(
            name = "body",
            named = true,
            positional = false,
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "The body to use for creating the merge request. This field also accepts a template"
                    + " with labels.\n"
                    + "Example: `title = Merge Request ${CONTEXT_REFERENCE}`",
            defaultValue = "None"),
        @Param(
            name = "assignees",
            named = true,
            positional = false,
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
            },
            doc =
                "The assignees to set when creating a new merge request. The assignees must be"
                    + " GitLab usernames or a label that can be resolved to a GitLab username. For"
                    + " example: `assignees = [\"gitlab-user1\", \"${YOUR_LABEL}\"]`",
            defaultValue = "[]"),
        @Param(
            name = "allow_empty_diff",
            named = true,
            positional = false,
            doc =
                "If `True`, Copybara migrates changes without checking existing MRs. "
                    + "If `False`, copybara will skip pushing a change to an existing MR "
                    + "only if the git tree of the pending migrating change is the same "
                    + "as the existing MR.",
            defaultValue = "False"),
        @Param(
            name = "allow_empty_diff_merge_statuses",
            allowedTypes = {@ParamType(type = Sequence.class, generic1 = String.class)},
            defaultValue = "[]",
            named = true,
            positional = false,
            doc =
                "By default, if `allow_empty_diff` is False,"
                    + " Copybara skips uploading the change if the tree hasn't changed and it can"
                    + " be merged. When this list is set with values from"
                    + " https://docs.gitlab.com/api/merge_requests/#merge-status,"
                    + " it will still upload for the configured statuses. For example, if a user"
                    + " sets it to `['ci_must_pass', 'need_rebase']` (the recommended set to use),"
                    + " it wouldn't skip upload if test failed in GitLab for previous export, or if"
                    + " the change cannot be merged. **Note that this field is experimental and is"
                    + " subject to change by GitLab without notice**. Please consult Copybara team"
                    + " before using this field."),
        @Param(
            name = "partial_fetch",
            named = true,
            positional = false,
            doc = "If true, partially fetch the Git repository by only fetching affected files.",
            defaultValue = "False"),
        @Param(
            name = "integrates",
            named = true,
            positional = false,
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = GitIntegrateChanges.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc =
                "Integrate changes from a url present in the migrated change"
                    + " label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is"
                    + " present in the message."),
        @Param(
            name = "checker",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc = "A checker that validates the commit files & message.",
            named = true,
            positional = false),
      })
  @UsesFlags({GitDestinationOptions.class})
  @SuppressWarnings("unused")
  public GitLabMrDestination gitLabMrDestination(
      String url,
      UsernamePasswordIssuer usernamePasswordIssuer,
      Object sourceBranchTemplate,
      String targetBranch,
      Object titleTemplate,
      Object bodyTemplate,
      Sequence<?> assigneeTemplates,
      boolean allowEmptyDiff,
      Sequence<?> allowEmptyDiffMergeStatuses,
      boolean partialFetch,
      Object integrates,
      Object checker)
      throws EvalException {
    checkNotEmpty(url, "url");

    GitLabOptions gitLabOptions = options.get(GitLabOptions.class);
    Console console = getGeneralConsole();

    return GitLabMrDestinationParams.builder()
        .setRepoUrl(URI.create(url))
        .setUsernamePasswordIssuer(usernamePasswordIssuer)
        .setTitleTemplate(convertToOptional(titleTemplate))
        .setBodyTemplate(convertToOptional(bodyTemplate))
        .setAssigneeTemplates(
            Sequence.cast(assigneeTemplates, String.class, "assignee_templates").getImmutableList())
        .setSourceBranchTemplate(convertToOptional(sourceBranchTemplate))
        .setTargetBranch(targetBranch)
        .setConfigFile(mainConfigFile)
        .setAllowEmptyDiff(allowEmptyDiff)
        .setAllowEmptyDiffMergeStatuses(
            ImmutableSet.copyOf(
                SkylarkUtil.stringListToEnumList(
                    Sequence.cast(
                        allowEmptyDiffMergeStatuses,
                        String.class,
                        "allow_empty_diff_merge_statuses"),
                    DetailedMergeStatus.class,
                    "allow_empty_diff_merge_statuses",
                    console)))
        .setGeneralOptions(options.get(GeneralOptions.class))
        .setGitOptions(options.get(GitOptions.class))
        .setGitLabOptions(options.get(GitLabOptions.class))
        .setDestinationOptions(options.get(GitDestinationOptions.class))
        .setPartialFetch(partialFetch)
        .setIntegrates(
            Starlark.isNullOrNone(integrates)
                ? defaultGitIntegrate
                : Sequence.cast(integrates, GitIntegrateChanges.class, "integrates"))
        .setChecker(convertToOptional(checker))
        .build()
        .createDestination();
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = GITHUB_API,
      doc =
          "Defines a feedback API endpoint for GitHub, that exposes relevant GitHub API"
              + " operations.",
      parameters = {
        @Param(name = "url", doc = "Indicates the GitHub repo URL.", named = true),
        @Param(
            name = "checker",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc = "A checker for the GitHub API transport.",
            named = true),
        @Param(
            name = "credentials",
            allowedTypes = {
                @ParamType(type = UsernamePasswordIssuer.class),
                @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            named = true,
            positional = false,
            doc = CREDENTIAL_DOC)
      },
      useStarlarkThread = true)
  @UsesFlags(GitHubOptions.class)
  public EndpointProvider<GitHubEndPoint> githubApi(
      String url, Object checkerObj, @Nullable Object credentials,
      StarlarkThread thread) throws EvalException {
    checkNotEmpty(url, "url");
    String cleanedUrl = fixHttp(url, thread.getCallerLocation());
    Checker checker = convertFromNoneable(checkerObj, null);
    validateEndpointChecker(checker, GITHUB_API);
    GitHubOptions gitHubOptions = options.get(GitHubOptions.class);
    CredentialFileHandler credentialHandler = getCredentialHandler(url, credentials);
    return EndpointProvider.wrap(
        new GitHubEndPoint(
            gitHubOptions.newGitHubApiSupplier(cleanedUrl, checker, credentialHandler, GITHUB_COM),
            cleanedUrl,
            getGeneralConsole(),
            GITHUB_COM, credentialHandler));
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = GERRIT_API,
      doc =
          ""
              + "Defines a feedback API endpoint for Gerrit, that exposes relevant Gerrit API "
              + "operations.",
      parameters = {
        @Param(name = "url", doc = "Indicates the Gerrit repo URL.", named = true),
        @Param(
            name = "checker",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc = "A checker for the Gerrit API transport.",
            named = true),
        @Param(
            name = "allow_submit",
            doc = "Enable the submit_change method",
            defaultValue = "False",
            named = true),
      },
      useStarlarkThread = true)
  @UsesFlags(GerritOptions.class)
  public EndpointProvider<GerritEndpoint> gerritApi(
      String url, Object checkerObj, Boolean allowSubmit, StarlarkThread thread)
      throws EvalException {
    checkNotEmpty(url, "url");
    String cleanedUrl = fixHttp(url, thread.getCallerLocation());
    Checker checker = convertFromNoneable(checkerObj, null);
    validateEndpointChecker(checker, GERRIT_API);
    GerritOptions gerritOptions = options.get(GerritOptions.class);
    return EndpointProvider.wrap(
        new GerritEndpoint(gerritOptions.newGerritApiSupplier(cleanedUrl, checker),
            cleanedUrl, getGeneralConsole(), allowSubmit));
  }

  private Console getGeneralConsole() {
    return options.get(GeneralOptions.class).console();
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = GERRIT_TRIGGER,
      doc = "Defines a feedback trigger based on updates on a Gerrit change.",
      parameters = {
        @Param(name = "url", doc = "Indicates the Gerrit repo URL.", named = true),
        @Param(
            name = "checker",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc = "A checker for the Gerrit API transport provided by this trigger.",
            named = true),
        @Param(
            name = "events",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
              @ParamType(type = Dict.class, generic1 = Sequence.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            defaultValue = "[]",
            doc =
                "Types of events to monitor. Optional. Can either be a list of event types or "
                    + "a dict of event types to particular events of that type, e.g. "
                    + "`['LABELS']` or `{'LABELS': 'my_label_name'}`.\n"
                    + "Valid values for event types are: `'LABELS'`, `'SUBMIT_REQUIREMENTS'`"),
        @Param(
            name = "allow_submit",
            doc = "Enable the submit_change method in the endpoint provided",
            defaultValue = "False",
            named = true),
      },
      useStarlarkThread = true)
  @UsesFlags(GerritOptions.class)
  public GerritTrigger gerritTrigger(
      String url, Object checkerObj, Object events, Boolean allowSubmit, StarlarkThread thread)
      throws EvalException {
    checkNotEmpty(url, "url");
    url = fixHttp(url, thread.getCallerLocation());
    Checker checker = convertFromNoneable(checkerObj, null);
    validateEndpointChecker(checker, GERRIT_TRIGGER);
    ImmutableSet<GerritEventTrigger> parsedEvents = handleGerritEventTypes(events);
    GerritOptions gerritOptions = options.get(GerritOptions.class);
    return new GerritTrigger(
        gerritOptions.newGerritApiSupplier(url, checker),
        url,
        parsedEvents,
        getGeneralConsole(),
        allowSubmit);
  }

  private ImmutableSet<GerritEventTrigger> handleGerritEventTypes(Object events)
      throws EvalException {
    LinkedHashSet<GerritEventTrigger> eventBuilder = new LinkedHashSet<>();
    LinkedHashSet<GerritEventType> types = new LinkedHashSet<>();

    if (events instanceof Sequence) {
      for (String e : Sequence.cast(events, String.class, "events")) {
        GerritEventType eventType = stringToEnum("events", e, GerritEventType.class);

        check(eventBuilder.add(GerritEventTrigger.create(eventType, ImmutableSet.of())),
            "Repeated element %s", e);
      }
    } else if (events instanceof Dict) {
      Dict<String, StarlarkList<String>> dict = SkylarkUtil.castOfSequence(
          events,
          String.class,
          String.class,
          "events");
      for (Entry<String, StarlarkList<String>> event : dict.entrySet()) {
        GerritEventType eventType = stringToEnum("events", event.getKey(), GerritEventType.class);

        check(types.add(eventType), "Repeated element %s", event);
        eventBuilder.add(
            GerritEventTrigger.create(
                eventType,
                ImmutableSet.copyOf(event.getValue())));
      }
    }

    return ImmutableSet.copyOf(eventBuilder);
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = GITHUB_TRIGGER,
      doc = "Defines a feedback trigger based on updates on a GitHub PR.",
      parameters = {
        @Param(name = "url", doc = "Indicates the GitHub repo URL.", named = true),
        @Param(
            name = "checker",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc = "A checker for the GitHub API transport provided by this trigger.",
            named = true),
        @Param(
            name = "events",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
              @ParamType(type = Dict.class, generic1 = Sequence.class),
            },
            named = true,
            defaultValue = "[]",
            doc =
                "Types of events to subscribe. Can  either be a list of event types or a dict of "
                    + "event types to particular events of that type, e.g. "
                    + "`['CHECK_RUNS']` or `{'CHECK_RUNS': 'my_check_run_name'}`.\n"
                    + "Valid values for event types are: `'ISSUES'`, `'ISSUE_COMMENT'`,"
                    + " `'PULL_REQUEST'`,  `'PULL_REQUEST_REVIEW_COMMENT'`, `'PUSH'`,"
                    + " `'STATUS'`, `'CHECK_RUNS'`"),
          @Param(
              name = "credentials",
              allowedTypes = {
                  @ParamType(type = UsernamePasswordIssuer.class),
                  @ParamType(type = NoneType.class),
              },
              defaultValue = "None",
              named = true,
              positional = false,
              doc = CREDENTIAL_DOC)
      },
      useStarlarkThread = true)
  @UsesFlags(GitHubOptions.class)
  public GitHubTrigger gitHubTrigger(
      String url,
      Object checkerObj,
      Object events,
      @Nullable Object credentials,
      StarlarkThread thread)
      throws EvalException {
    checkNotEmpty(url, "url");
    url = fixHttp(url, thread.getCallerLocation());
    Checker checker = convertFromNoneable(checkerObj, null);
    LinkedHashSet<EventTrigger> eventBuilder = new LinkedHashSet<>();
    LinkedHashSet<GitHubEventType> types = new LinkedHashSet<>();
    ImmutableSet<EventTrigger> parsedEvents = handleEventTypes(events, eventBuilder, types);
    validateEndpointChecker(checker, GITHUB_TRIGGER);
    GitHubOptions gitHubOptions = options.get(GitHubOptions.class);
    CredentialFileHandler credentialHandler;
    try {
      credentialHandler = getCredentialHandler(
          GITHUB_COM.getHost(), GITHUB_COM.getProjectNameFromUrl(url), credentials);
    } catch (ValidationException e) {
      throw new EvalException("Cannot parse url", e);
    }
    return new GitHubTrigger(
        gitHubOptions.newGitHubApiSupplier(url, checker, credentialHandler, GITHUB_COM),
        url,
        parsedEvents,
        getGeneralConsole(),
        GITHUB_COM,
        credentialHandler);
  }

  private ImmutableSet<EventTrigger> handleEventTypes(
      Object events, LinkedHashSet<EventTrigger> eventBuilder,
      LinkedHashSet<GitHubEventType> types) throws EvalException {
    if (events instanceof Sequence) {
      for (String e : Sequence.cast(events, String.class, "events")) {
        GitHubEventType event = stringToEnum("events", e, GitHubEventType.class);
        check(eventBuilder.add(EventTrigger.create(event, ImmutableSet.of())),
            "Repeated element %s", e);
      }
    } else if (events instanceof Dict) {
      Dict<String, StarlarkList<String>> dict = SkylarkUtil.castOfSequence(
          events,
          String.class,
          String.class,
          "events");
      for (Entry<String, StarlarkList<String>> trigger : dict.entrySet()) {
        check(types.add(
            stringToEnum("events", trigger.getKey(), GitHubEventType.class)),
            "Repeated element %s", trigger);
        eventBuilder.add(
            EventTrigger.create(
                stringToEnum("events", trigger.getKey(), GitHubEventType.class),
                ImmutableSet.copyOf(trigger.getValue())));
      }
    }
    for (EventTrigger trigger : eventBuilder) {
      check(
          WATCHABLE_EVENTS.contains(trigger.type()),
          "%s is not a valid value. Values: %s",
          trigger.type(),
          WATCHABLE_EVENTS);
    }
    check(!eventBuilder.isEmpty(), "events cannot be empty");
    return ImmutableSet.copyOf(eventBuilder);
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "review_input",
      doc = "Creates a review to be posted on Gerrit.",
      parameters = {
        @Param(name = "labels", doc = "The labels to post.", named = true, defaultValue = "{}"),
        @Param(
            name = "message",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            doc = "The message to be added as review comment.",
            named = true,
            defaultValue = "None"),
        @Param(
            name = "tag",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            doc = "Tag to be applied to the review, for instance 'autogenerated:copybara'.",
            named = true,
            defaultValue = "None"),
          @Param(
              name = "notify",
              allowedTypes = {
                  @ParamType(type = String.class),
                  @ParamType(type = NoneType.class),
              },
              doc = "Notify setting, defaults to 'ALL'",
              named = true,
              defaultValue = "'ALL'")
      })
  @UsesFlags(GerritOptions.class)
  public SetReviewInput reviewInput(
      Dict<?, ?> labels, // <String, Int>
      Object message,
      Object tag,
      Object notify)
      throws EvalException {
    // Convert values from StarlarkInt to Integer (in anticipation of Gson).
    ImmutableMap.Builder<String, Integer> copy = ImmutableMap.builder();
    for (Map.Entry<String, StarlarkInt> e :
        Dict.noneableCast(labels, String.class, StarlarkInt.class, "Gerrit review labels")
            .entrySet()) {
      copy.put(e.getKey(), e.getValue().toInt("element of Gerrit review labels"));
    }
    Optional<NotifyType> notifyVal =
        Enums.getIfPresent(NotifyType.class, convertFromNoneable(notify, "ALL")).toJavaUtil();
    return SetReviewInput.create(
        convertFromNoneable(message, null),
        // The need for an ImmutableMap here is extremely subtle, and suggests a bug but I'm not
        // sure where. A Dict is both a Map and an Iterable, whereas an ImmutableMap
        // is not iterable. The SetReviewInput object makes its way into
        // com.google.api.client.json.JsonFactory,
        //
        //   post_review_to_gerrit (feedback.bara.sky)
        //   -> GoogleGerritApiTransport.post (ctx.destination.post_review)
        //   -> GerritApiTransport.post
        //   -> GerritApi.setReview
        //   -> GerritEndpoint.postReview
        //   -> JsonFactory.toByteArray
        //
        // where the Iterability of the labels map causes it to be treated like a list of keys.
        copy.buildOrThrow(),
        convertFromNoneable(tag, null),
        notifyVal.orElseThrow(() -> new EvalException(
            "%s is not a valid NotifyType, valid values are: %s"
                .formatted(notify, EnumSet.allOf(NotifyType.class))))
        );
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "latest_version",
      doc =
          "DEPRECATED: Use core.latest_version.\n\n"
              + "Customize what version of the available branches and tags to pick."
              + " By default it ignores the reference passed as parameter. Using --force"
              + " in the CLI will force to use the reference passed as argument instead.",
      parameters = {
        @Param(
            name = "refspec_format",
            doc = "The format of the branch/tag",
            named = true,
            defaultValue = "\"refs/tags/${n0}.${n1}.${n2}\""),
        @Param(
            name = "refspec_groups",
            named = true,
            doc =
                "A set of named regexes that can be used to match part of the versions. Copybara"
                    + " uses [re2](https://github.com/google/re2/wiki/Syntax) syntax. Use the"
                    + " following nomenclature n0, n1, n2 for the version part (will use numeric"
                    + " sorting) or s0, s1, s2 (alphabetic sorting). Note that there can be mixed"
                    + " but the numbers cannot be repeated. In other words n0, s1, n2 is valid but"
                    + " not n0, s0, n1. n0 has more priority than n1. If there are fields where"
                    + " order is not important, use s(N+1) where N ist he latest sorted field."
                    + " Example {\"n0\": \"[0-9]+\", \"s1\": \"[a-z]+\"}",
            defaultValue = "{'n0' : '[0-9]+', 'n1' : '[0-9]+', 'n2' : '[0-9]+'}"),
      },
      useStarlarkThread = true)
  public VersionSelector versionSelector(
      String refspec, Dict<?, ?> groups, StarlarkThread thread) // <String, String>
      throws EvalException {
    Map<String, String> groupsMap = Dict.cast(groups, String.class, String.class, "refspec_groups");
    TreeMap<Integer, VersionElementType> elements = new TreeMap<>();
    Pattern regexKey = Pattern.compile("([sn])([0-9])");
    for (String s : groupsMap.keySet()) {
      Matcher matcher = regexKey.matcher(s);
      check(
          matcher.matches(),
          "Incorrect key for refspec_group. Should be in the "
              + "format of n0, n1, etc. or s0, s1, etc. Value: %s",
          s);
      VersionElementType type = matcher.group(1).equals("s") ? ALPHABETIC : NUMERIC;
      int num = Integer.parseInt(matcher.group(2));
      check(
          !elements.containsKey(num) || elements.get(num) == type,
          "Cannot use same n in both s%s and n%s: %s",
          num,
          num,
          s);
      elements.put(num, type);
    }
    for (Integer num : elements.keySet()) {
      if (num > 0 ) {
        check(
            elements.containsKey(num - 1),
            "Cannot have s%s or n%s if s%s or n%s" + " doesn't exist",
            num,
            num,
            num - 1,
            num - 1);
      }
    }

    LatestVersionSelector versionPicker = new LatestVersionSelector(
        refspec, Replace.parsePatterns(groupsMap), elements, thread.getCallerLocation());
    ImmutableList<String> extraGroups = versionPicker.getUnmatchedGroups();
    check(extraGroups.isEmpty(), "Extra refspec_groups not used in pattern: %s", extraGroups);

    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    if (generalOptions.isForced() || generalOptions.isVersionSelectorUseCliRef()) {
      return new OrderedVersionSelector(ImmutableList.of(
          new RequestedVersionSelector(),
          versionPicker));
    }
    return versionPicker;
  }

  @Override
  public void setConfigFile(ConfigFile mainConfigFile, ConfigFile currentConfigFile) {
    this.mainConfigFile = mainConfigFile;
  }

  @Override
  public void setWorkflowName(String workflowName)
  {
    this.workflowName = workflowName;
  }

  @CheckReturnValue
  private String fixHttp(String url, Location location) {
    try {
      RepositoryUtil.validateNotHttp(url);
    } catch (ValidationException e) {
      String fixed = "https" + url.substring("http".length());
      getGeneralConsole()
          .warnFmt(
              "%s: Url '%s' does not use https - please change the URL. Proceeding with '%s'.",
              location, url, fixed);
      return fixed;
    }
    return url;
  }

  /** Do not use this for github origins */
  protected ApprovalsProvider approvalsProvider(String url) {
    Preconditions.checkArgument(
        !GITHUB_COM.isGitHubUrl(url),
        "Git origins with github should use github approval providers!");
    return options.get(GitOriginOptions.class).approvalsProvider;
  }

  protected ApprovalsProvider githubPreSubmitApprovalsProvider(
      String url, CredentialFileHandler creds) {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    GitHubOptions githubOptions = options.get(GitHubOptions.class);
    return new GitHubPreSubmitApprovalsProvider(
        githubOptions,
        GITHUB_COM,
        new GitHubSecuritySettingsValidator(
            githubOptions.newGitHubApiSupplier(url, null, creds, GITHUB_COM),
            ImmutableList.copyOf(githubOptions.allStarAppIds),
            generalOptions.console()),
        new GitHubUserApprovalsValidator(
            githubOptions.newGitHubApiSupplier(url, null, creds, GITHUB_COM),
            githubOptions.newGitHubGraphQLApiSupplier(url, null, creds, GITHUB_COM),
            generalOptions.console(),
            GITHUB_COM,
            new GetCommitHistoryParams(
                /* commits= */ githubOptions.gqlOverride.get(0),
                /* pullRequests= */ githubOptions.gqlOverride.get(1),
                /* reviews= */ githubOptions.gqlOverride.get(2))),
        creds);
  }

  protected ApprovalsProvider githubPostSubmitApprovalsProvider(
      String url, String branch, CredentialFileHandler creds) {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    GitHubOptions githubOptions = options.get(GitHubOptions.class);
    return new GitHubPostSubmitApprovalsProvider(
        GITHUB_COM,
        branch,
        new GitHubSecuritySettingsValidator(
            githubOptions.newGitHubApiSupplier(url, null, creds, GITHUB_COM),
            ImmutableList.copyOf(githubOptions.allStarAppIds),
            generalOptions.console()),
        new GitHubUserApprovalsValidator(
            githubOptions.newGitHubApiSupplier(url, null, creds, GITHUB_COM),
            githubOptions.newGitHubGraphQLApiSupplier(url, null, creds, GITHUB_COM),
            generalOptions.console(),
            GITHUB_COM,
            new GetCommitHistoryParams(
                /* commits= */ githubOptions.gqlOverride.get(0),
                /* pullRequests= */ githubOptions.gqlOverride.get(1),
                /* reviews= */ githubOptions.gqlOverride.get(2))));
  }

  /** Validates the {@link Checker} provided to a feedback endpoint. */
  @SuppressWarnings({"unused", "RedundantThrows"})
  protected void validateEndpointChecker(Checker checker, String functionName)
      throws EvalException {}

  @Override
  public void setPrintHandler(StarlarkThread.PrintHandler printHandler) {
    this.printHandler = printHandler;
  }

  private void checkSubmoduleConfig(String submodules, List<String> excludedSubmodules)
      throws EvalException {
    check(
        !submodules.equals("NO") || excludedSubmodules.isEmpty(),
        "Expected excluded submodule list to be empty when submodules is NO, but got %s",
        excludedSubmodules);
  }

  @Nullable
  protected LazyResourceLoader<EndpointProvider<?>> getEndpointProvider(
      String url, @Nullable Checker checker, @Nullable CredentialFileHandler creds,
      boolean allowSubmit, StarlarkThread thread) {
    if (url == null) {
      return null;
    }
    LazyResourceLoader<EndpointProvider<?>> gerritApiLoader =
        maybeGetGerritApi(url, checker, allowSubmit, thread);
    if (gerritApiLoader != null) {
      return gerritApiLoader;
    }

    LazyResourceLoader<EndpointProvider<?>> githubApiLoader =
        maybeGetGitHubApi(url, checker, creds, thread);
    if (githubApiLoader != null) {
      return githubApiLoader;
    }

    return null;
  }

  @Nullable
  protected LazyResourceLoader<EndpointProvider<?>> maybeGetGerritApi(
      String url, @Nullable Checker checker, boolean allowSubmit, StarlarkThread thread) {
    String host = URI.create(url).getHost();
    if (Strings.isNullOrEmpty(host) || !host.endsWith(".googlesource.com")) {
      return null;
    }
    return (console) -> {
      try {
        return gerritApi(url, checker, allowSubmit, thread);
      } catch (EvalException e) {
        throw new ValidationException(
            String.format(
                "Detected a gerrit repository URL, but was not able to construct a Gerrit API"
                    + " loader. Error = '%s'",
                e.getMessage()),
            e);
      }
    };
  }

  @Nullable
  protected LazyResourceLoader<EndpointProvider<?>> maybeGetGitHubApi(
      String url, @Nullable Checker checker, @Nullable CredentialFileHandler creds,
      StarlarkThread thread) {
    if (!GITHUB_COM.isGitHubUrl(url)) {
      return null;
    }
    return (console) -> {
      try {
        return githubApi(url, checker, creds, thread);
      } catch (EvalException e) {
        throw new ValidationException(
            String.format(
                "Detected a GitHub repository URL, but was not able to construct a GitHub API"
                    + " loader. Error = '%s'",
                e.getMessage()),
            e);
      }
    };
  }

  @Nullable protected CredentialFileHandler getCredentialHandler(
      String host, String path, @Nullable Object starlarkValue) {
    UsernamePasswordIssuer issuer = SkylarkUtil.convertFromNoneable(starlarkValue, null);
    if (issuer == null) {
      return null;
    }
    return new CredentialFileHandler(host, path.replaceAll("^/+", ""),
        issuer.username(), issuer.password(), !options.get(GitOptions.class).useConfigCredentials);
  }

  @Nullable protected CredentialFileHandler getCredentialHandler(
      String url, @Nullable Object starlarkValue) {
    try {
      if (GITHUB_COM.isGitHubUrl(url)) {
        url = GITHUB_COM.normalizeUrl(url);
      }
      URI uri = URI.create(url);
      return getCredentialHandler(uri.getHost(), uri.getPath(), starlarkValue);
    } catch (ValidationException | IllegalArgumentException parseEx) {
      options.get(GeneralOptions.class).console().verboseFmt("Unable to parse %s as URI", url);
    }
    return null;
  }
}
