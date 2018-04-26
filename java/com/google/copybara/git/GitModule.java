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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.copybara.config.SkylarkUtil.checkNotEmpty;
import static com.google.copybara.config.SkylarkUtil.convertFromNoneable;
import static com.google.copybara.git.GitRepoType.GERRIT_CHANGE_DESCRIPTION_LABEL;
import static com.google.copybara.git.GitRepoType.GERRIT_CHANGE_ID_LABEL;
import static com.google.copybara.git.GitRepoType.GERRIT_CHANGE_NUMBER_LABEL;
import static com.google.copybara.git.GithubPROrigin.GITHUB_BASE_BRANCH;
import static com.google.copybara.git.GithubPROrigin.GITHUB_BASE_BRANCH_SHA1;
import static com.google.copybara.git.GithubPROrigin.GITHUB_PR_BODY;
import static com.google.copybara.git.GithubPROrigin.GITHUB_PR_TITLE;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.GlobalMigrations;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.config.OptionsAwareModule;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.copybara.git.GerritDestination.ChangeIdPolicy;
import com.google.copybara.git.GitDestination.DefaultCommitGenerator;
import com.google.copybara.git.GitDestination.ProcessPushStructuredOutput;
import com.google.copybara.git.GitIntegrateChanges.Strategy;
import com.google.copybara.git.GitOrigin.SubmoduleStrategy;
import com.google.copybara.git.GithubPROrigin.StateFilter;
import com.google.copybara.git.gerritapi.SetReviewInput;
import com.google.copybara.git.github.util.GithubUtil;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.Runtime.NoneType;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Main module that groups all the functions that create Git origins and destinations.
 */
@SkylarkModule(
    name = "git",
    doc = "Set of functions to define Git origins and destinations.",
    category = SkylarkModuleCategory.BUILTIN)
@UsesFlags(GitOptions.class)
public class GitModule implements OptionsAwareModule, LabelsAwareModule {

  static final String DEFAULT_INTEGRATE_LABEL = "COPYBARA_INTEGRATE_REVIEW";
  static final SkylarkList<GitIntegrateChanges> NO_GIT_DESTINATION_INTEGRATES =
      SkylarkList.createImmutable(ImmutableList.of(
          new GitIntegrateChanges(DEFAULT_INTEGRATE_LABEL,
              Strategy.FAKE_MERGE_AND_INCLUDE_FILES,
              /*ignoreErrors=*/true)));

  private Options options;
  private ConfigFile<?> mainConfigFile;

