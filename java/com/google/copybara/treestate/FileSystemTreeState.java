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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link TreeState} imlementation that uses the {@code checkoutDir} filesystem for
 * looking for files.
 */
public class FileSystemTreeState implements TreeState {

  private final Path checkoutDir;
  private boolean fsRead = false;
  private boolean notified;
  private Map<Path, FileState> files = new HashMap<>();

  private final LoadingCache<PathMatcher, List<FileState>> cachedMatches =
      CacheBuilder.newBuilder().maximumSize(5).build(
          new CacheLoader<PathMatcher, List<FileState>>() {
            @Override
            public List<FileState> load(PathMatcher pathMatcher) throws Exception {
              return filter(pathMatcher, FileSystemTreeState.this.files.values());
            }
          });

  public FileSystemTreeState(Path checkoutDir) {
    this.checkoutDir = checkoutDir;
  }

  @Override
  public Iterable<FileState> find(PathMatcher pathMatcher) throws IOException {
    if (!fsRead) {
      files = readFileSystem();
      fsRead = true;
    }
    return cachedMatches.getUnchecked(pathMatcher);
  }

  private Map<Path, FileState> readFileSystem() throws IOException {
    Map<Path, FileState> result = new HashMap<>();
    Files.walkFileTree(checkoutDir, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
          throws IOException {
        result.put(file, new FileState(file));
        return FileVisitResult.CONTINUE;
      }
    });
    return result;
  }

  @Override
  public void notifyModify(Iterable<FileState> paths) {
    notified = true;
    for (FileState path : paths) {
      files.put(path.getPath(), path);
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
    if (fsRead && notified) {
      return new MapBasedTreeState(checkoutDir, files, cachedMatches);
    }
    return new FileSystemTreeState(checkoutDir);
  }
}
