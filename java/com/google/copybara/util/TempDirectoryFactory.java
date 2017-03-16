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

/**
 * A supplier of temporary directories under a given root.
 *
 * <p>Creates the intermediate directories lazily in the first temporary directory creation.
 */
public class TempDirectoryFactory {
  private final Path rootPath;

  public TempDirectoryFactory(Path rootPath) {
    this.rootPath = Preconditions.checkNotNull(rootPath);
  }

  public Path newTempDirectory(String prefix) throws IOException {
    try {
      Files.createDirectories(rootPath);
      return Files.createTempDirectory(rootPath, prefix);
    } catch (IOException e) {
      throw new IOException(String.format("Could not create temporary folder in %s", rootPath), e);
    }
  }
}