  @SuppressWarnings("unused")
  @SkylarkSignature(name = "origin", returnType = GitOrigin.class,
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
          @Param(name = "self", type = GitModule.class, doc = "this object"),
          @Param(name = "url", type = String.class,
              doc = "Indicates the URL of the git repository"),
          @Param(name = "ref", type = String.class, noneable = true, defaultValue = "None",
              doc = "Represents the default reference that will be used for reading the revision "
                  + "from the git repository. For example: 'master'"),
          @Param(name = "submodules", type = String.class, defaultValue = "'NO'",
              doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
          @Param(name = "include_branch_commit_logs", type = Boolean.class, defaultValue = "False",
              doc = "Whether to include raw logs of branch commits in the migrated change message."
                  + "WARNING: This field is deprecated in favor of 'first_parent' one."
              + " This setting *only* affects merge commits.", positional = false),
          @Param(name = "first_parent", type = Boolean.class, defaultValue = "True",
              doc = "If true, it only uses the first parent when looking for changes. Note that"
                  + " when disabled in ITERATIVE mode, it will try to do a migration for each"
                  + " change of the merged branch.", positional = false),
      },
      objectType = GitModule.class)
  public static final BuiltinFunction ORIGIN = new BuiltinFunction("origin") {
    public GitOrigin invoke(GitModule self, String url, Object ref, String submodules,
        Boolean includeBranchCommitLogs, Boolean firstParent) throws EvalException {
      return GitOrigin.newGitOrigin(
          self.options, url, Type.STRING.convertOptional(ref, "ref"), GitRepoType.GIT,
          SkylarkUtil.stringToEnum(location, "submodules",
              submodules, GitOrigin.SubmoduleStrategy.class),
          includeBranchCommitLogs, firstParent);
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(name = "integrate", returnType = GitIntegrateChanges.class,
      doc = "Integrate changes from a url present in the migrated change label.",
      parameters = {
          @Param(name = "self", type = GitModule.class, doc = "this object"),
          @Param(name = "label", type = String.class,
              doc = "The migration label that will contain the url to the change to integrate.",
              defaultValue = "\"" + DEFAULT_INTEGRATE_LABEL + "\""),
          @Param(name = "strategy", type = String.class,
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
          @Param(name = "ignore_errors", type = Boolean.class,
              doc = "If we should ignore integrate errors and continue the migration without the"
                  + " integrate", defaultValue = "True"),

      },
      objectType = GitModule.class)
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
  public static final BuiltinFunction INTEGRATE = new BuiltinFunction("integrate") {
    public GitIntegrateChanges invoke(GitModule self, String label, String strategy,
        Boolean ignoreErrors) throws EvalException {
      return new GitIntegrateChanges(
          label,
          SkylarkUtil.stringToEnum(location, "strategy", strategy, Strategy.class),
          ignoreErrors);
    }
  };


  @SuppressWarnings("unused")
  @SkylarkSignature(name = "mirror", returnType = NoneType.class,
      doc = "Mirror git references between repositories",
      parameters = {
          @Param(name = "self", type = GitModule.class, doc = "this object"),
          @Param(name = "name", type = String.class,
              doc = "Migration name"),
          @Param(name = "origin", type = String.class,
              doc = "Indicates the URL of the origin git repository"),
          @Param(name = "destination", type = String.class,
              doc = "Indicates the URL of the destination git repository"),
          @Param(name = "refspecs", type = SkylarkList.class, generic1 = String.class,
              defaultValue = "['refs/heads/*']",
              doc = "Represents a list of git refspecs to mirror between origin and destination."
                  + "For example 'refs/heads/*:refs/remotes/origin/*' will mirror any reference"
                  + "inside refs/heads to refs/remotes/origin."),
          @Param(name = "prune", type = Boolean.class,
              doc = "Remove remote refs that don't have a origin counterpart",
              defaultValue = "False"),

      },
      objectType = GitModule.class, useLocation = true, useEnvironment = true)
  @UsesFlags(GitMirrorOptions.class)
  public static final BuiltinFunction MIRROR = new BuiltinFunction("mirror") {
    public NoneType invoke(GitModule self, String name, String origin, String destination,
        SkylarkList<String> strRefSpecs, Boolean prune, Location location, Environment env)
        throws EvalException {
      GeneralOptions generalOptions = self.options.get(GeneralOptions.class);
      List<Refspec> refspecs = new ArrayList<>();

      for (String refspec : SkylarkList.castList(strRefSpecs, String.class, "refspecs")) {
        try {
          refspecs.add(Refspec.create(
              generalOptions.getEnvironment(), generalOptions.getCwd(), refspec));
        } catch (InvalidRefspecException e) {
          throw new EvalException(location, e);
        }
      }
      GlobalMigrations.getGlobalMigrations(env).addMigration(location, name,
          new Mirror(generalOptions, self.options.get(GitOptions.class),
              name, origin, destination, refspecs,
              self.options.get(GitMirrorOptions.class), prune, self.mainConfigFile));
      return Runtime.NONE;
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(name = "gerrit_origin", returnType = GitOrigin.class,
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
          @Param(name = "self", type = GitModule.class, doc = "this object"),
          @Param(name = "url", type = String.class,
              doc = "Indicates the URL of the git repository"),
          @Param(name = "ref", type = String.class, noneable = true, defaultValue = "None",
              doc = "DEPRECATED. Use git.origin for submitted branches."),
          @Param(name = "submodules", type = String.class, defaultValue = "'NO'",
              doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
          @Param(name = "first_parent", type = Boolean.class, defaultValue = "True",
              doc = "If true, it only uses the first parent when looking for changes. Note that"
                  + " when disabled in ITERATIVE mode, it will try to do a migration for each"
                  + " change of the merged branch.", positional = false),
      },
      objectType = GitModule.class, useLocation = true)
  public static final BuiltinFunction GERRIT_ORIGIN = new BuiltinFunction("gerrit_origin") {
    public GitOrigin invoke(GitModule self, String url, Object ref, String submodules,
        Boolean firstParent,
        Location location) throws EvalException {
      String refField = Type.STRING.convertOptional(ref, "ref");
      if (!Strings.isNullOrEmpty(refField)) {
        self.options.get(GeneralOptions.class).console().warn(
            "'ref' field detected in configuration. git.gerrit_origin"
                + " is deprecating its usage for submitted changes. Use git.origin instead.");
        return GitOrigin.newGitOrigin(
            self.options, url, refField, GitRepoType.GERRIT,
            SkylarkUtil.stringToEnum(location, "submodules",
                submodules, GitOrigin.SubmoduleStrategy.class),
          /*includeBranchCommitLogs=*/false, firstParent);
      }

      return GerritOrigin.newGerritOrigin(
          self.options, url, SkylarkUtil.stringToEnum(location, "submodules",
              submodules, GitOrigin.SubmoduleStrategy.class), firstParent);
    }
  };

  static final String GITHUB_PR_ORIGIN_NAME = "github_pr_origin";

  @SuppressWarnings("unused")
  @SkylarkSignature(name = "github_origin", returnType = GitOrigin.class,
      doc = "Defines a Git origin for a Github repository. This origin should be used for public"
          + " branches. Use " + GITHUB_PR_ORIGIN_NAME + " for importing Pull Requests.",
      parameters = {
          @Param(name = "self", type = GitModule.class, doc = "this object"),
          @Param(name = "url", type = String.class,
              doc = "Indicates the URL of the git repository"),
          @Param(name = "ref", type = String.class, noneable = true, defaultValue = "None",
              doc = "Represents the default reference that will be used for reading the revision "
                  + "from the git repository. For example: 'master'"),
          @Param(name = "submodules", type = String.class, defaultValue = "'NO'",
              doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
          @Param(name = "first_parent", type = Boolean.class, defaultValue = "True",
              doc = "If true, it only uses the first parent when looking for changes. Note that"
                  + " when disabled in ITERATIVE mode, it will try to do a migration for each"
                  + " change of the merged branch.", positional = false),

      },
      objectType = GitModule.class, useLocation = true)
  public static final BuiltinFunction GITHUB_ORIGIN = new BuiltinFunction("github_origin") {
    public GitOrigin invoke(GitModule self, String url, Object ref, String submodules,
        Boolean firstParent, Location location) throws EvalException {
      if (!GithubUtil.isGitHubUrl(url)) {
        throw new EvalException(location, "Invalid Github URL: " + url);
      }

      // TODO(copybara-team): See if we want to support includeBranchCommitLogs for GitHub repos.
      return GitOrigin.newGitOrigin(
          self.options, url, Type.STRING.convertOptional(ref, "ref"), GitRepoType.GITHUB,
          SkylarkUtil.stringToEnum(location, "submodules",
              submodules, GitOrigin.SubmoduleStrategy.class),
          /*includeBranchCommitLogs=*/false, firstParent);
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(name = GITHUB_PR_ORIGIN_NAME, returnType = GithubPROrigin.class,
      doc = "Defines a Git origin for Github pull requests.\n"
          + "\n"
          + "Implicit labels that can be used/exposed:\n"
          + "\n"
          + "  - " + GithubPROrigin.GITHUB_PR_NUMBER_LABEL + ": The pull request number if the"
          + " reference passed was in the form of `https://github.com/project/pull/123`, "
          + " `refs/pull/123/head` or `refs/pull/123/master`.\n"
          + "  - " + DEFAULT_INTEGRATE_LABEL + ": A label that when exposed, can be used to"
          + " integrate automatically in the reverse workflow.\n"
          + "  - " + GITHUB_BASE_BRANCH + ": The base branch name used for the Pull Request.\n"
          + "  - " + GITHUB_BASE_BRANCH_SHA1 + ": The base branch SHA-1 used as baseline.\n"
          + "  - " + GITHUB_PR_TITLE + ": Title of the Pull Request.\n"
          + "  - " + GITHUB_PR_BODY + ": Body of the Pull Request.\n",
      parameters = {
          @Param(name = "self", type = GitModule.class, doc = "this object"),
          @Param(name = "url", type = String.class,
              doc = "Indicates the URL of the GitHub repository"),
          @Param(name = "use_merge", type = Boolean.class, defaultValue = "False",
              doc = "If the content for refs/pull/<ID>/merge should be used instead of the PR"
                  + " head. The GitOrigin-RevId still will be the one from refs/pull/<ID>/head"
                  + " revision."),
          @Param(name = "required_labels", type = SkylarkList.class,
              generic1 = String.class, defaultValue = "[]",
              doc = "Required labels to import the PR. All the labels need to be present in order"
                  + " to migrate the Pull Request.", positional = false),
          @Param(name = "retryable_labels", type = SkylarkList.class,
              generic1 = String.class, defaultValue = "[]",
              doc = "Required labels to import the PR that should be retried. This parameter must"
                  + " be a subset of required_labels.", positional = false),
          @Param(name = "submodules", type = String.class, defaultValue = "'NO'",
              doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
          @Param(name = "baseline_from_branch", type = Boolean.class,
              doc = "WARNING: Use this field only for github -> git CHANGE_REQUEST workflows.<br>"
                  + "When the field is set to true for CHANGE_REQUEST workflows it will find the"
                  + " baseline comparing the Pull Request with the base branch instead of looking"
                  + " for the *-RevId label in the commit message.", defaultValue = "False"),
          @Param(name = "first_parent", type = Boolean.class, defaultValue = "True",
              doc = "If true, it only uses the first parent when looking for changes. Note that"
                  + " when disabled in ITERATIVE mode, it will try to do a migration for each"
                  + " change of the merged branch.", positional = false),
          @Param(name = "state", type = String.class, defaultValue = "'OPEN'",
              doc = "Only migrate Pull Request with that state."
                  + " Possible values: `'OPEN'`, `'CLOSED'` or `'ALL'`. Default 'OPEN'"),
      },
      objectType = GitModule.class, useLocation = true)
  @UsesFlags(GithubPrOriginOptions.class)
  public static final BuiltinFunction GITHUB_PR_ORIGIN = new BuiltinFunction(
      GITHUB_PR_ORIGIN_NAME) {
    public GithubPROrigin invoke(GitModule self, String url, Boolean merge,
        SkylarkList<String> requiredLabels, SkylarkList<String> retryableLabels, String submodules,
        Boolean baselineFromBranch, Boolean firstParent, String state, Location location)
        throws EvalException {
      if (!url.contains("github.com")) {
        throw new EvalException(location, "Invalid Github URL: " + url);
      }
      GithubPrOriginOptions githubPrOriginOptions = self.options.get(GithubPrOriginOptions.class);
      return new GithubPROrigin(url, merge,
          self.options.get(GeneralOptions.class),
          self.options.get(GitOptions.class),
          self.options.get(GitOriginOptions.class),
          self.options.get(GithubOptions.class),
          githubPrOriginOptions.getRequiredLabels(requiredLabels),
          githubPrOriginOptions.getRetryableLabels(retryableLabels),
          SkylarkUtil.stringToEnum(location, "submodules", submodules, SubmoduleStrategy.class),
          baselineFromBranch, firstParent,
          SkylarkUtil.stringToEnum(location,"state", state, StateFilter.class));
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(name = "destination", returnType = GitDestination.class,
      doc = "Creates a commit in a git repository using the transformed worktree."
          + "<br><br>Given that Copybara doesn't ask for user/password in the console when"
          + " doing the push to remote repos, you have to use ssh protocol, have the credentials"
          + " cached or use a credential manager.",
      parameters = {
          @Param(name = "self", type = GitModule.class, doc = "this object"),
          @Param(name = "url", type = String.class,
              doc = "Indicates the URL to push to as well as the URL from which to get the parent "
                  + "commit"),
          @Param(name = "push", type = String.class,
              doc = "Reference to use for pushing the change, for example 'master'",
              defaultValue = "master"),
          @Param(name = "fetch", type = String.class,
              doc = "Indicates the ref from which to get the parent commit",
              defaultValue = "push reference", noneable = true),
          @Param(name = "skip_push", type = Boolean.class, defaultValue = "False",
              doc = "If set, copybara will not actually push the result to the destination. This is"
                  + " meant for testing workflows and dry runs."),
          @Param(name = "integrates", type = SkylarkList.class,
              generic1 = GitIntegrateChanges.class, defaultValue = "[]",
              // TODO(malcon): fake-merges Flip this
              doc = "(NOT IMPLEMENTED) Integrate changes from a url present in the migrated change"
                  + " label.", positional = false),
      },
      objectType = GitModule.class, useLocation = true)
  @UsesFlags(GitDestinationOptions.class)
  public static final BuiltinFunction DESTINATION = new BuiltinFunction("destination",
      ImmutableList.of("master", Runtime.NONE, false, NO_GIT_DESTINATION_INTEGRATES)) {
    public GitDestination invoke(GitModule self, String url, String push, Object fetch,
        Boolean skipPush, SkylarkList<GitIntegrateChanges> integrates, Location location)
        throws EvalException {
      GitDestinationOptions destinationOptions = self.options.get(GitDestinationOptions.class);
      String resolvedPush = checkNotEmpty(firstNotNull(destinationOptions.push, push),
          "push", location);
      GeneralOptions generalOptions = self.options.get(GeneralOptions.class);
      return new GitDestination(
          checkNotEmpty(firstNotNull(destinationOptions.url, url),
              "url", location),
          checkNotEmpty(
              firstNotNull(destinationOptions.fetch,
                  convertFromNoneable(fetch, null),
                  resolvedPush),
              "fetch", location),
          resolvedPush,
          destinationOptions,
          self.options.get(GitOptions.class),
          generalOptions,
          skipPush,
          new DefaultCommitGenerator(),
          new ProcessPushStructuredOutput(),
          SkylarkList.castList(integrates, GitIntegrateChanges.class, "integrates"));
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(name = "github_pr_destination", returnType = GithubPrDestination.class,
      doc = "Creates changes in a new pull request in the destination.",
      parameters = {
          @Param(name = "self", type = GitModule.class, doc = "this object"),
          @Param(name = "url", type = String.class,
              doc = "Url of the GitHub project. For example"
                  + " \"https://github.com/google/copybara'\""),
          @Param(name = "destination_ref", type = String.class,
              doc = "Destination reference for the change. By default 'master'",
              defaultValue = "master"),
          @Param(name = "skip_push", type = Boolean.class, defaultValue = "False",
              doc = "If set, copybara will not actually push the result to the destination. This is"
                  + " meant for testing workflows and dry runs."),
          @Param(name = "title", type = String.class, defaultValue = "None", noneable = true,
              doc = "When creating a pull request, use this title. By default it uses the change"
                  + " first line."),
          @Param(name = "body", type = String.class, defaultValue = "None", noneable = true,
              doc = "When creating a pull request, use this body. By default it uses the change"
                  + " summary."),
      },
      objectType = GitModule.class, useLocation = true)
  @UsesFlags({GitDestinationOptions.class, GithubDestinationOptions.class})
  public static final BuiltinFunction GH_PR_DESTINATION = new BuiltinFunction(
      "github_pr_destination",
      ImmutableList.of("master", false, Runtime.NONE, Runtime.NONE)) {
    public GithubPrDestination invoke(GitModule self, String url, String destinationRef,
        Boolean skipPush, Object title, Object body, Location location) throws EvalException {
      GeneralOptions generalOptions = self.options.get(GeneralOptions.class);
      // We don't restrict to github.com domain so that we can support GH Enterprise
      // in the future.
      checkRemoteUrl(url, location);
      return new GithubPrDestination(
          url,
          destinationRef,
          generalOptions,
          self.options.get(GithubOptions.class),
          self.options.get(GitDestinationOptions.class),
          self.options.get(GithubDestinationOptions.class),
          self.options.get(GitOptions.class),
          skipPush,
          new DefaultCommitGenerator(),
          new ProcessPushStructuredOutput(),
          NO_GIT_DESTINATION_INTEGRATES,
          SkylarkUtil.convertFromNoneable(title, null),
          SkylarkUtil.convertFromNoneable(body, null));
    }
  };

  private static void checkRemoteUrl(String url, Location location) throws EvalException {
    try {
      URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new EvalException(location, url + " is not a valid git url");
    }
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
  @SkylarkSignature(name = "gerrit_destination", returnType = GerritDestination.class,
      doc = "Creates a change in Gerrit using the transformed worktree. If this is used in "
          + "iterative mode, then each commit pushed in a single Copybara invocation will have the "
          + "correct commit parent. The reviews generated can then be easily done in the correct "
          + "order without rebasing.",
      parameters = {
          @Param(name = "self", type = GitModule.class, doc = "this object"),
          @Param(name = "url", type = String.class,
              doc = "Indicates the URL to push to as well as the URL from which to get the parent "
                  + "commit"),
          @Param(name = "fetch", type = String.class,
              doc = "Indicates the ref from which to get the parent commit"),
          @Param(
              name = "push_to_refs_for", type = String.class, defaultValue = "''",
              doc = "Review branch to push the change to, for example setting this to 'feature_x'"
                  + " causes the destination to push to 'refs/for/feature_x'. It defaults to "
                  + "'fetch' value."),
          @Param(name = "submit", type = Boolean.class,
              doc =
                  "If true, skip the push thru Gerrit refs/for/branch and directly push to branch."
                      + " This is effectively a git.destination that sets a Change-Id",
              defaultValue = "False"),
          @Param(
              name = "change_id_policy", type = String.class, defaultValue = "'FAIL_IF_PRESENT'",
              doc = "What to do in the presence or absent of Change-Id in message:"
                  + "<ul>"
                  + "  <li>`'REQUIRE'`: Require that the change_id is present in the message as a"
                  + " valid label</li>"
                  + "  <li>`'FAIL_IF_PRESENT'`: Fail if found in message</li>"
                  + "  <li>`'REUSE'`: Reuse if present. Otherwise generate a new one</li>"
                  + "  <li>`'REPLACE'`: Replace with a new one if found</li>"
                  + "</ul>")
      },
      objectType = GitModule.class, useLocation = true)
  @UsesFlags(GitDestinationOptions.class)
  public static final BuiltinFunction GERRIT_DESTINATION =
      new BuiltinFunction("gerrit_destination") {
    public GerritDestination invoke(
        GitModule self, String url, String fetch, String pushToRefsFor, Boolean submit,
        String changeIdPolicy, Location location) throws EvalException {
      return GerritDestination.newGerritDestination(
          self.options,
          checkNotEmpty(url, "url", location),
          checkNotEmpty(fetch, "fetch", location),
          pushToRefsFor,
          submit,
          SkylarkUtil.stringToEnum(location, "change_id_policy", changeIdPolicy,
              ChangeIdPolicy.class));
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(
    name = "github_api",
    returnType = GitHubEndPoint.class,
    doc = "Defines a feedback API endpoint for GitHub, that exposes relevant GitHub API "
        + "operations.",
    parameters = {
      @Param(name = "self", type = GitModule.class, doc = "this object"),
      @Param(name = "url", type = String.class, doc = "Indicates the GitHub repo URL."),
    },
    objectType = GitModule.class,
    useLocation = true
  )
  @UsesFlags(GithubOptions.class)
  public static final BuiltinFunction GITHUB_ENDPOINT =
      new BuiltinFunction("github_api") {
        public GitHubEndPoint invoke(GitModule self, String url, Location location)
            throws EvalException {
          return new GitHubEndPoint(
              self.options.get(GithubOptions.class), checkNotEmpty(url, "url", location));
        }
      };

  @SuppressWarnings("unused")
  @SkylarkSignature(
      name = "gerrit_api",
      returnType = GerritEndpoint.class,
      documented = false,
      doc = "Defines a feedback API endpoint for Gerrit, that exposes relevant Gerrit API "
          + "operations.",
      parameters = {
          @Param(name = "self", type = GitModule.class, doc = "this object"),
          @Param(name = "url", type = String.class, doc = "Indicates the Gerrit repo URL."),
      },
      objectType = GitModule.class,
      useLocation = true
  )
  @UsesFlags(GerritOptions.class)
  public static final BuiltinFunction GERRIT_ENDPOINT =
      new BuiltinFunction("gerrit_api") {
        public GerritEndpoint invoke(GitModule self, String url, Location location)
            throws EvalException {
          return new GerritEndpoint(
              self.options.get(GerritOptions.class), checkNotEmpty(url, "url", location));
        }
      };

  @SuppressWarnings("unused")
  @SkylarkSignature(
      name = "gerrit_trigger",
      returnType = GerritTrigger.class,
      documented = false,
      doc = "Defines a feedback trigger based on updates on a Gerrit change.",
      parameters = {
        @Param(name = "self", type = GitModule.class, doc = "this object"),
        @Param(name = "url", type = String.class, doc = "Indicates the Gerrit repo URL."),
      },
      objectType = GitModule.class,
      useLocation = true)
  @UsesFlags(GerritOptions.class)
  public static final BuiltinFunction GERRIT_LABELS_TRIGGER =
      new BuiltinFunction("gerrit_trigger") {
        public GerritTrigger invoke(GitModule self, String url, Location location)
            throws EvalException {
          return new GerritTrigger(
              self.options.get(GerritOptions.class), checkNotEmpty(url, "url", location));
        }
      };

  @SuppressWarnings("unused")
  @SkylarkSignature(
      name = "review_input",
      returnType = SetReviewInput.class,
      documented = false,
      doc = "Creates a review to be posted on Gerrit.",
      parameters = {
        @Param(name = "self", type = GitModule.class, doc = "this object"),
        @Param(
            name = "labels",
            type = SkylarkDict.class,
            doc = "The labels to post.",
            defaultValue = "{}"),
      },
      objectType = GitModule.class,
      useLocation = true)
  @UsesFlags(GerritOptions.class)
  public static final BuiltinFunction CREATE_REVIEW_INPUT =
      new BuiltinFunction("review_input") {
        public SetReviewInput invoke(
            GitModule self, SkylarkDict<String, Integer> labels, Location location)
            throws EvalException {
          return SetReviewInput.create(
              SkylarkDict.castSkylarkDictOrNoneToDict(
                  labels, String.class, Integer.class, "Gerrit review  labels"));
        }
      };

  @Override
  public void setOptions(Options options) {
    this.options = checkNotNull(options);
  }

  @Override
  public void setConfigFile(ConfigFile<?> mainConfigFile, ConfigFile<?> currentConfigFile) {
    this.mainConfigFile = mainConfigFile;
  }
}
