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

import static java.lang.Math.max;
import static java.lang.Math.min;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.HasBinary;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkValue;
import net.starlark.java.syntax.TokenKind;

/**
 * A {@link PathMatcher} builder that creates a PathMatcher relative to a {@link Path}.
 *
 * <p>The returned {@link PathMatcher} returns true if any of the {@code paths} expressions match.
 * If {@code paths} is empty it will no match any file.
 */
@StarlarkBuiltin(
    name = "glob",
    doc = "A glob represents a set of relative filepaths in the Copybara workdir.")
public class Glob implements StarlarkValue, HasBinary {

  public static final Glob ALL_FILES = createGlob(ImmutableList.of("**"));

  private final ImmutableList<GlobAtom> include;
  private final ImmutableList<Glob> globInclude;
  @Nullable private final Glob exclude;

  Glob(Iterable<GlobAtom> include, Iterable<Glob> globInclude, @Nullable Glob exclude) {
    this.include = ImmutableList.copyOf(Preconditions.checkNotNull(include));
    this.globInclude = ImmutableList.copyOf(Preconditions.checkNotNull(globInclude));
    this.exclude = exclude;
  }

  @Override
  public final Glob binaryOp(TokenKind op, Object that, boolean thisLeft) throws EvalException {
    switch (op) {
      case PLUS:
        if (that instanceof Glob) {
          return union(this, (Glob) that);
        } else {
          throw Starlark.errorf(
              "Cannot concatenate %s with %s. Only a glob can be concatenated to a glob",
              this, that);
        }
      case MINUS:
        if (that instanceof Glob) {
          return difference(this, (Glob) that);
        } else {
          throw Starlark.errorf(
              "Cannot subtract %s from %s. Only a glob can be subtracted from a glob", that, this);
        }
      default:
        throw Starlark.errorf("Glob does not support %s", op);
    }
  }

  /**
   * Compute the 'set union' of two Globs, which is a Glob that will match any Path matched by at
   * least one of those two Globs.
   *
   * <p>Try to keep the resulting Glob as flat as possible. In the worst case, this will increase
   * the depth of each leaf (i.e. {@code GlobAtom}) by 1, but we can often do better.
   */
  private static Glob union(Glob glob1, Glob glob2) {
    if (Objects.equals(glob1.exclude, glob2.exclude)) {
      return new Glob(
          Iterables.concat(glob1.include, glob2.include),
          Iterables.concat(glob1.globInclude, glob2.globInclude),
          glob1.exclude);
    } else if (glob1.exclude == null) {
      return new Glob(
          glob1.include, Iterables.concat(glob1.globInclude, ImmutableList.of(glob2)), null);
    } else if (glob2.exclude == null) {
      return new Glob(
          glob2.include, Iterables.concat(glob2.globInclude, ImmutableList.of(glob1)), null);
    }
    return new Glob(ImmutableList.of(), ImmutableList.of(glob1, glob2), null);
  }

  /**
   * Compute the 'set difference' of two Globs, which is a Glob that will match any Path which is
   * matched by the first Glob, but not matched by the second Glob.
   */
  private static Glob difference(Glob glob1, Glob glob2) {
    if (glob1.exclude == null) {
      return new Glob(glob1.include, glob1.globInclude, glob2);
    }
    return new Glob(glob1.include, glob1.globInclude, Glob.union(glob1.exclude, glob2));
  }

