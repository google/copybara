// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.util;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.copybara.config.ConfigValidationException;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import java.nio.file.FileSystems;
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
public final class PathMatcherBuilder {

  private final ImmutableList<String> include;
  private final ImmutableList<String> exclude;

  /**
   * Creates a function {@link PathMatcherBuilder} that when a {@link Path} is passed it returns a
   * {@link PathMatcher} relative to the path.
   *
   * @param include list of strings representing the globs to include/match
   * @param exclude list of strings representing the globs to exclude from the include set
   *
   * @throws IllegalArgumentException if any glob is not valid
   */
  public PathMatcherBuilder(Iterable<String> include, Iterable<String> exclude) {
    this.include = ImmutableList.copyOf(include);
    this.exclude = ImmutableList.copyOf(exclude);

    // Validate the paths so that they don't contain invalid patterns.
    for (String glob : Iterables.concat(include, exclude)) {
      FileUtil.checkNormalizedRelative(glob);
      FileSystems.getDefault().getPathMatcher("glob:" + glob);
    }
  }

  /** Generates matchers that do not match any paths (i.e. return {@code false} for all paths). */
  public static final PathMatcherBuilder EMPTY =
      new PathMatcherBuilder(ImmutableList.<String>of(), ImmutableList.<String>of());

  public static final PathMatcherBuilder ALL_FILES =
      new PathMatcherBuilder(ImmutableList.of("**"), ImmutableList.<String>of());

  public PathMatcher relativeTo(Path path) {
    Preconditions.checkNotNull((Iterable<String>) include, "include argument cannot be null");
    Preconditions.checkNotNull((Iterable<String>) exclude, "exclude argument cannot be null");
    ImmutableList.Builder<PathMatcher> includeList = ImmutableList.builder();
    for (String path1 : include) {
      includeList.add(ReadablePathMatcher.relativeGlob(path, path1));
    }
    ImmutableList.Builder<PathMatcher> excludeList = ImmutableList.builder();
    for (String path1 : exclude) {
      excludeList.add(ReadablePathMatcher.relativeGlob(path, path1));
    }

    return new GlobPathMatcher(
        FileUtil.anyPathMatcher(includeList.build()),
        FileUtil.anyPathMatcher(excludeList.build()));
  }

  public boolean isEmpty() {
    return include.isEmpty();
  }

  @Override
  public String toString() {
    return "glob(include=[" + Joiner.on(", ").join(include) + "], "
        + "exclude=[" + Joiner.on(", ").join(exclude) + "])";
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
      return PathMatcherBuilder.this.toString();
    }
  }
}
