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

package com.google.copybara.treestate;

import static com.google.copybara.treestate.TreeStateUtil.filter;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link TreeState} that uses a cached version of the filesystem for doing
 * lookups.
 */
public class MapBasedTreeState implements TreeState {

  private boolean notified = false;
  private final Path checkoutDir;
  private final Map<Path, FileState> files;

  private final LoadingCache<PathMatcher, List<FileState>> cachedMatches =
      CacheBuilder.newBuilder().maximumSize(10).build(
          new CacheLoader<PathMatcher, List<FileState>>() {
            @Override
            public List<FileState> load(PathMatcher pathMatcher) throws Exception {
              return filter(pathMatcher, files.values());
            }
          });

  MapBasedTreeState(Path checkoutDir, Map<Path, FileState> files,
      LoadingCache<PathMatcher, List<FileState>> cachedMatches) {
    this.checkoutDir = checkoutDir;
    this.files = new HashMap<>(files);
    this.cachedMatches.putAll(cachedMatches.asMap());
  }

  @Override
  public Iterable<FileState> find(PathMatcher pathMatcher) throws IOException {
    return cachedMatches.getUnchecked(pathMatcher);
  }

  @Override
  public void notifyModify(Iterable<FileState> paths) {
    notified = true;
    for (FileState fileState : paths) {
      files.put(fileState.getPath(), fileState);
    }
  }

  @Override
  public void notifyAdd(Iterable<FileState> path) {
    throw new UnsupportedOperationException("Not supported. Don't notify!");
  }

  @Override
  public void notifyDelete(Iterable<FileState> path) {
    throw new UnsupportedOperationException("Not supported. Don't notify!");
  }

  @Override
  public void notifyNoChange() {
    notified = true;
  }

  @Override
  public TreeState newTreeState() {
    if (notified) {
      return new MapBasedTreeState(checkoutDir, files, cachedMatches);
    }
    return new FileSystemTreeState(checkoutDir);
  }
}
