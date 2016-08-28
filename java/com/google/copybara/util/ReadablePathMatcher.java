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

package com.google.copybara.util;

import java.nio.file.Path;
import java.nio.file.PathMatcher;

/**
 * A path matcher which delegates to another path matcher but has a specifiable {@link #toString()}
 * value.
 */
public final class ReadablePathMatcher implements PathMatcher {
  private final PathMatcher delegate;
  private final String toString;

  public ReadablePathMatcher(PathMatcher delegate, String toString) {
    this.delegate = delegate;
    this.toString = toString;
  }

  @Override
  public boolean matches(Path path) {
    return delegate.matches(path);
  }

  @Override
  public String toString() {
    return toString;
  }

  /**
   * Creates a {@link PathMatcher} based on a glob relative to {@code path}. The string
   * representation of the {@code PathMatcher} is the actual glob.
   *
   * For example a glob "dir/**.java" would match any java file inside {@code path}/dir directory.
   */
  public static ReadablePathMatcher relativeGlob(Path path, String glob) {
    FileUtil.checkNormalizedRelative(glob);
    return new ReadablePathMatcher(
        path.getFileSystem().getPathMatcher("glob:" + path.resolve(glob)), glob);
  }
}
