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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

/**
 * An object that allows to do potentially cached filesystem lookups.
 *
 * <p>In particular, if a transform does lookups (using find) and then notifies the affected
 * files, the next transform gets a cached version of the TreeState.
 */
public interface TreeState {

  /**
   * An object that contains a path found in the {@link TreeState}.
   *
   * <p>Wrapped so that we can include things like the hash of the file in the future.
   */
  class FileState {
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
      if (o == null || getClass() != o.getClass()) {
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

  /**
   * Find a a set of files in the checkout dir, using a {@link PathMatcher}.
   */
  Iterable<FileState> find(PathMatcher pathMatcher) throws IOException;

  /**
   * Notify the {@link TreeState} that {@code paths} have been modified.
   */
  void notifyModify(Iterable<FileState> paths);

  /**
   * Not implemented for now.
   */
  void notifyAdd(Iterable<FileState> path);

  /**
   * Not implemented for now.
   */
  void notifyDelete(Iterable<FileState> path);

  void notifyNoChange();

  /**
   * Returns a new {@link TreeState}. Iff find was invoked, and then any of the nofity* methods
   * where invoked, it will return a cached version of the TreeState. Otherwise it returns a
   * FileSystem based TreeState.
   */
  TreeState newTreeState();
}
