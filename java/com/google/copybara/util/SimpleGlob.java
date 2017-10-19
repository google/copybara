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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.skylarkinterface.SkylarkPrinter;
import com.google.devtools.build.lib.skylarkinterface.SkylarkValue;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A Glob implementation that supports having a list of include globs and that we can
 * then apply a list of excludes on that list.
 */
final class SimpleGlob extends Glob implements SkylarkValue {

  private final ImmutableList<String> include;
  @Nullable
  private final Glob exclude;

  SimpleGlob(Iterable<String> include, @Nullable Glob exclude) {
    this.include = ImmutableList.copyOf(include);
    this.exclude = exclude;

    // Validate the paths so that they don't contain invalid patterns.
    for (String glob : include) {
      Preconditions.checkArgument(!glob.isEmpty(), "unexpected empty string in glob list");
      FileUtil.checkNormalizedRelative(glob);
      FileSystems.getDefault().getPathMatcher("glob:" + glob);
    }
  }

  @Override
  public PathMatcher relativeTo(Path path) {
    Builder<PathMatcher> includeList = ImmutableList.builder();
    for (String path1 : include) {
      includeList.add(ReadablePathMatcher.relativeGlob(path, path1));
    }
    PathMatcher excludeMatcher = (exclude == null)
        ? FileUtil.anyPathMatcher(ImmutableList.of())
        : exclude.relativeTo(path);
    return new GlobPathMatcher(
        FileUtil.anyPathMatcher(includeList.build()),
        excludeMatcher);
  }

  @Override
  public ImmutableSet<String> roots() {
    return computeRootsFromIncludes(this.include);
  }

  @Override
  protected Iterable<String> getIncludes() {
    return include;
  }

  @Override
  public String toString() {
    return "glob(include = " + toStringList(include)
        + (exclude == null
        ? ""
        // TODO(malcon): Correct but messy. We should accept a list of excludes in the constructor
        : ", exclude = " + toStringList(exclude.getIncludes())) + ")";
  }

  private String toStringList(Iterable<String> iterable) {
    StringBuilder sb = new StringBuilder("[");
    boolean first = true;
    for (String s : iterable) {
      if (first) {
        first = false;
      } else {
        sb.append(", ");
      }
      sb.append('"').append(sanitize(s)).append('"');
    }
    return sb.append("]").toString();
  }

  private String sanitize(String s) {
    return s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace("\f", "\\f")
        .replace("\b", "\\b")
        .replace("\000", "\\000");
  }

  @Override
  public void repr(SkylarkPrinter printer) {
    printer.append(toString());
  }

  private class GlobPathMatcher implements PathMatcher {

    private final PathMatcher includeMatcher;
    private final PathMatcher excludeMatcher;

    GlobPathMatcher(PathMatcher includeMatcher, PathMatcher excludeMatcher) {
      this.includeMatcher = includeMatcher;
      this.excludeMatcher = excludeMatcher;
    }

    @Override
    public boolean matches(Path path) {
      return includeMatcher.matches(path) && !excludeMatcher.matches(path);
    }

    @Override
    public String toString() {
      return SimpleGlob.this.toString();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SimpleGlob that = (SimpleGlob) o;
    return Objects.equals(include, that.include)
        && Objects.equals(exclude, that.exclude);
  }

  @Override
  public int hashCode() {
    return Objects.hash(include, exclude);
  }
}
