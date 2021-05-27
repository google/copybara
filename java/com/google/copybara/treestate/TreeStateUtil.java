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

import com.google.copybara.treestate.TreeState.FileState;
import java.nio.file.PathMatcher;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utilities for dealing with {@link TreeState objects}.
 */
public final class TreeStateUtil {

  private TreeStateUtil() {
  }

  /**
   * Filter a collection of {@link FileState}s using a {@link PathMatcher}
   */
  static List<FileState> filter(PathMatcher pathMatcher, Collection<FileState> files) {
    return files.stream().filter(
        fileState -> pathMatcher.matches(fileState.getPath()))
        .collect(Collectors.toList());
  }

}