  /** Checks if the given {@code changedFiles} are or are descendants of the {@code roots}. */
  public static boolean affectsRoots(
      ImmutableSet<String> roots, ImmutableCollection<String> changedFiles) {
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

  public PathMatcher relativeTo(Path path) {
    ImmutableList.Builder<PathMatcher> includeList = ImmutableList.builder();
    for (GlobAtom path1 : include) {
      includeList.add(path1.matcher(path));
    }
    for (Glob g : globInclude) {
      includeList.add(g.relativeTo(path));
    }
    PathMatcher excludeMatcher =
        (exclude == null) ? FileUtil.anyPathMatcher(ImmutableList.of()) : exclude.relativeTo(path);
    return new GlobPathMatcher(FileUtil.anyPathMatcher(includeList.build()), excludeMatcher);
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

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof GlobPathMatcher)) {
        return false;
      }
      GlobPathMatcher that = (GlobPathMatcher) o;
      return Objects.equals(this.includeMatcher, that.includeMatcher)
          && Objects.equals(this.excludeMatcher, that.excludeMatcher);
    }

    @Override
    public int hashCode() {
      return Objects.hash(includeMatcher, excludeMatcher);
    }
  }

  /**
   * Creates a function {@link Glob} that when a {@link Path} is passed it returns a {@link
   * PathMatcher} relative to the path.
   *
   * @param include list of strings representing the globs to include/match
   * @param exclude list of strings representing the globs to exclude from the include set
   * @throws IllegalArgumentException if any glob is not valid
   */
  public static Glob createGlob(Iterable<String> include, Iterable<String> exclude) {
    return new Glob(
        ImmutableList.copyOf(GlobAtom.ofIterable(include, GlobAtom.AtomType.JAVA_GLOB)),
        ImmutableList.of(),
        Iterables.isEmpty(exclude) ? null : createGlob(exclude));
  }

  /**
   * Creates a function {@link Glob} that when a {@link Path} is passed it returns a {@link
   * PathMatcher} relative to the path.
   *
   * @param include list of strings representing the globs to include/match
   * @throws IllegalArgumentException if any glob is not valid
   */
  public static Glob createGlob(Iterable<String> include) {
    return createGlob(include, ImmutableList.of());
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
   *   <li>foo/bar.jar
   *   <li>foo/baz/**
   * </ul>
   *
   * This function will return a single string: {@code "foo"}.
   *
   * <p>If the include paths potentially include files in the root directory or use metacharacters
   * to specify the top level directory, a set with only the empty string is returned. For instance,
   * the following include globs will cause {@code [""]} (and no other string) to be returned:
   *
   * <ul>
   *   <li>{@code *.java}
   *   <li>{@code {foo,bar}/baz/**}
   * </ul>
   *
   * <p>Note that in the case of {@code origin_files} or {@code destination_files}, the origin or
   * destination may give special meaning to the roots of the glob. For instance, the destination
   * may store metadata at {root}/INFO for every root. See the documentation of the destination or
   * origin you are using for more information.
   */
  public ImmutableSet<String> roots() {
    return roots(false);
  }

  /**
   * If {@code allowFiles} is set to true, then Paths containing no meta characters are retained
   * exactly as they are - for example, {@code foo/bar.txt} is output unmodified and not shortened
   * to {@code foo}.
   */
  public ImmutableSet<String> roots(boolean allowFiles) {
    return computeRootsFromIncludes(getIncludes(), allowFiles);
  }

  /**
   * If roots is empty or contains a single elemnent that is not a subdirectory. See {@link
   * #roots()} for detail.
   */
  public static boolean isEmptyRoot(Iterable<String> roots) {
    return Iterables.isEmpty(roots) || Objects.equals(roots.iterator().next(), "");
  }

  protected Iterable<GlobAtom> getIncludes() {
    return Iterables.concat(
        include, Iterables.concat(Iterables.transform(globInclude, Glob::getIncludes)));
  }

  private static ImmutableSet<String> computeRootsFromIncludes(
      Iterable<GlobAtom> includes, boolean allowFiles) {
    List<String> roots = new ArrayList<>();

    for (GlobAtom atom : includes) {
      roots.add(atom.root(allowFiles));
    }

    // Remove redundant roots - e.g. "foo" covers all paths that start with "foo/"
    Collections.sort(roots, Glob::compareRoots);
    if (roots.contains("")) {
      return ImmutableSet.of("");
    }
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

  /** A lexicographical String comparator that sorts the '/' char before any other char. */
  private static int compareRoots(String s1, String s2) {
    int len1 = s1.length();
    int len2 = s2.length();
    int lim = min(len1, len2);
    for (int k = 0; k < lim; k++) {
      int c1 = s1.charAt(k);
      c1 = c1 == '/' ? -1 : c1;
      int c2 = s2.charAt(k);
      c2 = c2 == '/' ? -1 : c2;
      if (c1 != c2) {
        return c1 - c2;
      }
    }
    return len1 - len2;
  }

  @Override
  public String toString() {
    return toStringWithParentheses(true);
  }

  private String toStringWithParentheses(boolean isRootGlob) {
    StringBuilder builder = new StringBuilder();
    int numberOfTerms = 0;
    boolean inlineExclude =
        this.globInclude.isEmpty()
            && this.exclude != null
            && this.exclude.globInclude.isEmpty()
            && this.exclude.exclude == null;
    if (!include.isEmpty() || this.globInclude.isEmpty()) {
      builder
          .append("glob(include = ")
          .append(toStringList(include))
          .append(inlineExclude ? ", exclude = " + toStringList(this.exclude.include) : "")
          .append(")");
      numberOfTerms += 1;
    }
    for (Glob g : this.globInclude) {
      if (numberOfTerms > 0) {
        builder.append(" + ");
      }
      builder.append(g.toStringWithParentheses(false));
      numberOfTerms += 1;
    }
    if (this.exclude != null && !inlineExclude) {
      builder.append(" - ").append(this.exclude.toStringWithParentheses(false));
      numberOfTerms += 1;
    }

    if (!isRootGlob && numberOfTerms > 1) {
      return "(" + builder + ")";
    }
    return builder.toString();
  }

  private String toStringList(Iterable<GlobAtom> iterable) {
    StringBuilder sb = new StringBuilder("[");
    boolean first = true;
    for (GlobAtom atom : iterable) {
      if (first) {
        first = false;
      } else {
        sb.append(", ");
      }
      sb.append('"').append(sanitize(atom.toString())).append('"');
    }
    return sb.append("]").toString();
  }

  private String sanitize(String s) {
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace("\f", "\\f")
        .replace("\b", "\\b")
        .replace("\000", "\\000");
  }

  @Override
  public void repr(Printer printer) {
    printer.append(toString());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Glob)) {
      return false;
    }
    Glob that = (Glob) o;
    return Objects.equals(include, that.include)
        && Objects.equals(globInclude, that.globInclude)
        && Objects.equals(exclude, that.exclude);
  }

  @Override
  public int hashCode() {
    return Objects.hash(include, globInclude, exclude);
  }

  @VisibleForTesting
  public int heightOfGlobTree() {
    int includeHeight = this.globInclude.stream().mapToInt(Glob::heightOfGlobTree).max().orElse(-1);
    int excludeHeight = this.exclude == null ? -1 : this.exclude.heightOfGlobTree();
    return 1 + max(includeHeight, excludeHeight);
  }
}
