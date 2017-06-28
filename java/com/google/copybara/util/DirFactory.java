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
package com.google.copybara.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A supplier of output directories under a given root.
 *
 * <p>This factory allows Copybara to create all the files in a self-contained root, that can be
 * configured by users, and allows for temporary file cleanup, and directory reuse if necessary.
 *
 * <p>Can be configured to reuse output dirs, or create always new directories for the same name.
 */
public class DirFactory {
  @VisibleForTesting
  public static final String OUTPUT = "output";

  private final Path rootPath;

  public DirFactory(Path rootPath) {
    this.rootPath = Preconditions.checkNotNull(rootPath);
  }

  /** Creates a temp directory in the root path. */
  public Path newTempOutputDirectory(String name) throws IOException {
    Path outputPath = rootPath.resolve(OUTPUT);
    // Create the output if it doesn't exist.
    Files.createDirectories(outputPath);
    return Files.createTempDirectory(outputPath, name);
  }

  public void cleanupOutputDir() throws IOException {
    Path outputPath = rootPath.resolve(OUTPUT);
    if (Files.exists(outputPath)) {
        FileUtil.deleteRecursively(outputPath);
    }
  }
}
