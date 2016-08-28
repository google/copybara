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

package com.google.copybara.config;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A Skylark dependency resolver that only resolves relative paths to the current passed resolved
 * name.
 */
public class SimpleConfigFile implements ConfigFile {

  private final Path path;

  public SimpleConfigFile(Path path) {
    Preconditions.checkArgument(path.isAbsolute());
    this.path = path;
  }

  @Override
  public ConfigFile resolve(String label) throws CannotResolveLabel {
    Path child = this.path.getFileSystem().getPath(label);
    if (child.isAbsolute() || !child.equals(child.normalize())) {
      throw new CannotResolveLabel(
          "Only includes of files in the same directory or subdirectories is allowed. No '..' are allowed: "
              + label);
    }
    Path resolved = path.resolveSibling(child);
    if (!Files.exists(resolved)) {
      throw new CannotResolveLabel(
          String.format("Cannot find '%s'. '%s' does not exist.", label, resolved));
    }
    if (!Files.isRegularFile(resolved)) {
      throw new CannotResolveLabel(
          String.format("Cannot find '%s'. '%s' is not a file.", label, resolved));
    }
    return new SimpleConfigFile(resolved);
  }

  @Override
  public String path() {
    return path.toString();
  }

  @Override
  public byte[] content() throws IOException {
    return Files.readAllBytes(path);
  }
}
