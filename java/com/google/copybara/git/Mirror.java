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
import com.google.copybara.DestinationEffect;
import com.google.copybara.DestinationEffect.DestinationRef;
import com.google.copybara.GeneralOptions;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.Migration;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationFinishedEvent;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Mirror one or more refspects between git repositories.
 */
public class Mirror implements Migration {

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

  Mirror(GeneralOptions generalOptions, GitOptions gitOptions, String name, String origin,
      String destination, List<Refspec> refspec, GitMirrorOptions mirrorOptions, boolean prune,
      boolean partialFetch, ConfigFile mainConfigFile, @Nullable String description) {
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
  }

  @Override
  public void run(Path workdir, ImmutableList<String> sourceRefs)
      throws RepoException, IOException, ValidationException {
    try (ProfilerTask ignore = generalOptions.profiler().start("run/" + name)) {
      mirrorOptions.mirror(origin, destination, refspec, prune, partialFetch);
    } catch (RepoException e) {
      // non-fast-forward errors in git mirror usually means that the destination
      // has commits that the origin doesn't. Usually by a user submitting directly
      // to the destination instead of using Copybara.
      if (e.getMessage().contains("(non-fast-forward)")) {
        throw new ValidationException(e.getMessage(), e);
      }
      throw e;
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
