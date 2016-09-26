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

import com.google.common.base.Strings;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.config.base.OptionsAwareModule;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.copybara.git.GitDestination.DefaultCommitGenerator;
import com.google.copybara.git.GitDestination.ProcessPushOutput;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Type;

/**
 * Main module that groups all the functions that create Git origins and destinations.
 */
@SkylarkModule(
    name = "git",
    doc = "Set of functions to define Git origins and destinations.",
    category = SkylarkModuleCategory.BUILTIN)
@UsesFlags(GitOptions.class)
public class GitModule implements OptionsAwareModule {

  private Options options;

  @SkylarkSignature(name = "origin", returnType = GitOrigin.class,
      doc = "Defines a standard Git origin. For Git specific origins use: `github_origin` or "
          + "`gerrit_origin`.<br><br>"
          + "All the origins in this module accept several string formats as reference (When"
          + " copybara is called in the form of `copybara config workflow reference`):<br>"
          + "<ul>"
          + "<li>**Branch name:** For example `master`</li>"
          + "<li>**An arbitrary reference:** `refs/changes/20/50820/1`</li>"
          + "<li>**A SHA-1:** Note that currently it has to be reachable from the default refspec</li>"
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
      },
      objectType = GitModule.class)
  public static final BuiltinFunction ORIGIN = new BuiltinFunction("origin") {
    public GitOrigin invoke(GitModule self, String url, Object ref)
        throws EvalException {
      return GitOrigin.newGitOrigin(
          self.options, url, Type.STRING.convertOptional(ref, "ref"), GitRepoType.GIT,
          self.options.get(GeneralOptions.class).getEnvironment());
    }
  };

  @SkylarkSignature(name = "gerrit_origin", returnType = GitOrigin.class,
      doc = "Defines a Git origin of type Gerrit.",
      parameters = {
          @Param(name = "self", type = GitModule.class, doc = "this object"),
          @Param(name = "url", type = String.class,
              doc = "Indicates the URL of the git repository"),
          @Param(name = "ref", type = String.class, noneable = true, defaultValue = "None",
              doc = "Represents the default reference that will be used for reading the revision "
                  + "from the git repository. For example: 'master'"),
      },
      objectType = GitModule.class)
  public static final BuiltinFunction GERRIT_ORIGIN = new BuiltinFunction("gerrit_origin") {
    public GitOrigin invoke(GitModule self, String url, Object ref)
        throws EvalException {
      // TODO(copybara-team): Validate that URL is a Gerrit one
      return GitOrigin.newGitOrigin(
          self.options, url, Type.STRING.convertOptional(ref, "ref"), GitRepoType.GERRIT,
          self.options.get(GeneralOptions.class).getEnvironment());
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
      },
      objectType = GitModule.class, useLocation = true)
  public static final BuiltinFunction GITHUB_ORIGIN = new BuiltinFunction("github_origin") {
    public GitOrigin invoke(GitModule self, String url, Object ref, Location location)
        throws EvalException {
      if (!url.contains("github.com")) {
        throw new EvalException(location, "Invalid Github URL: " + url);
      }

      return GitOrigin.newGitOrigin(
          self.options, url, Type.STRING.convertOptional(ref, "ref"), GitRepoType.GITHUB,
          self.options.get(GeneralOptions.class).getEnvironment());
    }
  };

  @SkylarkSignature(name = "destination", returnType = GitDestination.class,
      doc = "Creates a commit in a git repository using the transformed worktree",
      parameters = {
          @Param(name = "self", type = GitModule.class, doc = "this object"),
          @Param(name = "url", type = String.class,
              doc = "Indicates the URL to push to as well as the URL from which to get the parent "
                  + "commit"),
          @Param(name = "fetch", type = String.class,
              doc = "Indicates the ref from which to get the parent commit"),
          @Param(name = "push", type = String.class,
              doc = "Reference to use for pushing the change, for example 'master'"),
      },
      objectType = GitModule.class, useLocation = true)
  @UsesFlags(GitDestinationOptions.class)
  public static final BuiltinFunction DESTINATION = new BuiltinFunction("destination") {
    public GitDestination invoke(GitModule self, String url, String fetch, String push,
        Location location) throws EvalException {
      GitDestinationOptions destinationOptions = self.options.get(GitDestinationOptions.class);
      return new GitDestination(
          checkNotEmpty(destinationUrl(url, destinationOptions), "url", location),
          checkNotEmpty(fetch, "fetch", location),
          checkNotEmpty(push, "push", location),
          destinationOptions,
          self.options.get(GeneralOptions.class).isVerbose(),
          new DefaultCommitGenerator(),
          new ProcessPushOutput(),
          self.options.get(GeneralOptions.class).getEnvironment());
    }
  };

  private static String destinationUrl(String url, GitDestinationOptions destinationOptions) {
    return Strings.isNullOrEmpty(destinationOptions.url) ? url : destinationOptions.url;
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
          self.options.get(GeneralOptions.class).getEnvironment());
    }
  };

  @Override
  public void setOptions(Options options) {
    this.options = checkNotNull(options);
  }
}
