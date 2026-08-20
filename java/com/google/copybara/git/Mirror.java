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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.EndpointProvider;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LazyResourceLoader;
import com.google.copybara.action.Action;
import com.google.copybara.action.ActionResult;
import com.google.copybara.action.ActionResult.Result;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.Migration;
import com.google.copybara.effect.DestinationEffect;
import com.google.copybara.effect.DestinationEffect.DestinationRef;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.github.util.GitHubHost;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationFinishedEvent;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.transform.SkylarkConsole;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.StarlarkThread;

/** Mirror one or more refspec between git repositories. */
public class Mirror implements Migration {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String MODE_STRING = "MIRROR";
  private static final ImmutableList<String> LS_REMOTE_FLAGS = ImmutableList.of("--refs");

  // TODO(b/549194654): Remove this flag once the feature is fully rolled out.
  public static final String GIT_MIRROR_CHECK_ALREADY_IN_SYNC = "GIT_MIRROR_CHECK_ALREADY_IN_SYNC";

  private final GeneralOptions generalOptions;
  private final GitOptions gitOptions;
  private final String name;
  private final String origin;
  private final String destination;
  private final List<Refspec> refspec;
  private final GitDestinationOptions gitDestinationOptions;
  private final boolean prune;
  private final boolean partialFetch;
  private final ConfigFile mainConfigFile;
  @Nullable private final String description;
  @Nullable private final Action action;
  private final LazyResourceLoader<EndpointProvider<?>> originApiEndpointProvider;
  private final LazyResourceLoader<EndpointProvider<?>> destinationApiEndpointProvider;
  private final ImmutableList<CredentialFileHandler> credentials;
  private final ImmutableList<StarlarkThread.CallStackEntry> definitionStack;

