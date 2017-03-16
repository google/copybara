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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

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
public interface Glob {

  Glob ALL_FILES = createGlob(ImmutableList.of("**"));

  PathMatcher relativeTo(Path path);

  /**
   * Creates a function {@link Glob} that when a {@link Path} is passed it returns a
   * {@link PathMatcher} relative to the path.
   *
   * @param include list of strings representing the globs to include/match
   * @param exclude list of strings representing the globs to exclude from the include set
   * @throws IllegalArgumentException if any glob is not valid
   */
  static Glob createGlob(Iterable<String> include, Iterable<String> exclude) {
    return new SimpleGlob(ImmutableList.copyOf(include),
                          Iterables.isEmpty(exclude) ? null : createGlob(exclude));
  }

  /**
   * Creates a function {@link Glob} that when a {@link Path} is passed it returns a
   * {@link PathMatcher} relative to the path.
   *
   * @param include list of strings representing the globs to include/match
   * @throws IllegalArgumentException if any glob is not valid
   */
  static Glob createGlob(Iterable<String> include) {
    return new SimpleGlob(include, null);
  }

  /**
   * Calculates a set of paths which recursively contain all files that could possibly match a file
   * in this glob. Generally, this returns the include paths but removing all segments that have a
   * metacharacter and following segments.
   *
   * <p>This can be used by an {@code Origin} or {@code Destination} implementation to determine
   * which directories to query from the repo. For instance, if the <em>include</em> paths are:
   *
   * <ul>
   * <li>foo/bar.jar
   * <li>foo/baz/**
   * </ul>
   *
   * This function will return a single string: {@code "foo"}.
   *
   * <p>If the include paths potentially include files in the root directory or use metacharacters
   * to specify the top level directory, a set with only the empty string is returned. For
   * instance, the following include globs will cause {@code [""]} (and no other string) to be
   * returned:
   *
   * <ul>
   * <li>{@code *.java}
   * <li>{@code {foo,bar}/baz/**}
   * <ul>
   *
   * <p>Note that in the case of {@code origin_files} or {@code destination_files}, the origin or
   * destination may give special meaning to the roots of the glob. For instance, the destination
   * may store metadata at {root}/INFO for every root. See the documentation of the destination or
   * origin you are using for more information.
   */
  ImmutableSet<String> roots();
}
