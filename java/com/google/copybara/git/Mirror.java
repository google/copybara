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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Migration;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.config.ConfigFile;
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
  private final boolean forcePush;
  private final boolean prune;
  private final ConfigFile<?> mainConfigFile;

  Mirror(GeneralOptions generalOptions, GitOptions gitOptions, String name, String origin,
      String destination, List<Refspec> refspec, boolean forcePush, boolean prune,
      ConfigFile<?> mainConfigFile) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
    this.name = Preconditions.checkNotNull(name);
    this.origin = Preconditions.checkNotNull(origin);
    this.destination = Preconditions.checkNotNull(destination);
    this.refspec = Preconditions.checkNotNull(refspec);
    this.forcePush = forcePush;
    this.prune = prune;
    this.mainConfigFile = Preconditions.checkNotNull(mainConfigFile);
  }

  @Override
  public void run(Path workdir, @Nullable String sourceRef)
      throws RepoException, IOException, ValidationException {
    GitRepository repo = getLocalRepo();
    List<String> fetchRefspecs = refspec.stream()
        .map(r -> r.getOrigin() + ":" + r.getOrigin())
        .collect(Collectors.toList());

    generalOptions.console().progress("Fetching from " + origin);

    repo.fetch(origin, /*prune=*/true, /*force=*/true, fetchRefspecs);

    generalOptions.console().progress("Pushing to " + destination);
    List<Refspec> pushRefspecs = forcePush
        ? refspec.stream().map(Refspec::withAllowNoFastForward).collect(Collectors.toList())
        : refspec;
    repo.push().prune(prune).withRefspecs(destination, pushRefspecs).run();
  }

  @VisibleForTesting
  GitRepository getLocalRepo() throws RepoException {
    return gitOptions.cachedBareRepoForUrl(origin);
  }

  @Override
  public ImmutableSetMultimap<String, String> getOriginDescription() {
    return new ImmutableSetMultimap.Builder<String, String>()
        .put("url", origin)
        .putAll("ref", refspec.stream().map(Refspec::toString).collect(Collectors.toList()))
        .build();
  }

  @Override
  public ImmutableSetMultimap<String, String> getDestinationDescription() {
    return new ImmutableSetMultimap.Builder<String, String>()
        .put("url", destination)
        .putAll("ref", refspec.stream().map(Refspec::toString).collect(Collectors.toList()))
        .build();
  }

  @Override
  public ConfigFile<?> getMainConfigFile() {
    return mainConfigFile;
  }

  @Override
  public String getName() {
    return name;
  }


  @Override
  public String getModeString() {
    return MODE_STRING;
  }

}
