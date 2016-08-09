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
import com.google.copybara.GeneralOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * A Skylark dependency resolver that resolves relative paths and absolute paths if
 * {@code rootPath} is defined.
 */
public class PathBasedConfigFile extends ConfigFile<Path> {

  private final Path path;
  @Nullable
  private final Path rootPath;

  public PathBasedConfigFile(Path path, @Nullable Path rootPath) {
    super(path.toString());
    this.rootPath = rootPath;
    Preconditions.checkArgument(path.isAbsolute());
    this.path = path;
  }

  @Override
  protected Path relativeToRoot(String label) throws CannotResolveLabel {
    if (rootPath == null) {
      throw new CannotResolveLabel("Absolute paths are not allowed because the root config path"
          + " couldn't be automatically detected. Use " + GeneralOptions.CONFIG_ROOT_FLAG);
    }
    return rootPath.resolve(label);
  }

  @Override
  protected Path relativeToCurrentPath(String label) throws CannotResolveLabel {
    return this.path.resolveSibling(label);
  }

  @Override
  protected ConfigFile createConfigFile(String label, Path resolved)
      throws CannotResolveLabel {
    if (!Files.exists(resolved)) {
      throw new CannotResolveLabel(
          String.format("Cannot find '%s'. '%s' does not exist.", label, resolved));
    }
    if (!Files.isRegularFile(resolved)) {
      throw new CannotResolveLabel(
          String.format("Cannot find '%s'. '%s' is not a file.", label, resolved));
    }
    return new PathBasedConfigFile(resolved, rootPath);
  }

  @Override
  public byte[] content() throws IOException {
    return Files.readAllBytes(path);
  }
}
