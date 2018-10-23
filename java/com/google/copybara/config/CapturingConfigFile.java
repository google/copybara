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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.exception.CannotResolveLabel;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A config file that records the children created from it. Useful for collecting dependencies in
 * dry runs.
 */
class CapturingConfigFile implements ConfigFile {
  private final Set<CapturingConfigFile> children = new LinkedHashSet<>();
  private final ConfigFile wrapped;

  CapturingConfigFile(ConfigFile config) {
    this.wrapped = Preconditions.checkNotNull(config);
  }

  @Override
  public ConfigFile resolve(String path) throws CannotResolveLabel {
    CapturingConfigFile resolved = new CapturingConfigFile(wrapped.resolve(path));
    children.add(resolved);
    return resolved;
  }

  @Override
  public String path() {
    return wrapped.path();
  }

  @Override
  public byte[] readContentBytes() throws IOException, CannotResolveLabel {
    return wrapped.readContentBytes();
  }

  @Override
  public String getIdentifier() {
    return wrapped.getIdentifier();
  }

  /**
   * Retrieve collected dependencies.
   * @return A Map mapping the path to the wrapped ConfigFile for each ConfigFile created by this or
   *     one of its descendants. Includes this.
   */
  ImmutableMap<String, ConfigFile> getAllLoadedFiles() {
    Map<String, ConfigFile> map = new HashMap<>();
    getAllLoadedFiles(map);
    return ImmutableMap.copyOf(map);
  }

  private void getAllLoadedFiles(Map<String, ConfigFile> map) {
    map.put(path(), this.wrapped);
    for (CapturingConfigFile child : children) {
      child.getAllLoadedFiles(map);
    }
  }

  @Override
  public boolean equals(Object otherObject) {
    if (otherObject instanceof CapturingConfigFile) {
      CapturingConfigFile other = (CapturingConfigFile) otherObject;
      return other.wrapped.equals(this.wrapped) && this.children.equals(other.children);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return this.path().hashCode();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("children", children)
        .add("wrapped", wrapped)
        .toString();
  }
}