  Mirror(
      GeneralOptions generalOptions,
      GitOptions gitOptions,
      String name,
      String origin,
      String destination,
      List<Refspec> refspec,
      GitDestinationOptions gitDestinationOptions,
      boolean prune,
      boolean partialFetch,
      ConfigFile mainConfigFile,
      @Nullable String description,
      @Nullable Action action,
      @Nullable LazyResourceLoader<EndpointProvider<?>> originApiEndpointProvider,
      @Nullable LazyResourceLoader<EndpointProvider<?>> destinationApiEndpointProvider,
      ImmutableList<CredentialFileHandler> credentials,
      ImmutableList<StarlarkThread.CallStackEntry> definitionStack) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
    this.name = Preconditions.checkNotNull(name);
    this.origin = Preconditions.checkNotNull(origin);
    this.destination = Preconditions.checkNotNull(destination);
    this.refspec = Preconditions.checkNotNull(refspec);
    this.gitDestinationOptions = gitDestinationOptions;
    this.prune = prune;
    this.partialFetch = partialFetch;
    this.mainConfigFile = Preconditions.checkNotNull(mainConfigFile);
    this.description = description;
    this.action = action;
    this.originApiEndpointProvider = originApiEndpointProvider;
    this.destinationApiEndpointProvider = destinationApiEndpointProvider;
    this.credentials = Preconditions.checkNotNull(credentials);
    this.definitionStack = definitionStack;
  }

  @Override
  public void run(Path workdir, ImmutableList<String> sourceRefs)
      throws RepoException, IOException, ValidationException {
    boolean updated = false;
    try (ProfilerTask ignore = generalOptions.profiler().start("run/" + name)) {
      GitRepository repo = getLocalRepo();
      maybeConfigureGitNameAndEmail(repo);
      if (action == null) {
        updated = defaultMirror(repo);
      } else {
        customMirror(repo, sourceRefs);
        updated = true;
      }
    }

    // More fine grain events based on the references created/updated/deleted:
    DestinationEffect.Type effectType =
        (generalOptions.dryRunMode || !updated)
            ? DestinationEffect.Type.NOOP
            : DestinationEffect.Type.UPDATED;
    String effectSummary;
    if (generalOptions.dryRunMode) {
      effectSummary = String.format("Refspecs %s can be mirrored", refspec);
    } else if (!updated) {
      effectSummary = String.format("Refspecs %s are already in sync", refspec);
    } else {
      effectSummary = String.format("Refspecs %s mirrored successfully", refspec);
    }

    ChangeMigrationFinishedEvent event =
        new ChangeMigrationFinishedEvent(
            ImmutableList.of(
                new DestinationEffect(
                    effectType,
                    effectSummary,
                    // TODO(danielromero): Populate OriginRef here
                    ImmutableList.of(),
                    new DestinationRef(
                        getOriginDestinationRef(destination), "mirror", /* url= */ null))),
            getOriginDescription(),
            getDestinationDescription());
    dispatchMigrationFinishedEvent(event);
  }

  private void dispatchMigrationFinishedEvent(ChangeMigrationFinishedEvent event) {
    generalOptions.eventMonitors().dispatchEvent(m -> m.onChangeMigrationFinished(event));
  }

  private void customMirror(GitRepository repo, ImmutableList<String> sourceRefs)
      throws ValidationException, RepoException {
    ActionResult actionResult = null;

    GitMirrorContext context =
        new GitMirrorContext(
            action,
            new SkylarkConsole(generalOptions.console()),
            generalOptions.profiler(),
            sourceRefs,
            refspec,
            origin,
            destination,
            generalOptions.isForced(),
            repo,
            generalOptions.getDirFactory(),
            Dict.empty(),
            gitOptions,
            originApiEndpointProvider,
            destinationApiEndpointProvider);
    try {
      action.run(context);
      actionResult = context.getActionResult();

      ValidationException.checkCondition(
          actionResult.getResult() != Result.ERROR,
          "An error occurred during the git.mirror migration '%s' on action `%s`. Detailed message:"
              + " %s",
          name,
          action.getName(),
          actionResult.getMsg());
    } catch (NonFastForwardRepositoryException e) {
      actionResult = ActionResult.error(action.getName() + ": " + e.getMessage());
      logger.atWarning().withCause(e).log();
    } finally {
      dispatchMigrationFinishedEvent(
          new ChangeMigrationFinishedEvent(
              context.getNewDestinationEffects(),
              getOriginDescription(),
              getDestinationDescription()));
    }

    if (actionResult.getResult() == Result.NO_OP) {
      throw new EmptyChangeException(
          String.format(
              "git.mirror migration '%s' was noop. Detailed message: %s",
              name, actionResult.getMsg()));
    }
  }

  private Optional<Refspec> findOriginRefspec(String originRef) {
    return refspec.stream().filter(r -> r.matchesOrigin(originRef)).findFirst();
  }

  private Optional<Refspec> findDestinationRefspec(String destRef) {
    return refspec.stream().filter(r -> r.invert().matchesOrigin(destRef)).findFirst();
  }

  private boolean isAlreadyInSync(GitRepository repo) throws RepoException, ValidationException {
    try (ProfilerTask ignore = generalOptions.profiler().start("ls_remote")) {
      ImmutableList<String> originRefPatterns =
          refspec.stream().map(Refspec::getOrigin).collect(toImmutableList());
      ImmutableList<String> destRefPatterns =
          refspec.stream().map(Refspec::getDestination).collect(toImmutableList());

      ImmutableMap<String, String> originRefs =
          ImmutableMap.copyOf(repo.lsRemote(origin, originRefPatterns, LS_REMOTE_FLAGS));
      ImmutableMap<String, String> destRefs =
          ImmutableMap.copyOf(repo.lsRemote(destination, destRefPatterns, LS_REMOTE_FLAGS));

      for (Entry<String, String> entry : originRefs.entrySet()) {
        String originRef = entry.getKey();
        String originSha = entry.getValue();

        if (findOriginRefspec(originRef)
            .filter(
                matchingRefspec ->
                    !Objects.equals(
                        originSha, destRefs.get(matchingRefspec.convert(originRef))))
            .isPresent()) {
          return false;
        }
      }

      // If prune, verify destination has no extra/dangling refs that were deleted on origin.
      if (prune) {
        for (String destRef : destRefs.keySet()) {
          if (findDestinationRefspec(destRef)
              .filter(
                  matchingRefspec ->
                      !originRefs.containsKey(
                          matchingRefspec.invert().convert(destRef)))
              .isPresent()) {
            return false;
          }
        }
      }

      return true;
    }
  }

  private boolean defaultMirror(GitRepository repo) throws RepoException, ValidationException {
    if (generalOptions.isTemporaryFeature(GIT_MIRROR_CHECK_ALREADY_IN_SYNC, false)
        && isAlreadyInSync(repo)) {
      generalOptions
          .console()
          .infoFmt(
              "Origin and destination refs are in sync for %s. Skipping fetch and push.", name);
      return false;
    }

    ImmutableList<String> fetchRefspecs =
        refspec.stream().map(r -> r.originToOrigin().toString()).collect(toImmutableList());

    generalOptions.console().progressFmt("Fetching from %s", origin);

    Profiler profiler = generalOptions.profiler();
    try (ProfilerTask ignore1 = profiler.start("fetch")) {
      repo.fetch(
          origin,
          /* prune= */ true,
          /* force= */ true,
          fetchRefspecs,
          partialFetch,
          Optional.empty(),
          false);
    }

    if (generalOptions.dryRunMode) {
      generalOptions.console().progressFmt("Skipping push to %s. You can check the"
          + " commits to push in: %s", destination, repo.getGitDir());
    } else {
      generalOptions.console().progressFmt("Pushing to %s", destination);
      List<Refspec> pushRefspecs =
          generalOptions.isForced()
              ? refspec.stream().map(Refspec::withAllowNoFastForward).collect(toImmutableList())
              : refspec;
      try (ProfilerTask ignore1 = profiler.start("push")) {
        repo.push()
            .prune(prune)
            .withRefspecs(destination, pushRefspecs)
            .withPushOptions(ImmutableList.copyOf(gitOptions.gitPushOptions))
            .run();
      } catch (NonFastForwardRepositoryException e) {
        // Normally we want non-fast-forward to retry, but for git.mirror, given that it handles
        // multiple refs, and that mirrors, it is better to just fail and tell the user.
        throw new ValidationException(
            "Error pushing some refs because origin is behind:" + e.getMessage(), e);
      }
    }
    return true;
  }

  private void maybeConfigureGitNameAndEmail(GitRepository repo) throws RepoException {
    if (!Strings.isNullOrEmpty(gitDestinationOptions.committerName)) {
      repo.simpleCommand("config", "user.name", gitDestinationOptions.committerName);
    }
    if (!Strings.isNullOrEmpty(gitDestinationOptions.committerEmail)) {
      repo.simpleCommand("config", "user.email", gitDestinationOptions.committerEmail);
    }
  }

  private static String getOriginDestinationRef(String url) throws ValidationException {
    // TODO(copybara-team): This is used just for normalization. We should be able to do it without
    // knowing the host.
    GitHubHost gitHubHost = new GitHubHost("github.com");
    return gitHubHost.isGitHubUrl(url) ? gitHubHost.normalizeUrl(url) : url;
  }

  @VisibleForTesting
  GitRepository getLocalRepo() throws RepoException {
    GitRepository repo = gitOptions.cachedBareRepoForUrl(origin);
    for (CredentialFileHandler cred : credentials) {
      try {
        cred.install(repo, gitOptions.getConfigCredsFile(generalOptions));
      } catch (IOException e) {
        throw new RepoException("Unable to store credentials", e);
      }
    }
    return repo;
  }

  @Override
  public ImmutableSetMultimap<String, String> getOriginDescription() {
    return new ImmutableSetMultimap.Builder<String, String>()
        .put("type", "git.mirror")
        .put("url", origin)
        .putAll("ref", refspec.stream().map(Refspec::getOrigin).collect(toImmutableList()))
        .build();
  }

  @Override
  public ImmutableSetMultimap<String, String> getDestinationDescription() {
    return new ImmutableSetMultimap.Builder<String, String>()
        .put("type", "git.mirror")
        .put("url", destination)
        .putAll("ref", refspec.stream().map(Refspec::getDestination).collect(toImmutableList()))
        .build();
  }

  @Override
  public ImmutableList<ImmutableSetMultimap<String, String>> getCredentialDescription() {
    ImmutableList.Builder<ImmutableSetMultimap<String, String>> desc = ImmutableList.builder();
    for (CredentialFileHandler cred : credentials) {
      desc.addAll(cred.describeCredentials());
    }
    return desc.build();
  }

  @Override
  public ConfigFile getMainConfigFile() {
    return mainConfigFile;
  }

  @Override
  public String getName() {
    return name;
  }

  @Nullable
  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public String getModeString() {
    return MODE_STRING;
  }

  @Override
  public ImmutableList<StarlarkThread.CallStackEntry> getDefinitionStack() {
    return definitionStack;
  }
}
