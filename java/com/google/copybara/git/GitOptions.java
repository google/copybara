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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.copybara.Option;
import javax.annotation.Nullable;

/**
 * Common arguments for {@link GitDestination}, {@link GitOrigin}, and other Git components.
 */
@Parameters(separators = "=")
public final class GitOptions implements Option {

  public static final String GIT_REPO_STORAGE = "--git-repo-storage";

  // Not used by git.destination but it will be at some point to make fetches more efficient.
  @Parameter(names = GIT_REPO_STORAGE,
      description = "Location of the storage path for git repositories")
  String repoStorage;

  public GitOptions(@Nullable String homeDir) {
    this.repoStorage = homeDir == null
        ? null
        : homeDir + "/copybara/repos";
  }

  public String getRepoStorage() {
    // Hack: I don't want to throw checked exception as it requires refactoring some code and
    // this will go away really soon
    checkNotNull(repoStorage,
        "Repo storage is null. Most likely your $HOME environment var is not set. "
            + "You can try to set: %s", GIT_REPO_STORAGE);
    return repoStorage;
  }
}
