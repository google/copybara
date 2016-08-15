package com.google.copybara.git;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.copybara.config.SkylarkUtil.checkNotEmpty;

import com.google.copybara.Options;
import com.google.copybara.config.EnvironmentAwareModule;
import com.google.copybara.config.OptionsAwareModule;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Type;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Main module that groups all the functions that create Git origins and destinations.
 */
@SkylarkModule(
    name = "git",
    doc = "Set of functions to define Git origins and destinations.",
    category = SkylarkModuleCategory.BUILTIN)
@UsesFlags(GitOptions.class)
public class Git implements OptionsAwareModule, EnvironmentAwareModule {

  private Options options;
  @Nullable
  private Map<String, String> environment;

  @SkylarkSignature(name = "origin", returnType = GitOrigin.class,
      doc = "Defines a standard Git origin. For Git specific origins use: github_origin or "
          + "gerrit_origin.",
      parameters = {
          @Param(name = "self", type = Git.class, doc = "this object"),
          @Param(name = "url", type = String.class,
              doc = "Indicates the URL of the git repository"),
          @Param(name = "ref", type = String.class, noneable = true, defaultValue = "None",
              doc = "Represents the default reference that will be used for reading the revision "
                  + "from the git repository. For example: 'master'"),
      },
      objectType = Git.class)
  public static final BuiltinFunction ORIGIN = new BuiltinFunction("origin") {
    public GitOrigin invoke(Git self, String url, Object ref)
        throws EvalException {
      return GitOrigin.newGitOrigin(
          self.options, url, Type.STRING.convertOptional(ref, "ref"), GitRepoType.GIT,
          self.environment);
    }
  };

  @SkylarkSignature(name = "gerrit_origin", returnType = GitOrigin.class,
      doc = "Defines a Git origin of type Gerrit.",
      parameters = {
          @Param(name = "self", type = Git.class, doc = "this object"),
          @Param(name = "url", type = String.class,
              doc = "Indicates the URL of the git repository"),
          @Param(name = "ref", type = String.class, noneable = true, defaultValue = "None",
              doc = "Represents the default reference that will be used for reading the revision "
                  + "from the git repository. For example: 'master'"),
      },
      objectType = Git.class)
  public static final BuiltinFunction GERRIT_ORIGIN = new BuiltinFunction("gerrit_origin") {
    public GitOrigin invoke(Git self, String url, Object ref)
        throws EvalException {
      // TODO(danielromero): Validate that URL is a Gerrit one
      return GitOrigin.newGitOrigin(
          self.options, url, Type.STRING.convertOptional(ref, "ref"), GitRepoType.GERRIT,
          self.environment);
    }
  };

  @SkylarkSignature(name = "github_origin", returnType = GitOrigin.class,
      doc = "Defines a Git origin of type Github.",
      parameters = {
          @Param(name = "self", type = Git.class, doc = "this object"),
          @Param(name = "url", type = String.class,
              doc = "Indicates the URL of the git repository"),
          @Param(name = "ref", type = String.class, noneable = true, defaultValue = "None",
              doc = "Represents the default reference that will be used for reading the revision "
                  + "from the git repository. For example: 'master'"),
      },
      objectType = Git.class, useLocation = true)
  public static final BuiltinFunction GITHUB_ORIGIN = new BuiltinFunction("github_origin") {
    public GitOrigin invoke(Git self, String url, Object ref, Location location)
        throws EvalException {
      if (!url.contains("github.com")) {
        throw new EvalException(location, "Invalid Github URL: " + url);
      }

      return GitOrigin.newGitOrigin(
          self.options, url, Type.STRING.convertOptional(ref, "ref"), GitRepoType.GITHUB,
          self.environment);
    }
  };

  @SkylarkSignature(name = "destination", returnType = GitDestination.class,
      doc = "Creates a commit in a git repository using the transformed worktree",
      parameters = {
          @Param(name = "self", type = Git.class, doc = "this object"),
          @Param(name = "url", type = String.class,
              doc = "Indicates the URL to push to as well as the URL from which to get the parent "
                  + "commit"),
          @Param(name = "fetch", type = String.class,
              doc = "Indicates the ref from which to get the parent commit"),
          @Param(name = "push", type = String.class,
              doc = "Reference to use for pushing the change, for example 'master'"),
      },
      objectType = Git.class, useLocation = true)
  public static final BuiltinFunction DESTINATION = new BuiltinFunction("destination") {
    public GitDestination invoke(Git self, String url, String fetch, String push, Location location)
        throws EvalException {
      return GitDestination.newGitDestination(
          self.options,
          checkNotEmpty(url, "url", location),
          checkNotEmpty(fetch, "fetch", location),
          checkNotEmpty(push, "push", location));
    }
  };

  @SkylarkSignature(name = "gerrit_destination", returnType = GerritDestination.class,
      doc = "Creates a change in Gerrit using the transformed worktree. If this is used in "
          + "iterative mode, then each commit pushed in a single Copybara invocation will have the "
          + "correct commit parent. The reviews generated can then be easily done in the correct "
          + "order without rebasing.",
      parameters = {
          @Param(name = "self", type = Git.class, doc = "this object"),
          @Param(name = "url", type = String.class,
              doc = "Indicates the URL to push to as well as the URL from which to get the parent "
                  + "commit"),
          @Param(name = "fetch", type = String.class,
              doc = "Indicates the ref from which to get the parent commit"),
          @Param(
              name = "pushToRefsFor", type = String.class, noneable = true, defaultValue = "None",
              doc = "Review branch to push the change to, for example setting this to 'feature_x'"
                  + " causes the destination to push to 'refs/for/feature_x'. It defaults to "
                  + "'fetch' value."),
      },
      objectType = Git.class, useLocation = true)
  public static final BuiltinFunction GERRIT_DESTINATION =
      new BuiltinFunction("gerrit_destination") {
    public GerritDestination invoke(
        Git self, String url, String fetch, Object pushToRefsFor, Location location)
        throws EvalException {
      return GerritDestination.newGerritDestination(
          self.options,
          checkNotEmpty(url, "url", location),
          checkNotEmpty(fetch, "fetch", location),
          Type.STRING.convertOptional(pushToRefsFor, "pushToRefsFor"));
    }
  };

  @Override
  public void setOptions(Options options) {
    this.options = checkNotNull(options);
  }

  @Override
  public void setEnvironment(@Nullable Map<String, String> environment) {
    this.environment = environment;
  }
}