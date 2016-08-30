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
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
      Preconditions.checkArgument(!glob.isEmpty(), "unexpected empty string in glob list");
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

  /**
   * Calculates a list of paths which recursively contain all files that could possibly match a file
   * in this glob. Generally, this returns the include paths but removing all segments that have a
   * metacharacter and following segments.
   *
   * <p>This can be used by an {@code Origin} or {@code Destination} implementation to determine
   * which directories to query from the repo. For instance, if the <em>include</em> paths are:
   *
   * <ul>
   *  <li>foo/bar.jar
   *  <li>foo/baz/**
   * </ul>
   *
   * This function will return a single string: {@code "foo"}.
   *
   * <p>If the include paths potentially include files in the root directory or use metacharacters
   * to specify the top level directory, a list with only the empty string is returned. For
   * instance, the following include globs will cause {@code [""]} (and no other string) to be
   * returned:
   *
   * <ul>
   *  <li>{@code *.java}
   *  <li>{@code {foo,bar}/baz/**}
   * <ul>
   *
   * <p>Note that in the case of {@code origin_files} or {@code destination_files}, the origin or
   * destination may give special meaning to the roots of the glob. For instance, the destination
   * may store metadata at {root}/INFO for every root. See the documentation of the destination or
   * origin you are using for more information.
   */
  public ImmutableList<String> roots() {
    List<String> roots = new ArrayList<>();

    for (String includePath : this.include) {
      ArrayDeque<String> components = new ArrayDeque<>();
      for (String component : Splitter.on('/').split(includePath)) {
        components.add(unescape(component));
        if (isMeta(component)) {
          break;
        }
      }
      components.removeLast();
      if (components.isEmpty()) {
        return ImmutableList.of("");
      }
      roots.add(Joiner.on('/').join(components));
    }

    // Remove redundant roots - e.g. "foo" covers all paths that start with "foo/"
    Collections.sort(roots);
    int r = 0;
    while (r < roots.size() - 1) {
      if (roots.get(r + 1).startsWith(roots.get(r) + "/")) {
        roots.remove(r + 1);
      } else {
        r++;
      }
    }

    return ImmutableList.copyOf(roots);
  }

  private String unescape(String pathComponent) {
    return pathComponent.replaceAll("\\\\(.)", "$1");
  }

  private boolean isMeta(String pathComponent) {
    int c = 0;
    while (c < pathComponent.length()) {
      switch (pathComponent.charAt(c)) {
        case '*':
        case '{':
        case '[':
        case '?':
          return true;
        case '\\':
          c++;
          break;
      }
      c++;
    }
    return false;
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
