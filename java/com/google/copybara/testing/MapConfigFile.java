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

package com.google.copybara.testing;

import com.google.common.collect.ImmutableMap;
import com.google.copybara.config.CannotResolveLabel;
import com.google.copybara.config.ConfigFile;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

/**
 * A Config file implementation that uses a map for storing the internal data structure.
 */
public class MapConfigFile implements ConfigFile {

  private final ImmutableMap<String, byte[]> configFiles;
  private final String current;

  public MapConfigFile(ImmutableMap<String, byte[]> configFiles, String current) {
    this.configFiles = configFiles;
    this.current = current;
  }

  @Override
  public ConfigFile resolve(String label) throws CannotResolveLabel {
    FileSystem fs = FileSystems.getDefault();
    Path currentAsPath = fs.getPath(current);
    Path child = fs.getPath(label);
    if (child.isAbsolute() || !child.equals(child.normalize())) {
      throw new CannotResolveLabel(
          "Only includes of files in the same directory or subdirectories is allowed. No '..' are allowed: "
              + label);
    }
    String resolved = currentAsPath.resolveSibling(child).toString();
    if (!configFiles.containsKey(resolved)) {
      throw new CannotResolveLabel(
          String.format("Cannot find '%s'. '%s' does not exist.", label, resolved));
    }
    return new MapConfigFile(configFiles, resolved);
  }

  @Override
  public String path() {
    return current;
  }

  @Override
  public byte[] content() throws IOException {
    return configFiles.get(current);
  }
}
