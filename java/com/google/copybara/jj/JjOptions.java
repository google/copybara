/*
 * Copyright (C) 2026 Google LLC.
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

package com.google.copybara.jj;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Preconditions;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.exception.RepoException;
import com.google.copybara.util.console.Console;
import java.nio.file.Path;

/** Common arguments and repository initialization factory for Jujutsu ({@code jj}) components. */
@Parameters(separators = "=")
public class JjOptions implements Option {

  private static final String JJ_REPO_SUBDIR = "jj_repo";

  private final GeneralOptions generalOptions;

  @Parameter(names = "--jj-bin", description = "Path to the jj binary")
  public String jjBin = "jj";

  @Parameter(
      names = "--jj-committer-name",
      description =
          "If set, overrides the committer name for the generated commits in the jj repository.")
  public String committerName = "";

  @Parameter(
      names = "--jj-committer-email",
      description =
          "If set, overrides the committer e-mail for the generated commits in the jj repository.")
  public String committerEmail = "";

  public JjOptions(GeneralOptions generalOptions) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
  }

  /** Creates and initializes a local {@link JjRepository} in a subdirectory of {@code workdir}. */
  public JjRepository createRepo(Path workdir, Console console) throws RepoException {
    Preconditions.checkNotNull(workdir);
    Preconditions.checkNotNull(console);

    Path repoDir = workdir.resolve(JJ_REPO_SUBDIR);
    console.verboseFmt("Initializing jj repository at %s", repoDir);
    JjRepository repo = newRepo(repoDir);
    initRepo(repo);
    return repo;
  }

  /** Instantiates an uninitialized {@link JjRepository} rooted at {@code repoDir}. */
  private JjRepository newRepo(Path repoDir) {
    return new JjRepository(
        repoDir,
        jjBin,
        generalOptions.getEnvironment(),
        generalOptions.isVerbose(),
        generalOptions.repoTimeout);
  }

  /**
   * Initializes {@code repo} on disk as a non-colocated jj repository and applies the configured
   * committer identity.
   */
  private void initRepo(JjRepository repo) throws RepoException {
    repo.init();
    if (!committerName.isEmpty()) {
      repo.simpleCommand("config", "set", "--repo", "user.name", committerName);
    }
    if (!committerEmail.isEmpty()) {
      repo.simpleCommand("config", "set", "--repo", "user.email", committerEmail);
    }
  }
}
