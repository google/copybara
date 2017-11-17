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

package com.google.copybara.modules;

import static com.google.copybara.GeneralOptions.OUTPUT_ROOT_FLAG;

import com.google.common.collect.ImmutableList;
import com.google.copybara.GeneralOptions;
import com.google.copybara.RepoException;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.ValidationException;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.git.GitRepository;
import com.google.copybara.util.DiffUtil;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Transformation for applying patch file during a workflow. Instantiated by {@link PatchModule}.
 */
class PatchTransformation implements Transformation {

  private final ImmutableList<ConfigFile<?>> patches;
  private final ImmutableList<String> excludedPaths;
  private final boolean reverse;
  private final GeneralOptions options;

  private static final int SLASHES_TO_STRIP = 1;


  PatchTransformation(
      ImmutableList<ConfigFile<?>> patches, ImmutableList<String> excludedPaths,
      GeneralOptions options, boolean reverse) {
    this.patches = patches;
    this.excludedPaths = excludedPaths;
    this.reverse = reverse;
    this.options = options;
  }

  @Override
  public void transform(TransformWork work) throws ValidationException, IOException {
    checkNotInsideGitRepo(work);

    for (int i = 0; i < patches.size(); i++) {
      ConfigFile<?> patch = patches.get(i);
      work.getConsole().info(
          String.format("Applying patch %d/%d: '%s'.", i + 1, patches.size(), patch.path()));
      try {
        DiffUtil.patch(
            work.getCheckoutDir(), patch.content(), excludedPaths, SLASHES_TO_STRIP,
            options.isVerbose(), reverse, options.getEnvironment());
      } catch (IOException ioException) {
        work.getConsole().error("Error applying patch: " + ioException.getMessage());
        throw new ValidationException("Error applying patch.", ioException);
      }
    }
  }

  /**
   * It is very common for users to have a git repo for their $HOME, so that they can version
   * their configurations. Unfortunately this fails for the default output directory (inside
   * $HOME).
   */
  private void checkNotInsideGitRepo(TransformWork work) throws IOException, ValidationException {
    Optional<String> gitDir;
    try {
      gitDir = GitRepository.revParse(work.getCheckoutDir(), options.getEnvironment(),
          options.isVerbose());
    } catch (RepoException e) {
      // We don't want RepoExceptions outside of repos.
      throw new IOException(e);
    }
    if (gitDir.isPresent()) {
      throw new ValidationException(String.format(
          "Cannot use patch.apply because Copybara temporary directory (%s) is inside a git"
              + " directory (%s). Please remove the git repository or use %s flag.",
          work.getCheckoutDir(), gitDir.get(), OUTPUT_ROOT_FLAG));
    }
  }

  @Override
  public Transformation reverse() {
    return new PatchTransformation(patches.reverse(), excludedPaths, options, !reverse);
  }

  @Override
  public String describe() {
    return
        "Patch.apply: " + patches.stream().map(ConfigFile::path).collect(Collectors.joining(", "));
  }
}
