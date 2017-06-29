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
import static com.google.copybara.config.base.SkylarkUtil.checkNotEmpty;
import static com.google.copybara.config.base.SkylarkUtil.convertFromNoneable;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.copybara.Core;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.config.base.OptionsAwareModule;
import com.google.copybara.config.base.SkylarkUtil;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.copybara.git.GitDestination.DefaultCommitGenerator;
import com.google.copybara.git.GitDestination.ProcessPushStructuredOutput;
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
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.Type;
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

  private Options options;
  private ConfigFile<?> mainConfigFile;

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
              + " This setting *only* affects merge commits.", positional = false),
      },
      objectType = GitModule.class)
  public static final BuiltinFunction ORIGIN = new BuiltinFunction("origin") {
    public GitOrigin invoke(GitModule self, String url, Object ref, String submodules,
        Boolean includeBranchCommitLogs) throws EvalException {
      return GitOrigin.newGitOrigin(
          self.options, url, Type.STRING.convertOptional(ref, "ref"), GitRepoType.GIT,
          SkylarkUtil.stringToEnum(location, "submodules",
              submodules, GitOrigin.SubmoduleStrategy.class),
          includeBranchCommitLogs);
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
        refspecs.add(Refspec.create(
            generalOptions.getEnvironment(), generalOptions.getCwd(), refspec, location));
      }
      Core.getCore(env).addMigration(location, name,
          new Mirror(generalOptions, self.options.get(GitOptions.class),
              name, origin, destination, refspecs,
              self.options.get(GitMirrorOptions.class).forcePush, prune, self.mainConfigFile));
      return Runtime.NONE;
    }
  };

  @SkylarkSignature(name = "gerrit_origin", returnType = GitOrigin.class,
      doc = "Defines a Git origin for Gerrit reviews.",
      parameters = {
          @Param(name = "self", type = GitModule.class, doc = "this object"),
          @Param(name = "url", type = String.class,
              doc = "Indicates the URL of the git repository"),
          @Param(name = "ref", type = String.class, noneable = true, defaultValue = "None",
              doc = "DEPRECATED. Use git.origin for submitted branches."),
          @Param(name = "submodules", type = String.class, defaultValue = "'NO'",
              doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
      },
      objectType = GitModule.class, useLocation = true)
  public static final BuiltinFunction GERRIT_ORIGIN = new BuiltinFunction("gerrit_origin") {
    public GitOrigin invoke(GitModule self, String url, Object ref, String submodules,
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
          /*includeBranchCommitLogs=*/false);
      }

      return GerritOrigin.newGerritOrigin(
          self.options, url, GitRepoType.GERRIT, SkylarkUtil.stringToEnum(location, "submodules",
              submodules, GitOrigin.SubmoduleStrategy.class));
    }
  };

  @SkylarkSignature(name = "github_origin", returnType = GitOrigin.class,
      doc = "Defines a Git origin of type Github.",
      parameters = {
          @Param(name = "self", type = GitModule.class, doc = "this object"),
          @Param(name = "url", type = String.class,
              doc = "Indicates the URL of the git repository"),
          @Param(name = "ref", type = String.class, noneable = true, defaultValue = "None",
              doc = "Represents the default reference that will be used for reading the revision "
                  + "from the git repository. For example: 'master'"),
          @Param(name = "submodules", type = String.class, defaultValue = "'NO'",
              doc = "Download submodules. Valid values: NO, YES, RECURSIVE."),
      },
      objectType = GitModule.class, useLocation = true)
  public static final BuiltinFunction GITHUB_ORIGIN = new BuiltinFunction("github_origin") {
    public GitOrigin invoke(GitModule self, String url, Object ref, String submodules,
        Location location) throws EvalException {
      if (!url.contains("github.com")) {
        throw new EvalException(location, "Invalid Github URL: " + url);
      }

      // TODO(copybara-team): See if we want to support includeBranchCommitLogs for GitHub repos.
      return GitOrigin.newGitOrigin(
          self.options, url, Type.STRING.convertOptional(ref, "ref"), GitRepoType.GITHUB,
          SkylarkUtil.stringToEnum(location, "submodules",
              submodules, GitOrigin.SubmoduleStrategy.class),
          /*includeBranchCommitLogs=*/false);
    }
  };

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
      },
      objectType = GitModule.class, useLocation = true)
  @UsesFlags(GitDestinationOptions.class)
  public static final BuiltinFunction DESTINATION = new BuiltinFunction("destination",
      ImmutableList.of("master", Runtime.NONE, false)) {
    public GitDestination invoke(GitModule self, String url, String push, Object fetch,
        Boolean skipPush, Location location) throws EvalException {
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
          generalOptions.isVerbose(),
          generalOptions.isForced(),
          skipPush,
          new DefaultCommitGenerator(),
          new ProcessPushStructuredOutput(generalOptions.getStructuredOutput()),
          generalOptions.console());
    }
  };

  private static String firstNotNull(String... values) {
    for (String value : values) {
      if (!Strings.isNullOrEmpty(value)) {
        return value;
      }
    }
    return null;
  }

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
      },
      objectType = GitModule.class, useLocation = true)
  @UsesFlags(GitDestinationOptions.class)
  public static final BuiltinFunction GERRIT_DESTINATION =
      new BuiltinFunction("gerrit_destination") {
    public GerritDestination invoke(
        GitModule self, String url, String fetch, String pushToRefsFor,
        Location location) throws EvalException {
      return GerritDestination.newGerritDestination(
          self.options,
          checkNotEmpty(url, "url", location),
          checkNotEmpty(fetch, "fetch", location),
          pushToRefsFor,
          self.options.get(GeneralOptions.class).isForced(),
          self.options.get(GeneralOptions.class).getEnvironment());
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
