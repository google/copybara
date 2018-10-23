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

package com.google.copybara.transform.patch;

import static com.google.copybara.GeneralOptions.OUTPUT_ROOT_FLAG;

import com.google.common.collect.ImmutableList;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.InsideGitDirException;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Transformation for applying patch file during a workflow. Instantiated by {@link PatchModule}.
 */
public class PatchTransformation implements Transformation {

  private final ImmutableList<ConfigFile> patches;
  private final ImmutableList<String> excludedPaths;
  private final boolean reverse;
  private final PatchingOptions options;

  private static final int SLASHES_TO_STRIP = 1;


  PatchTransformation(
      ImmutableList<ConfigFile> patches, ImmutableList<String> excludedPaths,
      PatchingOptions options, boolean reverse) {
    this.patches = patches;
    this.excludedPaths = excludedPaths;
    this.reverse = reverse;
    this.options = options;
  }

  @Override
  public void transform(TransformWork work) throws ValidationException, IOException {
    try {
      patch(work.getConsole(), work.getCheckoutDir(), /*gitDir=*/null);
    } catch (InsideGitDirException e) {
      throw new ValidationException(
          "Cannot use patch.apply because Copybara temporary directory (%s) is inside a git"
              + " directory (%s). Please remove the git repository or use %s flag.",
          e.getPath(), e.getGitDirPath(), OUTPUT_ROOT_FLAG);
    }
  }

  public void patch(Console console, Path checkoutDir, @Nullable Path gitDir)
      throws ValidationException, InsideGitDirException {
    try {
    for (int i = 0; i < patches.size(); i++) {
      ConfigFile patch = patches.get(i);
      console.infoFmt("Applying patch %d/%d: '%s'.", i + 1, patches.size(), patch.path());
      options.patch(
          checkoutDir, patch.readContentBytes(), excludedPaths, SLASHES_TO_STRIP, reverse, gitDir);
    }
    } catch (IOException ioException) {
      console.errorFmt("Error applying patch: %s", ioException.getMessage());
      throw new ValidationException(ioException, "Error applying patch.");
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
