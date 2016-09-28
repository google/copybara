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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Migration;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Mirror one or more refspects between git repositories.
 */
public class Mirror implements Migration {

  private final GeneralOptions generalOptions;
  private final GitOptions gitOptions;
  private final String origin;
  private final String destination;
  private final List<Refspec> refspec;
  private final boolean forcePush;

  Mirror(GeneralOptions generalOptions, GitOptions gitOptions, String origin,
      String destination,
      List<Refspec> refspec, boolean forcePush) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
    this.origin = Preconditions.checkNotNull(origin);
    this.destination = Preconditions.checkNotNull(destination);
    this.refspec = Preconditions.checkNotNull(refspec);
    this.forcePush = forcePush;
  }

  @Override
  public void run(Path workdir, @Nullable String sourceRef)
      throws RepoException, IOException, ValidationException {
    GitRepository repo = GitOrigin.bareRepoInCache(origin, generalOptions.getEnvironment(),
        generalOptions.isVerbose(), gitOptions.repoStorage);
    repo.initGitDir();
    List<String> fetchRefspecs = refspec.stream()
        .map(r -> r.getOrigin() + ":" + r.getOrigin())
        .collect(Collectors.toList());

    generalOptions.console().progress("Fetching from " + origin);
    repo.simpleCommand(Iterables.toArray(Iterables.concat(
        ImmutableList.of("fetch", origin), fetchRefspecs), String.class));

    List<String> pushRefspecs = refspec.stream()
        .map(r ->
            // Add '+' if we can force the push + origin/local repo refspec location + ':'
            // + remote refspec location.
            // For example in 'refs/foo:refs/bar' refspec with force push we would use
            // '+refs/foo:refs/bar'
            (r.isAllowNoFastForward() || forcePush ? "+" : "")
                + r.getOrigin() + ":" + r.getDestination())
        .collect(Collectors.toList());

    generalOptions.console().progress("Pushing to " + destination);
    repo.simpleCommand(Iterables.toArray(Iterables.concat(
        ImmutableList.of("push", destination), pushRefspecs), String.class));
  }

}
