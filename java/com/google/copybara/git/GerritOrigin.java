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

import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A {@link Origin} that can read Gerrit reviews
 * TODO(malcon): Implement Reader/getChanges to detect already migrated patchets
 */
public class GerritOrigin extends GitOrigin{

  private GerritOrigin(GeneralOptions generalOptions,
      String repoUrl, @Nullable String configRef,
      GitRepoType repoType, GitOptions gitOptions, GitOriginOptions gitOriginOptions,
      boolean verbose, @Nullable Map<String, String> environment,
      SubmoduleStrategy submoduleStrategy, boolean includeBranchCommitLogs) {
    super(generalOptions, repoUrl, configRef, repoType, gitOptions, gitOriginOptions,
        verbose, environment, submoduleStrategy, includeBranchCommitLogs);
  }

  /**
   * Builds a new {@link GerritOrigin}.
   */
  static GerritOrigin newGerritOrigin(Options options, String url, GitRepoType type,
      SubmoduleStrategy submoduleStrategy) {

    boolean verbose = options.get(GeneralOptions.class).isVerbose();
    Map<String, String> environment = options.get(GeneralOptions.class).getEnvironment();

    return new GerritOrigin(
        options.get(GeneralOptions.class),
        url, /*ref=*/null, type, options.get(GitOptions.class),
        options.get(GitOriginOptions.class), verbose, environment,
        submoduleStrategy, /*includeBranchCommitLogs=*/false);
  }
}
