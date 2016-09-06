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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Preconditions;
import com.google.copybara.GeneralOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * A Skylark dependency resolver that resolves relative paths and absolute paths if
 * {@code rootPath} is defined.
 */
public class PathBasedConfigFile extends ConfigFile<Path> {

  private static final Logger logger = Logger.getLogger(PathBasedConfigFile.class.getName());

  private final Path path;
  @Nullable
  private final Path rootPath;
  private final boolean logFileContent;

  public PathBasedConfigFile(Path path, @Nullable Path rootPath) {
    this(path, rootPath, /*logFileContent=*/false);
  }

  private PathBasedConfigFile(Path path, @Nullable Path rootPath, boolean logFileContent) {
    super(path.toString());
    Preconditions.checkArgument(path.isAbsolute());
    this.path = path;
    this.rootPath = rootPath;
    this.logFileContent = logFileContent;
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

  public PathBasedConfigFile withContentLogging() {
    return new PathBasedConfigFile(path, rootPath, /*logFileContent=*/true);
  }

  @Override
  public byte[] content() throws IOException {
    byte[] bytes = Files.readAllBytes(path);
    if (logFileContent) {
      logger.info(String.format("Content of '%s':\n%s", path, new String(bytes, UTF_8)));
    }
    return bytes;
  }
}
