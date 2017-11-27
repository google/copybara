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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Preconditions;
import com.google.copybara.CannotResolveRevisionException;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.RepoException;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Arguments for git.mirror migration.
 */
@Parameters(separators = "=")
public class GitMirrorOptions implements Option {

  private final GitOptions gitOptions;
  private final Supplier<GeneralOptions> generalOptionsSupplier;

  public GitMirrorOptions(Supplier<GeneralOptions> generalOptionsSupplier, GitOptions gitOptions) {
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
    this.generalOptionsSupplier = Preconditions.checkNotNull(generalOptionsSupplier);
  }

  @Parameter(names = "--git-mirror-force",
      description = "Force push even if it is not fast-forward")
  boolean forcePush = false;

  public void mirror(String origin, String destination, List<Refspec> refspec, boolean prune)
      throws RepoException, CannotResolveRevisionException {
    GitRepository repo = gitOptions.cachedBareRepoForUrl(origin);
    List<String> fetchRefspecs = refspec.stream()
        .map(r -> r.originToOrigin().toString())
        .collect(Collectors.toList());

    generalOptionsSupplier.get().console().progressFmt("Fetching from %s", origin);

    repo.fetch(origin, /*prune=*/true, /*force=*/true, fetchRefspecs);

    generalOptionsSupplier.get().console().progressFmt("Pushing to %s", destination);
    List<Refspec> pushRefspecs = forcePush
        ? refspec.stream().map(Refspec::withAllowNoFastForward).collect(Collectors.toList())
        : refspec;
    repo.push().prune(prune).withRefspecs(destination, pushRefspecs).run();
  }
}
