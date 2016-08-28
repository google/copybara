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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import javax.annotation.Nullable;

/**
 * A {@link PathMatcher} builder that creates a PathMatcher relative to a {@link Path}.
 *
 * <p>The returned {@link PathMatcher} returns true if any of the {@code paths} expressions match.
 * If {@code paths} is empty it will no match any file.
 */
@SkylarkModule(
    name = "glob",
    doc = "Glob returns a list of every file in the workdir that matches at least one"
        + " pattern in include and does not match any of the patterns in exclude.",
    category = SkylarkModuleCategory.BUILTIN)
public final class Glob {

  private final ImmutableList<String> include;
  @Nullable private final Glob exclude;

  public Glob(Iterable<String> include, @Nullable Glob exclude) {
    this.include = ImmutableList.copyOf(include);
    this.exclude = exclude;

    // Validate the paths so that they don't contain invalid patterns.
    for (String glob : include) {
      FileUtil.checkNormalizedRelative(glob);
      FileSystems.getDefault().getPathMatcher("glob:" + glob);
    }
  }

  /**
   * Creates a function {@link Glob} that when a {@link Path} is passed it returns a
   * {@link PathMatcher} relative to the path.
   *
   * @param include list of strings representing the globs to include/match
   * @param exclude list of strings representing the globs to exclude from the include set
   *
   * @throws IllegalArgumentException if any glob is not valid
   */
  public Glob(Iterable<String> include, Iterable<String> exclude) {
    this(ImmutableList.copyOf(include),
        Iterables.isEmpty(exclude) ? null : new Glob(exclude));
  }

  public Glob(Iterable<String> include) {
    this(include, (Glob) null);
  }

  public static final Glob ALL_FILES = new Glob(ImmutableList.of("**"));

  public PathMatcher relativeTo(Path path) {
    ImmutableList.Builder<PathMatcher> includeList = ImmutableList.builder();
    for (String path1 : include) {
      includeList.add(ReadablePathMatcher.relativeGlob(path, path1));
    }
    PathMatcher excludeMatcher = (exclude == null)
        ? FileUtil.anyPathMatcher(ImmutableList.<PathMatcher>of())
        : exclude.relativeTo(path);
    return new GlobPathMatcher(
        FileUtil.anyPathMatcher(includeList.build()),
        excludeMatcher);
  }

  public boolean isAllFiles() {
    return include.equals(ImmutableList.of("**")) && exclude == null;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("include", include)
        .add("exclude", exclude)
        .toString();
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
      return Glob.this.toString();
    }
  }
}
