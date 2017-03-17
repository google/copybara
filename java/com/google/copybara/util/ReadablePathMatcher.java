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

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Objects;

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

    FileSystem fs = path.getFileSystem();
    String root = path.normalize().toString();
    String separator = fs.getSeparator();

    if (!root.endsWith(separator)) {
      root += separator;
    }

    // If the current filesystem uses a backslash as the separator, the root must be escaped
    // first to be valid glob syntax since backslash is considered an escaping character.
    if ("\\".equals(separator)) {
      root = root.replace("\\", "\\\\");
      glob = glob.replace("/", "\\\\");
    }

    return new ReadablePathMatcher(fs.getPathMatcher("glob:" + root + glob), glob);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ReadablePathMatcher that = (ReadablePathMatcher) o;
    // Don't use the delegate as toString is unique.
    return Objects.equals(toString, that.toString);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(toString);
  }
}
