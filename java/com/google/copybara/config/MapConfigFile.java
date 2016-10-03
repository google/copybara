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

import com.google.common.collect.ImmutableMap;
import java.io.IOException;

/** A Config file implementation that uses a map for storing the internal data structure.
 *
 * <p>Assumes all paths to be absolute.
 */
public class MapConfigFile extends ConfigFile<String> {

  private final ImmutableMap<String, byte[]> configFiles;
  private final String current;

  public MapConfigFile(ImmutableMap<String, byte[]> configFiles, String current) {
    super(current);
    this.configFiles = configFiles;
    this.current = current;
  }

  @Override
  protected String relativeToRoot(String label) throws CannotResolveLabel {
    return containsLabel(label);
  }

  @Override
  protected String relativeToCurrentPath(String label) throws CannotResolveLabel {
    int i = current.lastIndexOf("/");
    String resolved = i == -1 ? label : current.substring(0, i) + "/" + label;
    return containsLabel(resolved);
  }

  private String containsLabel(String resolved) throws CannotResolveLabel {
    if (!configFiles.containsKey(resolved)) {
      throw new CannotResolveLabel(
          String.format("Cannot resolve '%s': does not exist.", resolved));
    }
    return resolved;
  }


  @Override
  protected ConfigFile createConfigFile(String label, String resolved)
      throws CannotResolveLabel {
    if (!configFiles.containsKey(resolved)) {
      throw new CannotResolveLabel(
          String.format("Cannot resolve '%s': '%s' does not exist.", label, resolved));
    }
    return new MapConfigFile(configFiles, resolved);
  }

  @Override
  public byte[] content() throws IOException {
    return configFiles.get(current);
  }
}
