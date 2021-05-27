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

import com.google.common.base.Preconditions;
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
 * An object that allows to do potentially cached filesystem lookups.
 *
 * <p>In particular, if a transform does lookups (using find) and then notifies the affected files,
 * the next transform gets a cached version of the TreeState.
 */
public class TreeState {

  /**
   * An object that contains a path found in the {@link TreeState}.
   *
   * <p>Wrapped so that we can include things like the hash of the file in the future.
   */
  public static class FileState {
    private final Path path;

    FileState(Path path) {
      this.path = Preconditions.checkNotNull(path);
    }

    public Path getPath() {
      return path;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof FileState)) {
        return false;
      }
      FileState fileState = (FileState) o;
      return path.equals(fileState.path);
    }

    @Override
    public int hashCode() {
      return path.hashCode();
    }

    @Override
    public String toString() {
      return path.toString();
    }
  }

  private final Path checkoutDir;
  private boolean isCached = false;
  private boolean notified = false;
  private Map<Path, FileState> files = new HashMap<>();

  private final LoadingCache<PathMatcher, List<FileState>> cachedMatches =
      CacheBuilder.newBuilder()
          .maximumSize(10)
          .build(
              new CacheLoader<PathMatcher, List<FileState>>() {
                @Override
                public List<FileState> load(PathMatcher pathMatcher) {
                  return filter(pathMatcher, files.values());
                }
              });

  public TreeState(Path checkoutDir) {
    this.checkoutDir = checkoutDir;
  }

  /** Find a a set of files in the checkout dir, using a {@link PathMatcher}. */
  public Iterable<FileState> find(PathMatcher pathMatcher) throws IOException {
    if (!isCached) {
      files = readFileSystem();
      isCached = true;
    }
    return cachedMatches.getUnchecked(pathMatcher);
  }

  private Map<Path, FileState> readFileSystem() throws IOException {
    Map<Path, FileState> result = new HashMap<>();
    Files.walkFileTree(
        checkoutDir,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            result.put(file, new FileState(file));
            return FileVisitResult.CONTINUE;
          }
        });
    return result;
  }

  /** Notify the {@link TreeState} that {@code paths} have been modified. */
  public void notifyModify(Iterable<FileState> paths) {
    notified = true;
    for (FileState path : paths) {
      files.put(path.getPath(), path);
    }
  }

  /** Not implemented for now. */
  public void notifyAdd(Iterable<FileState> path) {
    throw new UnsupportedOperationException("Not supported. Don't notify!");
  }

  /** Not implemented for now. */
  public void notifyDelete(Iterable<FileState> path) {
    throw new UnsupportedOperationException("Not supported. Don't notify!");
  }

  public void notifyNoChange() {
    notified = true;
  }

  public boolean isCached() {
    return isCached;
  }

  public void clearCache() {
    isCached = false;
    files = new HashMap<>();
    cachedMatches.invalidateAll();
    notified = false;
  }

  /**
   * If any of the notify* methods were invoked, it will retain the cached version of the TreeState.
   * Otherwise it clears the cache.
   *
   * <p>This method is called in between every pair of Transformations. Unless the previous
   * Transformation calls one of the notify* methods to indicate which files it has touched, we must
   * assume that the cache may be stale.
   */
  public void maybeClearCache() {
    if (!notified) {
      clearCache();
    }
    notified = false;
    return;
  }
}
