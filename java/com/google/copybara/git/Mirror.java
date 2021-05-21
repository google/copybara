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

import static com.google.copybara.git.github.util.GitHubHost.GITHUB_COM;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.DestinationEffect;
import com.google.copybara.DestinationEffect.DestinationRef;
import com.google.copybara.GeneralOptions;
import com.google.copybara.action.Action;
import com.google.copybara.action.ActionResult;
import com.google.copybara.action.ActionResult.Result;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.Migration;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationFinishedEvent;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.transform.SkylarkConsole;

import net.starlark.java.eval.Dict;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * Mirror one or more refspects between git repositories.
 */
public class Mirror implements Migration {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String MODE_STRING = "MIRROR";

  private final GeneralOptions generalOptions;
  private final GitOptions gitOptions;
  private final String name;
  private final String origin;
  private final String destination;
  private final List<Refspec> refspec;
  private final GitMirrorOptions mirrorOptions;
  private final boolean prune;
  private final boolean partialFetch;
  private final ConfigFile mainConfigFile;
  @Nullable private final String description;
  private final Iterable<Action> actions;

  Mirror(GeneralOptions generalOptions, GitOptions gitOptions, String name, String origin,
      String destination, List<Refspec> refspec, GitMirrorOptions mirrorOptions, boolean prune,
      boolean partialFetch, ConfigFile mainConfigFile, @Nullable String description,
      ImmutableList<Action> actions) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
    this.name = Preconditions.checkNotNull(name);
    this.origin = Preconditions.checkNotNull(origin);
    this.destination = Preconditions.checkNotNull(destination);
    this.refspec = Preconditions.checkNotNull(refspec);
    this.mirrorOptions = Preconditions.checkNotNull(mirrorOptions);
    this.prune = prune;
    this.partialFetch = partialFetch;
    this.mainConfigFile = Preconditions.checkNotNull(mainConfigFile);
    this.description = description;
    this.actions = Preconditions.checkNotNull(actions);
  }

  @Override
  public void run(Path workdir, ImmutableList<String> sourceRefs)
      throws RepoException, IOException, ValidationException {

    try (ProfilerTask ignore = generalOptions.profiler().start("run/" + name)) {

      GitRepository repo = gitOptions.cachedBareRepoForUrl(origin);

      if (Iterables.isEmpty(actions)) {
        defaultMirror(repo);
      } else {
        ImmutableList.Builder<ActionResult> allResultsBuilder = ImmutableList.builder();
        for (Action action : actions) {
          GitMirrorContext context = new GitMirrorContext(action,
              new SkylarkConsole(generalOptions.console()), sourceRefs, refspec, origin,
              destination, generalOptions.isForced(),
              repo, generalOptions.getDirFactory(),
              Dict.empty());
          try {
            action.run(context);
            ActionResult actionResult = context.getActionResult();
            allResultsBuilder.add(actionResult);
            // First error aborts the execution of the other actions unless --force is used
            ValidationException.checkCondition(generalOptions.isForced()
                    || actionResult.getResult() != Result.ERROR,
                "Feedback migration '%s' action '%s' returned error: %s. Aborting execution.",
                name, action.getName(), actionResult.getMsg());

          } catch (NonFastForwardRepositoryException e) {
            allResultsBuilder.add(ActionResult.error(action.getName() + ": " + e.getMessage()));
            if (!generalOptions.isForced()) {
              throw e;
            }
            logger.atWarning().withCause(e).log();
          } finally {
            generalOptions.eventMonitors().dispatchEvent(m -> m.onChangeMigrationFinished(
                new ChangeMigrationFinishedEvent(
                    ImmutableList.copyOf(context.getNewDestinationEffects()),
                    getOriginDescription(), getDestinationDescription())));
          }
        }
        ImmutableList<ActionResult> allResults = allResultsBuilder.build();
        if (allResults.stream().anyMatch(a -> a.getResult() == Result.ERROR)) {
          String errors = allResults.stream()
              .filter(a -> a.getResult() == Result.ERROR)
              .map(ActionResult::getMsg)
              .collect(Collectors.joining("\n - "));
          throw new ValidationException("One or more errors happened during the migration:\n"
              + " - " + errors);
        }

        // This check also returns true if there are no actions
        if (allResults.stream().allMatch(a -> a.getResult() == Result.NO_OP)) {
          String detailedMessage = allResults.isEmpty()
              ? "actions field is empty"
              : allResults.stream().map(ActionResult::getMsg)
                  .collect(ImmutableList.toImmutableList()).toString();
          throw new EmptyChangeException(
              String.format("git.mirror migration '%s' was noop. Detailed messages: %s",
                  name, detailedMessage));
        }
      }
    }

    // More fine grain events based on the references created/updated/deleted:
    ChangeMigrationFinishedEvent event =
        new ChangeMigrationFinishedEvent(
            ImmutableList.of(
                new DestinationEffect(
                    generalOptions.dryRunMode
                        ? DestinationEffect.Type.NOOP
                        : DestinationEffect.Type.UPDATED,
                    generalOptions.dryRunMode
                        ? "Refspecs " + refspec + " can be mirrored"
                        : "Refspecs " + refspec + " mirrored successfully",
                    // TODO(danielromero): Populate OriginRef here
                    ImmutableList.of(),
                    new DestinationRef(
                        getOriginDestinationRef(destination), "mirror", /*url=*/ null))),
            getOriginDescription(), getDestinationDescription());
    generalOptions.eventMonitors().dispatchEvent(m -> m.onChangeMigrationFinished(event));
  }

  private void defaultMirror(GitRepository repo) throws RepoException, ValidationException {
    List<String> fetchRefspecs = refspec.stream()
        .map(r -> r.originToOrigin().toString())
        .collect(Collectors.toList());

    generalOptions.console().progressFmt("Fetching from %s", origin);

    Profiler profiler = generalOptions.profiler();
    try (ProfilerTask ignore1 = profiler.start("fetch")) {
      repo.fetch(origin, /*prune=*/true,
          /*force=*/true, fetchRefspecs, partialFetch);
    }

    if (generalOptions.dryRunMode) {
      generalOptions.console().progressFmt("Skipping push to %s. You can check the"
          + " commits to push in: %s", destination, repo.getGitDir());
    } else {
      generalOptions.console().progressFmt("Pushing to %s", destination);
      List<Refspec> pushRefspecs = mirrorOptions.forcePush || generalOptions.isForced()
          ? refspec.stream().map(Refspec::withAllowNoFastForward).collect(Collectors.toList())
          : refspec;
      try (ProfilerTask ignore1 = profiler.start("push")) {
        repo.push().prune(prune).withRefspecs(destination, pushRefspecs).run();
      } catch (NonFastForwardRepositoryException e) {
        // Normally we want non-fast-forward to retry, but for git.mirror, given that it handles
        // multiple refs, and that mirrors, it is better to just fail and tell the user.
        throw new ValidationException(
            "Error pushing some refs because origin is behind:" + e.getMessage(), e);
      }
    }
  }

  private static String getOriginDestinationRef(String url) throws ValidationException {
    // TODO(copybara-team): This is used just for normalization. We should be able to do it without
    // knowing the host.
    return GITHUB_COM.isGitHubUrl(url) ? GITHUB_COM.normalizeUrl(url) : url;
  }

  @VisibleForTesting
  GitRepository getLocalRepo() throws RepoException {
    return gitOptions.cachedBareRepoForUrl(origin);
  }

  @Override
  public ImmutableSetMultimap<String, String> getOriginDescription() {
    return new ImmutableSetMultimap.Builder<String, String>()
        .put("type", "git.mirror")
        .put("url", origin)
        .putAll("ref", refspec.stream().map(Refspec::getOrigin).collect(Collectors.toList()))
        .build();
  }

  @Override
  public ImmutableSetMultimap<String, String> getDestinationDescription() {
    return new ImmutableSetMultimap.Builder<String, String>()
        .put("type", "git.mirror")
        .put("url", destination)
        .putAll("ref", refspec.stream().map(Refspec::getDestination).collect(Collectors.toList()))
        .build();
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
}
