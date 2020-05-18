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
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.HasBinary;
import com.google.devtools.build.lib.syntax.Starlark;
import com.google.devtools.build.lib.syntax.StarlarkValue;
import com.google.devtools.build.lib.syntax.TokenKind;
import com.google.re2j.Pattern;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkDocumentationCategory;

/**
 * A {@link PathMatcher} builder that creates a PathMatcher relative to a {@link Path}.
 *
 * <p>The returned {@link PathMatcher} returns true if any of the {@code paths} expressions match.
 * If {@code paths} is empty it will no match any file.
 */
@StarlarkBuiltin(
    name = "glob",
    doc =
        "Glob returns a list of every file in the workdir that matches at least one"
            + " pattern in include and does not match any of the patterns in exclude.",
    category = StarlarkDocumentationCategory.BUILTIN,
    documented = false)
public abstract class Glob implements StarlarkValue, HasBinary {

  private static final Pattern UNESCAPE = Pattern.compile("\\\\(.)");

  public static final Glob ALL_FILES = createGlob(ImmutableList.of("**"));

  @Override
  public final UnionGlob binaryOp(TokenKind op, Object that, boolean thisLeft)
      throws EvalException {
    if (op == TokenKind.PLUS && that instanceof Glob) {
      return new UnionGlob(this, (Glob) that);
    }
    throw Starlark.errorf(
        "Cannot concatenate %s with %s. Only a glob can be concatenated to a glob", this, that);
  };

  /**
   * Checks if the given {@code changedFiles} are or are descendants of the {@code roots}.
   */
  public static boolean affectsRoots(ImmutableSet<String> roots,
      ImmutableCollection<String> changedFiles) {
    if (changedFiles == null || isEmptyRoot(roots)) {
      return true;
    }
    // This is O(changes * files * roots) in the worse case. roots shouldn't be big and
    // files shouldn't be big for 99% of the changes.
    for (String file : changedFiles) {
      for (String root : roots) {
        if (file.equals(root) || file.startsWith(root + "/")) {
          return true;
        }
      }
    }
    return false;
  }

  public abstract PathMatcher relativeTo(Path path);

  /**
   * Creates a function {@link Glob} that when a {@link Path} is passed it returns a
   * {@link PathMatcher} relative to the path.
   *
   * @param include list of strings representing the globs to include/match
   * @param exclude list of strings representing the globs to exclude from the include set
   * @throws IllegalArgumentException if any glob is not valid
   */
  public static Glob createGlob(Iterable<String> include, Iterable<String> exclude) {
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
  public static Glob createGlob(Iterable<String> include) {
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
  public abstract ImmutableSet<String> roots();

  /**
   * If roots is empty or contains a single elemnent that is not a subdirectory. See
   * {@link #roots()} for detail.
   */
  public static boolean isEmptyRoot(Iterable<String> roots) {
    return Iterables.isEmpty(roots) || Objects.equals(roots.iterator().next(), "");
  }

  protected abstract Iterable<String> getIncludes();

  static ImmutableSet<String> computeRootsFromIncludes(Iterable<String> includes) {
    List<String> roots = new ArrayList<>();

    for (String includePath : includes) {
      ArrayDeque<String> components = new ArrayDeque<>();
      for (String component : Splitter.on('/').split(includePath)) {
        components.add(unescape(component));
        if (isMeta(component)) {
          break;
        }
      }
      components.removeLast();
      if (components.isEmpty()) {
        return ImmutableSet.of("");
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

    return ImmutableSet.copyOf(roots);
  }

  private static String unescape(String pathComponent) {
    return UNESCAPE.matcher(pathComponent).replaceAll("$1");
  }

  private static boolean isMeta(String pathComponent) {
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
        default: // fall out
      }
      c++;
    }
    return false;
  }

}
