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

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * A supplier of output directories under a given root.
 *
 * <p>This factory allows Copybara to create all the files in a self-contained root, that can be
 * configured by users, and allows for temporary file cleanup, and directory reuse if necessary.
 *
 * <p>Can be configured to reuse output dirs, or create always new directories for the same name.
 */
public class OutputDirFactory {

  private final Path rootPath;
  private final boolean reuseOutputDirs;

  public OutputDirFactory(Path rootPath, boolean reuseOutputDirs) {
    this.rootPath = Preconditions.checkNotNull(rootPath);
    this.reuseOutputDirs = reuseOutputDirs;
  }

  /**
   * Provides an output directory with the given name. If the directory already exists, files will
   * be removed.
   *
   * <p>Creates the intermediate directories lazily, if necessary.
   */
  public Path newDirectory(String name) throws IOException {
    try {
      Files.createDirectories(rootPath);
      if (!reuseOutputDirs) {
        return Files.createTempDirectory(rootPath, name);
      }
      Path outputDir = rootPath.resolve(name);
      if (Files.exists(outputDir)) {
        FileUtil.deleteRecursively(outputDir);
      }
      return Files.createDirectory(outputDir);
    } catch (IOException e) {
      throw new IOException(String.format("Could not create output directory in %s", rootPath), e);
    }
  }

}
